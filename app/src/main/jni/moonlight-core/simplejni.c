#include <Limelight.h>
#include "Limelight-internal.h"

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>

#include <arpa/inet.h>
#include <string.h>

#include "minisdl.h"
#include "controller_type.h"
#include "controller_list.h"

extern uint16_t MicPortNumber;
extern STREAM_CONFIGURATION StreamConfig;
extern uint32_t EncryptionFeaturesEnabled;

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
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseMoveAsMousePosition(JNIEnv *env, jclass clazz,
        jshort deltaX, jshort deltaY, jshort referenceWidth, jshort referenceHeight) {
    LiSendMouseMoveAsMousePositionEvent(deltaX, deltaY, referenceWidth, referenceHeight);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseButton(JNIEnv *env, jclass clazz, jbyte buttonEvent, jbyte mouseButton) {
    LiSendMouseButtonEvent(buttonEvent, mouseButton);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMultiControllerInput(JNIEnv *env, jclass clazz, jshort controllerNumber,
                                                           jshort activeGamepadMask, jint buttonFlags,
                                                           jbyte leftTrigger, jbyte rightTrigger,
                                                           jshort leftStickX, jshort leftStickY,
                                                           jshort rightStickX, jshort rightStickY) {
    LiSendMultiControllerEvent(controllerNumber, activeGamepadMask, buttonFlags,
        leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendTouchEvent(JNIEnv *env, jclass clazz,
                                                          jbyte eventType, jint pointerId,
                                                          jfloat x, jfloat y, jfloat pressureOrDistance,
                                                          jfloat contactAreaMajor, jfloat contactAreaMinor,
                                                          jshort rotation) {
    return LiSendTouchEvent(eventType, pointerId, x, y, pressureOrDistance,
                            contactAreaMajor, contactAreaMinor, rotation);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendTouchpadEvent(JNIEnv *env, jclass clazz,
                                                             jbyte eventType, jint pointerId,
                                                             jfloat x, jfloat y, jfloat pressure,
                                                             jfloat contactAreaMajor, jfloat contactAreaMinor,
                                                             jshort rotation, jshort deviceWidthMm,
                                                             jshort deviceHeightMm, jbyte buttonState) {
    return LiSendTouchpadEvent(eventType, pointerId, x, y, pressure,
                               contactAreaMajor, contactAreaMinor, rotation,
                               deviceWidthMm, deviceHeightMm, buttonState);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendTouchpadFrameEvent(JNIEnv *env, jclass clazz,
                                                                  jbyte contactCount,
                                                                  jbyteArray eventTypesArray,
                                                                  jintArray pointerIdsArray,
                                                                  jfloatArray xArray,
                                                                  jfloatArray yArray,
                                                                  jfloatArray pressureArray,
                                                                  jshort rotation,
                                                                  jshort deviceWidthMm,
                                                                  jshort deviceHeightMm,
                                                                  jbyte buttonState) {
    uint8_t count = (uint8_t)contactCount;
    uint8_t eventTypes[SS_TOUCHPAD_FRAME_MAX_CONTACTS];
    uint32_t pointerIds[SS_TOUCHPAD_FRAME_MAX_CONTACTS];
    float x[SS_TOUCHPAD_FRAME_MAX_CONTACTS];
    float y[SS_TOUCHPAD_FRAME_MAX_CONTACTS];
    float pressure[SS_TOUCHPAD_FRAME_MAX_CONTACTS];

    if (count > SS_TOUCHPAD_FRAME_MAX_CONTACTS) {
        return -3;
    }

    if (count > 0 &&
        (eventTypesArray == NULL || pointerIdsArray == NULL || xArray == NULL || yArray == NULL || pressureArray == NULL ||
         (*env)->GetArrayLength(env, eventTypesArray) < count ||
         (*env)->GetArrayLength(env, pointerIdsArray) < count ||
         (*env)->GetArrayLength(env, xArray) < count ||
         (*env)->GetArrayLength(env, yArray) < count ||
         (*env)->GetArrayLength(env, pressureArray) < count)) {
        return -3;
    }

    if (count > 0) {
        jbyte* eventTypesJava = (*env)->GetByteArrayElements(env, eventTypesArray, NULL);
        jint* pointerIdsJava = (*env)->GetIntArrayElements(env, pointerIdsArray, NULL);
        jfloat* xJava = (*env)->GetFloatArrayElements(env, xArray, NULL);
        jfloat* yJava = (*env)->GetFloatArrayElements(env, yArray, NULL);
        jfloat* pressureJava = (*env)->GetFloatArrayElements(env, pressureArray, NULL);

        if (eventTypesJava == NULL || pointerIdsJava == NULL || xJava == NULL || yJava == NULL || pressureJava == NULL) {
            if (eventTypesJava != NULL) {
                (*env)->ReleaseByteArrayElements(env, eventTypesArray, eventTypesJava, JNI_ABORT);
            }
            if (pointerIdsJava != NULL) {
                (*env)->ReleaseIntArrayElements(env, pointerIdsArray, pointerIdsJava, JNI_ABORT);
            }
            if (xJava != NULL) {
                (*env)->ReleaseFloatArrayElements(env, xArray, xJava, JNI_ABORT);
            }
            if (yJava != NULL) {
                (*env)->ReleaseFloatArrayElements(env, yArray, yJava, JNI_ABORT);
            }
            if (pressureJava != NULL) {
                (*env)->ReleaseFloatArrayElements(env, pressureArray, pressureJava, JNI_ABORT);
            }
            return -1;
        }

        for (uint8_t i = 0; i < count; i++) {
            eventTypes[i] = (uint8_t)eventTypesJava[i];
            pointerIds[i] = (uint32_t)pointerIdsJava[i];
            x[i] = xJava[i];
            y[i] = yJava[i];
            pressure[i] = pressureJava[i];
        }

        (*env)->ReleaseByteArrayElements(env, eventTypesArray, eventTypesJava, JNI_ABORT);
        (*env)->ReleaseIntArrayElements(env, pointerIdsArray, pointerIdsJava, JNI_ABORT);
        (*env)->ReleaseFloatArrayElements(env, xArray, xJava, JNI_ABORT);
        (*env)->ReleaseFloatArrayElements(env, yArray, yJava, JNI_ABORT);
        (*env)->ReleaseFloatArrayElements(env, pressureArray, pressureJava, JNI_ABORT);
    }

    return LiSendTouchpadFrameEvent(count, eventTypes, pointerIds, x, y, pressure,
                                    rotation, deviceWidthMm, deviceHeightMm, buttonState);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendPenEvent(JNIEnv *env, jclass clazz, jbyte eventType,
                                                        jbyte toolType, jbyte penButtons,
                                                        jfloat x, jfloat y, jfloat pressureOrDistance,
                                                        jfloat contactAreaMajor, jfloat contactAreaMinor,
                                                        jshort rotation, jbyte tilt) {
    return LiSendPenEvent(eventType, toolType, penButtons, x, y, pressureOrDistance,
                          contactAreaMajor, contactAreaMinor, rotation, tilt);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendControllerArrivalEvent(JNIEnv *env, jclass clazz,
                                                                      jbyte controllerNumber,
                                                                      jshort activeGamepadMask,
                                                                      jbyte type,
                                                                      jint supportedButtonFlags,
                                                                      jshort capabilities) {
    return LiSendControllerArrivalEvent(controllerNumber, activeGamepadMask, type, supportedButtonFlags, capabilities);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendControllerTouchEvent(JNIEnv *env, jclass clazz,
                                                                    jbyte controllerNumber,
                                                                    jbyte eventType,
                                                                    jint pointerId, jfloat x,
                                                                    jfloat y, jfloat pressure) {
    return LiSendControllerTouchEvent(controllerNumber, eventType, pointerId, x, y, pressure);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendControllerMotionEvent(JNIEnv *env, jclass clazz,
                                                                     jbyte controllerNumber,
                                                                     jbyte motionType, jfloat x,
                                                                     jfloat y, jfloat z) {
    return LiSendControllerMotionEvent(controllerNumber, motionType, x, y, z);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendControllerBatteryEvent(JNIEnv *env, jclass clazz,
                                                                      jbyte controllerNumber,
                                                                      jbyte batteryState,
                                                                      jbyte batteryPercentage) {
    return LiSendControllerBatteryEvent(controllerNumber, batteryState, batteryPercentage);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendKeyboardInput(JNIEnv *env, jclass clazz, jshort keyCode, jbyte keyAction, jbyte modifiers, jbyte flags) {
    LiSendKeyboardEvent2(keyCode, keyAction, modifiers, flags);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseHighResScroll(JNIEnv *env, jclass clazz, jshort scrollAmount) {
    LiSendHighResScrollEvent(scrollAmount);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMouseHighResHScroll(JNIEnv *env, jclass clazz, jshort scrollAmount) {
    LiSendHighResHScrollEvent(scrollAmount);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendUtf8Text(JNIEnv *env, jclass clazz, jstring text) {
    const char* utf8Text = (*env)->GetStringUTFChars(env, text, NULL);
    LiSendUtf8TextEvent(utf8Text, strlen(utf8Text));
    (*env)->ReleaseStringUTFChars(env, text, utf8Text);
}

// Forward an opaque clipboard payload to the host. Wire format (the v1 frame
// with kind/token/length) is built on the Java side; native passes bytes as-is.
JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendClipboardFrameNative(JNIEnv *env, jclass clazz, jbyteArray payload) {
    if (payload == NULL) {
        return -1;
    }
    jsize length = (*env)->GetArrayLength(env, payload);
    if (length <= 0) {
        return -1;
    }
    jbyte* data = (*env)->GetByteArrayElements(env, payload, NULL);
    if (data == NULL) {
        return -1;
    }
    int rc = LiSendClipboardData((const void*)data, (int)length);
    (*env)->ReleaseByteArrayElements(env, payload, data, JNI_ABORT);
    return rc;
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
Java_com_limelight_nvstream_jni_MoonBridge_getPortFlagsFromStage(JNIEnv *env, jclass clazz, jint stage) {
    return LiGetPortFlagsFromStage(stage);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPortFlagsFromTerminationErrorCode(JNIEnv *env, jclass clazz, jint errorCode) {
    return LiGetPortFlagsFromTerminationErrorCode(errorCode);
}

JNIEXPORT jstring JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_stringifyPortFlags(JNIEnv *env, jclass clazz, jint portFlags, jstring separator) {
    const char* separatorStr = (*env)->GetStringUTFChars(env, separator, NULL);
    char outputBuffer[512];

    LiStringifyPortFlags(portFlags, separatorStr, outputBuffer, sizeof(outputBuffer));

    (*env)->ReleaseStringUTFChars(env, separator, separatorStr);
    return (*env)->NewStringUTF(env, outputBuffer);
}

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getEstimatedRttInfo(JNIEnv *env, jclass clazz) {
    uint32_t rtt, variance;

    if (!LiGetEstimatedRttInfo(&rtt, &variance)) {
        return -1;
    }

    return ((uint64_t)rtt << 32U) | variance;
}

JNIEXPORT jstring JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getLaunchUrlQueryParameters(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, LiGetLaunchUrlQueryParameters());
}

JNIEXPORT jbyte JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_guessControllerType(JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    unsigned int unDeviceID = MAKE_CONTROLLER_ID(vendorId, productId);
    for (int i = 0; i < sizeof(arrControllers) / sizeof(arrControllers[0]); i++) {
        if (unDeviceID == arrControllers[i].m_unDeviceID) {
            switch (arrControllers[i].m_eControllerType) {
                case k_eControllerType_XBox360Controller:
                case k_eControllerType_XBoxOneController:
                    return LI_CTYPE_XBOX;

                case k_eControllerType_PS3Controller:
                case k_eControllerType_PS4Controller:
                case k_eControllerType_PS5Controller:
                    return LI_CTYPE_PS;

                case k_eControllerType_WiiController:
                case k_eControllerType_SwitchProController:
                case k_eControllerType_SwitchJoyConLeft:
                case k_eControllerType_SwitchJoyConRight:
                case k_eControllerType_SwitchJoyConPair:
                case k_eControllerType_SwitchInputOnlyController:
                    return LI_CTYPE_NINTENDO;

                default:
                    return LI_CTYPE_UNKNOWN;
            }
        }
    }
    return LI_CTYPE_UNKNOWN;
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_guessControllerHasPaddles(JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    // Xbox Elite and DualSense Edge controllers have paddles
    return SDL_IsJoystickXboxOneElite(vendorId, productId) || SDL_IsJoystickDualSenseEdge(vendorId, productId);
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_guessControllerHasShareButton(JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    // Xbox Elite and DualSense Edge controllers have paddles
    return SDL_IsJoystickXboxSeriesX(vendorId, productId);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getHostFeatureFlags(JNIEnv *env, jclass clazz) {
    return LiGetHostFeatureFlags();
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getNegotiatedAudioCodec(JNIEnv *env, jclass clazz) {
    return LiGetNegotiatedAudioCodec();
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getNegotiatedAudioBitrate(JNIEnv *env, jclass clazz) {
    return LiGetNegotiatedAudioBitrate();
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getMicPortNumber(JNIEnv *env, jclass clazz) {
    return MicPortNumber;
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_isMicrophoneRequested(JNIEnv *env, jclass clazz) {
    // Microphone is requested if port is negotiated and enableMic is set
    return (MicPortNumber != 0 && StreamConfig.enableMic) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendMicrophoneOpusData(JNIEnv *env, jclass clazz, jbyteArray opusData) {
    if (opusData == NULL) {
        return -1;
    }
    
    jsize length = (*env)->GetArrayLength(env, opusData);
    if (length <= 0) {
        return -1;
    }
    
    jbyte* data = (*env)->GetByteArrayElements(env, opusData, NULL);
    if (data == NULL) {
        return -1;
    }
    
    int result = sendMicrophoneOpusData((const unsigned char*)data, (int)length);
    
    (*env)->ReleaseByteArrayElements(env, opusData, data, JNI_ABORT);
    
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_isMicrophoneEncryptionEnabled(JNIEnv *env, jclass clazz) {
    return isMicrophoneEncryptionEnabled() ? JNI_TRUE : JNI_FALSE;
}

// ==================== Bass Energy Analyzer Control ====================

#include "bass_energy_bridge.h"
#include "audio_haptics_shadow_bridge.h"
#include "audio_haptics_android_adapter_bridge.h"

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setBassEnergyEnabled(JNIEnv *env, jclass clazz, jboolean enabled) {
    bass_energy_set_enabled(enabled ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setBassEnergySensitivity(JNIEnv *env, jclass clazz, jfloat sensitivity) {
    bass_energy_set_sensitivity(sensitivity);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setBassEnergySceneMode(JNIEnv *env, jclass clazz, jint mode) {
    bass_energy_set_scene_mode(mode);
    audio_haptics_shadow_set_scene_mode(mode);
    audio_haptics_android_adapter_set_scene_mode(mode);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setAudioHapticsShadowEnabled(JNIEnv *env, jclass clazz, jboolean enabled) {
    (void)env;
    (void)clazz;
    bass_energy_set_shadow_enabled(enabled ? 1 : 0);
    audio_haptics_shadow_set_enabled(enabled ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setAudioHapticsOutputEnabled(JNIEnv *env, jclass clazz, jboolean enabled) {
    (void)env;
    (void)clazz;
    audio_haptics_android_adapter_set_enabled(enabled ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_setAudioHapticsSessionHandle(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    audio_haptics_android_adapter_set_session_handle((uint64_t)handle);
}

// ==================== Surface DataSpace Control ====================
// Equivalent to HarmonyOS OH_NativeWindow_SetColorSpace()
// Uses ANativeWindow_setBuffersDataSpace() (API 28+) via dlsym

typedef int32_t (*pfn_ANativeWindow_setBuffersDataSpace)(ANativeWindow*, int32_t);
typedef int32_t (*pfn_ANativeWindow_getBuffersDataSpace)(ANativeWindow*);

// Resolve native window DataSpace functions. Try RTLD_DEFAULT first (already loaded),
// then explicitly dlopen libnativewindow.so (required on some OEM ROMs like ColorOS).
static pfn_ANativeWindow_setBuffersDataSpace setDataSpaceFunc = NULL;
static pfn_ANativeWindow_getBuffersDataSpace getDataSpaceFunc = NULL;
static int dataspace_resolved = 0;

static void resolveDataSpaceFuncs() {
    if (dataspace_resolved) return;
    dataspace_resolved = 1;

    setDataSpaceFunc = (pfn_ANativeWindow_setBuffersDataSpace)
        dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersDataSpace");
    getDataSpaceFunc = (pfn_ANativeWindow_getBuffersDataSpace)
        dlsym(RTLD_DEFAULT, "ANativeWindow_getBuffersDataSpace");

    if (!setDataSpaceFunc || !getDataSpaceFunc) {
        // Explicitly load libnativewindow.so - on some OEMs it's not in the default search
        void *nwLib = dlopen("libnativewindow.so", RTLD_NOW);
        if (nwLib) {
            if (!setDataSpaceFunc) {
                setDataSpaceFunc = (pfn_ANativeWindow_setBuffersDataSpace)
                    dlsym(nwLib, "ANativeWindow_setBuffersDataSpace");
            }
            if (!getDataSpaceFunc) {
                getDataSpaceFunc = (pfn_ANativeWindow_getBuffersDataSpace)
                    dlsym(nwLib, "ANativeWindow_getBuffersDataSpace");
            }
            // Don't dlclose - keep the library loaded
        }
    }

    __android_log_print(ANDROID_LOG_INFO, "MoonBridge",
        "DataSpace API resolve: set=%s, get=%s",
        setDataSpaceFunc ? "OK" : "UNAVAILABLE",
        getDataSpaceFunc ? "OK" : "UNAVAILABLE");
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_nativeGetSurfaceDataSpace(JNIEnv *env, jclass clazz,
                                                                      jobject surface) {
    resolveDataSpaceFuncs();

    if (!getDataSpaceFunc) {
        return -1;
    }

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        return -2;
    }

    int32_t dataSpace = getDataSpaceFunc(window);

    ANativeWindow_release(window);
    return dataSpace;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_nativeSetSurfaceDataSpace(JNIEnv *env, jclass clazz,
                                                                      jobject surface, jint dataSpace) {
    resolveDataSpaceFuncs();

    if (!setDataSpaceFunc) {
        return -1; // Not available on this API level
    }

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        __android_log_print(ANDROID_LOG_ERROR, "MoonBridge",
            "Failed to get ANativeWindow from Surface");
        return -2;
    }

    int32_t result = setDataSpaceFunc(window, (int32_t)dataSpace);
    __android_log_print(ANDROID_LOG_INFO, "MoonBridge",
        "ANativeWindow_setBuffersDataSpace(dataSpace=0x%08X) = %d", dataSpace, result);

    ANativeWindow_release(window);
    return result;
}
