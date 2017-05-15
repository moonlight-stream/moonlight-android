#include <jni.h>

#include <pthread.h>

#include <Limelight.h>

#include <opus_multistream.h>

#define PCM_FRAME_SIZE 240

static OpusMSDecoder* Decoder;
static OPUS_MULTISTREAM_CONFIGURATION OpusConfig;

static JavaVM *JVM;
static pthread_key_t JniEnvKey;
static jclass GlobalBridgeClass;
static jmethodID BridgeDrSetupMethod;
static jmethodID BridgeDrCleanupMethod;
static jmethodID BridgeDrSubmitDecodeUnitMethod;
static jmethodID BridgeArInitMethod;
static jmethodID BridgeArCleanupMethod;
static jmethodID BridgeArPlaySampleMethod;
static jmethodID BridgeClStageStartingMethod;
static jmethodID BridgeClStageCompleteMethod;
static jmethodID BridgeClStageFailedMethod;
static jmethodID BridgeClConnectionStartedMethod;
static jmethodID BridgeClConnectionTerminatedMethod;
static jmethodID BridgeClDisplayMessageMethod;
static jmethodID BridgeClDisplayTransientMessageMethod;

static void DetachThread(void* context) {
    (*JVM)->DetachCurrentThread(JVM);
}

static JNIEnv* GetThreadEnv(void) {
    JNIEnv* env;

    // Try the TLS to see if we already have a JNIEnv
    env = pthread_getspecific(JniEnvKey);
    if (env)
        return env;

    // This is the thread's first JNI call, so attach now
    (*JVM)->AttachCurrentThread(JVM, &env, NULL);

    // Write our JNIEnv to TLS, so we detach before dying
    pthread_setspecific(JniEnvKey, env);

    return env;
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_init(JNIEnv *env, jobject class) {
    GlobalBridgeClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/limelight/nvstream/jni/MoonBridge"));
    BridgeDrSetupMethod = (*env)->GetStaticMethodID(env, class, "bridgeDrSetup", "(IIII)V");
    BridgeDrCleanupMethod = (*env)->GetStaticMethodID(env, class, "bridgeDrCleanup", "()V");
    BridgeDrSubmitDecodeUnitMethod = (*env)->GetStaticMethodID(env, class, "bridgeDrSubmitDecodeUnit", "([B)I");
    BridgeArInitMethod = (*env)->GetStaticMethodID(env, class, "bridgeArInit", "(I)V");
    BridgeArCleanupMethod = (*env)->GetStaticMethodID(env, class, "bridgeArCleanup", "()V");
    BridgeArPlaySampleMethod = (*env)->GetStaticMethodID(env, class, "bridgeArPlaySample", "([B)V");
    BridgeClStageStartingMethod = (*env)->GetStaticMethodID(env, class, "bridgeClStageStarting", "(I)V");
    BridgeClStageCompleteMethod = (*env)->GetStaticMethodID(env, class, "bridgeClStageComplete", "(I)V");
    BridgeClStageFailedMethod = (*env)->GetStaticMethodID(env, class, "bridgeClStageFailed", "(IL)V");
    BridgeClConnectionStartedMethod = (*env)->GetStaticMethodID(env, class, "bridgeClConnectionStarted", "()V");
    BridgeClConnectionTerminatedMethod = (*env)->GetStaticMethodID(env, class, "bridgeClConnectionTerminated", "(L)V");
    BridgeClDisplayMessageMethod = (*env)->GetStaticMethodID(env, class, "bridgeClDisplayMessage", "(Ljava/lang/String;)V");
    BridgeClDisplayTransientMessageMethod = (*env)->GetStaticMethodID(env, class, "bridgeClDisplayTransientMessage", "(Ljava/lang/String;)V");

    // Create a TLS slot for the JNIEnv
    pthread_key_create(&JniEnvKey, DetachThread);
}

static void BridgeDrSetup(int videoFormat, int width, int height, int redrawRate, void* context, int drFlags) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeDrSetupMethod, videoFormat, width, height, redrawRate);
}

static void BridgeDrCleanup(void) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeDrCleanupMethod);
}

static int BridgeDrSubmitDecodeUnit(PDECODE_UNIT decodeUnit) {
    JNIEnv* env = GetThreadEnv();
    jbyteArray dataRef = (*env)->NewByteArray(env, decodeUnit->fullLength);
    jbyte* data = (*env)->GetByteArrayElements(env, dataRef, 0);
    PLENTRY currentEntry;
    int offset;

    currentEntry = decodeUnit->bufferList;
    offset = 0;
    while (currentEntry != NULL) {
        memcpy(&data[offset], currentEntry->data, currentEntry->length);
        offset += currentEntry->length;

        currentEntry = currentEntry->next;
    }

    // We must release the array elements first to ensure the data is copied before the callback
    (*env)->ReleaseByteArrayElements(env, dataRef, data, 0);

    int ret = (*env)->CallStaticIntMethod(env, GlobalBridgeClass, BridgeDrSubmitDecodeUnitMethod, dataRef);
    (*env)->DeleteLocalRef(env, dataRef);

    return ret;
}

