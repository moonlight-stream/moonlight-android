#include <stdlib.h>
#include <jni.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <linux/input.h>
#include <unistd.h>

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

// isMouse() and friends are based on Android's EventHub.cpp

#define test_bit(bit, array)    (array[bit/8] & (1<<(bit%8)))

JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_isMouse(JNIEnv *env, jobject this, jint fd) {
    unsigned char keyBitmask[(KEY_MAX + 1) / 8];
    unsigned char relBitmask[(REL_MAX + 1) / 8];
    
    ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBitmask)), keyBitmask);
    ioctl(fd, EVIOCGBIT(EV_REL, sizeof(relBitmask)), relBitmask);
    
    // If this device has all required features of a mouse, it's a mouse!
    return test_bit(BTN_MOUSE, keyBitmask) &&
        test_bit(REL_X, relBitmask) &&
        test_bit(REL_Y, relBitmask);
}

JNIEXPORT jint JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_read(JNIEnv *env, jobject this, jint fd, jbyteArray buffer) {
    jint ret;
    jbyte *data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        return -1;
    }

    ret = read(fd, data, sizeof(struct input_event));
    
    (*env)->ReleaseByteArrayElements(env, buffer, data, 0);

	return ret;
}

JNIEXPORT jint JNICALL
Java_com_limelight_binding_input_evdev_EvdevReader_close(JNIEnv *env, jobject this, jint fd) {
	return close(fd);
}
