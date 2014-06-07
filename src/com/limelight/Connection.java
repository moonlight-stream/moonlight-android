package com.limelight;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;

public class Connection extends Activity {
	private Button startButton, pairButton;
	private TextView hostText;
	private SharedPreferences prefs;
	private RadioButton rbutton720p30, rbutton720p60, rbutton1080p30, rbutton1080p60;
	private RadioButton forceSoftDec, autoDec, forceHardDec;
	private SeekBar bitrateSlider;
	private TextView bitrateLabel, currentAppLabel;
	private ListView appsListView;
	private List<String> supportedApps;
	private ArrayAdapter<String> appsArrayAdapter;
	
	private static final String DEFAULT_HOST = "";
	public static final String HOST_KEY = "hostText";

	@Override
	public void onPause() {
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putString(Connection.HOST_KEY, this.hostText.getText().toString());
		editor.putInt(Game.BITRATE_PREF_STRING, bitrateSlider.getProgress());
		editor.apply();
		
		super.onPause();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_connection);
		
		// Hide the keyboard by default
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		this.startButton = (Button) findViewById(R.id.startButton);
		this.pairButton = (Button) findViewById(R.id.pairButton);
		this.hostText = (TextView) findViewById(R.id.hostTextView);
		this.rbutton720p30 = (RadioButton) findViewById(R.id.config720p30Selected);
		this.rbutton720p60 = (RadioButton) findViewById(R.id.config720p60Selected);
		this.rbutton1080p30 = (RadioButton) findViewById(R.id.config1080p30Selected);
		this.rbutton1080p60 = (RadioButton) findViewById(R.id.config1080p60Selected);
		this.forceSoftDec = (RadioButton) findViewById(R.id.softwareDec);
		this.autoDec = (RadioButton) findViewById(R.id.autoDec);
		this.forceHardDec = (RadioButton) findViewById(R.id.hardwareDec);
		this.bitrateLabel = (TextView) findViewById(R.id.bitrateLabel);
		this.bitrateSlider = (SeekBar) findViewById(R.id.bitrateSeekBar);
		this.appsListView = (ListView) findViewById(R.id.gamesListView);
		this.currentAppLabel = (TextView) findViewById(R.id.currentAppLabel);

