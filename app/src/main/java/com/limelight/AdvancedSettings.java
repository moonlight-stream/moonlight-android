package com.limelight;

import com.limelight.R;
import com.limelight.utils.Dialog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class AdvancedSettings extends Activity {
	private SharedPreferences prefs;
	private RadioButton forceSoftDec, autoDec, forceHardDec;
	private SeekBar bitrateSlider;
	private TextView bitrateLabel;
	
	private static final int BITRATE_FLOOR = 1;
	private static final int BITRATE_CEILING = 100;

	@Override
	public void onPause() {
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putInt(Game.BITRATE_PREF_STRING, bitrateSlider.getProgress());
		editor.apply();
		
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		Dialog.closeDialogs();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_advanced_settings);
		
		this.forceSoftDec = (RadioButton) findViewById(R.id.softwareDec);
		this.autoDec = (RadioButton) findViewById(R.id.autoDec);
		this.forceHardDec = (RadioButton) findViewById(R.id.hardwareDec);
		this.bitrateLabel = (TextView) findViewById(R.id.bitrateLabel);
		this.bitrateSlider = (SeekBar) findViewById(R.id.bitrateSeekBar);

		prefs = getSharedPreferences(Game.PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		
		bitrateSlider.setMax(BITRATE_CEILING);
		bitrateSlider.setProgress(prefs.getInt(Game.BITRATE_PREF_STRING, Game.DEFAULT_BITRATE));
		updateBitrateLabel();
		
		switch (prefs.getInt(Game.DECODER_PREF_STRING, Game.DEFAULT_DECODER)) {
		case Game.FORCE_SOFTWARE_DECODER:
			forceSoftDec.setChecked(true);
			autoDec.setChecked(false);
			forceHardDec.setChecked(false);
			break;
		case Game.AUTOSELECT_DECODER:
			forceSoftDec.setChecked(false);
			autoDec.setChecked(true);
			forceHardDec.setChecked(false);
			break;
		case Game.FORCE_HARDWARE_DECODER:
			forceSoftDec.setChecked(false);
			autoDec.setChecked(false);
			forceHardDec.setChecked(true);
			break;
		}
		
		OnCheckedChangeListener occl = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (!isChecked) {
					// Ignore non-checked buttons
					return;
				}
				
				if (buttonView == forceSoftDec) {
					prefs.edit().putInt(Game.DECODER_PREF_STRING, Game.FORCE_SOFTWARE_DECODER).commit();
				}
				else if (buttonView == forceHardDec) {
					prefs.edit().putInt(Game.DECODER_PREF_STRING, Game.FORCE_HARDWARE_DECODER).commit();
				}
				else if (buttonView == autoDec) {
					prefs.edit().putInt(Game.DECODER_PREF_STRING, Game.AUTOSELECT_DECODER).commit();
				}
			}
		};
		forceSoftDec.setOnCheckedChangeListener(occl);
		forceHardDec.setOnCheckedChangeListener(occl);
		autoDec.setOnCheckedChangeListener(occl);
		
		this.bitrateSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {

				// Verify the user's selection
				if (fromUser) {
					if (progress < BITRATE_FLOOR) {
						seekBar.setProgress(BITRATE_FLOOR);
						return;
					}
				}

				updateBitrateLabel();
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}

	private void updateBitrateLabel() {
		bitrateLabel.setText("Max Bitrate: "+bitrateSlider.getProgress()+" Mbps");
	}
}
