#include "NativeMicEncoder.h"

OpusEncoder* NativeMicEncoderCreate(void)
{
    int error;
    OpusEncoder* encoder = opus_encoder_create(NATIVE_MIC_SAMPLE_RATE, 1,
                                               OPUS_APPLICATION_RESTRICTED_LOWDELAY, &error);
    if (encoder == NULL) {
        return NULL;
    }
    if (opus_encoder_ctl(encoder, OPUS_SET_BITRATE(32000)) != OPUS_OK ||
        opus_encoder_ctl(encoder, OPUS_SET_VBR(0)) != OPUS_OK ||
        opus_encoder_ctl(encoder, OPUS_SET_DTX(0)) != OPUS_OK) {
        opus_encoder_destroy(encoder);
        return NULL;
    }
    return encoder;
}

int NativeMicEncode(OpusEncoder* encoder, const int16_t* pcm, size_t samples,
                    unsigned char* packet, size_t capacity)
{
    if (encoder == NULL || pcm == NULL || packet == NULL ||
        samples != NATIVE_MIC_FRAME_SAMPLES || capacity < NATIVE_MIC_FRAME_BYTES) {
        return OPUS_BAD_ARG;
    }
    int bytes = opus_encode(encoder, pcm, NATIVE_MIC_FRAME_SAMPLES, packet,
                            NATIVE_MIC_FRAME_BYTES);
    if (bytes < 0) {
        return bytes;
    }
    return bytes == NATIVE_MIC_FRAME_BYTES ? OPUS_OK : OPUS_INTERNAL_ERROR;
}
