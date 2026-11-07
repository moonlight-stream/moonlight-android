#ifndef NATIVE_MIC_ENCODER_H
#define NATIVE_MIC_ENCODER_H

#include <stddef.h>
#include <stdint.h>
#include <opus.h>

enum {
    NATIVE_MIC_SAMPLE_RATE = 48000,
    NATIVE_MIC_FRAME_SAMPLES = 240,
    NATIVE_MIC_FRAME_BYTES = 20
};

// Serialize encoder use and destruction. Ownership may transfer before encoding.
OpusEncoder* NativeMicEncoderCreate(void);
// Returns zero for a complete packet and a negative Opus error otherwise.
int NativeMicEncode(OpusEncoder* encoder, const int16_t* pcm, size_t samples,
                    unsigned char* packet, size_t capacity);

#endif
