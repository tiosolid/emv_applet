/**
 * 
 */
package smart;

import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.ISO7816;
import javacard.framework.APDU;
import javacard.framework.Util;
import javacard.framework.JCSystem;

/**
 * @author EMV
 * 
 */

public class Emv extends Applet {
	// Constants for the Supported CLAs (afaik, 00 and 80 are a must)
	final static byte EMV_CLA = (byte) 0x80;

	// Constants for supported INS (ISO7816)
	// Bare minimum
	final static byte INS_VERIFY = (byte) 0x20; // EMV
	final static byte INS_PUT_DATA = (byte) 0xDA;
	final static byte INS_GET_DATA = (byte) 0xCA; // EMV
	final static byte INS_SELECT_FILE = (byte) 0xA4;
	final static byte INS_READ_RECORD = (byte) 0xB2; // EMV
	final static byte INS_WRITE_RECORD = (byte) 0xD2;
	final static byte INS_APPEND_RECORD = (byte) 0xE2;
	final static byte INS_PIN_UNBLOCK = (byte) 0x24; // EMV

	// Must be supported according to a commercial EMV applet, not sure for
	// current application
	final static byte INS_EXTERNAL_AUTH = (byte) 0x82; // EMV
	final static byte INS_INTERNAL_AUTH = (byte) 0x88; // EMV
	final static byte INS_GENERATE_AC = (byte) 0xAE; // Page 57 - EMV Book
	final static byte INS_GET_CHALLENGE = (byte) 0x84; // Page 60 - EMV Book
	final static byte INS_GET_PROC_OPTIONS = (byte) 0xA8; // Page 63 - EMV Book

	// Custom Instructions
	// TODO Add support to an INS to block ATC / LOATC incrementing?
	final static byte INS_UPDATE_AC = (byte) 0x72;
	final static byte INS_DISABLE_AC_REP = (byte) 0x74;
	final static byte INS_CLEAR_LOG = (byte) 0x76;
	final static byte INS_DISABLE_IAD_REP = (byte) 0x78;

	/* codes for cryptogram types used in P1 */
	final static byte ARQC_CODE = (byte) 0x80;
	final static byte TC_CODE = (byte) 0x40;
	final static byte AAC_CODE = (byte) 0x00;
	final static byte RFU_CODE = (byte) 0xC0;

	/* types of AC */
	final static byte NONE = (byte) 0x00;
	final static byte ARQC = (byte) 0x01;
	final static byte TC = (byte) 0x02;
	final static byte AAC = (byte) 0x03;

	final Pin pin;
	final ProtocolState protocolState;
	final FileSystem fileSystem;
	final Crypto crypto;
	final Log log;

	private final byte[] response;

