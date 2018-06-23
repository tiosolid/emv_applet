package smart;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

//import javacard.framework.JCSystem;

public class FileSystem {
	/*
	 * Usual Structure: MF | |-+ EF 01 | --- Record 01 |-+ EF 02 | --- Record 01
	 * | --- Record 02 |-+ EF 03 | --- Record 01 (for offline data / EMV reader
	 * different icon) | --- Record 02
	 */

	// File Allocation Table (for now, use a fixed EF limit)
	private static final byte EF_1_ID = 1;
	private static final byte EF_2_ID = 2;
	private static final byte EF_3_ID = 3;

	// Dynamic EFs (will contain the Record Files (as array of bytes too)
	private static byte[] ef_1_data;
	private static byte[] ef_2_data;
	private static byte[] ef_3_data;

	private final static byte[] ef01_r01 = { (byte) 0x70, (byte) 0x33, (byte) 0x57, (byte) 0x13, (byte) 0x41, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0xD2, (byte) 0x10, (byte) 0x72, (byte) 0x06, (byte) 0x14, (byte) 0x71, (byte) 0x09, (byte) 0x40, (byte) 0x90, (byte) 0x87, (byte) 0x0F, (byte) 0x5F, (byte) 0x20, (byte) 0x08, (byte) 0x4A, (byte) 0x4F, (byte) 0x45, (byte) 0x20, (byte) 0x54, (byte) 0x45, (byte) 0x53, (byte) 0x54, (byte) 0x9F, (byte) 0x1F, (byte) 0x10, (byte) 0x31, (byte) 0x34, (byte) 0x37, (byte) 0x31, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x39, (byte) 0x34, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x30 };

	private final static byte[] ef01_r02 = { (byte) 0x70, (byte) 0x13, (byte) 0x9F, (byte) 0x08, (byte) 0x02, (byte) 0x00, (byte) 0x8D, (byte) 0x5F, (byte) 0x30, (byte) 0x02, (byte) 0x02, (byte) 0x06, (byte) 0x9F, (byte) 0x42, (byte) 0x02, (byte) 0x09, (byte) 0x86, (byte) 0x9F, (byte) 0x44, (byte) 0x01, (byte) 0x02 };

	private final static byte[] ef01_r03 = { (byte) 0x70, (byte) 0x30, (byte) 0x8C, (byte) 0x15, (byte) 0x9F, (byte) 0x02, (byte) 0x06, (byte) 0x9F, (byte) 0x03, (byte) 0x06, (byte) 0x9F, (byte) 0x1A, (byte) 0x02, (byte) 0x95, (byte) 0x05, (byte) 0x5F, (byte) 0x2A, (byte) 0x02, (byte) 0x9A, (byte) 0x03, (byte) 0x9C, (byte) 0x01, (byte) 0x9F, (byte) 0x37, (byte) 0x04, (byte) 0x8D, (byte) 0x17, (byte) 0x8A, (byte) 0x02, (byte) 0x9F, (byte) 0x02, (byte) 0x06, (byte) 0x9F, (byte) 0x03, (byte) 0x06, (byte) 0x9F, (byte) 0x1A, (byte) 0x02, (byte) 0x95, (byte) 0x05, (byte) 0x5F, (byte) 0x2A, (byte) 0x02, (byte) 0x9A, (byte) 0x03, (byte) 0x9C, (byte) 0x01, (byte) 0x9F, (byte) 0x37, (byte) 0x04 };
	
	private final static byte[] ef01_r04 = { (byte) 0x00 };
	
	private final static byte[] ef01_r05 = { (byte) 0x00 };
	
	private final static byte[] ef01_r06 = { (byte) 0x00 };
	
	private final static byte[] ef02_r01 = { (byte) 0x70, (byte) 0x0E, (byte) 0x5A, (byte) 0x08, (byte) 0x41, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x5F, (byte) 0x34, (byte) 0x01, (byte) 0x00 };

