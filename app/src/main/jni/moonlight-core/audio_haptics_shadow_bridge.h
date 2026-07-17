// SPDX-License-Identifier: GPL-3.0-or-later

#ifndef AUDIO_HAPTICS_SHADOW_BRIDGE_H
#define AUDIO_HAPTICS_SHADOW_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void audio_haptics_shadow_set_enabled(int enabled);
void audio_haptics_shadow_set_scene_mode(int mode);
void audio_haptics_shadow_init(int sample_rate, int channel_count);
void audio_haptics_shadow_process_frame(
    const int16_t* pcm_data,
    int frame_count,
    int reference_event);
void audio_haptics_shadow_maybe_log(void);
void audio_haptics_shadow_cleanup(void);
int audio_haptics_shadow_is_compiled(void);

#ifdef __cplusplus
}
#endif

#endif // AUDIO_HAPTICS_SHADOW_BRIDGE_H
