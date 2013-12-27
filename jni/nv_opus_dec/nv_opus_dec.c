#include <stdlib.h>
#include <opus.h>
#include "nv_opus_dec.h"

OpusDecoder* decoder;

// This function must be called before
// any other decoding functions
int nv_opus_init(void) {
	int err;
	decoder = opus_decoder_create(
		nv_opus_get_sample_rate(),
		nv_opus_get_channel_count(),
		&err);
	return err;
}

// This function must be called after
// decoding is finished
void nv_opus_destroy(void) {
	if (decoder != NULL) {
		opus_decoder_destroy(decoder);
	}
}

// The Opus stream is stereo
int nv_opus_get_channel_count(void) {
	return 2;
}

// This number assumes 2 channels at 48 KHz
int nv_opus_get_max_out_shorts(void) {
	return 512*nv_opus_get_channel_count();
}

// The Opus stream is 48 KHz
int nv_opus_get_sample_rate(void) {
	return 48000;
}

// outpcmdata must be 5760*2 shorts in length
// packets must be decoded in order
// a packet loss must call this function with NULL indata and 0 inlen
// returns the number of decoded samples
int nv_opus_decode(unsigned char* indata, int inlen, short* outpcmdata) {
	int err;

	// Decoding to 16-bit PCM with FEC off
	// Maximum length assuming 48KHz sample rate
	err = opus_decode(decoder, indata, inlen,
		outpcmdata, 512, 0);

	return err;
}
