package com.limelight.ui

import android.content.Context
import android.database.DataSetObserver
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * A small bridge to reuse existing BaseAdapter implementations (like GenericGridAdapter)
 * inside a RecyclerView. It will call BaseAdapter.getView() and attach the returned
 * view into the ViewHolder container.
 */
class AdapterRecyclerBridge(
    private val context: Context,
    private val baseAdapter: BaseAdapter?
) : RecyclerView.Adapter<AdapterRecyclerBridge.VH>() {

    private var onItemClickListener: OnItemClickListener? = null
    private var onItemKeyListener: OnItemKeyListener? = null
    private var onItemLongClickListener: OnItemLongClickListener? = null

    // A键长按检测相关
    private var aKeyDownTime = 0L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private val dataSetObserver = object : DataSetObserver() {
        override fun onChanged() {
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            notifyDataSetChanged()
        }
    }
    private var observerRegistered = false

    init {
        setHasStableIds(true)
        baseAdapter?.registerDataSetObserver(dataSetObserver)
        observerRegistered = baseAdapter != null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        if (baseAdapter == null) {
            return VH(View(parent.context))
        }
        val v = baseAdapter.getView(0, null, parent)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (baseAdapter == null) return

        var needsListenerSetup = holder.container.tag == null

        val convert = holder.container
        val parentGroup = (convert.parent as? ViewGroup) ?: (holder.itemView.rootView as? ViewGroup)
        val populated = baseAdapter.getView(position, convert, parentGroup)

        if (populated !== convert) {
            val actualParent = convert.parent as? ViewGroup
            if (actualParent != null) {
                val index = actualParent.indexOfChild(convert)
                actualParent.removeViewAt(index)
                actualParent.addView(populated, index)
                holder.container = populated
                needsListenerSetup = true
            }
        }

        if (needsListenerSetup) {
            holder.container.isFocusable = true
            holder.container.isClickable = true
            holder.container.tag = "listeners_set"

            holder.container.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onItemClick(adapterPosition, baseAdapter.getItem(adapterPosition))
                }
            }

            holder.container.setOnKeyListener { _, keyCode, event ->
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnKeyListener false

                onItemKeyListener?.let {
                    return@setOnKeyListener it.onItemKey(adapterPosition, baseAdapter.getItem(adapterPosition), keyCode, event)
                }

                // 适配器内部处理A键长按检测
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            aKeyDownTime = System.currentTimeMillis()
                            longPressRunnable = Runnable {
                                val pos = holder.bindingAdapterPosition
                                if (pos != RecyclerView.NO_POSITION) {
                                    onItemLongClickListener?.onItemLongClick(pos, baseAdapter.getItem(pos))
                                }
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION)
                            return@setOnKeyListener true
                        }
                        KeyEvent.ACTION_UP -> {
                            val pressDuration = System.currentTimeMillis() - aKeyDownTime
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            longPressRunnable = null
                            if (pressDuration < LONG_PRESS_DURATION && adapterPosition != RecyclerView.NO_POSITION) {
                                onItemClickListener?.onItemClick(adapterPosition, baseAdapter.getItem(adapterPosition))
                            }
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }

            holder.container.setOnLongClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemLongClickListener?.onItemLongClick(adapterPosition, baseAdapter.getItem(adapterPosition)) ?: false
                } else {
                    false
                }
            }
        }
    }

    override fun getItemCount(): Int = baseAdapter?.count ?: 0

    override fun getItemId(position: Int): Long {
        return baseAdapter?.getItemId(position) ?: RecyclerView.NO_ID
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.onItemClickListener = listener
    }

    fun setOnItemKeyListener(listener: OnItemKeyListener?) {
        this.onItemKeyListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        this.onItemLongClickListener = listener
    }

    fun interface OnItemClickListener {
        fun onItemClick(position: Int, item: Any)
    }

    fun interface OnItemKeyListener {
        fun onItemKey(position: Int, item: Any, keyCode: Int, event: KeyEvent): Boolean
    }

    fun interface OnItemLongClickListener {
        fun onItemLongClick(position: Int, item: Any): Boolean
    }

    fun cleanup() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        if (observerRegistered) {
            baseAdapter?.unregisterDataSetObserver(dataSetObserver)
            observerRegistered = false
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var container: View = itemView
    }

    companion object {
        private const val LONG_PRESS_DURATION = 1000L
    }
}