static void BridgeArInit(int audioConfiguration, POPUS_MULTISTREAM_CONFIGURATION opusConfig) {
    JNIEnv* env = GetThreadEnv();
    int err;

    memcpy(&OpusConfig, opusConfig, sizeof(*opusConfig));
    Decoder = opus_multistream_decoder_create(opusConfig->sampleRate,
                                              opusConfig->channelCount,
                                              opusConfig->streams,
                                              opusConfig->coupledStreams,
                                              opusConfig->mapping,
                                              &err);

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArInitMethod, audioConfiguration);
}

static void BridgeArCleanup() {
    JNIEnv* env = GetThreadEnv();

    opus_multistream_decoder_destroy(Decoder);

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArCleanupMethod);
}

static void BridgeArDecodeAndPlaySample(char* sampleData, int sampleLength) {
    JNIEnv* env = GetThreadEnv();
    jbyteArray decodedDataRef = (*env)->NewByteArray(env, OpusConfig.channelCount * 240);
    jbyte* decodedData = (*env)->GetByteArrayElements(env, decodedDataRef, 0);

    int decodeLen = opus_multistream_decode(Decoder,
                                            (const unsigned char*)sampleData,
                                            sampleLength,
                                            (opus_int16*)decodedData,
                                            PCM_FRAME_SIZE,
                                            0);
    if (decodeLen > 0) {
        // We must release the array elements first to ensure the data is copied before the callback
        (*env)->ReleaseByteArrayElements(env, decodedDataRef, decodedData, 0);

        (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArPlaySampleMethod, decodedDataRef);
    }
    else {
        // We can abort here to avoid the copy back since no data was modified
        (*env)->ReleaseByteArrayElements(env, decodedDataRef, decodedData, JNI_ABORT);
    }

    (*env)->DeleteLocalRef(env, decodedDataRef);
}

static void BridgeClStageStarting(int stage) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClStageStartingMethod, stage);
}

static void BridgeClStageComplete(int stage) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClStageCompleteMethod, stage);
}

static void BridgeClStageFailed(int stage, long errorCode) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClStageFailedMethod, stage, errorCode);
}

static void BridgeClConnectionStarted(void) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClConnectionStartedMethod, NULL);
}

static void BridgeClConnectionTerminated(long errorCode) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClConnectionTerminatedMethod, errorCode);
}

static void BridgeClDisplayMessage(const char* message) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClDisplayMessageMethod, (*env)->NewStringUTF(env, message));
}

static void BridgeClDisplayTransientMessage(const char* message) {
    JNIEnv* env = GetThreadEnv();

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClDisplayTransientMessageMethod, (*env)->NewStringUTF(env, message));
}

static DECODER_RENDERER_CALLBACKS BridgeVideoRendererCallbacks = {
        .setup = BridgeDrSetup,
        .cleanup = BridgeDrCleanup,
        .submitDecodeUnit = BridgeDrSubmitDecodeUnit,
};

static AUDIO_RENDERER_CALLBACKS BridgeAudioRendererCallbacks = {
        .init = BridgeArInit,
        .cleanup = BridgeArCleanup,
        .decodeAndPlaySample = BridgeArDecodeAndPlaySample,
};

static CONNECTION_LISTENER_CALLBACKS BridgeConnListenerCallbacks = {
        .stageStarting = BridgeClStageStarting,
        .stageComplete = BridgeClStageComplete,
        .stageFailed = BridgeClStageFailed,
        .connectionStarted = BridgeClConnectionStarted,
        .connectionTerminated = BridgeClConnectionTerminated,
        .displayMessage = BridgeClDisplayMessage,
        .displayTransientMessage = BridgeClDisplayTransientMessage,
};

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_startConnection(JNIEnv *env, jobject class,
                                                           jstring address, jstring appVersion, jstring gfeVersion,
                                                           jint width, jint height, jint fps,
                                                           jint bitrate, jboolean streamingRemotely,
                                                           jint audioConfiguration, jboolean supportsHevc,
                                                           jbyteArray riAesKey, jbyteArray riAesIv) {
    SERVER_INFORMATION serverInfo = {
            .address = address,
            .serverInfoAppVersion = appVersion,
            .serverInfoGfeVersion = gfeVersion,
    };
    STREAM_CONFIGURATION streamConfig = {
            .width = width,
            .height = height,
            .fps = fps,
            .bitrate = bitrate,
            .streamingRemotely = streamingRemotely,
            .audioConfiguration = audioConfiguration,
            .supportsHevc = supportsHevc,
    };

    jbyte* riAesKeyBuf = (*env)->GetByteArrayElements(env, riAesKey, NULL);
    memcpy(streamConfig.remoteInputAesKey, riAesKeyBuf, sizeof(streamConfig.remoteInputAesKey));
    (*env)->ReleaseByteArrayElements(env, riAesKey, riAesKeyBuf, JNI_ABORT);

    jbyte* riAesIvBuf = (*env)->GetByteArrayElements(env, riAesIv, NULL);
    memcpy(streamConfig.remoteInputAesIv, riAesIvBuf, sizeof(streamConfig.remoteInputAesIv));
    (*env)->ReleaseByteArrayElements(env, riAesIv, riAesIvBuf, JNI_ABORT);

    LiStartConnection(&serverInfo, &streamConfig, &BridgeConnListenerCallbacks, &BridgeVideoRendererCallbacks, &BridgeAudioRendererCallbacks, NULL, 0);
}