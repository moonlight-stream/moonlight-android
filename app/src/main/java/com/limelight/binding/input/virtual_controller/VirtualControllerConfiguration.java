package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.R;

/**
 * Created by Karim Mreisi on 22.01.2015.
 */
public class VirtualControllerConfiguration extends Activity
{
	VirtualController virtualController;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// We don't want a title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Full-screen and don't let the display go off
		getWindow().addFlags(
			WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Inflate the content
		setContentView(R.layout.activity_configure_virtual_controller);

		FrameLayout frameLayout =
			(FrameLayout) findViewById(R.id.configure_virtual_controller_frameLayout);

		// start with configuration constructor
		virtualController = new VirtualController(null, frameLayout, this);

		Toast.makeText(getApplicationContext(), "Not implemented yet!", Toast.LENGTH_SHORT).show();
	}
}
