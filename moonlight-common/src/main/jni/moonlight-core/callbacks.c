#include <jni.h>

#include <pthread.h>

#include <Limelight.h>

#include <opus_multistream.h>

#define PCM_FRAME_SIZE 240

static OpusMSDecoder* Decoder;
static OPUS_MULTISTREAM_CONFIGURATION OpusConfig;

static JavaVM *JVM;
static pthread_key_t JniEnvKey;
static pthread_once_t JniEnvKeyInitOnce = PTHREAD_ONCE_INIT;
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

void DetachThread(void* context) {
    (*JVM)->DetachCurrentThread(JVM);
}

void JniEnvKeyInit(void) {
    // Create a TLS slot for the JNIEnv. We aren't in
    // a pthread during init, so we must wait until we
    // are to initialize this.
    pthread_key_create(&JniEnvKey, DetachThread);
}

JNIEnv* GetThreadEnv(void) {
    JNIEnv* env;

    // First check if this is already attached to the JVM
    if ((*JVM)->GetEnv(JVM, (void**)&env, JNI_VERSION_1_4) == JNI_OK) {
        return env;
    }

    // Create the TLS slot now that we're safely in a pthread
    pthread_once(&JniEnvKeyInitOnce, JniEnvKeyInit);

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
    (*env)->GetJavaVM(env, &JVM);
    GlobalBridgeClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/limelight/nvstream/jni/MoonBridge"));
    BridgeDrSetupMethod = (*env)->GetStaticMethodID(env, class, "bridgeDrSetup", "(IIII)V");
    BridgeDrCleanupMethod = (*env)->GetStaticMethodID(env, class, "bridgeDrCleanup", "()V");
    BridgeDrSubmitDecodeUnitMethod = (*env)->GetStaticMethodID(env, class, "bridgeDrSubmitDecodeUnit", "([B)I");
    BridgeArInitMethod = (*env)->GetStaticMethodID(env, class, "bridgeArInit", "(I)V");
    BridgeArCleanupMethod = (*env)->GetStaticMethodID(env, class, "bridgeArCleanup", "()V");
    BridgeArPlaySampleMethod = (*env)->GetStaticMethodID(env, class, "bridgeArPlaySample", "([B)V");
    BridgeClStageStartingMethod = (*env)->GetStaticMethodID(env, class, "bridgeClStageStarting", "(I)V");
    BridgeClStageCompleteMethod = (*env)->GetStaticMethodID(env, class, "bridgeClStageComplete", "(I)V");
    BridgeClStageFailedMethod = (*env)->GetStaticMethodID(env, class, "bridgeClStageFailed", "(IJ)V");
    BridgeClConnectionStartedMethod = (*env)->GetStaticMethodID(env, class, "bridgeClConnectionStarted", "()V");
    BridgeClConnectionTerminatedMethod = (*env)->GetStaticMethodID(env, class, "bridgeClConnectionTerminated", "(J)V");
    BridgeClDisplayMessageMethod = (*env)->GetStaticMethodID(env, class, "bridgeClDisplayMessage", "(Ljava/lang/String;)V");
    BridgeClDisplayTransientMessageMethod = (*env)->GetStaticMethodID(env, class, "bridgeClDisplayTransientMessage", "(Ljava/lang/String;)V");
}

void BridgeDrSetup(int videoFormat, int width, int height, int redrawRate, void* context, int drFlags) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeDrSetupMethod, videoFormat, width, height, redrawRate);
}

void BridgeDrCleanup(void) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeDrCleanupMethod);
}

int BridgeDrSubmitDecodeUnit(PDECODE_UNIT decodeUnit) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return DR_OK;
    }

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

void BridgeArInit(int audioConfiguration, POPUS_MULTISTREAM_CONFIGURATION opusConfig) {
    JNIEnv* env = GetThreadEnv();
    int err;

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    memcpy(&OpusConfig, opusConfig, sizeof(*opusConfig));
    Decoder = opus_multistream_decoder_create(opusConfig->sampleRate,
                                              opusConfig->channelCount,
                                              opusConfig->streams,
                                              opusConfig->coupledStreams,
                                              opusConfig->mapping,
                                              &err);

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArInitMethod, audioConfiguration);
}

void BridgeArCleanup() {
    JNIEnv* env = GetThreadEnv();

    opus_multistream_decoder_destroy(Decoder);

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeArCleanupMethod);
}

void BridgeArDecodeAndPlaySample(char* sampleData, int sampleLength) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    jbyteArray decodedDataRef = (*env)->NewByteArray(env, OpusConfig.channelCount * PCM_FRAME_SIZE * sizeof(short));
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

void BridgeClStageStarting(int stage) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClStageStartingMethod, stage);
}

void BridgeClStageComplete(int stage) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClStageCompleteMethod, stage);
}

void BridgeClStageFailed(int stage, long errorCode) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClStageFailedMethod, stage, errorCode);
}

void BridgeClConnectionStarted(void) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClConnectionStartedMethod, NULL);
}

void BridgeClConnectionTerminated(long errorCode) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClConnectionTerminatedMethod, errorCode);
}

void BridgeClDisplayMessage(const char* message) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClDisplayMessageMethod, (*env)->NewStringUTF(env, message));
}

void BridgeClDisplayTransientMessage(const char* message) {
    JNIEnv* env = GetThreadEnv();

    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, GlobalBridgeClass, BridgeClDisplayTransientMessageMethod, (*env)->NewStringUTF(env, message));
}

static DECODER_RENDERER_CALLBACKS BridgeVideoRendererCallbacks = {
        .setup = BridgeDrSetup,
        .cleanup = BridgeDrCleanup,
        .submitDecodeUnit = BridgeDrSubmitDecodeUnit,
        .capabilities = CAPABILITY_SLICES_PER_FRAME(4), // HACK: This was common-java's default
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

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_startConnection(JNIEnv *env, jobject class,
                                                           jstring address, jstring appVersion, jstring gfeVersion,
                                                           jint width, jint height, jint fps,
                                                           jint bitrate, jboolean streamingRemotely,
                                                           jint audioConfiguration, jboolean supportsHevc,
                                                           jbyteArray riAesKey, jbyteArray riAesIv) {
    SERVER_INFORMATION serverInfo = {
            .address = (*env)->GetStringUTFChars(env, address, 0),
            .serverInfoAppVersion = (*env)->GetStringUTFChars(env, appVersion, 0),
            .serverInfoGfeVersion = (*env)->GetStringUTFChars(env, gfeVersion, 0),
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

    int ret = LiStartConnection(&serverInfo, &streamConfig, &BridgeConnListenerCallbacks, &BridgeVideoRendererCallbacks, &BridgeAudioRendererCallbacks, NULL, 0);

    (*env)->ReleaseStringUTFChars(env, address, serverInfo.address);
    (*env)->ReleaseStringUTFChars(env, appVersion, serverInfo.serverInfoAppVersion);
    (*env)->ReleaseStringUTFChars(env, gfeVersion, serverInfo.serverInfoGfeVersion);

    return ret;
}