#include <jni.h>

int nv_avc_init(int width, int height, int perf_lvl, int thread_count);
void nv_avc_destroy(void);

int nv_avc_get_raw_frame(char* buffer, int size);

int nv_avc_get_rgb_frame(char* buffer, int size);
int nv_avc_set_render_target(JNIEnv *env, jobject surface);
int nv_avc_redraw(void);

int nv_avc_get_input_padding_size(void);
int nv_avc_decode(unsigned char* indata, int inlen);
