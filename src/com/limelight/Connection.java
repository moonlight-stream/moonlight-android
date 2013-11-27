package com.limelight;

import java.io.IOException;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvHTTP;
import com.limelight.nvstream.NvmDNS;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
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
		
		Log.v("NvmDNS", "onCreate");
		
			
		NvmDNS dns = new NvmDNS();
		dns.execute();
		
		
		setContentView(R.layout.activity_connection);
		
		this.statusButton = (Button) findViewById(R.id.statusButton);
		this.pairButton = (Button) findViewById(R.id.pairButton);
		this.hostText = (TextView) findViewById(R.id.hostTextView);
		this.qualityCheckbox = (CheckBox) findViewById(R.id.imageQualityCheckbox);
		
		prefs = getSharedPreferences(Game.PREFS_FILE_NAME, Context.MODE_MULTI_PROCESS);
		this.hostText.setText(prefs.getString(Connection.HOST_KEY, Connection.DEFAULT_HOST));
		this.qualityCheckbox.setChecked(prefs.getBoolean(Game.QUALITY_PREF_STRING, false));
		
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
							httpConn = new NvHTTP(InetAddress.getByName(hostText.getText().toString()), macAddress);
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
