// SPDX-License-Identifier: GPL-3.0-or-later

#ifndef AUDIO_HAPTICS_ANDROID_ADAPTER_BRIDGE_H
#define AUDIO_HAPTICS_ANDROID_ADAPTER_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void audio_haptics_android_adapter_set_session_handle(uint64_t handle);
void audio_haptics_android_adapter_set_enabled(int enabled);
void audio_haptics_android_adapter_set_scene_mode(int mode);
void audio_haptics_android_adapter_init(int sample_rate, int channel_count);
int audio_haptics_android_adapter_process_frame(
    const int16_t* pcm_data,
    int frame_count);
void audio_haptics_android_adapter_notify(void);
void audio_haptics_android_adapter_cleanup(void);

#ifdef __cplusplus
}
#endif

#endif // AUDIO_HAPTICS_ANDROID_ADAPTER_BRIDGE_H