	private final static byte[] ef02_r02 = { (byte) 0x70, (byte) 0x16, (byte) 0x5F, (byte) 0x24, (byte) 0x03, (byte) 0x21, (byte) 0x07, (byte) 0x31, (byte) 0x9F, (byte) 0x07, (byte) 0x02, (byte) 0xFF, (byte) 0x80, (byte) 0x5F, (byte) 0x28, (byte) 0x02, (byte) 0x00, (byte) 0x76, (byte) 0x5F, (byte) 0x25, (byte) 0x03, (byte) 0x15, (byte) 0x07, (byte) 0x23 };

	private final static byte[] ef02_r03 = { (byte) 0x70, (byte) 0x81, (byte) 0xE0, (byte) 0x8F, (byte) 0x01, (byte) 0x08, (byte) 0x90, (byte) 0x81, (byte) 0xB0, (byte) 0xBF, (byte) 0x63, (byte) 0xBF, (byte) 0xF5, (byte) 0x61, (byte) 0x97, (byte) 0x0C, (byte) 0x85, (byte) 0xA8, (byte) 0x27, (byte) 0xAD, (byte) 0xAB, (byte) 0xF8, (byte) 0x56, (byte) 0x68, (byte) 0x0B, (byte) 0xD6, (byte) 0x81, (byte) 0xD0, (byte) 0x99, (byte) 0xDD, (byte) 0xDD, (byte) 0x9F, (byte) 0xD4, (byte) 0xD9, (byte) 0xAB, (byte) 0xE5, (byte) 0x85, (byte) 0x09, (byte) 0xEC, (byte) 0x65, (byte) 0x38, (byte) 0x0C, (byte) 0xA5, (byte) 0xEE, (byte) 0x87, (byte) 0x3C, (byte) 0xA4, (byte) 0x9A, (byte) 0x15, (byte) 0x68, (byte) 0xEA, (byte) 0x77, (byte) 0x8B, (byte) 0x0A, (byte) 0x29, (byte) 0x9A, (byte) 0x83, (byte) 0x45, (byte) 0x8A, (byte) 0x8A, (byte) 0x0D, (byte) 0x70, (byte) 0x47, (byte) 0x3F, (byte) 0xE3, (byte) 0x5E, (byte) 0xF1, (byte) 0x36, (byte) 0x98, (byte) 0xBA, (byte) 0x6F, (byte) 0x94, (byte) 0xDF, (byte) 0xAB, (byte) 0x19, (byte) 0x43, (byte) 0xD9, (byte) 0xEF, (byte) 0x75, (byte) 0xAC, (byte) 0x3B, (byte) 0xFE, (byte) 0xC1, (byte) 0x6B, (byte) 0x47, (byte) 0x74, (byte) 0x4D, (byte) 0x32, (byte) 0x02, (byte) 0xA6, (byte) 0x03, (byte) 0x78, (byte) 0x31, (byte) 0x96, (byte) 0x0E, (byte) 0x1A, (byte) 0x2A, (byte) 0xF4, (byte) 0x30, (byte) 0xAE, (byte) 0x41, (byte) 0xDB, (byte) 0xD3, (byte) 0xE9, (byte) 0x63, (byte) 0xE0, (byte) 0x08, (byte) 0xD7, (byte) 0x91, (byte) 0xE7, (byte) 0xDC, (byte) 0x8F, (byte) 0x46, (byte) 0xC2, (byte) 0x54, (byte) 0x24, (byte) 0xFF, (byte) 0xF8, (byte) 0x08, (byte) 0xB5, (byte) 0xE3, (byte) 0xEE, (byte) 0xBC, (byte) 0x96, (byte) 0x0F, (byte) 0x80, (byte) 0xBD, (byte) 0x8E, (byte) 0x0F, (byte) 0x82, (byte) 0xD6, (byte) 0xC1, (byte) 0x98, (byte) 0x14, (byte) 0x00, (byte) 0xA9, (byte) 0xC7, (byte) 0x32, (byte) 0x3E, (byte) 0xE3, (byte) 0x38, (byte) 0x8D, (byte) 0xA4, (byte) 0xFA, (byte) 0xFF, (byte) 0x7B, (byte) 0xFE, (byte) 0x53, (byte) 0xA5, (byte) 0x98, (byte) 0xDA, (byte) 0x10, (byte) 0xC1, (byte) 0xB1, (byte) 0xDE, (byte) 0xF7, (byte) 0x5A, (byte) 0x6F, (byte) 0x7D, (byte) 0xE7, (byte) 0xC8, (byte) 0x71, (byte) 0x25, (byte) 0xC7, (byte) 0xB3, (byte) 0x74, (byte) 0x15, (byte) 0x86, (byte) 0x5F, (byte) 0x6B, (byte) 0xE0, (byte) 0x48, (byte) 0xDB, (byte) 0x66, (byte) 0x0F, (byte) 0x9D, (byte) 0x50, (byte) 0xC9, (byte) 0xB2, (byte) 0x5C, (byte) 0xF8, (byte) 0x1A, (byte) 0xBB, (byte) 0x96, (byte) 0x73, (byte) 0x9F, (byte) 0x32, (byte) 0x01, (byte) 0x03, (byte) 0x92, (byte) 0x24, (byte) 0xF8, (byte) 0xE1, (byte) 0x75, (byte) 0x61, (byte) 0x48, (byte) 0x32, (byte) 0x33, (byte) 0xFA, (byte) 0x5B, (byte) 0x36, (byte) 0x40, (byte) 0xBA, (byte) 0xE1, (byte) 0xCD, (byte) 0x7D, (byte) 0xA5, (byte) 0xEC, (byte) 0x55, (byte) 0xF4, (byte) 0xC4, (byte) 0xB3, (byte) 0x24, (byte) 0x61, (byte) 0xC6, (byte) 0x87, (byte) 0x39, (byte) 0xF8, (byte) 0x79, (byte) 0xBC, (byte) 0x9F, (byte) 0xB8, (byte) 0xD8, (byte) 0xEE, (byte) 0xA9, (byte) 0x0D, (byte) 0x2D };

