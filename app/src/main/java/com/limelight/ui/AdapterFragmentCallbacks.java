package com.limelight.ui;

import android.widget.AbsListView;

public interface AdapterFragmentCallbacks {
    public int getAdapterFragmentLayoutId();
    public void receiveAbsListView(AbsListView gridView);
}
