#include <stdlib.h>
#include <opus_multistream.h>
#include "nv_opus_dec.h"

OpusMSDecoder* decoder;

// This function must be called before
// any other decoding functions
int nv_opus_init(int sampleRate, int channelCount, int streams,
				 int coupledStreams, const unsigned char *mapping) {
	int err;
	decoder = opus_multistream_decoder_create(
			sampleRate,
			channelCount,
			streams,
			coupledStreams,
			mapping,
			&err);
	return err;
}

// This function must be called after
// decoding is finished
void nv_opus_destroy(void) {
	if (decoder != NULL) {
		opus_multistream_decoder_destroy(decoder);
	}
}

// packets must be decoded in order
// a packet loss must call this function with NULL indata and 0 inlen
// returns the number of decoded samples
int nv_opus_decode(unsigned char* indata, int inlen, short* outpcmdata, int framesize) {
	int err;

	// Decoding to 16-bit PCM with FEC off
	// Maximum length assuming 48KHz sample rate
	err = opus_multistream_decode(decoder, indata, inlen,
		outpcmdata, framesize, 0);

	return err;
}
