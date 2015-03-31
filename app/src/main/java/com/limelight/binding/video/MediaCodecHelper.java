package com.limelight.binding.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.os.Build;

import com.limelight.LimeLog;

public class MediaCodecHelper {
	
	private static final List<String> preferredDecoders;

	private static final List<String> blacklistedDecoderPrefixes;
	private static final List<String> spsFixupBitstreamFixupDecoderPrefixes;
	private static final List<String> whitelistedAdaptiveResolutionPrefixes;
    private static final List<String> baselineProfileHackPrefixes;
    private static final List<String> directSubmitPrefixes;

    static {
        directSubmitPrefixes = new LinkedList<String>();

        // These decoders have low enough input buffer latency that they
        // can be directly invoked from the receive thread
        directSubmitPrefixes.add("omx.qcom");
        directSubmitPrefixes.add("omx.sec");
        directSubmitPrefixes.add("omx.exynos");
        directSubmitPrefixes.add("omx.intel");
        directSubmitPrefixes.add("omx.brcm");
        directSubmitPrefixes.add("omx.TI");
        directSubmitPrefixes.add("omx.arc");
    }

	static {
		preferredDecoders = new LinkedList<String>();
	}
	
	static {
		blacklistedDecoderPrefixes = new LinkedList<String>();
		
		// Software decoders that don't support H264 high profile
		blacklistedDecoderPrefixes.add("omx.google");
		blacklistedDecoderPrefixes.add("AVCDecoder");
	}
	
	static {
		spsFixupBitstreamFixupDecoderPrefixes = new LinkedList<String>();
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.nvidia");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.qcom");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.mtk");
        spsFixupBitstreamFixupDecoderPrefixes.add("omx.brcm");

        baselineProfileHackPrefixes = new LinkedList<String>();
        baselineProfileHackPrefixes.add("omx.intel");

