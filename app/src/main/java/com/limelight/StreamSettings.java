package com.limelight;

import com.limelight.R;
import com.limelight.utils.Dialog;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class StreamSettings extends Activity {
	private Button advancedSettingsButton;
	private SharedPreferences prefs;
	private RadioButton rbutton720p30, rbutton720p60, rbutton1080p30, rbutton1080p60;
	private CheckBox stretchToFill, enableSops, toastsDisabled;
	
	@Override
	protected void onStop() {
		super.onStop();
		
		Dialog.closeDialogs();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_stream_settings);
		
		this.stretchToFill = (CheckBox) findViewById(R.id.stretchToFill);
		this.enableSops = (CheckBox) findViewById(R.id.enableSops);
		this.toastsDisabled = (CheckBox) findViewById(R.id.disableToasts);
		this.advancedSettingsButton = (Button) findViewById(R.id.advancedSettingsButton);
		this.rbutton720p30 = (RadioButton) findViewById(R.id.config720p30Selected);
		this.rbutton720p60 = (RadioButton) findViewById(R.id.config720p60Selected);
		this.rbutton1080p30 = (RadioButton) findViewById(R.id.config1080p30Selected);
		this.rbutton1080p60 = (RadioButton) findViewById(R.id.config1080p60Selected);

		prefs = getSharedPreferences(Game.PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		
		boolean res720p = prefs.getInt(Game.HEIGHT_PREF_STRING, Game.DEFAULT_HEIGHT) == 720;
		boolean fps30 = prefs.getInt(Game.REFRESH_RATE_PREF_STRING, Game.DEFAULT_REFRESH_RATE) == 30;

		stretchToFill.setChecked(prefs.getBoolean(Game.STRETCH_PREF_STRING, Game.DEFAULT_STRETCH));
		enableSops.setChecked(prefs.getBoolean(Game.SOPS_PREF_STRING, Game.DEFAULT_SOPS));
		toastsDisabled.setChecked(prefs.getBoolean(Game.DISABLE_TOASTS_PREF_STRING, Game.DEFAULT_DISABLE_TOASTS));
		
		rbutton720p30.setChecked(false);
		rbutton720p60.setChecked(false);
		rbutton1080p30.setChecked(false);
		rbutton1080p60.setChecked(false);
		if (res720p) {
			if (fps30) {
				rbutton720p30.setChecked(true);
			}
			else {
				rbutton720p60.setChecked(true);
			}
		}
		else {
			if (fps30) {
				rbutton1080p30.setChecked(true);
			}
			else {
				rbutton1080p60.setChecked(true);
			}
		}
		
		OnCheckedChangeListener occl = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (!isChecked) {
					// Ignore non-checked buttons
					return;
				}
				
				if (buttonView == rbutton720p30) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1280).
					putInt(Game.HEIGHT_PREF_STRING, 720).
					putInt(Game.REFRESH_RATE_PREF_STRING, 30).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_720_30).commit();
				}
				else if (buttonView == rbutton720p60) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1280).
					putInt(Game.HEIGHT_PREF_STRING, 720).
					putInt(Game.REFRESH_RATE_PREF_STRING, 60).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_720_60).commit();
				}
				else if (buttonView == rbutton1080p30) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
					putInt(Game.HEIGHT_PREF_STRING, 1080).
					putInt(Game.REFRESH_RATE_PREF_STRING, 30).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_1080_30).commit();
				}
				else if (buttonView == rbutton1080p60) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
					putInt(Game.HEIGHT_PREF_STRING, 1080).
					putInt(Game.REFRESH_RATE_PREF_STRING, 60).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_1080_60).commit();
				}
			}
		};
		rbutton720p30.setOnCheckedChangeListener(occl);
		rbutton720p60.setOnCheckedChangeListener(occl);
		rbutton1080p30.setOnCheckedChangeListener(occl);
		rbutton1080p60.setOnCheckedChangeListener(occl);
		
		advancedSettingsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(StreamSettings.this, AdvancedSettings.class);
				startActivity(i);
			}
		});
		stretchToFill.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				prefs.edit().putBoolean(Game.STRETCH_PREF_STRING, isChecked).commit();
			}
		});
		enableSops.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				prefs.edit().putBoolean(Game.SOPS_PREF_STRING, isChecked).commit();
			}
		});
		toastsDisabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				prefs.edit().putBoolean(Game.DISABLE_TOASTS_PREF_STRING, isChecked).commit();
			}
		});
	}
}