		prefs = getSharedPreferences(Game.PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		this.hostText.setText(prefs.getString(Connection.HOST_KEY, Connection.DEFAULT_HOST));
		
		boolean res720p = prefs.getInt(Game.HEIGHT_PREF_STRING, Game.DEFAULT_HEIGHT) == 720;
		boolean fps30 = prefs.getInt(Game.REFRESH_RATE_PREF_STRING, Game.DEFAULT_REFRESH_RATE) == 30;
		
		bitrateSlider.setMax(Game.BITRATE_CEILING);
		bitrateSlider.setProgress(prefs.getInt(Game.BITRATE_PREF_STRING, Game.DEFAULT_BITRATE));
		updateBitrateLabel();

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
		
		// loading the apps from preferences
		supportedApps = new ArrayList<String>(prefs.getStringSet(Game.APP_LIST, Game.DEFAULT_APP_LIST));
		Collections.sort(supportedApps);
		
		if (supportedApps.isEmpty()) {
			appsListView.setVisibility(View.GONE);
		}
		
	    updateCurrentAppLabel();
		
	    appsArrayAdapter = new ArrayAdapter<String>(this,
	        R.layout.app_layout, supportedApps);
	    appsListView.setAdapter(appsArrayAdapter);
	    
	    // taken from http://stackoverflow.com/questions/18367522/android-list-view-inside-a-scroll-view
	    appsListView.setOnTouchListener(new OnTouchListener() {
	        // Setting on Touch Listener for handling the touch inside ScrollView
	        @Override
	        public boolean onTouch(View v, MotionEvent event) {
	        // Disallow the touch request for parent scroll on touch of child view
	        v.getParent().requestDisallowInterceptTouchEvent(true);
	        return false;
	        }
	    });
	    
	    appsListView.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	        	prefs.edit().
	        	putString(Game.SELECTED_APP, appsArrayAdapter.getItem(position)).
	        	commit();
	        	updateCurrentAppLabel();
	        }
	    });
	    
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
					bitrateSlider.setProgress(Game.BITRATE_DEFAULT_720_30);
				}
				else if (buttonView == rbutton720p60) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1280).
					putInt(Game.HEIGHT_PREF_STRING, 720).
					putInt(Game.REFRESH_RATE_PREF_STRING, 60).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_720_60).commit();
					bitrateSlider.setProgress(Game.BITRATE_DEFAULT_720_60);
				}
				else if (buttonView == rbutton1080p30) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
					putInt(Game.HEIGHT_PREF_STRING, 1080).
					putInt(Game.REFRESH_RATE_PREF_STRING, 30).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_1080_30).commit();
					bitrateSlider.setProgress(Game.BITRATE_DEFAULT_1080_30);
				}
				else if (buttonView == rbutton1080p60) {
					prefs.edit().putInt(Game.WIDTH_PREF_STRING, 1920).
					putInt(Game.HEIGHT_PREF_STRING, 1080).
					putInt(Game.REFRESH_RATE_PREF_STRING, 60).
					putInt(Game.BITRATE_PREF_STRING, Game.BITRATE_DEFAULT_1080_60).commit();
					bitrateSlider.setProgress(Game.BITRATE_DEFAULT_1080_60);
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
		
		this.bitrateSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				
				// Verify the user's selection
				if (fromUser) {
					int floor;
					if (rbutton720p30.isChecked()) {
						floor = Game.BITRATE_FLOOR_720_30;
					}
					else if (rbutton720p60.isChecked()){
						floor = Game.BITRATE_FLOOR_720_60;
					}
					else if (rbutton1080p30.isChecked()){
						floor = Game.BITRATE_FLOOR_1080_30;
					}
					else /*if (rbutton1080p60.isChecked())*/ {
						floor = Game.BITRATE_FLOOR_1080_60;
					}
					
					if (progress < floor) {
						seekBar.setProgress(floor);
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
		
		this.startButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (Connection.this.hostText.getText().length() == 0) {
					Toast.makeText(Connection.this, "Please enter the target PC's IP address in the text box at the top of the screen.", Toast.LENGTH_LONG).show();
					return;
				} else if (getCurrentApp() == null) {
					Toast.makeText(Connection.this, "Please choose an app to stream. The app list is showed after pairing with PC.", Toast.LENGTH_LONG).show();
					return;
				}
				
				// Ensure that the bitrate preference is up to date before
				// starting the game activity
				prefs.edit().
				putInt(Game.BITRATE_PREF_STRING, bitrateSlider.getProgress()).
				commit();
				
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
									loadSupportedApps();
								}
								else {
									int session = httpConn.getSessionId();
									if (session == 0) {
										message = "Pairing was declined by the target";
									}
									else {
										message = "Pairing was successful";
										loadSupportedApps();
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
	
	private void loadSupportedApps() {
		if (Connection.this.hostText.getText().length() != 0) {
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
			try {
				httpConn = new NvHTTP(InetAddress.getByName(hostText.getText().toString()),
						macAddress, PlatformBinding.getDeviceName());
					if (httpConn.getPairState()) {
						supportedApps.clear();
						for (NvApp app : httpConn.getAppList()) {
							supportedApps.add(app.getAppName());
						}
						
						Collections.sort(supportedApps);
						
						prefs.edit().
						putStringSet(Game.APP_LIST, new HashSet<String>(supportedApps)).
						commit();
						
						runOnUiThread(new Runnable() {
							public void run() {
								appsArrayAdapter.notifyDataSetChanged();
								appsListView.setVisibility(View.VISIBLE);
							}
						});
						
					}
			} catch (Throwable t) {
				//TODO
				t.printStackTrace();
			}
		}
	}

	private void updateBitrateLabel() {
		bitrateLabel.setText("Max Bitrate: "+bitrateSlider.getProgress()+" Mbps");
	}

	private void updateCurrentAppLabel() {
		String selectedApp = getCurrentApp();
		currentAppLabel.setText(String.format(getString(R.string.current_app), 
				selectedApp != null ? selectedApp : getString(R.string.no_app_selected)));
	}

	private String getCurrentApp() {
		return prefs.getString(Game.SELECTED_APP, Game.DEFAULT_SELECTED_APP);
	}
}
