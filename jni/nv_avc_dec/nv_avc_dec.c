#include <stdlib.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include <pthread.h>
#include <android/log.h>
#include "nv_avc_dec.h"

AVCodec* decoder;
AVCodecContext* decoder_ctx;
AVFrame* yuv_frame;
AVFrame* tmp_frame;
AVFrame* rgb_frame;
pthread_mutex_t mutex;
char* rgb_frame_buf;
int picture_valid;
int rgb_dirty;
struct SwsContext* scaler_ctx;

// This function must be called before
// any other decoding functions
int nv_avc_init(int width, int height) {
	int err;

	pthread_mutex_init(&mutex, NULL);

	// Initialize the avcodec library and register codecs
	av_log_set_level(AV_LOG_QUIET);
	avcodec_register_all();

	decoder = avcodec_find_decoder(AV_CODEC_ID_H264);
	if (decoder == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't find H264 decoder");
		return -1;
	}

	decoder_ctx = avcodec_alloc_context3(decoder);
	if (decoder_ctx == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't allocate context");
		return -1;
	}

	// Show frames even before a reference frame
	decoder_ctx->flags2 |= CODEC_FLAG2_SHOW_ALL;

	// Skip the loop filter for performance reasons
	decoder_ctx->skip_loop_filter = AVDISCARD_ALL;

	// Run 2 threads for decoding
	decoder_ctx->thread_count = 2;
	decoder_ctx->thread_type = FF_THREAD_FRAME;

	decoder_ctx->width = width;
	decoder_ctx->height = height;
	decoder_ctx->pix_fmt = PIX_FMT_YUV420P;

	err = avcodec_open2(decoder_ctx, decoder, NULL);
	if (err < 0) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't open codec");
		return err;
	}

	tmp_frame = av_frame_alloc();
	if (tmp_frame == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't allocate frame");
		return -1;
	}
	
	rgb_frame = av_frame_alloc();
	if (rgb_frame == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't allocate frame");
		return -1;
	}

	rgb_frame_buf = (char*)av_malloc(nv_avc_get_rgb_frame_size());
	if (rgb_frame_buf == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't allocate picture");
		return -1;
	}
	
	err = avpicture_fill((AVPicture*)rgb_frame,
		rgb_frame_buf,
		AV_PIX_FMT_RGB32,
		decoder_ctx->width,
		decoder_ctx->height);
	if (err < 0) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't fill picture");
		return err;
	}
	
	scaler_ctx = sws_getContext(decoder_ctx->width,
		decoder_ctx->height,
		decoder_ctx->pix_fmt,
		decoder_ctx->width,
		decoder_ctx->height,
		AV_PIX_FMT_RGB32,
		SWS_BICUBIC,
		NULL, NULL, NULL);
	if (scaler_ctx == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't get scaler context");
		return -1;
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
	if (scaler_ctx) {
		sws_freeContext(scaler_ctx);
		scaler_ctx = NULL;
	}
	if (tmp_frame) {
		av_frame_free(&yuv_frame);
		tmp_frame = NULL;
	}
	if (rgb_frame) {
		av_frame_free(&rgb_frame);
		rgb_frame = NULL;
	}
	if (rgb_frame_buf) {
		av_free(rgb_frame_buf);
		rgb_frame_buf = NULL;
	}
	pthread_mutex_destroy(&mutex);
}

// The decoded frame is ARGB
// Returns 1 on success, 0 on failure
int nv_avc_get_current_frame(char* rgbframe, int size) {
	int err;

	if (size != nv_avc_get_rgb_frame_size()) {
		return 0;
	}

	pthread_mutex_lock(&mutex);

	// Check if the RGB frame needs updating
	if (rgb_dirty) {
		// If the decoder doesn't have a new picture, we fail
		if (!picture_valid) {
			pthread_mutex_unlock(&mutex);
			return 0;
		}

		// Convert the YUV image to RGB
		err = sws_scale(scaler_ctx,
			yuv_frame->data,
			yuv_frame->linesize,
			0,
			decoder_ctx->height,
			rgb_frame->data,
			rgb_frame->linesize);
		if (err != decoder_ctx->height) {
			__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
					"Scaling failed");
			pthread_mutex_unlock(&mutex);
			return 0;
		}

		// RGB frame is now clean
		rgb_dirty = 0;
	}

	// The remaining processing can be done without the mutex
	pthread_mutex_unlock(&mutex);

	err = avpicture_layout((AVPicture*)rgb_frame,
		AV_PIX_FMT_RGB32,
		decoder_ctx->width,
		decoder_ctx->height,
		rgbframe,
		size);
	if (err < 0) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
				"Picture fill failed");
		return 0;
	}

	return 1;
}

int nv_avc_get_rgb_frame_size(void) {
	return avpicture_get_size(AV_PIX_FMT_RGB32,
		decoder_ctx->width,
		decoder_ctx->height);
}

// packets must be decoded in order
int nv_avc_decode(unsigned char* indata, int inlen) {
	int err;
	AVPacket pkt;
	int got_pic;

	err = av_new_packet(&pkt, inlen);
	if (err < 0) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Failed to allocate packet");
		return err;
	}

	memcpy(pkt.data, indata, inlen);

	while (pkt.size > 0) {
		err = avcodec_decode_video2(
			decoder_ctx,
			tmp_frame,
			&got_pic,
			&pkt);
		if (err < 0) {
			__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
				"Decode failed");
			pthread_mutex_unlock(&mutex);
			break;
		}

		if (got_pic) {
			// Update the frame if it's not being read
			if (pthread_mutex_trylock(&mutex) == 0) {
				// Free the old frame
				if (yuv_frame) {
					av_frame_free(&yuv_frame);
				}

				// Clone a new frame
				yuv_frame = av_frame_clone(tmp_frame);
				if (yuv_frame) {
					// If we got a new picture, the RGB frame needs refreshing
					picture_valid = 1;
					rgb_dirty = 1;
				}
				else {
					picture_valid = 0;
				}

				pthread_mutex_unlock(&mutex);
			}
		}

		pkt.size -= err;
		pkt.data += err;
	}

	av_free_packet(&pkt);

	return err < 0 ? err : 0;
}
