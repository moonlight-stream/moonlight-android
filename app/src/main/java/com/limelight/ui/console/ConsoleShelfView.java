package com.limelight.ui.console;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConsoleShelfView extends RecyclerView {
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
