This file serves to document some of the decoder errata when using MediaCodec hardware decoders on certain devices.

1. num_ref_frames is set to 16 by NVENC which causes decoders to allocate 16+ buffers. This can cause an error or lag on some devices.
	- Affected decoders: TI OMAP4 crashes, Allwinner A20, MT8176 lags (HEVC not affected)
	
2. Some H.264 decoders have a huge per-frame latency with the unmodified SPS sent from NVENC. Setting max_dec_frame_buffering=1 fixes this latency issue.
	- Affected decoders: NVIDIA Tegra 3 and 4, Broadcom VideoCore IV

3. Some decoders strictly require that you pass BUFFER_FLAG_CODEC_CONFIG and crash upon the IDR frame if you don't
	- Affected decoders: TI OMAP4

4. Some decoders require num_ref_frames=1 and max_dec_frame_buffering=1 to avoid crashing on SPS on first I-frame
	- Affected decoders: Qualcomm in GS3 on 4.3+, Exynos 4 at 1080p only

5. Some decoders will hang or crash if max_dec_frame_buffering is not present and level_idc is >= 50
	- Affected decoders: MediaTek decoder in Fire HD 6/7 (2014)
	
6. Some decoders will hang if max_dec_frame_buffering IS present
	- Affected decoders: Exynos 5 in Galaxy Note 10.1 (2014)

7. Some decoders will not enter low latency mode if adaptive playback is enabled
	- Affected decoders: Intel decoder in Nexus Player

8. Some decoders will not enter low latency mode if the profile isn't baseline in the first SPS because B-frames may be present.
	- Affected decoders: Intel decoder in Nexus Player (prior to Android 6.0)

9. Some decoders will not enter low latency mode if the profile isn't constrained high profile because B-frames may be present.
	- Affected decoders: Intel decoder in Nexus Player (after Android 6.0)

10. Some decoders actually suffer increased latency when max_dec_frame_buffering=1
	- Affected decoders: MediaTek decoder in Fire TV 2015

11. Attempting to use reference picture invalidation at 1080p causes the decoder to crash on low-end Snapdragon SoCs. 720p is unaffected.
	- Affected decoders: Snapdragon 200, 410, 415, 430, 435, 616

12. Enabling adaptive playback causes H.265 1080p and 4K playback to fail on some MediaTek SoCs.
	- Affected decoders: MT5832 in Sony BRAVIA 4K GB (BRAVIA_ATV2) and MT5890 in Phillips 55PUS6501

13. Some HEVC decoders hang when receiving a stream with 16 reference frames
	- Affected decoders: Amlogic S905Z in Fire TV 3

14. Some HEVC decoders lag when receiving a stream with 16 reference frames
	- Affected decoders: Tegra X1 in Pixel C (but NOT in SHIELD TV darcy)

15. Some devices that support Android 11's FEATURE_LowLatency don't support it on their first compatible H.264/HEVC decoder. It is important to examine *all* decoders for FEATURE_LowLatency before deciding on one.
    - Affected devices: Pixel 4 (c2.qti.avc.decoder.low_latency vs c2.qti.avc.decoder) and Galaxy S21 Exynos (OMX.Exynos.avc.dec [FEATURE_LowLatency] vs C2.Exynos.avc.decoder [no FEATURE_LowLatency])

16. Some decoder have magic undocumented MediaFormat options to enable low latency prior to the introduction of KEY_LOW_LATENCY in Android 11. See MediaCodecHelper.java for info.
    - Affected devices: MediaTek, Amlogic, Amazon, Qualcomm, Exynos, Huawei

17. Fire TV 3's Amlogic HEVC decoder doesn't produce any output frames without setting the magic "vdec-lowlatency" MediaFormat option
    - Affected devices: Fire TV 3