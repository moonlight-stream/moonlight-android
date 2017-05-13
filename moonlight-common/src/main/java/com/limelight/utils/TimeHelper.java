package com.limelight.utils;

public class TimeHelper {
	public static long getMonotonicMillis() {
		return System.nanoTime() / 1000000L;
	}
}
