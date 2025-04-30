package com.limelight;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.ui.StreamView;

/**
 * Description
 * Date: 2024-03-29
 * Time: 17:26
 */
public class SecondaryDisplayPresentation extends Presentation {

    private FrameLayout view;
    public SecondaryDisplayPresentation(Context context, Display display) {
        super(context, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = (FrameLayout) View.inflate(getContext(),R.layout.activity_game_display,null);
        setContentView(view);
    }

    public void addView(StreamView streamView){
        view.addView(streamView);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        view.removeAllViews();
    }
}