		whitelistedAdaptiveResolutionPrefixes = new LinkedList<String>();
		whitelistedAdaptiveResolutionPrefixes.add("omx.nvidia");
		whitelistedAdaptiveResolutionPrefixes.add("omx.qcom");
		whitelistedAdaptiveResolutionPrefixes.add("omx.sec");
		whitelistedAdaptiveResolutionPrefixes.add("omx.TI");
	}

	private static boolean isDecoderInList(List<String> decoderList, String decoderName) {
		for (String badPrefix : decoderList) {
			if (decoderName.length() >= badPrefix.length()) {
				String prefix = decoderName.substring(0, badPrefix.length());
				if (prefix.equalsIgnoreCase(badPrefix)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static boolean decoderSupportsAdaptivePlayback(String decoderName, MediaCodecInfo decoderInfo) {
		/*
        FIXME: Intel's decoder on Nexus Player forces the high latency path if adaptive playback is enabled
        so we'll keep it off for now, since we don't know whether other devices also do the same

		if (isDecoderInList(whitelistedAdaptiveResolutionPrefixes, decoderName)) {
			LimeLog.info("Adaptive playback supported (whitelist)");
			return true;
		}
		
		// Possibly enable adaptive playback on KitKat and above
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			try {
				if (decoderInfo.getCapabilitiesForType("video/avc").
						isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback))
				{
					// This will make getCapabilities() return that adaptive playback is supported
					LimeLog.info("Adaptive playback supported (FEATURE_AdaptivePlayback)");
					return true;
				}
			} catch (Exception e) {
				// Tolerate buggy codecs
			}
		}*/
		
		return false;
	}

    public static boolean decoderCanDirectSubmit(String decoderName, MediaCodecInfo decoderInfo) {
        return isDecoderInList(directSubmitPrefixes, decoderName) && !isExynos4Device();
    }
	
	public static boolean decoderNeedsSpsBitstreamRestrictions(String decoderName, MediaCodecInfo decoderInfo) {
		return isDecoderInList(spsFixupBitstreamFixupDecoderPrefixes, decoderName);
	}

    public static boolean decoderNeedsBaselineSpsHack(String decoderName, MediaCodecInfo decoderInfo) {
        return isDecoderInList(baselineProfileHackPrefixes, decoderName);
    }
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private static LinkedList<MediaCodecInfo> getMediaCodecList() {
		LinkedList<MediaCodecInfo> infoList = new LinkedList<MediaCodecInfo>();
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            Collections.addAll(infoList, mcl.getCodecInfos());
		}
		else {
			for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
				infoList.add(MediaCodecList.getCodecInfoAt(i));
			}	
		}
		
		return infoList;
	}
	
	@SuppressWarnings("RedundantThrows")
    public static String dumpDecoders() throws Exception {
		String str = "";
		for (MediaCodecInfo codecInfo : getMediaCodecList()) {
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			str += "Decoder: "+codecInfo.getName()+"\n";
			for (String type : codecInfo.getSupportedTypes()) {
				str += "\t"+type+"\n";
				CodecCapabilities caps = codecInfo.getCapabilitiesForType(type);
				
				for (CodecProfileLevel profile : caps.profileLevels) {
					str += "\t\t"+profile.profile+" "+profile.level+"\n";
				}
			}
		}
		return str;
	}
	
	private static MediaCodecInfo findPreferredDecoder() {
		// This is a different algorithm than the other findXXXDecoder functions,
		// because we want to evaluate the decoders in our list's order
		// rather than MediaCodecList's order
		
		for (String preferredDecoder : preferredDecoders) {
			for (MediaCodecInfo codecInfo : getMediaCodecList()) {
				// Skip encoders
				if (codecInfo.isEncoder()) {
					continue;
				}
				
				// Check for preferred decoders
				if (preferredDecoder.equalsIgnoreCase(codecInfo.getName())) {
					LimeLog.info("Preferred decoder choice is "+codecInfo.getName());
					return codecInfo;
				}
			}
		}
		
		return null;
	}
	
	public static MediaCodecInfo findFirstDecoder() {
		for (MediaCodecInfo codecInfo : getMediaCodecList()) {
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			// Check for explicitly blacklisted decoders
			if (isDecoderInList(blacklistedDecoderPrefixes, codecInfo.getName())) {
				LimeLog.info("Skipping blacklisted decoder: "+codecInfo.getName());
				continue;
			}
			
			// Find a decoder that supports H.264
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase("video/avc")) {
					LimeLog.info("First decoder choice is "+codecInfo.getName());
					return codecInfo;
				}
			}
		}
		
		return null;
	}
	
	public static MediaCodecInfo findProbableSafeDecoder() {
		// First look for a preferred decoder by name
		MediaCodecInfo info = findPreferredDecoder();
		if (info != null) {
			return info;
		}
		
		// Now look for decoders we know are safe
		try {
			// If this function completes, it will determine if the decoder is safe
			return findKnownSafeDecoder();
		} catch (Exception e) {
			// Some buggy devices seem to throw exceptions
			// from getCapabilitiesForType() so we'll just assume
			// they're okay and go with the first one we find
			return findFirstDecoder();
		}
	}

	// We declare this method as explicitly throwing Exception
	// since some bad decoders can throw IllegalArgumentExceptions unexpectedly
	// and we want to be sure all callers are handling this possibility
	@SuppressWarnings("RedundantThrows")
    private static MediaCodecInfo findKnownSafeDecoder() throws Exception {
		for (MediaCodecInfo codecInfo : getMediaCodecList()) {		
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			// Check for explicitly blacklisted decoders
			if (isDecoderInList(blacklistedDecoderPrefixes, codecInfo.getName())) {
				LimeLog.info("Skipping blacklisted decoder: "+codecInfo.getName());
				continue;
			}
			
			// Find a decoder that supports H.264 high profile
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase("video/avc")) {
					LimeLog.info("Examining decoder capabilities of "+codecInfo.getName());
					
					CodecCapabilities caps = codecInfo.getCapabilitiesForType(mime);
					for (CodecProfileLevel profile : caps.profileLevels) {
						if (profile.profile == CodecProfileLevel.AVCProfileHigh) {
							LimeLog.info("Decoder "+codecInfo.getName()+" supports high profile");
							LimeLog.info("Selected decoder: "+codecInfo.getName());
							return codecInfo;
						}
					}
					
					LimeLog.info("Decoder "+codecInfo.getName()+" does NOT support high profile");
				}
			}
		}
		
		return null;
	}
	
	public static String readCpuinfo() throws Exception {
		StringBuilder cpuInfo = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(new File("/proc/cpuinfo")));
		try {
			for (;;) {
				int ch = br.read();
				if (ch == -1)
					break;
				cpuInfo.append((char)ch);
			}
			
			return cpuInfo.toString();
		} finally {
			br.close();
		}
	}
	
	private static boolean stringContainsIgnoreCase(String string, String substring) {
		return string.toLowerCase(Locale.ENGLISH).contains(substring.toLowerCase(Locale.ENGLISH));
	}
	
	public static boolean isExynos4Device() {
		try {
			// Try reading CPU info too look for 
			String cpuInfo = readCpuinfo();
			
			// SMDK4xxx is Exynos 4 
			if (stringContainsIgnoreCase(cpuInfo, "SMDK4")) {
				LimeLog.info("Found SMDK4 in /proc/cpuinfo");
				return true;
			}
			
			// If we see "Exynos 4" also we'll count it
			if (stringContainsIgnoreCase(cpuInfo, "Exynos 4")) {
				LimeLog.info("Found Exynos 4 in /proc/cpuinfo");
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			File systemDir = new File("/sys/devices/system");
			File[] files = systemDir.listFiles();
			if (files != null) {
				for (File f : files) {
					if (stringContainsIgnoreCase(f.getName(), "exynos4")) {
						LimeLog.info("Found exynos4 in /sys/devices/system");
						return true;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
}
