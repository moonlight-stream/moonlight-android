package com.limelight.grid.assets

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView

import com.limelight.AppView
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.AppIconCache

import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CachedAppAssetLoader(
    private val context: Context,
    private val computer: ComputerDetails,
    private val scalingDivider: Double,
    private val networkLoader: NetworkAssetLoader,
    private val memoryLoader: MemoryAssetLoader,
    private val diskLoader: DiskAssetLoader,
    private val noAppImageBitmap: Bitmap
) {

    private val cacheExecutor = ThreadPoolExecutor(
        MAX_CONCURRENT_CACHE_LOADS, MAX_CONCURRENT_CACHE_LOADS,
        Long.MAX_VALUE, TimeUnit.DAYS,
        LinkedBlockingQueue(MAX_PENDING_CACHE_LOADS),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    private val foregroundExecutor = ThreadPoolExecutor(
        MAX_CONCURRENT_DISK_LOADS, MAX_CONCURRENT_DISK_LOADS,
        Long.MAX_VALUE, TimeUnit.DAYS,
        LinkedBlockingQueue(MAX_PENDING_DISK_LOADS),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    private val networkExecutor = ThreadPoolExecutor(
        MAX_CONCURRENT_NETWORK_LOADS, MAX_CONCURRENT_NETWORK_LOADS,
        Long.MAX_VALUE, TimeUnit.DAYS,
        LinkedBlockingQueue(MAX_PENDING_NETWORK_LOADS),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    private val placeholderBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val loadLocks = Array(LOAD_LOCK_STRIPES) { Any() }

    fun cancelBackgroundLoads() {
        var r: Runnable?
        while (cacheExecutor.queue.poll().also { r = it } != null) {
            cacheExecutor.remove(r)
        }
    }

    fun cancelForegroundLoads() {
        var r: Runnable?
        while (foregroundExecutor.queue.poll().also { r = it } != null) {
            foregroundExecutor.remove(r)
        }
        while (networkExecutor.queue.poll().also { r = it } != null) {
            networkExecutor.remove(r)
        }
    }

    fun freeCacheMemory() {
        memoryLoader.clearCache()
    }

    fun getBitmapFromCache(tuple: LoaderTuple): ScaledBitmap? {
        return diskLoader.loadBitmapFromCache(tuple, scalingDivider.toInt())
    }

    /**
     * 统一的全分辨率大图加载方法：内存缓存 → 异步磁盘加载 → 回调
     * 如果内存缓存命中，直接在当前线程回调；否则异步从磁盘加载后在主线程回调。
     * 解码尺寸按屏幕短边截断，避免低内存设备 OOM。
     */
    fun loadFullBitmap(app: NvApp, callback: FullBitmapCallback) {
        // 先查内存缓存
        val cached = AppIconCache.instance.getFullIcon(computer, app)
        if (cached != null) {
            callback.onBitmapLoaded(cached)
            return
        }

        // 按屏幕长边作为解码上限（背景显示再大也无意义且爆内存）
        val dm = context.resources.displayMetrics
        val maxDim = max(dm.widthPixels, dm.heightPixels).coerceAtMost(1920)

        // 异步从磁盘加载
        foregroundExecutor.execute {
            val diskBitmap = diskLoader.loadFullBitmapFromCache(computer.uuid!!, app.appId, maxDim)
            if (diskBitmap != null) {
                AppIconCache.instance.putFullIcon(computer, app, diskBitmap)
                Handler(Looper.getMainLooper()).post { callback.onBitmapLoaded(diskBitmap) }
            }
        }
    }

    fun interface FullBitmapCallback {
        fun onBitmapLoaded(bitmap: Bitmap)
    }

    private fun doNetworkAssetLoad(tuple: LoaderTuple, task: LoaderTask?): ScaledBitmap? {
        // Try 3 times
        for (i in 0 until 3) {
            // Check again whether we've been cancelled or the image view is gone
            if (task != null && (task.isCancelled || task.imageViewRef.get() == null)) {
                return null
            }

            val input = networkLoader.getBitmapStream(tuple)
            if (input != null) {
                // Write the stream straight to disk
                diskLoader.populateCacheWithStream(tuple, input)

                // Close the network input stream
                try {
                    input.close()
                } catch (_: IOException) {
                }

                // If there's a task associated with this load, we should return the bitmap
                if (task != null) {
                    val bmp = diskLoader.loadBitmapFromCache(tuple, scalingDivider.toInt())
                    if (bmp != null) {
                        return bmp
                    }
                } else {
                    return null
                }
            }

            // Wait 1 second with a bit of fuzz
            try {
                Thread.sleep((1000 + (Math.random() * 500)).toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Thread.currentThread().interrupt()
                return null
            }
        }

        return null
    }

    private inner class LoaderTask constructor(
        imageView: ImageView,
        textView: TextView?,
        private val diskOnly: Boolean,
        private val isBackground: Boolean = false,
        private val onLoadComplete: ImageLoadCallback? = null
    ) : Runnable {

        val imageViewRef: WeakReference<ImageView> = WeakReference(imageView)
        private val textViewRef: WeakReference<TextView?> = WeakReference(textView)
        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var cancelled = false
        @Volatile
        private var runningThread: Thread? = null
        @Volatile
        var tuple: LoaderTuple? = null

        val isCancelled: Boolean
            get() = cancelled

        fun cancel(mayInterruptIfRunning: Boolean) {
            cancelled = true
            if (mayInterruptIfRunning) {
                runningThread?.interrupt()
            }
        }

        fun executeOnExecutor(executor: ThreadPoolExecutor, tuple: LoaderTuple) {
            this.tuple = tuple
            executor.execute(this)
        }

        override fun run() {
            runningThread = Thread.currentThread()
            val result = doInBackground()
            if (!cancelled) {
                mainHandler.post { onPostExecute(result) }
            }
        }

        private fun doInBackground(): ScaledBitmap? {
            if (cancelled || imageViewRef.get() == null || textViewRef.get() == null) {
                return null
            }

            val localTuple = tuple ?: return null
            val lock = loadLocks[getLoadLockIndex(localTuple)]

            return synchronized(lock) {
                if (cancelled || imageViewRef.get() == null || textViewRef.get() == null) {
                    return@synchronized null
                }

                val cachedBmp = memoryLoader.loadBitmapFromCache(localTuple)
                if (cachedBmp != null) {
                    return@synchronized cachedBmp
                }

                var bmp = diskLoader.loadBitmapFromCache(localTuple, scalingDivider.toInt())
                if (bmp == null) {
                    if (!diskOnly) {
                        bmp = doNetworkAssetLoad(localTuple, this)
                    } else if (!cancelled) {
                        mainHandler.post { onProgressUpdate() }
                    }
                }

                if (bmp != null) {
                    memoryLoader.populateCache(localTuple, bmp)
                }

                bmp
            }
        }

        private fun onProgressUpdate() {
            if (cancelled) return

            val imageView = imageViewRef.get()
            val textView = textViewRef.get()
            if (getLoaderTask(imageView) === this) {
                val task = LoaderTask(imageView!!, textView, false, isBackground)
                val asyncDrawable = AsyncDrawable(imageView.resources, noAppImageBitmap, task)
                imageView.setImageDrawable(asyncDrawable)
                if (isBackground) {
                    imageView.startAnimation(AnimationUtils.loadAnimation(imageView.context, R.anim.background_fadein))
                }
                imageView.visibility = View.VISIBLE
                textView?.visibility = View.VISIBLE
                task.executeOnExecutor(networkExecutor, tuple ?: return)
            }
        }

        private fun onPostExecute(bitmap: ScaledBitmap?) {
            if (cancelled) return

            val imageView = imageViewRef.get()
            val textView = textViewRef.get()
            if (getLoaderTask(imageView) === this) {
                if (bitmap != null) {
                    textView?.visibility = if (isBitmapPlaceholder(bitmap)) View.VISIBLE else View.GONE

                    val wasVisible = imageView?.visibility == View.VISIBLE
                    imageView?.clearAnimation()
                    imageView?.animate()?.cancel()
                    imageView?.setImageBitmap(bitmap.bitmap)

                    if (isBackground) {
                        imageView?.startAnimation(AnimationUtils.loadAnimation(imageView.context, R.anim.background_fadein))
                    } else {
                        if (!wasVisible) {
                            imageView?.alpha = 0f
                            imageView?.animate()
                                ?.alpha(1f)
                                ?.setDuration(BOXART_FADE_IN_DURATION_MS)
                                ?.start()
                        } else {
                            imageView?.alpha = 1f
                        }
                    }

                    imageView?.visibility = View.VISIBLE
                }

                onLoadComplete?.onImageLoaded(bitmap?.bitmap)
            }
        }
    }

    private class AsyncDrawable(
        res: Resources, bitmap: Bitmap?,
        loaderTask: LoaderTask
    ) : BitmapDrawable(res, bitmap) {
        private val loaderTaskReference: WeakReference<LoaderTask> = WeakReference(loaderTask)

        fun getLoaderTask(): LoaderTask? {
            return loaderTaskReference.get()
        }
    }

    fun queueCacheLoad(app: NvApp) {
        val tuple = LoaderTuple(computer, app)

        if (memoryLoader.loadBitmapFromCache(tuple) != null) {
            return
        }

        cacheExecutor.execute {
            if (diskLoader.checkCacheExists(tuple)) {
                return@execute
            }
            doNetworkAssetLoad(tuple, null)
        }
    }

    private fun isBitmapPlaceholder(bitmap: ScaledBitmap?): Boolean {
        return bitmap == null ||
                (bitmap.originalWidth == 130 && bitmap.originalHeight == 180) || // GFE 2.0
                (bitmap.originalWidth == 628 && bitmap.originalHeight == 888)     // GFE 3.0
    }

    fun populateImageView(
        obj: AppView.AppObject,
        imgView: ImageView,
        textView: TextView?,
        isBackground: Boolean = false,
        onLoadComplete: ImageLoadCallback? = null
    ): Boolean {
        val tuple = LoaderTuple(computer, obj.app)

        if (!cancelPendingLoad(tuple, imgView)) {
            return true
        }

        textView?.text = obj.app.appName

        val bmp = memoryLoader.loadBitmapFromCache(tuple)
        if (bmp != null) {
            imgView.visibility = View.VISIBLE
            imgView.setImageBitmap(bmp.bitmap)
            textView?.visibility = if (isBitmapPlaceholder(bmp)) View.VISIBLE else View.GONE
            onLoadComplete?.onImageLoaded(bmp.bitmap)
            return true
        }

        val task = LoaderTask(imgView, textView, true, isBackground, onLoadComplete)
        val asyncDrawable = AsyncDrawable(imgView.resources, placeholderBitmap, task)
        textView?.visibility = View.INVISIBLE
        imgView.visibility = View.INVISIBLE
        imgView.setImageDrawable(asyncDrawable)

        task.executeOnExecutor(foregroundExecutor, tuple)
        return false
    }

    class LoaderTuple(
        val computer: ComputerDetails,
        val app: NvApp
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is LoaderTuple) return false
            return computer.uuid == other.computer.uuid && app.appId == other.app.appId
        }

        override fun hashCode(): Int {
            var result = computer.uuid.hashCode()
            result = 31 * result + app.appId
            return result
        }

        override fun toString(): String {
            return "(${computer.uuid}, ${app.appId})"
        }
    }

    fun interface ImageLoadCallback {
        fun onImageLoaded(bitmap: Bitmap?)
    }

    companion object {
        private const val BOXART_FADE_IN_DURATION_MS = 120L
        private const val MAX_CONCURRENT_DISK_LOADS = 3
        private const val MAX_CONCURRENT_NETWORK_LOADS = 3
        private const val MAX_CONCURRENT_CACHE_LOADS = 1

        private const val MAX_PENDING_CACHE_LOADS = 100
        private const val MAX_PENDING_NETWORK_LOADS = 40
        private const val MAX_PENDING_DISK_LOADS = 40
        private const val LOAD_LOCK_STRIPES = 64

        private fun getLoadLockIndex(tuple: LoaderTuple): Int {
            return (tuple.hashCode() and Int.MAX_VALUE) % LOAD_LOCK_STRIPES
        }

        private fun getLoaderTask(imageView: ImageView?): LoaderTask? {
            if (imageView == null) return null
            val drawable = imageView.drawable
            return if (drawable is AsyncDrawable) {
                drawable.getLoaderTask()
            } else null
        }

        private fun cancelPendingLoad(tuple: LoaderTuple, imageView: ImageView): Boolean {
            val loaderTask = getLoaderTask(imageView)

            if (loaderTask != null && !loaderTask.isCancelled) {
                val taskTuple = loaderTask.tuple
                if (taskTuple == null || taskTuple != tuple) {
                    loaderTask.cancel(true)
                } else {
                    return false
                }
            }

            return true
        }
    }
}
