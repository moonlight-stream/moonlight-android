package com.limelight;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.NvConnection;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.content.SharedPreferences;

public class Connection extends Activity {
	private Button statusButton;
	private TextView hostText;
	private SharedPreferences prefs;
	
	private static final String DEFAULT_HOST = "141.213.191.238";
	public static final String HOST_KEY = "hostText";
	

	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onPause() {
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putString(this.HOST_KEY, this.hostText.toString());
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
		setContentView(R.layout.activity_connection);
		
		
		this.statusButton = (Button) findViewById(R.id.statusButton);
		this.hostText = (TextView) findViewById(R.id.hostTextView);
		
		prefs = getPreferences(0);
		this.hostText.setText(prefs.getString(this.HOST_KEY, this.DEFAULT_HOST));
		
		this.statusButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				try {
					new NvConnection(Connection.this.statusButton.getText().toString()).doShit();
				} catch (XmlPullParserException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		});
	}

}
