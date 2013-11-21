#include <stdlib.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include <pthread.h>
#include <android/log.h>
#include "nv_avc_dec.h"

#include <jni.h>
#include <android/native_window_jni.h>

AVCodec* decoder;
AVCodecContext* decoder_ctx;
AVFrame* yuv_frame;
AVFrame* rgb_frame;
AVFrame* rnd_frame;
AVFrame* dec_frame;
pthread_mutex_t mutex;
char* rgb_frame_buf;
struct SwsContext* scaler_ctx;
int picture_new;

#define RENDER_PIX_FMT AV_PIX_FMT_RGBA
#define BYTES_PER_PIXEL 4

#define VERY_LOW_PERF 0
#define LOW_PERF 1
#define MED_PERF 2
#define HIGH_PERF 3

// This function must be called before
// any other decoding functions
int nv_avc_init(int width, int height, int perf_lvl) {
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

	if (perf_lvl <= LOW_PERF) {
		// Skip the loop filter for performance reasons
		decoder_ctx->skip_loop_filter = AVDISCARD_ALL;
	}

	if (perf_lvl <= MED_PERF) {
		// Run 2 threads for decoding
		decoder_ctx->thread_count = 2;
		decoder_ctx->thread_type = FF_THREAD_FRAME;

		// Use some tricks to make things faster
		decoder_ctx->flags2 |= CODEC_FLAG2_FAST;
	}
	else {
		// Use low delay single threaded encoding
		decoder_ctx->flags |= CODEC_FLAG_LOW_DELAY;
	}

	decoder_ctx->width = width;
	decoder_ctx->height = height;
	decoder_ctx->pix_fmt = PIX_FMT_YUV420P;

	err = avcodec_open2(decoder_ctx, decoder, NULL);
	if (err < 0) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't open codec");
		return err;
	}

	dec_frame = av_frame_alloc();
	if (dec_frame == NULL) {
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

	rgb_frame_buf = (char*)av_malloc(width * height * BYTES_PER_PIXEL);
	if (rgb_frame_buf == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
			"Couldn't allocate picture");
		return -1;
	}

	err = avpicture_fill((AVPicture*)rgb_frame,
		rgb_frame_buf,
		RENDER_PIX_FMT,
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
		RENDER_PIX_FMT,
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
	if (dec_frame) {
		av_frame_free(&dec_frame);
		dec_frame = NULL;
	}
	if (yuv_frame) {
		av_frame_free(&yuv_frame);
		yuv_frame = NULL;
	}
	if (rgb_frame) {
		av_frame_free(&rgb_frame);
		rgb_frame = NULL;
	}
	if (rgb_frame_buf) {
		av_free(rgb_frame_buf);
		rgb_frame_buf = NULL;
	}
	if (rnd_frame) {
		av_frame_free(&rnd_frame);
		rnd_frame = NULL;
	}
	pthread_mutex_destroy(&mutex);
}

void nv_avc_redraw(JNIEnv *env, jobject surface) {
	ANativeWindow* window;
	ANativeWindow_Buffer buffer;
	int err;

	// Free the old decoded frame
	if (rnd_frame) {
		av_frame_free(&rnd_frame);
	}

	pthread_mutex_lock(&mutex);

	// Check if there's a new frame
	if (picture_new) {
		// Clone the decoder's last frame
		rnd_frame = av_frame_clone(yuv_frame);

		// The remaining processing can be done without the mutex
		pthread_mutex_unlock(&mutex);

		if (rnd_frame == NULL) {
			__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
					"Cloning failed");
			return;
		}

		// Convert the YUV image to RGB
		err = sws_scale(scaler_ctx,
			rnd_frame->data,
			rnd_frame->linesize,
			0,
			decoder_ctx->height,
			rgb_frame->data,
			rgb_frame->linesize);
		if (err != decoder_ctx->height) {
			__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
					"Scaling failed");
			return;
		}

		window = ANativeWindow_fromSurface(env, surface);
		if (window == NULL) {
			__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
					"Failed to get window from surface");
			return;
		}

		// Lock down a render buffer
		if (ANativeWindow_lock(window, &buffer, NULL) >= 0) {
			// Draw the frame to the buffer
			err = avpicture_layout((AVPicture*)rgb_frame,
				RENDER_PIX_FMT,
				decoder_ctx->width,
				decoder_ctx->height,
				buffer.bits,
				decoder_ctx->width *
				decoder_ctx->height *
				BYTES_PER_PIXEL);
			if (err < 0) {
				__android_log_write(ANDROID_LOG_ERROR, "NVAVCDEC",
					"Picture fill failed");
			}

			// Draw the frame to the surface
			ANativeWindow_unlockAndPost(window);
		}

		ANativeWindow_release(window);
	}
	else {
		pthread_mutex_unlock(&mutex);
		rnd_frame = NULL;
	}
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
			dec_frame,
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
				yuv_frame = av_frame_clone(dec_frame);
				if (yuv_frame) {
					picture_new = 1;
				}
				else {
					picture_new = 0;
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
