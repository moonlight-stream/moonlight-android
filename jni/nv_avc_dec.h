int nv_avc_init(int width, int height);
void nv_avc_destroy(void);
int nv_avc_get_current_frame(char* yuvframe, int size);
int nv_avc_get_frame_size(void);
int nv_avc_decode(unsigned char* indata, int inlen);
