package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.widget.Toast;

import com.limelight.R;

/**
 * Created by Karim on 26.01.2015.
 */
public class VirtualControllerSettings extends Activity
{
	private VirtualController controller = null;

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