	private final static byte[] ef02_r04 = { (byte) 0x70, (byte) 0x1C, (byte) 0x9F, (byte) 0x0E, (byte) 0x05, (byte) 0x2C, (byte) 0x10, (byte) 0x98, (byte) 0x00, (byte) 0x00, (byte) 0x9F, (byte) 0x0F, (byte) 0x05, (byte) 0xD0, (byte) 0x68, (byte) 0x24, (byte) 0xF8, (byte) 0x00, (byte) 0x9F, (byte) 0x0D, (byte) 0x05, (byte) 0xD0, (byte) 0x68, (byte) 0x24, (byte) 0xA8, (byte) 0x00, (byte) 0x9F, (byte) 0x4A, (byte) 0x01, (byte) 0x82 };

	private final static byte[] ef02_r05 = { (byte) 0x70, (byte) 0x16, (byte) 0x8E, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x42, (byte) 0x01, (byte) 0x41, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

	private final static byte[] ef02_r06 = { (byte) 0x70, (byte) 0x81, (byte) 0xB3, (byte) 0x93, (byte) 0x81, (byte) 0xB0, (byte) 0x8E, (byte) 0x69, (byte) 0xF5, (byte) 0xFA, (byte) 0x9C, (byte) 0xBC, (byte) 0xB6, (byte) 0x5B, (byte) 0x1C, (byte) 0x97, (byte) 0xCE, (byte) 0x3A, (byte) 0xB3, (byte) 0xBC, (byte) 0x5E, (byte) 0xE6, (byte) 0x3C, (byte) 0xB4, (byte) 0x0C, (byte) 0x7A, (byte) 0xF4, (byte) 0x5B, (byte) 0xFE, (byte) 0xED, (byte) 0x4C, (byte) 0x5E, (byte) 0xB7, (byte) 0x1A, (byte) 0xE1, (byte) 0x10, (byte) 0x57, (byte) 0x10, (byte) 0x62, (byte) 0x24, (byte) 0xFD, (byte) 0x78, (byte) 0x24, (byte) 0x80, (byte) 0x8E, (byte) 0xA1, (byte) 0x8B, (byte) 0x38, (byte) 0xF9, (byte) 0x79, (byte) 0x9F, (byte) 0x3C, (byte) 0xF8, (byte) 0x66, (byte) 0x41, (byte) 0xB2, (byte) 0xB0, (byte) 0x8B, (byte) 0xED, (byte) 0x29, (byte) 0x22, (byte) 0xB4, (byte) 0xB3, (byte) 0x77, (byte) 0x31, (byte) 0x3F, (byte) 0x6D, (byte) 0xF1, (byte) 0x19, (byte) 0x13, (byte) 0x3D, (byte) 0xBD, (byte) 0xC9, (byte) 0x3B, (byte) 0x14, (byte) 0x31, (byte) 0x09, (byte) 0xAD, (byte) 0x26, (byte) 0xCE, (byte) 0xBC, (byte) 0x7B, (byte) 0xB4, (byte) 0xF7, (byte) 0x10, (byte) 0x1F, (byte) 0x53, (byte) 0xE4, (byte) 0x99, (byte) 0xCA, (byte) 0x05, (byte) 0x22, (byte) 0x0D, (byte) 0x3C, (byte) 0xC6, (byte) 0xF6, (byte) 0xE3, (byte) 0xE0, (byte) 0x37, (byte) 0x86, (byte) 0xBC, (byte) 0x27, (byte) 0x47, (byte) 0x27, (byte) 0x42, (byte) 0x0D, (byte) 0x3A, (byte) 0x91, (byte) 0x00, (byte) 0xB0, (byte) 0x5F, (byte) 0xE8, (byte) 0x51, (byte) 0x41, (byte) 0x65, (byte) 0x8D, (byte) 0xA7, (byte) 0xD0, (byte) 0x68, (byte) 0x9C, (byte) 0x7C, (byte) 0x20, (byte) 0xF2, (byte) 0x82, (byte) 0xAE, (byte) 0x21, (byte) 0x58, (byte) 0x0F, (byte) 0xC4, (byte) 0x25, (byte) 0x2E, (byte) 0x30, (byte) 0xC6, (byte) 0xFA, (byte) 0x60, (byte) 0xF1, (byte) 0x80, (byte) 0x56, (byte) 0x93, (byte) 0xFB, (byte) 0xEF, (byte) 0x5D, (byte) 0xFF, (byte) 0xEF, (byte) 0xB0, (byte) 0x9A, (byte) 0x11, (byte) 0x04, (byte) 0xEC, (byte) 0x22, (byte) 0x5A, (byte) 0xB7, (byte) 0x9D, (byte) 0xD4, (byte) 0xC8, (byte) 0xCA, (byte) 0xCC, (byte) 0x25, (byte) 0x34, (byte) 0xF6, (byte) 0xEF, (byte) 0x1B, (byte) 0x2D, (byte) 0xB6, (byte) 0xA1, (byte) 0x96, (byte) 0xE2, (byte) 0x1B, (byte) 0x40, (byte) 0xB6, (byte) 0xD3, (byte) 0xAD, (byte) 0x8A, (byte) 0xC0, (byte) 0x99, (byte) 0xA6, (byte) 0x4C, (byte) 0x8C, (byte) 0x70, (byte) 0xBE, (byte) 0xD2, (byte) 0x8C };

	private final static byte[] ef02_r07 = { (byte) 0x70, (byte) 0x1C, (byte) 0x9F, (byte) 0x0E, (byte) 0x05, (byte) 0x2C, (byte) 0x10, (byte) 0x98, (byte) 0x00, (byte) 0x00, (byte) 0x9F, (byte) 0x0F, (byte) 0x05, (byte) 0xD0, (byte) 0x68, (byte) 0x24, (byte) 0xF8, (byte) 0x00, (byte) 0x9F, (byte) 0x0D, (byte) 0x05, (byte) 0xD0, (byte) 0x68, (byte) 0x24, (byte) 0xA8, (byte) 0x00, (byte) 0x9F, (byte) 0x4A, (byte) 0x01, (byte) 0x82 };

	private final static byte[] ef02_r08 = { (byte) (byte) 0x70, (byte) 0x16, (byte) 0x8E, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x42, (byte) 0x03, (byte) 0x41, (byte) 0x03, (byte) 0x1E, (byte) 0x03, (byte) 0x1F, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
	
	private final static byte[] ef02_r09 = { (byte) 0x70, (byte) 0x81, (byte) 0xB3, (byte) 0x93, (byte) 0x81, (byte) 0xB0, (byte) 0x9F, (byte) 0x06, (byte) 0x1E, (byte) 0xEF, (byte) 0x4A, (byte) 0x23, (byte) 0xD0, (byte) 0x1B, (byte) 0x46, (byte) 0x5E, (byte) 0x86, (byte) 0x42, (byte) 0x3C, (byte) 0xBA, (byte) 0xD1, (byte) 0x88, (byte) 0x87, (byte) 0xE2, (byte) 0x43, (byte) 0x02, (byte) 0x9D, (byte) 0x79, (byte) 0x69, (byte) 0x54, (byte) 0x90, (byte) 0xA3, (byte) 0xC2, (byte) 0x1C, (byte) 0xB1, (byte) 0x4E, (byte) 0xFD, (byte) 0x8D, (byte) 0xAC, (byte) 0xAD, (byte) 0x94, (byte) 0x22, (byte) 0x77, (byte) 0x34, (byte) 0xB1, (byte) 0x6D, (byte) 0xCE, (byte) 0x2C, (byte) 0xA3, (byte) 0x65, (byte) 0x0B, (byte) 0x5C, (byte) 0xA0, (byte) 0xC9, (byte) 0x21, (byte) 0x88, (byte) 0x0A, (byte) 0x24, (byte) 0x8E, (byte) 0x04, (byte) 0x40, (byte) 0x15, (byte) 0xA6, (byte) 0x0D, (byte) 0xDA, (byte) 0x42, (byte) 0x08, (byte) 0x03, (byte) 0x57, (byte) 0x1A, (byte) 0x03, (byte) 0x42, (byte) 0x2D, (byte) 0x59, (byte) 0x5F, (byte) 0xD3, (byte) 0xB5, (byte) 0x91, (byte) 0x43, (byte) 0x75, (byte) 0x5B, (byte) 0xD5, (byte) 0x32, (byte) 0x77, (byte) 0x7C, (byte) 0x3A, (byte) 0x25, (byte) 0xAB, (byte) 0x1C, (byte) 0x95, (byte) 0x32, (byte) 0x18, (byte) 0xBF, (byte) 0xF8, (byte) 0xEC, (byte) 0x0E, (byte) 0xFA, (byte) 0xA0, (byte) 0x41, (byte) 0x1B, (byte) 0x6E, (byte) 0x62, (byte) 0xF0, (byte) 0x67, (byte) 0xED, (byte) 0x21, (byte) 0x35, (byte) 0x49, (byte) 0xA1, (byte) 0x27, (byte) 0x30, (byte) 0xCB, (byte) 0x47, (byte) 0x0A, (byte) 0xAE, (byte) 0x59, (byte) 0xB7, (byte) 0x01, (byte) 0x1E, (byte) 0xDE, (byte) 0x7D, (byte) 0x43, (byte) 0x1E, (byte) 0x66, (byte) 0x04, (byte) 0xBF, (byte) 0x72, (byte) 0x97, (byte) 0xD6, (byte) 0x86, (byte) 0x42, (byte) 0x0F, (byte) 0xBF, (byte) 0xB5, (byte) 0x4D, (byte) 0xBE, (byte) 0x44, (byte) 0x74, (byte) 0x95, (byte) 0x67, (byte) 0xE6, (byte) 0x1A, (byte) 0x98, (byte) 0xC1, (byte) 0x7B, (byte) 0x10, (byte) 0x8A, (byte) 0xB8, (byte) 0xF2, (byte) 0x59, (byte) 0xA1, (byte) 0x2E, (byte) 0xAD, (byte) 0x14, (byte) 0xA6, (byte) 0xF7, (byte) 0x00, (byte) 0x06, (byte) 0x31, (byte) 0xB6, (byte) 0x42, (byte) 0x7C, (byte) 0xAD, (byte) 0xFE, (byte) 0x49, (byte) 0xAC, (byte) 0xE9, (byte) 0x71, (byte) 0xBD, (byte) 0xCA, (byte) 0xE6, (byte) 0xAD, (byte) 0x7C, (byte) 0x23, (byte) 0x67, (byte) 0xE3, (byte) 0x58, (byte) 0xA9, (byte) 0x05, (byte) 0xD2, (byte) 0xAD, (byte) 0x7C };
	
	private final static byte[] ef04_r01 = { (byte) 0x00 };

	private static byte[] selectFile; // Will hold the ID of the selected EF (probably not supported for now)

	public final static byte[] fci = { (byte) 0x6F, (byte) 0x81, (byte) 0x88, (byte) 0x84, (byte) 0x07, (byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x20, (byte) 0x10, (byte) 0xA5, (byte) 0x29, (byte) 0x87, (byte) 0x01, (byte) 0x02, (byte) 0x50, (byte) 0x0C, (byte) 0x56, (byte) 0x49, (byte) 0x53, (byte) 0x41, (byte) 0x45, (byte) 0x4C, (byte) 0x45, (byte) 0x43, (byte) 0x54, (byte) 0x52, (byte) 0x4F, (byte) 0x4E, (byte) 0x9F, (byte) 0x38, (byte) 0x03, (byte) 0x9F, (byte) 0x1A, (byte) 0x02, (byte) 0x5F, (byte) 0x2D, (byte) 0x02, (byte) 0x70, (byte) 0x74, (byte) 0x9F, (byte) 0x11, (byte) 0x01, (byte) 0x01, (byte) 0x9F, (byte) 0x12, (byte) 0x06, (byte) 0x44, (byte) 0x45, (byte) 0x42, (byte) 0x49, (byte) 0x54, (byte) 0x4F, (byte) 0x87, (byte) 0x01, (byte) 0x02, (byte) 0x50, (byte) 0x0C, (byte) 0x56, (byte) 0x49, (byte) 0x53, (byte) 0x41, (byte) 0x45, (byte) 0x4C, (byte) 0x45, (byte) 0x43, (byte) 0x54, (byte) 0x52, (byte) 0x4F, (byte) 0x4E, (byte) 0x9F, (byte) 0x38, (byte) 0x03, (byte) 0x9F, (byte) 0x1A, (byte) 0x02, (byte) 0x5F, (byte) 0x2D, (byte) 0x02, (byte) 0x70, (byte) 0x74, (byte) 0x9F, (byte) 0x11, (byte) 0x01, (byte) 0x01, (byte) 0x9F, (byte) 0x12, (byte) 0x06, (byte) 0x44, (byte) 0x45, (byte) 0x42, (byte) 0x49, (byte) 0x54, (byte) 0x4F, (byte) 0x87, (byte) 0x01, (byte) 0x02, (byte) 0x50, (byte) 0x0C, (byte) 0x56, (byte) 0x49, (byte) 0x53, (byte) 0x41, (byte) 0x45, (byte) 0x4C, (byte) 0x45, (byte) 0x43, (byte) 0x54, (byte) 0x52, (byte) 0x4F, (byte) 0x4E, (byte) 0x9F, (byte) 0x38, (byte) 0x03, (byte) 0x9F, (byte) 0x1A, (byte) 0x02, (byte) 0x5F, (byte) 0x2D, (byte) 0x04, (byte) 0x70, (byte) 0x74, (byte) 0x65, (byte) 0x6E, (byte) 0x9F, (byte) 0x11, (byte) 0x01, (byte) 0x01, (byte) 0x9F, (byte) 0x12, (byte) 0x06, (byte) 0x44, (byte) 0x45, (byte) 0x42, (byte) 0x49, (byte) 0x54, (byte) 0x4F };

	public final static byte[] aip_afl = { (byte) 0x80, (byte) 0x0E,
			(byte) 0x5C, (byte) 0x00, (byte) 0x08, (byte) 0x01, (byte) 0x03,
			(byte) 0x00, (byte) 0x10, (byte) 0x01, (byte) 0x03, (byte) 0x02,
			(byte) 0x10, (byte) 0x04, (byte) 0x05, (byte) 0x01 }; // AIP

	/*
	 * AIP - TAG 82 5800 (01011000) ---- 4000 SDA supported 1000 Cardholder
	 * verification supported 0800 Terminal risk management is to be performed
	 */
	public final short aip = (short) 0x5800;

	/*
	 * AFL: TAG 94 For now, use a static / fixed AFL TODO: Change the AFL based
	 * in the actual card data structure
	 */
	public final static byte[] afl = {
			// 080101001001020018010201 = 08010100 10010200 18010201
			(byte) 0x08, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x10,
			(byte) 0x01, (byte) 0x02, (byte) 0x00, (byte) 0x18, (byte) 0x01,
			(byte) 0x02, (byte) 0x01 };

	// "THE" INS (E2). Append a record (SIMPLE-TLV data) to the end of an EF
	public static void appendRecord(byte p2, byte[] data) {
		/*
		 * P2 = EF identifier in SFI format data = record to be appended
		 */

	}

	public static void writeRecord(APDU apdu) {
		// For now, receives the data and writes it to the EF without any
		// validation
		byte[] buf = apdu.getBuffer();
		byte data = buf[ISO7816.OFFSET_CDATA];

	}

	public short getAIP() {
		return aip;
	}

	public void setAIP(short newAip) {

	}

	public static void selectFile() {
		// In theory, a direct select file (instead of an AID) is never used
		// When used, should select an EF / DF to read its information
		// Comercial EMV suport only the "native" SELECT command
		// TODO: Must return somenthing here but it cant be function not
		// supported since select is supported (but only and AID)
	}

	// TODO: Support only fixed size / quantity of EFs first
	public static void createFile() {

	}

	public static void readRecord(APDU apdu) {
		// TODO: Change this case to an array of EFs so we can address them by
		// position instead of switching

		/*
		 * TODO: Se voce passa o read como 00B2010C o campo LE "nao existe" e o
		 * bytesleft = 0, trigando o primeiro condicional do if se voce passa
		 * qualquer outro campo (mesmo 00, que � "leia tudo que puder") o else �
		 * trigado a unica forma de trigar bytesleft = 0 � NAO PASSANDO NADA, o
		 * que � invalido (deveria ser considerado como passado 00 Ou seja:
		 * colocado um 1 no if para sempre trigar o segundo, mas arrumar
		 * corretamente para a le esperada
		 */

		byte[] buf = apdu.getBuffer();
		short bytesLeft;
		short record = buf[ISO7816.OFFSET_P1];
		byte ef = (byte) ((buf[ISO7816.OFFSET_P2] ^ 4) >> 3);

		switch (ef) {
		case (byte) 0x01: // three records
			switch (record) {
			/*
			 * CASE ANTIGO, ANTES N�O LIDO case (byte)0x01: //three records
			 * switch (record) { case (byte) 0x01: bytesLeft =
			 * apdu.setOutgoing(); if (bytesLeft == (short) 1) {
			 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
			 * (ef01_r01,(short)0,(short)0); } else {
			 * apdu.setOutgoingLength((short)ef01_r01.length);
			 * apdu.sendBytesLong (ef01_r01,(short)0,(short)ef01_r01.length); }
			 */
			case (byte) 0x01:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef01_r01.length);
				apdu.sendBytesLong(ef01_r01, (short) 0, (short) ef01_r01.length);

				break;
			case (byte) 0x02:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef01_r02.length);
				apdu.sendBytesLong(ef01_r02, (short) 0, (short) ef01_r02.length);

				break;
			case (byte) 0x03:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef01_r03.length);
				apdu.sendBytesLong(ef01_r03, (short) 0, (short) ef01_r03.length);

				break;
			case (byte) 0x04:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef01_r04.length);
				apdu.sendBytesLong(ef01_r04, (short) 0, (short) ef01_r04.length);

				break;
			case (byte) 0x05:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef01_r05.length);
				apdu.sendBytesLong(ef01_r05, (short) 0, (short) ef01_r05.length);

				break;
			case (byte) 0x06:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef01_r06.length);
				apdu.sendBytesLong(ef01_r06, (short) 0, (short) ef01_r06.length);

