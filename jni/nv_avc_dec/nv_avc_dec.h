#include <jni.h>

int nv_avc_init(int width, int height, int perf_lvl, int thread_count);
void nv_avc_destroy(void);
void nv_avc_redraw(JNIEnv *env, jobject surface);
int nv_avc_decode(unsigned char* indata, int inlen);
