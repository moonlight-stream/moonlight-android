package com.limelight;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.limelight.nvstream.NvmDNS;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

public class Connection extends Activity {
	private Button statusButton;
	private TextView hostText;
	private SharedPreferences prefs;
	
	private static final String DEFAULT_HOST = "35.0.113.120";
	public static final String HOST_KEY = "hostText";
	

	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onPause() {
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putString(Connection.HOST_KEY, this.hostText.getText().toString());
		editor.apply();
		
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.v("NvmDNS", "onCreate");
		
		try {
			
			NvmDNS dns = new NvmDNS();
			dns.execute();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		
		setContentView(R.layout.activity_connection);
		
		
	//	this.statusButton = (Button) findViewById(R.id.statusButton);
	//	this.hostText = (TextView) findViewById(R.id.hostTextView);
		
		//prefs = getPreferences(0);
		//this.hostText.setText(prefs.getString(Connection.HOST_KEY, Connection.DEFAULT_HOST));
		
		/*this.statusButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(Connection.this, Game.class);
				intent.putExtra("host", Connection.this.hostText.getText().toString());
				Connection.this.startActivity(intent);
			}
		});*/
	}

}