				break;
			}
				break;

		case (byte) 0x02: // five records
			switch (record) {
			case (byte) 0x01:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r01.length);
				apdu.sendBytesLong(ef02_r01, (short) 0, (short) ef02_r01.length);
				break;
			case (byte) 0x02:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r02.length);
				apdu.sendBytesLong(ef02_r02, (short) 0, (short) ef02_r02.length);
				break;
			case (byte) 0x03:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r03.length);
				apdu.sendBytesLong(ef02_r03, (short) 0, (short) ef02_r03.length);

				break;
			case (byte) 0x04:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r04.length);
				apdu.sendBytesLong(ef02_r04, (short) 0, (short) ef02_r04.length);

				break;
			case (byte) 0x05:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r05.length);
				apdu.sendBytesLong(ef02_r05, (short) 0, (short) ef02_r05.length);

				break;
			case (byte) 0x06:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r06.length);
				apdu.sendBytesLong(ef02_r06, (short) 0, (short) ef02_r06.length);

				break;
			case (byte) 0x07:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r07.length);
				apdu.sendBytesLong(ef02_r07, (short) 0, (short) ef02_r07.length);

				break;
			
			case (byte) 0x08:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r08.length);
				apdu.sendBytesLong(ef02_r08, (short) 0, (short) ef02_r08.length);
		
				break;
			
			case (byte) 0x09:
				bytesLeft = apdu.setOutgoing();
				apdu.setOutgoingLength((short) ef02_r09.length);
				apdu.sendBytesLong(ef02_r09, (short) 0, (short) ef02_r09.length);
		
				break;
			}
	
			break;
		case (byte) 0x04: // mostra o pan
			bytesLeft = apdu.setOutgoing();
			apdu.setOutgoingLength((short) ef04_r01.length);
			apdu.sendBytesLong(ef04_r01, (short) 0, (short) ef04_r01.length);

			break;
		}

		/*
		 * try { bytesLeft = apdu.setOutgoing(); if (bytesLeft == (short) 0) {
		 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (fs[ef][record],(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)fs[ef][record].length);
		 * apdu.sendBytesLong
		 * (fs[ef][record],(short)0,(short)fs[ef][record].length); } } catch
		 * (Exception e) { ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND); }
		 */

		/*
		 * switch (buf[ISO7816.OFFSET_P1]) { case (byte) 0x01: bytesLeft =
		 * apdu.setOutgoing(); if (bytesLeft == (short) 1) {
		 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (ef01_r01,(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)ef01_r01.length); apdu.sendBytesLong
		 * (ef01_r01,(short)0,(short)ef01_r01.length); } break;
		 * 
		 * case (short) 0x02: bytesLeft = apdu.setOutgoing(); if (bytesLeft ==
		 * (short) 1) { apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (ef01_r02,(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)ef01_r02.length); apdu.sendBytesLong
		 * (ef01_r02,(short)0,(short)ef01_r02.length); } break; case (short)
		 * 0x03: bytesLeft = apdu.setOutgoing(); if (bytesLeft == (short) 1) {
		 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (ef01_r03,(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)ef01_r03.length); apdu.sendBytesLong
		 * (ef01_r03,(short)0,(short)ef01_r03.length); } break; case (short)
		 * 0x04: bytesLeft = apdu.setOutgoing(); if (bytesLeft == (short) 1) {
		 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (ef01_r04,(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)ef01_r04.length); apdu.sendBytesLong
		 * (ef01_r04,(short)0,(short)ef01_r04.length); } break; case (short)
		 * 0x05: bytesLeft = apdu.setOutgoing(); if (bytesLeft == (short) 1) {
		 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (ef01_r05,(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)ef01_r05.length); apdu.sendBytesLong
		 * (ef01_r05,(short)0,(short)ef01_r05.length); } break; case (short)
		 * 0x06: bytesLeft = apdu.setOutgoing(); if (bytesLeft == (short) 1) {
		 * apdu.setOutgoingLength((short) 0); apdu.sendBytesLong
		 * (ef01_r06,(short)0,(short)0); } else {
		 * apdu.setOutgoingLength((short)ef01_r06.length); apdu.sendBytesLong
		 * (ef01_r06,(short)0,(short)ef01_r06.length); } break; default:
		 * ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2); }
		 */
	}

	public short getCDOL2DataLength() {
		// TODO Check DOL Length Dinamically (TAG / LENGTH - no value)
		return 0x11; // 17 bytes
	}

	public short getCDOL1DataLength() {
		// TODO Check DOL Length Dinamically (TAG / LENGTH - no value)
		return 0x20; // 32 bytes
	}
}