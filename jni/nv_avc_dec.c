#include <stdlib.h>
#include <libavcodec/avcodec.h>
#include <pthread.h>
#include "nv_avc_dec.h"

AVCodec* decoder;
AVCodecContext* decoder_ctx;
AVFrame* curr_picture;
AVFrame* frame;
pthread_mutex_t mutex;

// This function must be called before
// any other decoding functions
int nv_avc_init(int width, int height) {
	int err;

	pthread_mutex_init(&mutex, NULL);

	decoder = avcodec_find_decoder(CODEC_ID_H264);
	if (decoder == NULL) {
		return -1;
	}

	decoder_ctx = avcodec_alloc_context3(decoder);
	if (decoder_ctx == NULL) {
		return -1;
	}

	curr_picture = av_frame_alloc();
	if (curr_picture == NULL) {
		return -1;
	}

	frame = av_frame_alloc();
	if (frame == NULL) {
		return -1;
	}

	// We don't have to send complete frames
	if (decoder->capabilities & CODEC_CAP_TRUNCATED) {
		decoder_ctx->flags |= CODEC_FLAG_TRUNCATED;
	}

	// We're a latency-sensitive application
	decoder_ctx->flags |= CODEC_FLAG_LOW_DELAY;

	decoder_ctx->width = width;
	decoder_ctx->height = height;

	err = avcodec_open2(decoder_ctx, decoder, NULL);
	if (err < 0) {
		return err;
	}

	return 0;
}

// This function must be called after
// decoding is finished
void nv_avc_destroy(void) {
	if (decoder_ctx) {
		avcodec_close(decoder_ctx);
		av_free(decoder_ctx);
		decoder_ctx = NULL;
	}
	if (curr_picture) {
		av_frame_free(&curr_picture);
		curr_picture = NULL;
	}
	if (frame) {
		av_frame_free(&frame);
		frame = NULL;
	}
}

// The decoded frame is YUV 4:2:0
// Returns 1 on success, 0 on failure
int nv_avc_get_current_frame(char* yuvframe, int size) {
	int lu_size = decoder_ctx->width * decoder_ctx->height;
	int ch_size = lu_size << 2;

	if (size < nv_avc_get_frame_size())
		return 0;

	pthread_mutex_lock(&mutex);

	memcpy(&yuvframe[0],
		curr_picture->data[0],
		lu_size);
	memcpy(&yuvframe[lu_size],
		curr_picture->data[1],
		ch_size);
	memcpy(&yuvframe[lu_size+ch_size],
		curr_picture->data[2],
		ch_size);

	pthread_mutex_unlock(&mutex);

	return 1;
}

int nv_avc_get_frame_size(void) {
	int size = 0;

	pthread_mutex_lock(&mutex);

	// Luminance
	size += decoder_ctx->width * decoder_ctx->height;

	// Chrominance
	size += (decoder_ctx->width * decoder_ctx->height) / 2;

	pthread_mutex_unlock(&mutex);

	return size;
}

// packets must be decoded in order
int nv_avc_decode(unsigned char* indata, int inlen) {
	int err;
	AVPacket pkt;
	int got_pic;

	err = av_new_packet(&pkt, inlen);
	if (err < 0) {
		return err;
	}

	memcpy(pkt.data, indata, inlen);

	while (pkt.size > 0) {
		err = avcodec_decode_video2(
			decoder_ctx,
			frame,
			&got_pic,
			&pkt);
		if (err < 0) {
			break;
		}

		if (got_pic) {
			pthread_mutex_lock(&mutex);

			// Dereference the last frame
			av_frame_unref(curr_picture);

			// Reference the new frame
			err = av_frame_ref(curr_picture, frame);

			pthread_mutex_unlock(&mutex);

			if (err < 0) {
				break;
			}
		}

		pkt.size -= err;
		pkt.data += err;
	}

	av_free_packet(&pkt);

	return err < 0 ? err : 0;
}
