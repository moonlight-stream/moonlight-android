package com.limelight.nvstream.av;

public class SequenceHelper {
	public static boolean isBeforeSigned(int numA, int numB, boolean ambiguousCase) {
		// This should be the common case for most callers
		if (numA == numB) {
			return false;
		}
		
		// If numA and numB have the same signs,
		// we can just do a regular comparison.
		if ((numA < 0 && numB < 0) || (numA >= 0 && numB >= 0)) {
			return numA < numB;
		}
		else {
			// The sign switch is ambiguous
			return ambiguousCase;
		}
	}
}
