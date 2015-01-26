package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.limelight.R;

import org.apache.http.util.VersionInfo;

/**
 * Created by Karim on 26.01.2015.
 */
public class VirtualControllerSettings extends Activity
{
    private static VirtualController    controller = null;
    private static View                 view = null;

    static void  setController(VirtualController value)
    {
        controller = value;
    }

    static void  setView(View value)
    {
        view = value;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Inflate the content
        setContentView(R.layout.activity_virtual_controller_settings);

        Toast.makeText(getApplicationContext(), "Not implemented yet!", Toast.LENGTH_SHORT).show();
    }
}
