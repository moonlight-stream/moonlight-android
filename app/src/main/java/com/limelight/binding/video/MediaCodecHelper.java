package com.limelight.binding.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
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
	private static final List<String> blacklistedAdaptivePlaybackPrefixes;
	private static final List<String> deprioritizedHevcDecoders;
    private static final List<String> baselineProfileHackPrefixes;
    private static final List<String> directSubmitPrefixes;
	private static final List<String> constrainedHighProfilePrefixes;
	private static final List<String> whitelistedHevcDecoders;
	private static final List<String> refFrameInvalidationAvcPrefixes;
    private static final List<String> refFrameInvalidationHevcPrefixes;
    private static final List<String> blacklisted49FpsDecoderPrefixes;

    private static boolean isLowEndSnapdragon = false;
    private static boolean initialized = false;

    static {
        directSubmitPrefixes = new LinkedList<>();

        // These decoders have low enough input buffer latency that they
        // can be directly invoked from the receive thread
        directSubmitPrefixes.add("omx.qcom");
        directSubmitPrefixes.add("omx.sec");
        directSubmitPrefixes.add("omx.exynos");
        directSubmitPrefixes.add("omx.intel");
        directSubmitPrefixes.add("omx.brcm");
        directSubmitPrefixes.add("omx.TI");
        directSubmitPrefixes.add("omx.arc");
		directSubmitPrefixes.add("omx.nvidia");
    }

    static {
        refFrameInvalidationAvcPrefixes = new LinkedList<>();
        refFrameInvalidationHevcPrefixes = new LinkedList<>();

		// Qualcomm and NVIDIA may be added at runtime
	}

	static {
		preferredDecoders = new LinkedList<>();
	}
	
	static {
		blacklistedDecoderPrefixes = new LinkedList<>();

		// Blacklist software decoders that don't support H264 high profile,
		// but exclude the official AOSP and CrOS emulator from this restriction.
		if (!Build.HARDWARE.equals("ranchu") && !Build.HARDWARE.equals("cheets")) {
			blacklistedDecoderPrefixes.add("omx.google");
			blacklistedDecoderPrefixes.add("AVCDecoder");
		}

		// Never use ffmpeg decoders since they're software decoders
		blacklistedDecoderPrefixes.add("OMX.ffmpeg");

		// Force these decoders disabled because:
		// 1) They are software decoders, so the performance is terrible
		// 2) They crash with our HEVC stream anyway (at least prior to CSD batching)
		blacklistedDecoderPrefixes.add("OMX.qcom.video.decoder.hevcswvdec");
		blacklistedDecoderPrefixes.add("OMX.SEC.hevc.sw.dec");
	}
	
	static {
		// If a decoder qualifies for reference frame invalidation,
		// these entries will be ignored for those decoders.
		spsFixupBitstreamFixupDecoderPrefixes = new LinkedList<>();
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.nvidia");
		spsFixupBitstreamFixupDecoderPrefixes.add("omx.qcom");
        spsFixupBitstreamFixupDecoderPrefixes.add("omx.brcm");

        baselineProfileHackPrefixes = new LinkedList<>();
        baselineProfileHackPrefixes.add("omx.intel");

		blacklistedAdaptivePlaybackPrefixes = new LinkedList<>();
		// The Intel decoder on Lollipop on Nexus Player would increase latency badly
		// if adaptive playback was enabled so let's avoid it to be safe.
		blacklistedAdaptivePlaybackPrefixes.add("omx.intel");
		// The MediaTek decoder crashes at 1080p when adaptive playback is enabled
		// on some Android TV devices with H.265 only.
		blacklistedAdaptivePlaybackPrefixes.add("omx.mtk");

		constrainedHighProfilePrefixes = new LinkedList<>();
		constrainedHighProfilePrefixes.add("omx.intel");
	}

	static {
		whitelistedHevcDecoders = new LinkedList<>();

		// Allow software HEVC decoding in the official AOSP emulator
		if (Build.HARDWARE.equals("ranchu") && Build.BRAND.equals("google")) {
			whitelistedHevcDecoders.add("omx.google");
		}

		// Exynos seems to be the only HEVC decoder that works reliably
		whitelistedHevcDecoders.add("omx.exynos");

		// On Darcy (Shield 2017), HEVC runs fine with no fixups required.
		// For some reason, other X1 implementations require bitstream fixups.
		if (Build.DEVICE.equalsIgnoreCase("darcy")) {
			whitelistedHevcDecoders.add("omx.nvidia");
		}
		else {
			// TODO: This needs a similar fixup to the Tegra 3 otherwise it buffers 16 frames
		}

		// Sony ATVs have broken MediaTek codecs (decoder hangs after rendering the first frame).
		// I know the Fire TV 2 and 3 works, so I'll just whitelist Amazon devices which seem
		// to actually be tested.
		if (Build.MANUFACTURER.equalsIgnoreCase("Amazon")) {
			whitelistedHevcDecoders.add("omx.mtk");
			whitelistedHevcDecoders.add("omx.amlogic");
		}

		// These theoretically have good HEVC decoding capabilities (potentially better than
		// their AVC decoders), but haven't been tested enough
		//whitelistedHevcDecoders.add("omx.amlogic");
		//whitelistedHevcDecoders.add("omx.rk");

		// Based on GPU attributes queried at runtime, the omx.qcom prefix will be added
		// during initialization to avoid SoCs with broken HEVC decoders.
	}

	static {
		deprioritizedHevcDecoders = new LinkedList<>();

		// These are decoders that work but aren't used by default for various reasons.

		// Qualcomm is currently the only decoders in this group.
	}

	static {
		blacklisted49FpsDecoderPrefixes = new LinkedList<>();

		// We see a bunch of crashes on MediaTek Android TVs running
		// at 49 FPS (PAL 50 Hz - 1). Blacklist this frame rate for
		// these devices and hope they fix it in Pie.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
			blacklisted49FpsDecoderPrefixes.add("omx.mtk");
		}
	}

	private static boolean isPowerVR(String glRenderer) {
		return glRenderer.toLowerCase().contains("powervr");
	}

	private static String getAdrenoVersionString(String glRenderer) {
		glRenderer = glRenderer.toLowerCase().trim();

		if (!glRenderer.contains("adreno")) {
			return null;
		}

		Pattern modelNumberPattern = Pattern.compile("(.*)([0-9]{3})(.*)");

		Matcher matcher = modelNumberPattern.matcher(glRenderer);
		if (!matcher.matches()) {
			return null;
		}

		String modelNumber = matcher.group(2);
		LimeLog.info("Found Adreno GPU: "+modelNumber);
		return modelNumber;
	}

	private static boolean isLowEndSnapdragonRenderer(String glRenderer) {
		String modelNumber = getAdrenoVersionString(glRenderer);
		if (modelNumber == null) {
			// Not an Adreno GPU
			return false;
		}

		// The current logic is to identify low-end SoCs based on a zero in the x0x place.
		return modelNumber.charAt(1) == '0';
	}

	// This is a workaround for some broken devices that report
	// only GLES 3.0 even though the GPU is an Adreno 4xx series part.
	// An example of such a device is the Huawei Honor 5x with the
	// Snapdragon 616 SoC (Adreno 405).
	private static boolean isGLES31SnapdragonRenderer(String glRenderer) {
		String modelNumber = getAdrenoVersionString(glRenderer);
		if (modelNumber == null) {
			// Not an Adreno GPU
			return false;
		}

		// Snapdragon 4xx and higher support GLES 3.1
		return modelNumber.charAt(0) >= '4';
	}

	public static void initialize(Context context, String glRenderer) {
		if (initialized) {
			return;
		}

		ActivityManager activityManager =
				(ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
		if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
			LimeLog.info("OpenGL ES version: "+configInfo.reqGlEsVersion);

			isLowEndSnapdragon = isLowEndSnapdragonRenderer(glRenderer);

			// Tegra K1 and later can do reference frame invalidation properly
			if (configInfo.reqGlEsVersion >= 0x30000) {
				LimeLog.info("Added omx.nvidia to AVC reference frame invalidation support list");
				refFrameInvalidationAvcPrefixes.add("omx.nvidia");

				LimeLog.info("Added omx.qcom to AVC reference frame invalidation support list");
				refFrameInvalidationAvcPrefixes.add("omx.qcom");

				// Prior to M, we were tricking the decoder into using baseline profile, which
				// won't support RFI properly.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					LimeLog.info("Added omx.intel to AVC reference frame invalidation support list");
					refFrameInvalidationAvcPrefixes.add("omx.intel");
				}
			}

			// Qualcomm's early HEVC decoders break hard on our HEVC stream. The best check to
			// tell the good from the bad decoders are the generation of Adreno GPU included:
			// 3xx - bad
			// 4xx - good
			//
			// The "good" GPUs support GLES 3.1, but we can't just check that directly
			// (see comment on isGLES31SnapdragonRenderer).
			//
			if (isGLES31SnapdragonRenderer(glRenderer)) {
				// We prefer reference frame invalidation support (which is only doable on AVC on
				// older Qualcomm chips) vs. enabling HEVC by default. The user can override using the settings
				// to force HEVC on. If HDR or mobile data will be used, we'll override this and use
				// HEVC anyway.
				LimeLog.info("Added omx.qcom to deprioritized HEVC decoders based on GLES 3.1+ support");
				deprioritizedHevcDecoders.add("omx.qcom");
			}
			else {
				blacklistedDecoderPrefixes.add("OMX.qcom.video.decoder.hevc");
			}

			// Older MediaTek SoCs have issues with HEVC rendering but the newer chips with
			// PowerVR GPUs have good HEVC support.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPowerVR(glRenderer)) {
				LimeLog.info("Added omx.mtk to HEVC decoders based on PowerVR GPU");
				whitelistedHevcDecoders.add("omx.mtk");

				// This SoC (MT8176 in GPD XD+) supports AVC RFI too, but the maxNumReferenceFrames setting
				// required to make it work adds a huge amount of latency.
				LimeLog.info("Added omx.mtk to RFI list for HEVC");
				refFrameInvalidationHevcPrefixes.add("omx.mtk");
			}
		}

		initialized = true;
    }

	private static boolean isDecoderInList(List<String> decoderList, String decoderName) {
		if (!initialized) {
			throw new IllegalStateException("MediaCodecHelper must be initialized before use");
		}

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

	public static long getMonotonicMillis() {
		return System.nanoTime() / 1000000L;
	}

	public static boolean decoderSupportsAdaptivePlayback(MediaCodecInfo decoderInfo) {
		// Possibly enable adaptive playback on KitKat and above
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			if (isDecoderInList(blacklistedAdaptivePlaybackPrefixes, decoderInfo.getName())) {
				LimeLog.info("Decoder blacklisted for adaptive playback");
				return false;
			}

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
		}
		
		return false;
	}

	public static boolean decoderNeedsConstrainedHighProfile(String decoderName) {
		return isDecoderInList(constrainedHighProfilePrefixes, decoderName);
	}

    public static boolean decoderCanDirectSubmit(String decoderName) {
        return isDecoderInList(directSubmitPrefixes, decoderName) && !isExynos4Device();
    }
	
	public static boolean decoderNeedsSpsBitstreamRestrictions(String decoderName) {
		return isDecoderInList(spsFixupBitstreamFixupDecoderPrefixes, decoderName);
	}

    public static boolean decoderNeedsBaselineSpsHack(String decoderName) {
        return isDecoderInList(baselineProfileHackPrefixes, decoderName);
    }

	public static boolean decoderBlacklistedFor49Fps(String decoderName) {
		return isDecoderInList(blacklisted49FpsDecoderPrefixes, decoderName);
	}

	public static boolean decoderSupportsRefFrameInvalidationAvc(String decoderName, int videoHeight) {
		// Reference frame invalidation is broken on low-end Snapdragon SoCs at 1080p.
		if (videoHeight > 720 && isLowEndSnapdragon) {
			return false;
		}

		// This device seems to crash constantly at 720p, so try disabling
		// RFI to see if we can get that under control.
		if (Build.DEVICE.equals("b3") || Build.DEVICE.equals("b5")) {
			return false;
		}

		return isDecoderInList(refFrameInvalidationAvcPrefixes, decoderName);
	}

    public static boolean decoderSupportsRefFrameInvalidationHevc(String decoderName) {
        return isDecoderInList(refFrameInvalidationHevcPrefixes, decoderName);
    }

	public static boolean decoderIsWhitelistedForHevc(String decoderName, boolean meteredData) {
		// TODO: Shield Tablet K1/LTE?
		//
		// NVIDIA does partial HEVC acceleration on the Shield Tablet. I don't know
		// whether the performance is good enough to use for streaming, but they're
		// using the same omx.nvidia.h265.decode name as the Shield TV which has a
		// fully accelerated HEVC pipeline. AFAIK, the only K1 device with this
		// partially accelerated HEVC decoder is the Shield Tablet, so I'll
		// check for it here.
		//
		// TODO: Temporarily disabled with NVIDIA HEVC support
		/*if (Build.DEVICE.equalsIgnoreCase("shieldtablet")) {
			return false;
		}*/

		// Google didn't have official support for HEVC (or more importantly, a CTS test) until
		// Lollipop. I've seen some MediaTek devices on 4.4 crash when attempting to use HEVC,
		// so I'm restricting HEVC usage to Lollipop and higher.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			return false;
		}

		//
		// Software decoders are terrible and we never want to use them.
		// We want to catch decoders like:
		// OMX.qcom.video.decoder.hevcswvdec
		// OMX.SEC.hevc.sw.dec
		//
		if (decoderName.contains("sw")) {
			return false;
		}

		// Some devices have HEVC decoders that we prefer not to use
		// typically because it can't support reference frame invalidation.
		// However, we will use it for HDR and for streaming over mobile networks
		// since it works fine otherwise.
		if (meteredData && isDecoderInList(deprioritizedHevcDecoders, decoderName)) {
			LimeLog.info("Selected deprioritized decoder");
			return true;
		}

		return isDecoderInList(whitelistedHevcDecoders, decoderName);
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private static LinkedList<MediaCodecInfo> getMediaCodecList() {
		LinkedList<MediaCodecInfo> infoList = new LinkedList<>();
		
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

		if (!initialized) {
			throw new IllegalStateException("MediaCodecHelper must be initialized before use");
		}
		
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

	private static boolean isCodecBlacklisted(MediaCodecInfo codecInfo) {
		// Use the new isSoftwareOnly() function on Android Q
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			if (codecInfo.isSoftwareOnly()) {
				LimeLog.info("Skipping software-only decoder: "+codecInfo.getName());
				return true;
			}
		}

		// Check for explicitly blacklisted decoders
		if (isDecoderInList(blacklistedDecoderPrefixes, codecInfo.getName())) {
			LimeLog.info("Skipping blacklisted decoder: "+codecInfo.getName());
			return true;
		}

		return false;
	}
	
	public static MediaCodecInfo findFirstDecoder(String mimeType) {
		for (MediaCodecInfo codecInfo : getMediaCodecList()) {
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			// Find a decoder that supports the specified video format
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase(mimeType)) {
					// Skip blacklisted codecs
					if (isCodecBlacklisted(codecInfo)) {
						continue;
					}

					LimeLog.info("First decoder choice is "+codecInfo.getName());
					return codecInfo;
				}
			}
		}
		
		return null;
	}
	
	public static MediaCodecInfo findProbableSafeDecoder(String mimeType, int requiredProfile) {
		// First look for a preferred decoder by name
		MediaCodecInfo info = findPreferredDecoder();
		if (info != null) {
			return info;
		}
		
		// Now look for decoders we know are safe
		try {
			// If this function completes, it will determine if the decoder is safe
			return findKnownSafeDecoder(mimeType, requiredProfile);
		} catch (Exception e) {
			// Some buggy devices seem to throw exceptions
			// from getCapabilitiesForType() so we'll just assume
			// they're okay and go with the first one we find
			return findFirstDecoder(mimeType);
		}
	}

	// We declare this method as explicitly throwing Exception
	// since some bad decoders can throw IllegalArgumentExceptions unexpectedly
	// and we want to be sure all callers are handling this possibility
	@SuppressWarnings("RedundantThrows")
    private static MediaCodecInfo findKnownSafeDecoder(String mimeType, int requiredProfile) throws Exception {
		for (MediaCodecInfo codecInfo : getMediaCodecList()) {		
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			// Find a decoder that supports the requested video format
			for (String mime : codecInfo.getSupportedTypes()) {
				if (mime.equalsIgnoreCase(mimeType)) {
					LimeLog.info("Examining decoder capabilities of "+codecInfo.getName());

					// Skip blacklisted codecs
					if (isCodecBlacklisted(codecInfo)) {
						continue;
					}

					CodecCapabilities caps = codecInfo.getCapabilitiesForType(mime);

					if (requiredProfile != -1) {
						for (CodecProfileLevel profile : caps.profileLevels) {
							if (profile.profile == requiredProfile) {
								LimeLog.info("Decoder " + codecInfo.getName() + " supports required profile");
								return codecInfo;
							}
						}

						LimeLog.info("Decoder " + codecInfo.getName() + " does NOT support required profile");
					}
					else {
						return codecInfo;
					}
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
