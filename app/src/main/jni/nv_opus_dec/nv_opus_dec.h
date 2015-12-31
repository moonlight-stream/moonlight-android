int nv_opus_init(int sampleRate, int channelCount, int streams,
                 int coupledStreams, const unsigned char *mapping);
void nv_opus_destroy(void);
int nv_opus_decode(unsigned char* indata, int inlen, short* outpcmdata, int framesize);
