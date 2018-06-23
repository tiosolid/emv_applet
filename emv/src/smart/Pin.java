package smart;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;

public class Pin {
	private final OwnerPIN pinObject;
	final static byte PIN_TRY_LIMIT = (byte) 0x03;
	final static byte PIN_SIZE = (byte) 0x02;

	public Pin() {
		pinObject = new OwnerPIN(PIN_TRY_LIMIT, PIN_SIZE);
		pinObject.update(new byte[] { (byte) 0x12, (byte) 0x34 }, (short) 0,
				(byte) 2);
	}

	public void verify(APDU apdu) {
		byte[] buf = apdu.getBuffer();

		// ALWAYS return 9000
		apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000

		if (buf[ISO7816.OFFSET_P2] != (byte) (0x80)) {
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2); // we only support
															// transaction_data
															// PIN
		}
		if (pinObject.getTriesRemaining() == 0) {
			ISOException.throwIt((short) 0x6983); // PIN blocked
			return;
		}

		/*
		 * EP: For the code below to be correct, digits in the PIN object need
		 * to be coded in the same way as in the APDU, ie. using 4 bit words.
		 */

		if (pinObject.check(buf, (short) (ISO7816.OFFSET_CDATA + 1), PIN_SIZE)) {
			// protocolState.setCVMPerformed(PLAINTEXT_PIN);
			apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
		} else {
			ISOException.throwIt((short) ((short) (0x63C0) + (short) pinObject
					.getTriesRemaining()));
		}
	}

	public void update(APDU apdu) {
		byte[] buf = apdu.getBuffer();
		if ((buf[ISO7816.OFFSET_P1] != (byte) 0x00)
				|| (buf[ISO7816.OFFSET_P2] != (byte) 0x01)) {
			ISOException.throwIt((short) 0x6A86); // '86': Incorrect parameters
													// P1-P2
		}
		// TODO: Check Data size (cant be smaller than 2 bytes)
		pinObject.update(buf, (short) (ISO7816.OFFSET_CDATA), (byte) 2);
		apdu.setOutgoingAndSend((short) 0, (short) 0); // return 9000
	}

	public void update(byte[] pin, short offset, byte length) {
		pinObject.update(pin, offset, length);
	}

	public byte getTriesRemaining() {
		return (byte) 0x03;
		// return pinObject.getTriesRemaining();
	}
}
