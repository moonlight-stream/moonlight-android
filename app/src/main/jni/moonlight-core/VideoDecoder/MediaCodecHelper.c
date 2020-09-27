//
// Created by Viktor Pih on 2020/8/10.
//

#include "MediaCodecHelper.h"
#include <sys/system_properties.h>
#include <android/log.h>
#include <ctype.h>

#define LOG_TAG    "MediaCodecHelper"
#ifdef LC_DEBUG
#define LOGD(...)  {__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); /*printCache();*/}
#else
#define LOGD(...)
#endif

const char* qualcommDecoderPrefixes[] = {"omx.qcom", "c2.qti"};
const char* baselineProfileHackPrefixes[] = {"omx.intel"};
const char* spsFixupBitstreamFixupDecoderPrefixes[] = {"omx.nvidia", "omx.qcom", "omx.brcm"};

char * __cdecl stristr (
        const char * str1,
        const char * str2
)
{
    char *cp = (char *) str1;
    char *s1, *s2;

    if ( !*str2 )
        return((char *)str1);

    while (*cp)
    {
        s1 = cp;
        s2 = (char *) str2;

        while ( *s1 && *s2 && !(tolower(*s1)-tolower(*s2)) )
            s1++, s2++;

        if (!*s2)
            return(cp);

        cp++;
    }

    return(0);
}

bool isDecoderInList(const char* list[], size_t size, const char* decoderName) {

    for (int i = 0; i < size; i++) {
        if (stristr(decoderName, list[i]) == decoderName) {
            return true;
        }
    }
    return false;
}

int _Build_VERSION_SDK_INT() {
    static char sdk_ver_str[PROP_VALUE_MAX+1];
    static int sdk_ver = 0;
    if (sdk_ver == 0) {
        if (__system_property_get("ro.build.version.sdk", sdk_ver_str)) {
            sdk_ver = atoi(sdk_ver_str);
        } else {
            // Not running on Android or SDK version is not available
            // ...
            sdk_ver = -1;
            LOGD("sdk_version fail!");
        }
    }
    return sdk_ver;
}

#define IS_DECODER_IN_LIST(a, b) isDecoderInList(a, sizeof(a)/sizeof(*a), b);

bool MediaCodecHelper_decoderSupportsQcomVendorLowLatency(const char* decoderName) {
    // MediaCodec vendor extension support was introduced in Android 8.0:
    // https://cs.android.com/android/_/android/platform/frameworks/av/+/01c10f8cdcd58d1e7025f426a72e6e75ba5d7fc2
    return Build_VERSION_SDK_INT >= Build_VERSION_CODES_O &&
            IS_DECODER_IN_LIST(qualcommDecoderPrefixes, decoderName);
}

bool MediaCodecHelper_decoderNeedsBaselineSpsHack(const char* decoderName) {
    return IS_DECODER_IN_LIST(baselineProfileHackPrefixes, decoderName);
}

bool MediaCodecHelper_decoderNeedsSpsBitstreamRestrictions(const char* decoderName) {
    return IS_DECODER_IN_LIST(spsFixupBitstreamFixupDecoderPrefixes, decoderName);
}