package com.limelight.grid

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

import com.limelight.R

abstract class GenericGridAdapter<T>(
    protected val context: Context,
    private var layoutId: Int
) : BaseAdapter() {

    val itemList = ArrayList<T>()
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    // Track a selected position for UI updates (some activities call setSelectedPosition)
    var selectedPosition = -1
        set(value) {
            field = value
        }

    fun setLayoutId(layoutId: Int) {
        if (layoutId != this.layoutId) {
            this.layoutId = layoutId
            // Force the view to be redrawn with the new layout
            notifyDataSetInvalidated()
        }
    }

    open fun clear() {
        itemList.clear()
    }

    override fun getCount(): Int = itemList.size

    override fun getItem(i: Int): Any? = itemList[i] as Any

    override fun getItemId(i: Int): Long = i.toLong()

    abstract fun populateView(parentView: View, imgView: ImageView?, spinnerView: View?, txtView: TextView?, overlayView: ImageView?, obj: T)

    override fun getView(i: Int, convertView: View?, viewGroup: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(layoutId, viewGroup, false)

        // onCreateViewHolder may call getView(0) when itemList is empty just to inflate a layout.
        // Guard against out-of-bounds access to avoid NPE/IndexOutOfBoundsException.
        if (i < 0 || i >= itemList.size) {
            return view
        }

        val imgView = view.findViewById<ImageView>(R.id.grid_image)
        val overlayView = view.findViewById<ImageView>(R.id.grid_overlay)
        val txtView = view.findViewById<TextView>(R.id.grid_text)
        val spinnerView = view.findViewById<View>(R.id.grid_spinner)

        populateView(view, imgView, spinnerView, txtView, overlayView, itemList[i])

        return view
    }
}
