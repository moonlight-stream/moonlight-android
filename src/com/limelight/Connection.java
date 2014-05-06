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
	private RadioButton rbutton720p30, rbutton720p60, rbutton1080p30, rbutton1080p60;
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
		
		// Hide the keyboard by default
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		this.statusButton = (Button) findViewById(R.id.statusButton);
		this.pairButton = (Button) findViewById(R.id.pairButton);
		this.hostText = (TextView) findViewById(R.id.hostTextView);
		this.rbutton720p30 = (RadioButton) findViewById(R.id.config720p30Selected);
		this.rbutton720p60 = (RadioButton) findViewById(R.id.config720p60Selected);
		this.rbutton1080p30 = (RadioButton) findViewById(R.id.config1080p30Selected);
		this.rbutton1080p60 = (RadioButton) findViewById(R.id.config1080p60Selected);
		this.forceSoftDec = (RadioButton) findViewById(R.id.softwareDec);
		this.autoDec = (RadioButton) findViewById(R.id.autoDec);
		this.forceHardDec = (RadioButton) findViewById(R.id.hardwareDec);

		prefs = getSharedPreferences(Game.PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		this.hostText.setText(prefs.getString(Connection.HOST_KEY, Connection.DEFAULT_HOST));
		
		boolean res720p = prefs.getInt(Game.HEIGHT_PREF_STRING, Game.DEFAULT_HEIGHT) == 720;
		boolean fps30 = prefs.getInt(Game.REFRESH_RATE_PREF_STRING, Game.DEFAULT_REFRESH_RATE) == 30;

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
				
				if (buttonView == rbutton720p30) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1280).
					putInt(Game.HEIGHT_PREF_STRING, 720).
					putInt(Game.REFRESH_RATE_PREF_STRING, 30).commit();
				}
				else if (buttonView == rbutton720p60) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1280).
					putInt(Game.HEIGHT_PREF_STRING, 720).
					putInt(Game.REFRESH_RATE_PREF_STRING, 60).commit();
				}
				else if (buttonView == rbutton1080p30) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
					putInt(Game.HEIGHT_PREF_STRING, 1080).
					putInt(Game.REFRESH_RATE_PREF_STRING, 30).commit();
				}
				else if (buttonView == rbutton1080p60) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
					putInt(Game.HEIGHT_PREF_STRING, 1080).
					putInt(Game.REFRESH_RATE_PREF_STRING, 60).commit();
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
		rbutton720p30.setOnCheckedChangeListener(occl);
		rbutton720p60.setOnCheckedChangeListener(occl);
		rbutton1080p30.setOnCheckedChangeListener(occl);
		rbutton1080p60.setOnCheckedChangeListener(occl);
		forceSoftDec.setOnCheckedChangeListener(occl);
		forceHardDec.setOnCheckedChangeListener(occl);
		autoDec.setOnCheckedChangeListener(occl);
		
		this.statusButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (Connection.this.hostText.getText().length() == 0) {
					Toast.makeText(Connection.this, "Please enter the target PC's IP address in the text box at the top of the screen.", Toast.LENGTH_LONG).show();
					return;
				}
				
				Intent intent = new Intent(Connection.this, Game.class);
				intent.putExtra("host", Connection.this.hostText.getText().toString());
				Connection.this.startActivity(intent);
			}
		});
		
		this.pairButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (Connection.this.hostText.getText().length() == 0) {
					Toast.makeText(Connection.this, "Please enter the target PC's IP address in the text box at the top of the screen.", Toast.LENGTH_LONG).show();
					return;
				}
				
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
							LimeLog.severe("Couldn't find a MAC address");
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
