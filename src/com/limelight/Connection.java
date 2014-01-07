package com.limelight;

import java.io.IOException;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.NvHTTP;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class Connection extends Activity {
	private Button statusButton, pairButton;
	private TextView hostText;
	private SharedPreferences prefs;
	private CheckBox qualityCheckbox;
	private RadioButton rbutton720p, rbutton1080p, rbutton30fps, rbutton60fps;
	private RadioButton forceSoftDec, autoDec, forceHardDec;
	
	private static final String DEFAULT_HOST = "";
	public static final String HOST_KEY = "hostText";

	@Override
	public void onPause() {
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putString(Connection.HOST_KEY, this.hostText.getText().toString());
		editor.apply();
		
		super.onPause();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_connection);
		
		this.statusButton = (Button) findViewById(R.id.statusButton);
		this.pairButton = (Button) findViewById(R.id.pairButton);
		this.hostText = (TextView) findViewById(R.id.hostTextView);
		this.qualityCheckbox = (CheckBox) findViewById(R.id.imageQualityCheckbox);
		this.rbutton720p = (RadioButton) findViewById(R.id.res720pSelected);
		this.rbutton1080p = (RadioButton) findViewById(R.id.res1080pSelected);
		this.rbutton30fps = (RadioButton) findViewById(R.id.rr30Selected);
		this.rbutton60fps = (RadioButton) findViewById(R.id.rr60Selected);
		this.forceSoftDec = (RadioButton) findViewById(R.id.softwareDec);
		this.autoDec = (RadioButton) findViewById(R.id.autoDec);
		this.forceHardDec = (RadioButton) findViewById(R.id.hardwareDec);
		
		//avoid keyboard popup on start
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		prefs = getSharedPreferences(Game.PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		this.hostText.setText(prefs.getString(Connection.HOST_KEY, Connection.DEFAULT_HOST));
		this.qualityCheckbox.setChecked(prefs.getBoolean(Game.QUALITY_PREF_STRING, false));
		
		if (prefs.getInt(Game.HEIGHT_PREF_STRING, Game.DEFAULT_HEIGHT) == 720) {
			rbutton720p.setChecked(true);
			rbutton1080p.setChecked(false);
		}
		else {
			rbutton1080p.setChecked(true);
			rbutton720p.setChecked(false);
		}
		
		if (prefs.getInt(Game.REFRESH_RATE_PREF_STRING, Game.DEFAULT_REFRESH_RATE) == 30) {
			rbutton30fps.setChecked(true);
			rbutton60fps.setChecked(false);
		}
		else {
			rbutton60fps.setChecked(true);
			rbutton30fps.setChecked(false);
		}
		
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
				
				if (buttonView == rbutton30fps) {
					prefs.edit().putInt(Game.REFRESH_RATE_PREF_STRING, 30).commit();
				}
				else if (buttonView == rbutton60fps) {
					prefs.edit().putInt(Game.REFRESH_RATE_PREF_STRING, 60).commit();
				}
				else if (buttonView == rbutton720p) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1280).
						putInt(Game.HEIGHT_PREF_STRING, 720).commit();
				}
				else if (buttonView == rbutton1080p) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
						putInt(Game.HEIGHT_PREF_STRING, 1080).commit();
				}
				else if (buttonView == forceSoftDec) {
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
		rbutton720p.setOnCheckedChangeListener(occl);
		rbutton1080p.setOnCheckedChangeListener(occl);
		rbutton30fps.setOnCheckedChangeListener(occl);
		rbutton60fps.setOnCheckedChangeListener(occl);
		forceSoftDec.setOnCheckedChangeListener(occl);
		forceHardDec.setOnCheckedChangeListener(occl);
		autoDec.setOnCheckedChangeListener(occl);
		
		this.qualityCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton checkbox, boolean isChecked) {
				SharedPreferences.Editor editor = prefs.edit();
				editor.putBoolean(Game.QUALITY_PREF_STRING, isChecked);
				editor.commit();
			}
		});
		
		this.statusButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(Connection.this, Game.class);
				intent.putExtra("host", Connection.this.hostText.getText().toString());
				Connection.this.startActivity(intent);
			}
		});
		
		this.pairButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Toast.makeText(Connection.this, "Pairing...", Toast.LENGTH_LONG).show();
				new Thread(new Runnable() {
					@Override
					public void run() {
						String macAddress;
						try {
							macAddress = NvConnection.getMacAddressString();
						} catch (SocketException e) {
							e.printStackTrace();
							return;
						}
						
						if (macAddress == null) {
							System.out.println("Couldn't find a MAC address");
							return;
						}
						
						NvHTTP httpConn;
						String message;
						try {
							httpConn = new NvHTTP(InetAddress.getByName(hostText.getText().toString()),
									macAddress, PlatformBinding.getDeviceName());
							try {
								if (httpConn.getPairState()) {
									message = "Already paired";
								}
								else {
									int session = httpConn.getSessionId();
									if (session == 0) {
										message = "Pairing was declined by the target";
									}
									else {
										message = "Pairing was successful";
									}
								}
							} catch (IOException e) {
								message = e.getMessage();
							} catch (XmlPullParserException e) {
								message = e.getMessage();
							}
						} catch (UnknownHostException e1) {
							message = "Failed to resolve host";
						}
						
						final String toastMessage = message;
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(Connection.this, toastMessage, Toast.LENGTH_LONG).show();
							}
						});
					}
				}).start();
			}
		});

	}

}
