package com.limelight.grid

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

import com.limelight.LimeLog
import com.limelight.PcView
import com.limelight.R
import com.limelight.networkquality.StreamNetworkQualityStore
import com.limelight.networkquality.supportsNetworkQualityProbe
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.CacheHelper

import java.io.StringReader
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class PcGridAdapter(
    context: Context,
    prefs: PreferenceConfiguration
) : GenericGridAdapter<PcView.ComputerObject>(context, R.layout.pc_grid_item) {

    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val boxArtCache: MutableMap<String, Bitmap> = ConcurrentHashMap()
    private val loadingUuids: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private var showUnpairedDevices: Boolean = sharedPreferences.getBoolean(PREF_SHOW_UNPAIRED_DEVICES, true)

    fun interface AvatarClickListener {
        fun onAvatarClick(computer: ComputerDetails, itemView: View)
    }

    private var avatarClickListener: AvatarClickListener? = null

    fun setAvatarClickListener(listener: AvatarClickListener?) {
        this.avatarClickListener = listener
    }

    fun updateLayoutWithPreferences(context: Context, prefs: PreferenceConfiguration) {
        setLayoutId(R.layout.pc_grid_item)
    }

    private fun loadFirstAppBoxArt(imgView: ImageView, computer: ComputerDetails, allowAsyncLoad: Boolean): Boolean {
        if (computer.uuid == null) return false

        val cachedBitmap = boxArtCache[computer.uuid]
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            applyBoxArt(imgView, cachedBitmap)
            return true
        }

        if (!allowAsyncLoad) {
            val diskCachedBitmap = loadBoxArtFromDiskCache(computer)
            if (diskCachedBitmap != null) {
                boxArtCache[computer.uuid!!] = diskCachedBitmap
                applyBoxArt(imgView, diskCachedBitmap)
                return true
            }
            return false
        }

        if (computer.uuid!! in loadingUuids) return false

        loadingUuids.add(computer.uuid!!)
        LoadBoxArtTask.execute(imgView, computer, context, this)
        return false
    }

    private fun loadBoxArtFromDiskCache(computer: ComputerDetails): Bitmap? {
        if (computer.uuid == null) return null
        return loadBoxArtFromDisk(context, computer.uuid!!, false)
    }

    internal fun cacheBoxArt(uuid: String?, bitmap: Bitmap?) {
        if (uuid != null && bitmap != null) {
            boxArtCache[uuid] = bitmap
        }
        loadingUuids.remove(uuid)
    }

    internal fun markLoadingComplete(uuid: String?) {
        loadingUuids.remove(uuid)
    }

    private object LoadBoxArtTask {
        private val executor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun execute(imageView: ImageView, computer: ComputerDetails, context: Context, adapter: PcGridAdapter) {
            val imageViewRef = WeakReference(imageView)
            val contextRef = WeakReference(context)
            val adapterRef = WeakReference(adapter)

            executor.execute {
                val ctx = contextRef.get()
                val bitmap = loadBoxArtFromDisk(ctx, computer.uuid, true)

                mainHandler.post {
                    val a = adapterRef.get()
                    if (a != null) {
                        if (bitmap != null) {
                            a.cacheBoxArt(computer.uuid, bitmap)
                        } else {
                            a.markLoadingComplete(computer.uuid)
                        }
                    }

                    val iv = imageViewRef.get()
                    if (iv != null && bitmap != null) {
                        applyBoxArt(iv, bitmap)
                    }
                }
            }
        }
    }

    fun addComputer(computer: PcView.ComputerObject) {
        itemList.add(computer)
        sortList()
    }

    fun resort() {
        val beforeOrder = itemList.map { obj ->
            obj?.details?.uuid ?: ""
        }

        sortList()

        if (beforeOrder.size != itemList.size) return

        for (i in itemList.indices) {
            val obj = itemList[i]
            val currentUuid = obj?.details?.uuid ?: ""
            if (beforeOrder[i] != currentUuid) return
        }
    }

    private fun sortList() {
        itemList.sortWith { lhs, rhs ->
            val lhsIsAdd = isAddComputerCard(lhs)
            val rhsIsAdd = isAddComputerCard(rhs)
            if (lhsIsAdd && !rhsIsAdd) return@sortWith 1
            if (!lhsIsAdd && rhsIsAdd) return@sortWith -1
            if (lhsIsAdd) return@sortWith 0

            val lhsOnline = lhs.details.state == ComputerDetails.State.ONLINE
            val rhsOnline = rhs.details.state == ComputerDetails.State.ONLINE
            if (lhsOnline && !rhsOnline) return@sortWith -1
            if (!lhsOnline && rhsOnline) return@sortWith 1

            if (lhsOnline) {
                val lhsUnpaired = isUnpairedComputer(lhs)
                val rhsUnpaired = isUnpairedComputer(rhs)
                if (lhsUnpaired && !rhsUnpaired) return@sortWith 1
                if (!lhsUnpaired && rhsUnpaired) return@sortWith -1
            }

            (lhs.details.name ?: "").lowercase().compareTo((rhs.details.name ?: "").lowercase())
        }
    }

    fun removeComputer(computer: PcView.ComputerObject) {
        itemList.remove(computer)
    }

    val rawCount: Int
        get() = itemList.size

    fun getRawItem(i: Int): PcView.ComputerObject = itemList[i]

    fun setShowUnpairedDevices(show: Boolean) {
        if (showUnpairedDevices != show) {
            showUnpairedDevices = show
            sharedPreferences.edit()
                .putBoolean(PREF_SHOW_UNPAIRED_DEVICES, show)
                .apply()
            notifyDataSetChanged()
        }
    }

    fun isShowUnpairedDevices(): Boolean = showUnpairedDevices

    private fun getFilteredItems(): List<PcView.ComputerObject> {
        if (showUnpairedDevices) return itemList

        return itemList.filter { !isUnpairedComputer(it) }
    }

    override fun getCount(): Int = getFilteredItems().size

    override fun getItem(i: Int): Any? {
        val filtered = getFilteredItems()
        if (i < 0 || i >= filtered.size) return null
        return filtered[i]
    }

    override fun getView(i: Int, convertView: View?, viewGroup: ViewGroup?): View {
        val filtered = getFilteredItems()
        if (i < 0 || i >= filtered.size) {
            return convertView ?: inflater.inflate(R.layout.pc_grid_item, viewGroup, false)
        }

        val view = convertView ?: inflater.inflate(R.layout.pc_grid_item, viewGroup, false)

        val imgView = view.findViewById<ImageView>(R.id.grid_image)
        val overlayView = view.findViewById<ImageView>(R.id.grid_overlay)
        val txtView = view.findViewById<TextView>(R.id.grid_text)
        val spinnerView = view.findViewById<View>(R.id.grid_spinner)
        val networkQualityView = view.findViewById<LinearLayout>(R.id.grid_network_quality)
        val networkQualityText = view.findViewById<TextView>(R.id.grid_network_quality_text)

        val computer = filtered[i]
        // 把 UUID 挂到 view tag 上，PcView FLIP 重排动画需要从可见 view 反查"当前显示的 PC"
        // —— 不能依赖 adapter.getItem(pos) 因为 itemList 在 sortList() 后已同步重排
        view.setTag(R.id.grid_text, computer.details?.uuid)
        populateView(view, imgView, spinnerView, txtView, overlayView, computer)
        updateNetworkQuality(networkQualityView, networkQualityText, computer)

        if (imgView != null) {
            setupImageTouchListener(imgView, view, computer)
        }

        return view
    }

    private fun updateNetworkQuality(
        container: LinearLayout?,
        textView: TextView?,
        computer: PcView.ComputerObject
    ) {
        if (container == null || textView == null || isAddComputerCard(computer)) {
            container?.visibility = View.GONE
            return
        }

        val details = computer.details
        val supportsProbe = details.state == ComputerDetails.State.ONLINE &&
                details.pairState == PairingManager.PairState.PAIRED &&
                details.supportsNetworkQualityProbe()
        if (!supportsProbe) {
            container.visibility = View.GONE
            return
        }

        val endpointIdentity = details.activeAddress?.let { "${it.address}:${it.port}" }
        val result = StreamNetworkQualityStore.load(context, details.uuid, endpointIdentity)
        if (result == null) {
            container.visibility = View.GONE
            return
        }

        textView.text = context.getString(
            R.string.network_quality_card_summary,
            result.responseLatencyMs,
            result.bandwidthMbps
        )
        container.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageTouchListener(imageView: ImageView, itemView: View, computer: PcView.ComputerObject) {
        if (isAddComputerCard(computer) || avatarClickListener == null) {
            imageView.setOnTouchListener(null)
            imageView.isClickable = false
            return
        }

        val computerDetails = computer.details

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                avatarClickListener?.onAvatarClick(computerDetails, itemView)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                itemView.performLongClick()
            }
        })

        imageView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        imageView.isClickable = true
        imageView.isFocusable = false
    }

    @SuppressLint("SetTextI18n")
    override fun populateView(parentView: View, imgView: ImageView?, spinnerView: View?, txtView: TextView?, overlayView: ImageView?, obj: PcView.ComputerObject) {
        if (isAddComputerCard(obj)) {
            populateAddComputerCard(parentView, imgView!!, spinnerView!!, txtView!!, overlayView!!)
            return
        }

        populateComputerCard(parentView, imgView!!, spinnerView!!, txtView!!, overlayView!!, obj.details)
    }

    private fun populateAddComputerCard(parentView: View, imgView: ImageView, spinnerView: View, txtView: TextView, overlayView: ImageView) {
        imgView.setImageResource(R.drawable.ic_add)
        imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        imgView.alpha = 0.7f

        parentView.setBackgroundResource(R.drawable.pc_item_selector)
        spinnerView.visibility = View.INVISIBLE
        overlayView.visibility = View.GONE

        txtView.text = context.getString(R.string.title_add_pc)
        txtView.alpha = 0.7f
        txtView.setTextColor(ContextCompat.getColor(context, R.color.pc_item_text_primary))
    }

    private fun populateComputerCard(parentView: View, imgView: ImageView, spinnerView: View, txtView: TextView, overlayView: ImageView, details: ComputerDetails) {
        val isOnline = details.state == ComputerDetails.State.ONLINE
        val isUnknown = details.state == ComputerDetails.State.UNKNOWN
        val isOffline = details.state == ComputerDetails.State.OFFLINE

        val hasBoxArt = details.uuid != null && loadFirstAppBoxArt(imgView, details, isOnline)
        if (!hasBoxArt) {
            imgView.setImageResource(R.drawable.ic_computer)
            imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        }
        imgView.alpha = if (isOnline) ONLINE_ALPHA else OFFLINE_ALPHA

        parentView.setBackgroundResource(
            if (isOnline && details.hasMultipleAddresses())
                R.drawable.pc_item_multiple_addresses_selector
            else
                R.drawable.pc_item_selector
        )

        val isLoadingBoxArt = details.uuid != null && details.uuid in loadingUuids
        updateSpinner(spinnerView as ImageView, isUnknown || isLoadingBoxArt)

        var displayName = details.name
        if (isOnline && details.sunshineVersion != null && details.sunshineVersion?.endsWith("杂鱼") == true) {
            displayName += "⚡"
        }
        txtView.text = displayName
        txtView.alpha = if (isOffline) 0.5f else 1.0f
        txtView.setTextColor(
            ContextCompat.getColor(
                context,
                if (isOffline) R.color.pc_item_text_disabled else R.color.pc_item_text_primary
            )
        )

        updateOverlay(overlayView, details, isOnline, isOffline)
    }

    private fun updateSpinner(spinnerView: ImageView, shouldShow: Boolean) {
        spinnerView.visibility = if (shouldShow) View.VISIBLE else View.INVISIBLE
        val drawable = spinnerView.drawable
        if (drawable is AnimatedVectorDrawable) {
            if (shouldShow) drawable.start() else drawable.stop()
        }
    }

    private fun updateOverlay(overlayView: ImageView, details: ComputerDetails, isOnline: Boolean, isOffline: Boolean) {
        when {
            isOffline -> {
                overlayView.setImageResource(R.drawable.ic_pc_offline)
                overlayView.alpha = 0.35f
                overlayView.visibility = View.VISIBLE
                overlayView.setPadding(0, 0, 10, 12)
                overlayView.scaleX = 1.4f
                overlayView.scaleY = 1.4f
            }
            isOnline && details.pairState == PairingManager.PairState.NOT_PAIRED -> {
                overlayView.setImageResource(R.drawable.ic_lock)
                overlayView.alpha = 1.0f
                overlayView.visibility = View.VISIBLE
                overlayView.setPadding(0, 0, 0, 0)
                overlayView.scaleX = 1.0f
                overlayView.scaleY = 1.0f
                overlayView.bringToFront()
            }
            else -> {
                overlayView.visibility = View.GONE
            }
        }
    }

    companion object {
        const val ADD_COMPUTER_UUID = "__ADD_COMPUTER__"

        private const val PREF_SHOW_UNPAIRED_DEVICES = "show_unpaired_devices"
        private const val TARGET_SIZE = 128
        private const val ONLINE_ALPHA = 0.95f
        private const val OFFLINE_ALPHA = 0.45f

        fun isAddComputerCard(obj: PcView.ComputerObject?): Boolean {
            return obj != null && ADD_COMPUTER_UUID == obj.details.uuid
        }

        private fun isUnpairedComputer(obj: PcView.ComputerObject?): Boolean {
            if (obj?.details == null) return false
            if (isAddComputerCard(obj)) return false
            return obj.details.state == ComputerDetails.State.ONLINE &&
                    obj.details.pairState == PairingManager.PairState.NOT_PAIRED
        }

        private fun applyBoxArt(imgView: ImageView, bitmap: Bitmap) {
            imgView.setImageBitmap(bitmap)
            imgView.scaleType = ImageView.ScaleType.CENTER_CROP
            imgView.clipToOutline = true
        }

        private fun loadBoxArtFromDisk(ctx: Context?, uuid: String?, useAdaptiveSampleSize: Boolean): Bitmap? {
            if (ctx == null || uuid == null) return null

            try {
                val rawAppList = CacheHelper.readInputStreamToString(
                    CacheHelper.openCacheFileForInput(ctx.cacheDir, "applist", uuid)
                )

                if (rawAppList.isEmpty()) return null

                val appList: List<NvApp> = NvHTTP.getAppListByReader(StringReader(rawAppList))
                if (appList.isEmpty()) return null

                val cacheDir = ctx.cacheDir
                for (app in appList) {
                    val boxArtFile = CacheHelper.openPath(false, cacheDir, "boxart", uuid, "${app.appId}.png")
                    if (!boxArtFile.exists() || boxArtFile.length() == 0L) continue

                    val options = BitmapFactory.Options()
                    if (useAdaptiveSampleSize) {
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeFile(boxArtFile.absolutePath, options)
                        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
                        options.inJustDecodeBounds = false
                    } else {
                        options.inSampleSize = 2
                    }

                    val bitmap = BitmapFactory.decodeFile(boxArtFile.absolutePath, options)
                    if (bitmap != null) {
                        LimeLog.info("Loaded box art from disk: ${app.appName}")
                        return bitmap
                    }
                }
            } catch (e: Exception) {
                LimeLog.warning("Failed to load disk cached box art: ${e.message}")
            }

            return null
        }

        private fun calculateSampleSize(width: Int, height: Int): Int {
            var sampleSize = 1
            if (height > TARGET_SIZE || width > TARGET_SIZE) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / sampleSize >= TARGET_SIZE && halfWidth / sampleSize >= TARGET_SIZE) {
                    sampleSize *= 2
                }
            }
            return sampleSize
        }
    }
}
