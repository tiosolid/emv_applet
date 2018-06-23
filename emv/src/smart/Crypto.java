package smart;

import javacard.framework.ISO7816;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.DESKey;
import javacard.security.KeyBuilder;
import javacard.security.Signature;
import javacardx.crypto.Cipher;
import javacard.framework.APDU;

public class Crypto {

	/* Reference back to the applet that uses this EMVCrypto object */
	private final Emv theApplet;

	private final byte[] sessionkey;

	/** 3DESKey ICC Master Key, shared with the bank */
	private final DESKey mk;

	private final Cipher desCipher;
	private final Signature desMAC;

	/** 3DESKey session keys, derived from Master Key mk */
	private final DESKey sk;

	/**
	 * Scratchpad transient byte array for diversification data used to build
	 * session key
	 */
	private final byte[] diversification_data;

	/** Transient byte array for storing ac transaction_data */
	byte[] transaction_data;

	// Used to store an injected ac for replication
	private final byte[] replicated_ac;
	private boolean replicateAc;

	// Used to store an injected IAD for replication
	private final byte[] replicated_iad;
	private boolean replicate_iad;
	private short replicated_iad_length;

	public Crypto(Emv x) {
		theApplet = x; // reference back to the applet

		diversification_data = JCSystem.makeTransientByteArray((short) 8,
				JCSystem.CLEAR_ON_DESELECT);
		sessionkey = JCSystem.makeTransientByteArray((short) 16,
				JCSystem.CLEAR_ON_DESELECT);
		transaction_data = JCSystem.makeTransientByteArray((short) 256,
				JCSystem.CLEAR_ON_DESELECT);

		desCipher = Cipher.getInstance(Cipher.ALG_DES_CBC_ISO9797_M2, false);
		desMAC = Signature
				.getInstance(Signature.ALG_DES_MAC8_ISO9797_M2, false);

		mk = (DESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_DES,
				KeyBuilder.LENGTH_DES3_2KEY, false);
		mk.setKey(new byte[] { 0x11, 0x0C, 0x1D, 0x02, 0x03, 0x04, 0x0C,
				(byte) 0xDA, (byte) 0xFA, (byte) 0xCA, 0x04, 0x11, 0x12, 0x14,
				(byte) 0xCA, 0x16 }, (short) 0);
		sk = (DESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_DES,
				KeyBuilder.LENGTH_DES3_2KEY, false);

		replicated_ac = new byte[] { (byte) 0x4D, (byte) 0xE1, (byte) 0x4B,
				(byte) 0xFC, (byte) 0x2F, (byte) 0x73, (byte) 0xBA, (byte) 0xC4 };
		replicateAc = true;

		replicated_iad = new byte[] { (byte) 0x02, (byte) 0x10, (byte) 0xA0,
				(byte) 0x00, (byte) 0x03, (byte) 0x22, (byte) 0x00,
				(byte) 0x00, (byte) 0x27, (byte) 0x11, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0xFF };
		replicate_iad = true;
		replicated_iad_length = (short) 18;
	}

	/*
	 * Sets the current 3DES session key, based on the Application Transaction
	 * Counter (ATC).
	 * 
	 * It is done as described in Book 2, Annex A1.3.1, by encrypting ATC || F0
	 * || 00 || 00 || 00 || 00 || 00 with the card's 3DES Master Key to obtain
	 * the left 8 bytes, and encrypting ATC || OF || 00 || 00 || 00 || 00 || 00
	 * with the card's 3DES Master Key to obtain the right 8 bytes.
	 */
	private void setSessionKey() {
		// as 8-byte diversification data we take the ATC followed by all zeroes
		Util.setShort(diversification_data, (short) 0,
				theApplet.protocolState.getATC());
		Util.arrayFillNonAtomic(diversification_data, (short) 2, (short) 6,
				(byte) 0);

		desCipher.init(mk, Cipher.MODE_ENCRYPT);

		// compute left 8 bytes of the session key
		diversification_data[2] = (byte) 0xF0;
		desCipher.doFinal(diversification_data, (short) 0, (short) 8,
				sessionkey, (short) 0);

		// compute right 8 byte of the session key
		diversification_data[2] = (byte) 0x0F;
		desCipher.doFinal(diversification_data, (short) 0, (short) 8,
				sessionkey, (short) 0);

		sk.setKey(sessionkey, (short) 0);
	}

