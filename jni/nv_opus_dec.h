int nv_opus_init(void);
void nv_opus_destroy(void);
int nv_opus_get_channel_count(void);
int nv_opus_get_max_out_shorts(void);
int nv_opus_get_sample_rate(void);
int nv_opus_decode(unsigned char* indata, int inlen, short* outpcmdata);