	private Emv() {
		pin = new Pin();
		protocolState = new ProtocolState();
		fileSystem = new FileSystem();
		crypto = new Crypto(this);
		log = new Log();

		response = JCSystem.makeTransientByteArray((short) 256,
				JCSystem.CLEAR_ON_DESELECT);
	}

	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// GP-complaint JavaCard applet registration
		new Emv().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
	}

	public void process(APDU apdu) {
		byte[] buf = apdu.getBuffer();

		// Good practice: Return 9000 on SELECT
		if (selectingApplet()) {
			log.write(buf); // Log applet selection
			// When selecting the application, returns it description (FCI)
			Util.arrayCopyNonAtomic(FileSystem.fci, (byte) 0, buf,
					ISO7816.OFFSET_CDATA, (short) FileSystem.fci.length);
			apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA,
					(short) FileSystem.fci.length);
			return;
		}

		if (buf[ISO7816.OFFSET_INS] != INS_SELECT_FILE) {
			log.write(buf);
		} // for now only log non select commands to make the log smaller

		// Switch the INS parameter from the APDU
		switch (buf[ISO7816.OFFSET_INS]) {
		case INS_APPEND_RECORD:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			break;
		case INS_EXTERNAL_AUTH:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			break;
		case INS_GENERATE_AC:
			// get remaining data
			short len = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
			if (len != apdu.setIncomingAndReceive()) {
				ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
			}
			// check for request of CDA signature
			if ((buf[ISO7816.OFFSET_P1] & 0x10) == 0x10) {
				// CDA signature requested, which we don't support (yet)
				ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
			}
			if (protocolState.getFirstACGenerated() == NONE) {
				generateFirstAC(apdu, buf);
			} else if (protocolState.getSecondACGenerated() == NONE) {
				generateSecondAC(apdu, buf);
			} else
				// trying to generate a third AC
				ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			break;
		case INS_GET_CHALLENGE:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			break;
		case INS_GET_DATA:
			getData(apdu);
			break;
		case INS_GET_PROC_OPTIONS:
			getProcessingOptions(apdu);
			break;
		case INS_INTERNAL_AUTH:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			break;
		case INS_PIN_UNBLOCK:
			pin.update(apdu);
			break;
		case INS_PUT_DATA:
			putData(apdu);
			break;
		case INS_READ_RECORD:
			FileSystem.readRecord(apdu);
			break;
		case INS_SELECT_FILE:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			// FileSystem.selectFile();
			break;
		case INS_VERIFY:
			pin.verify(apdu);
			break;
		case INS_WRITE_RECORD:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
			break;
		case INS_DISABLE_AC_REP:
			crypto.disableAcReplication();
			break;
		case INS_DISABLE_IAD_REP:
			crypto.disableIadReplication();
		case INS_CLEAR_LOG:
			log.clear();
			break;
		case (byte) 0x00:
			break;
		default:
			// good practice: If you don't know the INStruction, say so:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}

	/*
	 * Process the PUT DATA APDU (INS=DA) PUT DATA is used to store primitive
	 * data (like values) can be used, for example, to store a new pin retry
	 * count (if it could be altered via APDUs)
	 */
	public void putData(APDU apdu) {
		/*
		 * buf[OFFSET_P1..OFFSET_P2] should contains of the following tags 9F36
		 * - ATC 9F13 - Last online ATC 9F4F - Log Format 9F70 - AC to be
		 * replicated 9F74 - IAD to be replicated See: EMV BOOK - Page 61
		 */
		byte[] buf = apdu.getBuffer();

		if (buf[ISO7816.OFFSET_P1] == (byte) 0x9F) {
			// TODO: Check data length (cant be smaller than 2 bytes)
			switch (buf[ISO7816.OFFSET_P2]) {
			case 0x36: // ATC
				protocolState.setATC(Util.makeShort(buf[ISO7816.OFFSET_CDATA],
						buf[ISO7816.OFFSET_CDATA + 1]));
				apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
				break;
			case 0x13: // Last online ATC
				protocolState.setLastOnlineATC(Util.makeShort(
						buf[ISO7816.OFFSET_CDATA],
						buf[ISO7816.OFFSET_CDATA + 1]));
				apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
				break;
			case 0x70: // AC Replication
				crypto.setAc(apdu);
				break;
			case 0x74: // IAD replication
				setIad(apdu);
				break;
			case 0x4F: // Log Format - not supported yet
			default:
				ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
				break;
			}
		}
	}

	/*
	 * Process the GET DATA APDU (CLA=80 INS=CA) GET DATA is used to read
	 * primitive data (like the PIN try counter value)
	 */
	private void getData(APDU apdu) {
		/*
		 * buf[OFFSET_P1..OFFSET_P2] should contains of the following tags 9F36
		 * - ATC 9F17 - PIN Try Counter 9F13 - Last online ATC 9F4F - Log Format
		 * 9F72 - Log Data See: EMV BOOK - Page 61
		 */
		byte[] buf = apdu.getBuffer();

		if (buf[ISO7816.OFFSET_P1] == (byte) 0x9F) {
			buf[0] = (byte) 0x9F;
			buf[1] = buf[ISO7816.OFFSET_P2];
			switch (buf[ISO7816.OFFSET_P2]) {
			// The buf[ISO7816.OFFSET_P1,ISO7816.OFFSET_P2] already contains the
			// right Tag,
			// so we can write the Length and Value to the next bytes in the buf
			// and then send this.
			case 0x36: // ATC
				buf[ISO7816.OFFSET_P2 + 1] = (byte) 0x02; // length 2 bytes
				Util.setShort(buf, (short) (ISO7816.OFFSET_P2 + 2),
						protocolState.getATC()); // value
				// send the 5 byte long TLV for ATC
				apdu.setOutgoingAndSend(ISO7816.OFFSET_P1, (short) 5);
				break;

			case 0x17: // PIN Try Counter
				buf[ISO7816.OFFSET_P2 + 1] = (byte) 0x01; // length 1 byte
				buf[ISO7816.OFFSET_P2 + 2] = pin.getTriesRemaining(); // value
				// send the 4 byte TLV for PIN Try counter
				apdu.setOutgoingAndSend(ISO7816.OFFSET_P1, (short) 4);
				break;

			case 0x13: // Last online ATC
				buf[ISO7816.OFFSET_P2 + 1] = (byte) 0x02; // length 2 bytes
				Util.setShort(buf, (short) (ISO7816.OFFSET_P2 + 2),
						protocolState.getLastOnlineATC()); // value
				// send the 5 byte long TLV for last online ATC
				apdu.setOutgoingAndSend(ISO7816.OFFSET_P1, (short) 5);
				break;
			case 0x72: // Log Data
				apdu.setOutgoing();
				apdu.setOutgoingLength(log.getSize());
				apdu.sendBytesLong(log.read(), (short) 0, log.getSize());
				break;
			case 0x4F: // Log Format - not supported yet
			default:
				ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
				break;
			}
		} else {
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}
	}

	/*
	 * Process the GET PROCESSING OPTIONS APDU (CLA=80 INS=A8) returns the
	 * Application Interchange Profile (AIP) and the Application File Locator
	 * (AFL) See Page 63 - EMV BOOK
	 */
	public void getProcessingOptions(APDU apdu) {
		byte[] buf = apdu.getBuffer();
		short readCount;
		readCount = apdu.setIncomingAndReceive();
		Util.arrayCopyNonAtomic(FileSystem.aip_afl, (byte) 0, buf,
				ISO7816.OFFSET_CDATA, (short) FileSystem.aip_afl.length);
		apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA,
				(short) FileSystem.aip_afl.length);
	}

	public void generateFirstAC(APDU apdu, byte[] apduBuffer) {
		// First 2 bits of P1 specify the type
		// These bits also have to be returned, as the Cryptogram Information
		// Data (CID);
		// See Book 3, Annex C6.5.5.4
		byte cid = (byte) (apduBuffer[ISO7816.OFFSET_P1] & 0xC0);
		if (cid == RFU_CODE || cid == AAC_CODE) {
			// not a request for TC or ARQC
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}

		crypto.generateFirstACReponse(cid, apduBuffer,
				fileSystem.getCDOL1DataLength(), null, (short) 0, response,
				(short) 0);
		protocolState.setFirstACGenerated(cid);

		apdu.setOutgoing();
		apdu.setOutgoingLength((short) (response[1] + 2));
		apdu.sendBytesLong(response, (short) 0, (short) (response[1] + 2));
	}

	public void generateSecondAC(APDU apdu, byte[] apduBuffer) {
		// First 2 bits of P1 specify the type
		// These bits also have to be returned, as the Cryptogram Information
		// Data (CID);
		// See Book 3, Sect 6.5.5.4 of the Common Core Definitions.
		byte cid = (byte) (apduBuffer[ISO7816.OFFSET_P1] & 0xC0);
		if (cid == RFU_CODE || cid == ARQC_CODE) {
			// not a request for TC or AAC
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		}

		crypto.generateSecondACReponse(cid, apduBuffer,
				fileSystem.getCDOL2DataLength(), null, (short) 0, response,
				(short) 0);
		protocolState.setSecondACGenerated(cid);

		apdu.setOutgoing();
		apdu.setOutgoingLength((short) (response[1] + 2));
		apdu.sendBytesLong(response, (short) 0, (short) (response[1] + 2));
	}

	public void setIad(APDU apdu) {
		byte[] buf = apdu.getBuffer();
		if (buf[ISO7816.OFFSET_LC] > (byte) 0x12) { // Max IAD size is 18 bytes
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}

		crypto.setIad(buf);
		apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
	}
}