	/*
	 * Computes a cryptogram, as described in Book 2, Sec 8.1, and stores it in
	 * the given response buffer at the given offset.
	 * 
	 * The cryptogram is an 8 byte MAC over data supplied by the terminal (as
	 * specified by the CDOL1 or CDOL2) and data provided by the ICC.
	 * 
	 * The data supplied by the terminal is in the ADPU buffer. This method does
	 * not need to know what this data is, ie. does not need to know the CDOLs,
	 * but only needs to know the total length of these data elements.
	 * 
	 * As data provided by the ICC this method just uses the minimum recommended
	 * set of data elements, ie the AIP and ATC (see Book 2, Sect 8.1.1), for
	 * both the first and the second AC. Hence one method can be used for both.
	 * 
	 * @requires apduBuffer != response, to avoid problems overwriting the
	 * apduBuffer??
	 * 
	 * @param cid the type of AC, ie. AAC_CODE, TC_CODE, or ARCQ_CODE
	 * 
	 * @param apduBuffer contains the terminal-supplied data to be signed in the
	 * AC
	 * 
	 * @param length length of the terminal-supplied data
	 * 
	 * @param response the destination array where the AC is stored at given
	 * offset
	 * 
	 * @param offset offset in this response array
	 */
	private void computeAC(byte cid, byte[] apduBuffer, short length,
			byte[] response, short offset) {

		// Check if replicated AC func is enabled and act accordingly
		if (replicateAc == true) {
			Util.arrayCopy(replicated_ac, (short) 0, response, offset,
					(short) 8);
		} else {
			/* Collect the data to be MAC-ed in the array transaction_data */

			// Copy CDOL from the APDU buffer, at offset 0:
			Util.arrayCopy(apduBuffer, ISO7816.OFFSET_CDATA, transaction_data,
					(short) 0, length);
			// 2 bytes AIP, at offset length:
			Util.setShort(transaction_data, length,
					theApplet.fileSystem.getAIP());
			// 2 bytes ATC, at offset length + 2:
			Util.setShort(transaction_data, (short) (length + 2),
					theApplet.protocolState.getATC());

			// TODO What is the following data?
			transaction_data[(short) (length + 4)] = (byte) 0x80;
			transaction_data[(short) (length + 5)] = (byte) 0x0;
			transaction_data[(short) (length + 6)] = (byte) 0x0;

			// MAC is a CBC-MAC computed according to ISO/IEC 9797-1, padding
			// method 2
			desMAC.init(sk, Signature.MODE_SIGN);
			desMAC.sign(transaction_data, (short) 0, (short) (length + 7),
					response, offset);
		}
	}

	public void disableAcReplication() {
		replicateAc = false;
	}

	public void setAc(APDU apdu) {
		byte[] buf = apdu.getBuffer();

		// Store the new AC to be replicated later and enable the functionality
		// TODO: Validate the new AC length before trying to save it (array out
		// of bounds)
		Util.arrayCopy(buf, (short) (ISO7816.OFFSET_CDATA), replicated_ac,
				(short) 0, (short) 8);
		replicateAc = true;

		apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
	}

	public void disableIadReplication(APDU apdu) {
		replicate_iad = false;
		apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
	}

	public void setIad(byte[] buf) {
		// Store the new IAD to be replicated later and enable the functionality
		if (buf[ISO7816.OFFSET_LC] > (byte) 0x12) {
			Util.arrayCopy(buf, (short) (ISO7816.OFFSET_CDATA), replicated_iad,
					(short) 0, (short) 18);
			replicated_iad_length = (short) 18;
		} else {
			Util.arrayCopy(buf, (short) (ISO7816.OFFSET_CDATA), replicated_iad,
					(short) 0, (short) buf[ISO7816.OFFSET_LC]);
			replicated_iad_length = (short) buf[ISO7816.OFFSET_LC];
		}

		replicate_iad = true;
	}

	public void disableIadReplication() {
		replicate_iad = false;
	}

	/*
	 * Compute the first AC response APDU using Format 1. (See Book 3, Section
	 * 6.5.5.4.) This method also sets the session key.
	 * 
	 * The response contains the - CID: Cryptogram Information Data, 1 byte long
	 * - ATC Application Transaction Counter, 2 bytes long - AC: Application
	 * Cryptogram, 8 bytes long - optionally, IAD: Issuer Application Data, 30
	 * bytes long
	 * 
	 * @param cid the type of AC, ie. AAC_CODE, TC_CODE, or ARCQ_CODE
	 * 
	 * @param apduBuffer contains the terminal-supplied data
	 * 
	 * @param length length of the terminal-supplied data
	 * 
	 * @param iad the IAD, or null, if IAD is omitted
	 * 
	 * @param response the destination array where the response is stored, at
	 * given offset
	 */
	public void generateFirstACReponse(byte cid, byte[] apduBuffer,
			short length, byte[] iad, short iad_length, byte[] response,
			short offset) {
		setSessionKey();
		generateSecondACReponse(cid, apduBuffer, length, iad, iad_length,
				response, offset);
	}

