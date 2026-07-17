/*
 * C-compatible bridge for BassEnergyAnalyzer (C++ engine)
 *
 * Exposes a pure C API that can be called from callbacks.c
 * while the actual DSP processing runs in C++.
 */

#ifndef BASS_ENERGY_BRIDGE_H
#define BASS_ENERGY_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize the bass energy analyzer.
 * Call once when audio renderer initializes (in BridgeArInit).
 *
 * @param sampleRate   Sample rate (typically 48000)
 * @param channelCount Number of audio channels
 */
void bass_energy_init(int sampleRate, int channelCount);

/**
 * Enable or disable bass energy analysis.
 * @param enabled  1 = enable, 0 = disable
 */
void bass_energy_set_enabled(int enabled);

/** Keep reference analysis active for shadow comparison without Java output. */
void bass_energy_set_shadow_enabled(int enabled);

/**
 * Set analysis sensitivity (0.1 - 3.0, default 1.0).
 */
void bass_energy_set_sensitivity(float sensitivity);

/**
 * Set scene mode:
 *   0 = Game/Movie (continuous low-freq vibration)
 *   1 = Music/Rhythm (onset-based pulse vibration)
 *   2 = Auto (automatic content detection)
 */
void bass_energy_set_scene_mode(int mode);

/**
 * Process one frame of decoded PCM data.
 * Call from BridgeArDecodeAndPlaySample after opus_decode.
 *
 * IMPORTANT: This is a pure C++ computation with no JNI calls,
 * safe to call inside GetPrimitiveArrayCritical region.
 *
 * @param pcmData        PCM 16-bit signed interleaved samples
 * @param sampleCount    Per-channel sample count (decodeLen from opus)
 * @param outIntensity   Output vibration intensity (0-100)
 * @param outLowFreqRatio Output low-freq energy ratio (0-100), for low/high motor allocation
 * @return 1 if intensity should be reported (throttle-controlled), 0 otherwise
 */
int bass_energy_process_frame(
    const int16_t* pcmData,
    int sampleCount,
    int* outIntensity,
    int* outLowFreqRatio,
    int* outReferenceEvent);

#ifdef __cplusplus
}
#endif

#endif /* BASS_ENERGY_BRIDGE_H */
