package com.limelight;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.NvConnection;

import android.os.Bundle;
import android.app.Activity;

public class Connection extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connection);
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					new NvConnection("141.213.191.238").doShit();
				} catch (XmlPullParserException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}).start();
	}

}
