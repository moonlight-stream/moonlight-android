#include <stdlib.h>
#include <jni.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <linux/input.h>
#include <unistd.h>
#include <poll.h>
#include <errno.h>

#include <android/log.h>

JNIEXPORT jint JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_open(JNIEnv *env, jobject this, jstring absolutePath) {
	const char *path;
	
	path = (*env)->GetStringUTFChars(env, absolutePath, NULL);
	
	return open(path, O_RDWR);
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_grab(JNIEnv *env, jobject this, jint fd) {
    return ioctl(fd, EVIOCGRAB, 1) == 0;
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_ungrab(JNIEnv *env, jobject this, jint fd) {
    return ioctl(fd, EVIOCGRAB, 0) == 0;
}

// has*() and friends are based on Android's EventHub.cpp

#define test_bit(bit, array)    (array[bit/8] & (1<<(bit%8)))

JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_hasRelAxis(JNIEnv *env, jobject this, jint fd, jshort axis) {
    unsigned char relBitmask[(REL_MAX + 1) / 8];
    
    ioctl(fd, EVIOCGBIT(EV_REL, sizeof(relBitmask)), relBitmask);
    
    return test_bit(axis, relBitmask);
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_hasAbsAxis(JNIEnv *env, jobject this, jint fd, jshort axis) {
    unsigned char absBitmask[(ABS_MAX + 1) / 8];
    
    ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBitmask)), absBitmask);
    
    return test_bit(axis, absBitmask);
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_hasKey(JNIEnv *env, jobject this, jint fd, jshort key) {
    unsigned char keyBitmask[(KEY_MAX + 1) / 8];
    
    ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBitmask)), keyBitmask);
    
    return test_bit(key, keyBitmask);
}

JNIEXPORT jint JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_read(JNIEnv *env, jobject this, jint fd, jbyteArray buffer) {
    jint ret;
    jbyte *data;
    int pollres;
    struct pollfd pollinfo;
    
    data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "EvdevReader",
            "Failed to get byte array");
        return -1;
    }

    do
    {
        // Unwait every 250 ms to return to caller if the fd is closed
        pollinfo.fd = fd;
        pollinfo.events = POLLIN;
        pollinfo.revents = 0;
        pollres = poll(&pollinfo, 1, 250);
    }
    while (pollres == 0);
    
    if (pollres > 0 && (pollinfo.revents & POLLIN)) {
        // We'll have data available now
        ret = read(fd, data, sizeof(struct input_event));
        if (ret < 0) {
            __android_log_print(ANDROID_LOG_ERROR, "EvdevReader",
                "read() failed: %d", errno);
        }
    }
    else {
        // There must have been a failure
        ret = -1;
    
        if (pollres < 0) {
            __android_log_print(ANDROID_LOG_ERROR, "EvdevReader",
                "poll() failed: %d", errno);
        }
        else {
            __android_log_print(ANDROID_LOG_ERROR, "EvdevReader",
                "Unexpected revents: %d", pollinfo.revents);
        }
    }
    
    (*env)->ReleaseByteArrayElements(env, buffer, data, 0);

	return ret;
}

JNIEXPORT jint JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_close(JNIEnv *env, jobject this, jint fd) {
	return close(fd);
}