	/*
	 * Compute the second AC response APDU using Format 1. (See Book 3, Section
	 * 6.5.5.4.)
	 */
	public void generateSecondACReponse(byte cid, byte[] apduBuffer,
			short length, byte[] iad, short iad_length, byte[] response,
			short offset) {

		// ARQC with CDA - Book 3, page 57, table 11
		// if (cid == (byte) 0x90) {
		if (true) {
			response[offset] = (byte) 0x77; // Tag for Format 2 cryptogram
			response[(short) offset + 1] = (byte) 0x00; // Base Length

			// 1 byte CID, the type of AC returned - TAG 9F27 length 01
			response[(short) offset + 1] = (byte) (response[(short) offset + 1] + (byte) 4); // Base
																								// Length
			Util.setShort(response, (short) (offset + 2), (short) 0x9F27); // Tag
			response[(short) (offset + 4)] = (byte) 0x01; // Length
			response[(short) (offset + 5)] = cid; // Value

			// 2 byte ATC - Tag 9F36 length 02
			response[(short) offset + 1] = (byte) (response[(short) offset + 1] + (byte) 5); // Base
																								// Length
			Util.setShort(response, (short) (offset + 6), (short) 0x9F36); // Tag
			response[(short) (offset + 8)] = (byte) 0x02; // Length
			Util.setShort(response, (short) (offset + 9),
					theApplet.protocolState.getATC()); // Value

			// the AC itself - Tag 9F26 - length 08
			response[(short) offset + 1] = (byte) (response[(short) offset + 1] + (byte) 11); // Base
																								// Length
			Util.setShort(response, (short) (offset + 11), (short) 0x9F26); // Tag
			response[(short) (offset + 13)] = (byte) 0x08; // Length
			computeAC(cid, apduBuffer, length, response, (short) (offset + 14));

			// finally we get the IAD - 9F10 - length 18
			Util.setShort(response, (short) (offset + 22), (short) 0x9F10); // Tag
			if (iad != null) {
				response[(short) (offset + 24)] = (byte) iad.length; // Length
				Util.arrayCopy(iad, (short) 0, response, (short) (offset + 25),
						(short) iad.length);
				response[(short) offset + 1] = (byte) (response[(short) offset + 1]
						+ (byte) 0x03 + (byte) iad.length); // Base Length
			} else {
				if (replicate_iad == true) {
					// We use the IAD set for replication
					response[(short) (offset + 24)] = (byte) replicated_iad_length; // Length
					Util.arrayCopy(replicated_iad, (short) 0, response,
							(short) (offset + 25), replicated_iad_length);
					response[(short) offset + 1] = (byte) (response[(short) offset + 1]
							+ (byte) 0x03 + (byte) replicated_iad_length); // Base
																			// Length
				} else {
					// Force an IAD of 18 bytes consisting of all 0s
					response[(short) (offset + 24)] = (byte) 0x12; // Length
					Util.arrayFillNonAtomic(response, (short) (offset + 25),
							(short) 18, (byte) 0x0);
					response[(short) offset + 1] = (byte) (response[(short) offset + 1]
							+ (byte) 0x03 + (byte) 18); // Base Length
				}
			}
		}
		// ARQC without CDA (cid 80) - Book 3, page 57, table 11
		else {
			// Code for old Tag format 80 response
			response[offset] = (byte) 0x80; // Tag for Format 1 cryptogram

			if (iad == null) { // Length: 1 byte CID + 2 byte ATC + 8 byte AC =
								// 11
				response[(short) (offset + 1)] = (byte) 11;
			} else { // Length: 1 byte CID + 2 byte ATC + 8 byte AC + iad_length
						// byte IAD
				response[(short) (offset + 1)] = (byte) (11 + iad_length);
			}
			// 1 byte CID, ie the type of AC returned - TAG 9F27
			response[(short) (offset + 2)] = cid;

			// 2 byte ATC, at offset 3: - 9F36
			Util.setShort(response, (short) (offset + 3),
					theApplet.protocolState.getATC());

			// the AC itself - 9F26
			computeAC(cid, apduBuffer, length, response, (short) (offset + 5));

			// finally we get the (optional) IAD - 9F10
			if (iad != null) {
				Util.arrayCopy(iad, (short) 0, response, (short) (offset + 13),
						(short) 18);
			}

			// Force an IAD of 18 bytes consisting of all 0s - Needed for
			// EMV-CAP reader
			response[(short) (offset + 1)] = (byte) 29;
			Util.arrayFillNonAtomic(response, (short) (offset + 13),
					(short) 18, (byte) 0x0);
		}
	}
}
