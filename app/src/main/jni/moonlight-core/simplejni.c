#include <Limelight.h>

#include <jni.h>
#include <android/log.h>

#include <arpa/inet.h>
#include <string.h>
#include <stdlib.h>

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseMove(JNIEnv *env, jclass clazz, jshort deltaX, jshort deltaY) {
    LiSendMouseMoveEvent(deltaX, deltaY);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMousePosition(JNIEnv *env, jclass clazz,
        jshort x, jshort y, jshort referenceWidth, jshort referenceHeight) {
    LiSendMousePositionEvent(x, y, referenceWidth, referenceHeight);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseButton(JNIEnv *env, jclass clazz, jbyte buttonEvent, jbyte mouseButton) {
    LiSendMouseButtonEvent(buttonEvent, mouseButton);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMultiControllerInput(JNIEnv *env, jclass clazz, jshort controllerNumber,
                                                           jshort activeGamepadMask, jshort buttonFlags,
                                                           jbyte leftTrigger, jbyte rightTrigger,
                                                           jshort leftStickX, jshort leftStickY,
                                                           jshort rightStickX, jshort rightStickY) {
    LiSendMultiControllerEvent(controllerNumber, activeGamepadMask, buttonFlags,
        leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendControllerInput(JNIEnv *env, jclass clazz, jshort buttonFlags,
                                                      jbyte leftTrigger, jbyte rightTrigger,
                                                      jshort leftStickX, jshort leftStickY,
                                                      jshort rightStickX, jshort rightStickY) {
    LiSendControllerEvent(buttonFlags, leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendKeyboardInput(JNIEnv *env, jclass clazz, jshort keyCode, jbyte keyAction, jbyte modifiers) {
    LiSendKeyboardEvent(keyCode, keyAction, modifiers);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseScroll(JNIEnv *env, jclass clazz, jbyte scrollClicks) {
    LiSendScrollEvent(scrollClicks);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseHighResScroll(JNIEnv *env, jclass clazz, jshort scrollAmount) {
    LiSendHighResScrollEvent(scrollAmount);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_stopConnection(JNIEnv *env, jclass clazz) {
    LiStopConnection();
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_interruptConnection(JNIEnv *env, jclass clazz) {
    LiInterruptConnection();
}

JNIEXPORT jstring JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getStageName(JNIEnv *env, jclass clazz, jint stage) {
    return (*env)->NewStringUTF(env, LiGetStageName(stage));
}

JNIEXPORT jstring JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_findExternalAddressIP4(JNIEnv *env, jclass clazz, jstring stunHostName, jint stunPort) {
    int err;
    struct in_addr wanAddr;
    const char* stunHostNameStr = (*env)->GetStringUTFChars(env, stunHostName, NULL);

    err = LiFindExternalAddressIP4(stunHostNameStr, stunPort, &wanAddr.s_addr);
    (*env)->ReleaseStringUTFChars(env, stunHostName, stunHostNameStr);

    if (err == 0) {
        char addrStr[INET_ADDRSTRLEN];

        inet_ntop(AF_INET, &wanAddr, addrStr, sizeof(addrStr));

        __android_log_print(ANDROID_LOG_INFO, "moonlight-common-c", "Resolved WAN address to %s", addrStr);

        return (*env)->NewStringUTF(env, addrStr);
    }
    else {
        __android_log_print(ANDROID_LOG_ERROR, "moonlight-common-c", "STUN failed to get WAN address: %d", err);
        return NULL;
    }
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPendingAudioDuration(JNIEnv *env, jclass clazz) {
    return LiGetPendingAudioDuration();
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPendingVideoFrames(JNIEnv *env, jclass clazz) {
    return LiGetPendingVideoFrames();
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_testClientConnectivity(JNIEnv *env, jclass clazz, jstring testServerHostName, jint referencePort, jint testFlags) {
    int ret;
    const char* testServerHostNameStr = (*env)->GetStringUTFChars(env, testServerHostName, NULL);

    ret = LiTestClientConnectivity(testServerHostNameStr, (unsigned short)referencePort, testFlags);

    (*env)->ReleaseStringUTFChars(env, testServerHostName, testServerHostNameStr);

    return ret;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPortFromPortFlagIndex(JNIEnv *env, jclass clazz, jint portFlagIndex) {
    return LiGetPortFromPortFlagIndex(portFlagIndex);
}

JNIEXPORT jstring JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getProtocolFromPortFlagIndex(JNIEnv *env, jclass clazz, jint portFlagIndex) {
    int protocol = LiGetProtocolFromPortFlagIndex(portFlagIndex);
    return (*env)->NewStringUTF(env, protocol == IPPROTO_TCP ? "TCP" : "UDP");
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPortFlagsFromStage(JNIEnv *env, jclass clazz, jint stage) {
    return LiGetPortFlagsFromStage(stage);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPortFlagsFromTerminationErrorCode(JNIEnv *env, jclass clazz, jint errorCode) {
    return LiGetPortFlagsFromTerminationErrorCode(errorCode);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_nativeCopy(JNIEnv *env, jclass clazz, jobject buffer0,
                                                      jint offset0, jobject buffer1, jint offset1,
                                                      jint length) {
//    jclass cls = (*env)->GetObjectClass(env, buffer0);
//    jmethodID mid = (*env)->GetMethodID(env, cls, "limit", "(I)Ljava/nio/Buffer;");

    char *buf0 = (char*)(*env)->GetDirectBufferAddress(env, buffer0);
//    jlong capacity0 = (*env)->GetDirectBufferCapacity(env, buffer0);
    char *buf1 = (char*)(*env)->GetDirectBufferAddress(env, buffer1);
//    jlong capacity1 = (*env)->GetDirectBufferCapacity(env, buffer1);
//    int written = length;

    // Do something spectacular with the buffer...
    memcpy(buf1+offset1, buf0+offset0, length);

//    (*env)->CallObjectMethod(env, buffer0, mid, written);
//    (*env)->CallObjectMethod(env, buffer1, mid, written);
}

JNIEXPORT jobject JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_nativeCreate(JNIEnv *env, jclass clazz, jint size) {
    void* buf = malloc(size);
    jobject obj = (*env)->NewDirectByteBuffer(env, buf, size);
    return (*env)->NewGlobalRef(env, obj);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_nativeFree(JNIEnv *env, jclass clazz, jobject buffer) {
    void* buf = (*env)->GetDirectBufferAddress(env, buffer);
    free(buf);
}

#include "VideoDecoder/VideoDecoder.h"

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_createMediaCodec(JNIEnv *env, jclass clazz, jobject surface, jstring name,
                                                            jstring mime_type, jint width, jint height, jint refreshRate, jint prefsFps, jboolean lowLatency,
                                                            jboolean adaptivePlayback, jboolean maxOperatingRate, jboolean constrainedHighProfile, jboolean refFrameInvalidationActive, jboolean isExynos4) {
    const char *c_name = (*env)->GetStringUTFChars(env, name, 0);
    const char *c_mime_type = (*env)->GetStringUTFChars(env, mime_type, 0);

    VideoDecoder* videoDecoder = VideoDecoder_create(env, surface, c_name, c_mime_type, width, height, refreshRate, prefsFps, lowLatency, adaptivePlayback, maxOperatingRate);

    videoDecoder->constrainedHighProfile = constrainedHighProfile;
    videoDecoder->refFrameInvalidationActive = refFrameInvalidationActive;
    videoDecoder->isExynos4 = isExynos4;

    (*env)->ReleaseStringUTFChars(env, name, c_name);
    (*env)->ReleaseStringUTFChars(env, mime_type, c_mime_type);

    return (jlong)videoDecoder;
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_deleteMediaCodec(JNIEnv *env, jclass clazz,
                                                            jlong videoDecoder) {
//    ()video_codec;
    VideoDecoder_release((VideoDecoder*)videoDecoder);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_startMediaCodec(JNIEnv *env, jclass clazz,
                                                           jlong video_decoder) {

    VideoDecoder_start((VideoDecoder*)video_decoder);
}

//JNIEXPORT jint JNICALL
//Java_com_limelight_nvstream_jni_MoonBridge_submitDecodeUnit(JNIEnv *env, jclass clazz,
//                                                            jlong video_decoder,
//                                                            jobject decode_unit_data,
//                                                            jint decode_unit_length,
//                                                            jint decode_unit_type,
//                                                            jint frame_number,
//                                                            jlong receive_time_ms) {
//    void* data = (*env)->GetDirectBufferAddress(env, decode_unit_data);
//    return VideoDecoder_submitDecodeUnit((VideoDecoder*)video_decoder, data, decode_unit_length, decode_unit_type, frame_number, receive_time_ms);
//}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_stopMediaCodec(JNIEnv *env, jclass clazz,
                                                          jlong video_decoder) {
    VideoDecoder_stop((VideoDecoder*)video_decoder);
}

JNIEXPORT jstring JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_formatDecoderInfo(JNIEnv *env, jclass clazz,
                                                             jlong video_decoder, jstring format) {
    const char* c_format = (*env)->GetStringUTFChars(env, format, 0);

    const char* result = VideoDecoder_formatInfo((VideoDecoder*)video_decoder, c_format);

    (*env)->ReleaseStringUTFChars(env, format, c_format);
    
    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getVideoStats(JNIEnv *env, jclass clazz,
                                                         jlong video_decoder, jobject stats) {

    VideoDecoder* videoDecoder = (VideoDecoder*)video_decoder;

    jclass class = (*env)->FindClass(env,"com/limelight/binding/video/VideoStats");
//    jmethodID cid = (*env)->GetMethodID(env, class, "<init>", "()V");
//    jobject stats = (*env)->NewObject(env, class, cid);

    VideoStats vs = videoDecoder->globalVideoStats;

    jfieldID fid;
    fid = (*env)->GetFieldID(env, class, "decoderTimeMs", "J");
    if (fid) (*env)->SetLongField(env, stats, fid, (jlong)vs.decoderTimeMs);

    fid = (*env)->GetFieldID(env, class, "totalTimeMs", "J");
    if (fid) (*env)->SetLongField(env, stats, fid, (jlong)vs.totalTimeMs);

    fid = (*env)->GetFieldID(env, class, "totalFrames", "I");
    if (fid) (*env)->SetIntField(env, stats, fid, vs.totalFrames);

    fid = (*env)->GetFieldID(env, class, "totalFramesReceived", "I");
    if (fid) (*env)->SetIntField(env, stats, fid, vs.totalFramesReceived);

    fid = (*env)->GetFieldID(env, class, "totalFramesRendered", "I");
    if (fid) (*env)->SetIntField(env, stats, fid, vs.totalFramesRendered);

    fid = (*env)->GetFieldID(env, class, "frameLossEvents", "I");
    if (fid) (*env)->SetIntField(env, stats, fid, vs.frameLossEvents);

    fid = (*env)->GetFieldID(env, class, "framesLost", "I");
    if (fid) (*env)->SetIntField(env, stats, fid, vs.framesLost);

    fid = (*env)->GetFieldID(env, class, "measurementStartTimestamp", "J");
    if (fid) (*env)->SetLongField(env, stats, fid, (jlong)vs.measurementStartTimestamp);

}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setBufferCount(JNIEnv *env, jclass clazz,
                                                          jlong video_decoder, jint buffer_count) {

    ((VideoDecoder*)video_decoder)->bufferCount = buffer_count;
}