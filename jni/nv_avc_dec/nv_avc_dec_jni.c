#include "nv_avc_dec.h"

#include <stdlib.h>
#include <jni.h>

// This function must be called before
// any other decoding functions
JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_init(JNIEnv *env, jobject this, jint width,
	jint height, jint perflvl, jint threadcount)
{
	return nv_avc_init(width, height, perflvl, threadcount);
}

// This function must be called after
// decoding is finished
JNIEXPORT void JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_destroy(JNIEnv *env, jobject this) {
	nv_avc_destroy();
}

// fills the output buffer with a raw YUV frame
JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_getRawFrame(
	JNIEnv *env, jobject this, // JNI parameters
	jbyteArray outdata, jint outlen) // Output data
{
	jint ret;
	jbyte* jni_output_data;

	jni_output_data = (*env)->GetByteArrayElements(env, outdata, 0);

	ret = nv_avc_get_raw_frame(jni_output_data, outlen);

	(*env)->ReleaseByteArrayElements(env, outdata, jni_output_data, 0);

	return ret != 0 ? JNI_TRUE : JNI_FALSE;
}

// fills the output buffer with an RGB frame
JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_getRgbFrame(
	JNIEnv *env, jobject this, // JNI parameters
	jbyteArray outdata, jint outlen) // Output data
{
	jint ret;
	jbyte* jni_output_data;

	jni_output_data = (*env)->GetByteArrayElements(env, outdata, 0);

	ret = nv_avc_get_rgb_frame(jni_output_data, outlen);

	(*env)->ReleaseByteArrayElements(env, outdata, jni_output_data, 0);

	return ret != 0 ? JNI_TRUE : JNI_FALSE;
}

// This function sets the rendering target for redraw
JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_setRenderTarget(JNIEnv *env, jobject this, jobject surface) {
	return nv_avc_set_render_target(env, surface) != 0 ? JNI_TRUE : JNI_FALSE;
}

// This function redraws the surface
JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_redraw(JNIEnv *env, jobject this) {
	return nv_avc_redraw() != 0 ? JNI_TRUE : JNI_FALSE;
}

// This function returns the required input buffer padding
JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_getInputPaddingSize(JNIEnv *env, jobject this) {
	return nv_avc_get_input_padding_size();
}

// packets must be decoded in order
// the input buffer must have proper padding
// returns 0 on success, < 0 on error
JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_av_video_cpu_AvcDecoder_decode(
	JNIEnv *env, jobject this, // JNI parameters
	jbyteArray indata, jint inoff, jint inlen)
{
	jint ret;
	jbyte* jni_input_data;

	jni_input_data = (*env)->GetByteArrayElements(env, indata, 0);

	ret = nv_avc_decode(&jni_input_data[inoff], inlen);

	// The input data isn't changed so it can be safely aborted
	(*env)->ReleaseByteArrayElements(env, indata, jni_input_data, JNI_ABORT);

	return ret;
}
