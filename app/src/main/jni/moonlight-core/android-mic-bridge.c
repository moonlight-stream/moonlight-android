#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "Limelight.h"
#include "NativeMicEncoder.h"

typedef struct {
    NATIVE_MIC_SENDER* sender;
    OpusEncoder* encoder;
} AndroidMic;

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_jni_NativeMicCapture_create(JNIEnv* env, jclass type)
{
    (void)env;
    (void)type;
    AndroidMic* mic = calloc(1, sizeof(*mic));
    if (!mic) {
        return 0;
    }
    mic->encoder = NativeMicEncoderCreate();
    if (mic->encoder) {
        mic->sender = LiCreateNativeMicSender();
    }
    if (!mic->sender) {
        if (mic->encoder) {
            opus_encoder_destroy(mic->encoder);
        }
        free(mic);
        return 0;
    }
    return (jlong)(intptr_t)mic;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_NativeMicCapture_send(JNIEnv* env, jclass type,
                                                     jlong handle, jshortArray frame)
{
    (void)type;
    AndroidMic* mic = (AndroidMic*)(intptr_t)handle;
    if (!mic || !frame || (*env)->GetArrayLength(env, frame) != NATIVE_MIC_FRAME_SAMPLES) {
        return -1;
    }
    jshort pcm[NATIVE_MIC_FRAME_SAMPLES];
    unsigned char packet[NATIVE_MIC_FRAME_BYTES];
    (*env)->GetShortArrayRegion(env, frame, 0, NATIVE_MIC_FRAME_SAMPLES, pcm);
    if ((*env)->ExceptionCheck(env) ||
        NativeMicEncode(mic->encoder, pcm, NATIVE_MIC_FRAME_SAMPLES,
                        packet, sizeof(packet)) != OPUS_OK) {
        return -1;
    }
    return LiSendNativeMicFrame(mic->sender, packet, sizeof(packet));
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_NativeMicCapture_destroy(JNIEnv* env, jclass type, jlong handle)
{
    (void)env;
    (void)type;
    AndroidMic* mic = (AndroidMic*)(intptr_t)handle;
    if (mic) {
        LiDestroyNativeMicSender(mic->sender);
        opus_encoder_destroy(mic->encoder);
        free(mic);
    }
}
