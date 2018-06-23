package smart;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class ProtocolState {
	private short atc;
	private short lastOnlineATC;

	// constants to record the (persistent) lifecycle state
	public static byte PERSONALISATION = (byte) 0x00;
	public static byte READY = (byte) 0x01;
	public static byte BLOCKED = (byte) 0x02;

	/**
	 * Volatile protocol state; records if CVM has been performed, and if ACs
	 * have been generated
	 */
	private final byte volatileState[];

	/**
	 * 2 byte Card Verification Results
	 */
	private final byte[] cvr;

	public byte getFirstACGenerated() {
		return volatileState[1];
	}

	public void setFirstACGenerated(byte ACType) {
		volatileState[1] = ACType;
	}

	public byte getSecondACGenerated() {
		return volatileState[2];
	}

	public void setSecondACGenerated(byte ACType) {
		volatileState[2] = ACType;
	}

	public byte getCVMPerformed() {
		return volatileState[0];
	}

	public void setCVMPerformed(byte CVMType) {
		volatileState[0] = CVMType;
	}

	public short getATC() {
		return (short) 0x00C3;
		// return atc;
	}

	public void setATC(short newATC) {
		atc = newATC;
	}

	private void increaseATC() {
		// if (atc == MAX) { BLOCK THIS CARD!! }, but we ignore security here
		atc = (short) (atc + 1);
	}

	public short getLastOnlineATC() {
		return lastOnlineATC;
	}

	public void setLastOnlineATC(short newLOATC) {
		lastOnlineATC = newLOATC;
	}

	public ProtocolState() {
		volatileState = JCSystem.makeTransientByteArray((short) 3,
				JCSystem.CLEAR_ON_DESELECT);
		cvr = JCSystem.makeTransientByteArray((short) 2,
				JCSystem.CLEAR_ON_DESELECT);
		atc = (short) 0x0005;
		lastOnlineATC = (short) 0x0005;
	}

	/*
	 * Starts a new session. This resets all session data and increases the ATC,
	 * but does not generate a session key yet.
	 */
	public void startNewSession() {
		setFirstACGenerated(smart.Emv.NONE);
		setSecondACGenerated(smart.Emv.NONE);
		setCVMPerformed(smart.Emv.NONE);
		increaseATC();
	}

	/*
	 * Sets the last online ATC equal to the current ATC
	 */
	public void onlineSessionCompleted() {
		lastOnlineATC = atc;
	}

	/*
	 * Returns the 4 byte CVR (Card Verification Results). Details are described
	 * in Book 3, Annex C7.3
	 */
	public byte[] getCVR() {
		return null;
	}
}
