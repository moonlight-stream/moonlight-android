package com.limelight.ui.console;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConsoleShelfView extends RecyclerView {
    private int centeredItemWidthPx;
    private int centeredItemGapPx;

    public ConsoleShelfView(Context context) {
        this(context, null);
    }

    public ConsoleShelfView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutManager(new LinearLayoutManager(context, HORIZONTAL, false));
        setClipToPadding(false);
        setClipChildren(false);
        setHasFixedSize(true);
        setItemAnimator(null);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
    }

    public void setCenteredItemMetrics(int itemWidthPx, int itemGapPx) {
        centeredItemWidthPx = itemWidthPx;
        centeredItemGapPx = itemGapPx;
        refreshHorizontalCentering();
    }

    public void refreshHorizontalCentering() {
        Adapter<?> adapter = getAdapter();
        int itemCount = adapter == null ? 0 : adapter.getItemCount();
        if (getWidth() == 0 || centeredItemWidthPx <= 0 || itemCount <= 0) {
            if (getPaddingLeft() != 0 || getPaddingRight() != 0) {
                setPadding(0, getPaddingTop(), 0, getPaddingBottom());
            }
            return;
        }

        // pc_grid_item applies layout_marginEnd as the inter-item gap on every row,
        // including the trailing item, so include that trailing margin here.
        int contentWidth = itemCount * (centeredItemWidthPx + centeredItemGapPx);
        int sidePadding = Math.max(0, (getWidth() - contentWidth) / 2);
        if (getPaddingLeft() != sidePadding || getPaddingRight() != sidePadding) {
            setPadding(sidePadding, getPaddingTop(), sidePadding, getPaddingBottom());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            refreshHorizontalCentering();
        }
    }

    public void centerFocusedChild(View child) {
        if (child == null || getWidth() == 0) {
            return;
        }
        int childCenter = child.getLeft() + child.getWidth() / 2;
        int viewportCenter = getPaddingLeft() +
                (getWidth() - getPaddingLeft() - getPaddingRight()) / 2;
        smoothScrollBy(childCenter - viewportCenter, 0);
    }
}
