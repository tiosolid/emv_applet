package smart;

import javacard.framework.Util;
import javacard.framework.ISO7816;

public class Log {
	private boolean log_enabled;
	public final static short MAX_LOG_SIZE = 255;
	private final byte[] log_array;
	private short log_size;

	public Log() {
		log_enabled = true;
		log_array = new byte[MAX_LOG_SIZE];
		log_size = 0;
	}

	// Append data to the end of the log
	public void write(byte[] buffer) {
		if (!isEnabled() || isLogFull()) {
			return;
		} // Do nothing if log is disabled or full

		short apdu_size = 4; // CLA - INS - P1 - P2, minimum
		if (buffer[ISO7816.OFFSET_LC] != 0x00) {
			apdu_size = (short) (apdu_size + buffer[ISO7816.OFFSET_LC] + 1);
		} // LC != 0 means we have a data body

		// Check if the message fits in the remaining log space
		if (apdu_size > getFreeSpace()) {
			return;
		}

		// Prepare the message to be inserted atomically
		byte[] entry = new byte[apdu_size + 1]; // Extra byte for the length
		entry[0] = (byte) apdu_size;
		Util.arrayCopy(buffer, (short) 0, entry, (short) 1, apdu_size);

		// Finally, add the message to the log
		Util.arrayCopy(entry, (short) 0, log_array, getSize(),
				(short) entry.length);

		// Increment the log size
		log_size = (short) (log_size + entry.length);
	}

	/** Read all the entries in the log */
	public byte[] read() {
		return log_array;
	}

	// Clear all the log entries
	public void clear() {
		Util.arrayFillNonAtomic(log_array, (short) 0, MAX_LOG_SIZE, (byte) 0x00);
		log_size = (short) 0;
	}

	/** Return the actual log size */
	public short getSize() {
		return log_size;
	}

	/** Return log free space */
	public short getFreeSpace() {
		return (short) (MAX_LOG_SIZE - getSize());
	}

	/** Check if the log is full */
	public boolean isLogFull() {
		if (log_size < MAX_LOG_SIZE) {
			return false;
		}

		return true;
	}

	// Get if the log facility is enabled or not
	public boolean isEnabled() {
		return log_enabled;
	}

	public void enable() {
		log_enabled = true;
	}

	public void disable() {
		log_enabled = false;
	}
}
