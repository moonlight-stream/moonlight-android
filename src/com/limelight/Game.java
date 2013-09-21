package com.limelight;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;


public class Game extends Activity {
	private VideoView vv;
	private MediaController mc;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_game);

		vv = (VideoView) findViewById(R.id.videoView);
		
		mc = new MediaController(this);
		mc.setAnchorView(vv);
		
		Uri video = Uri.parse("rtsp://141.213.191.236:47902/nvstream");
		vv.setMediaController(mc);
		vv.setVideoURI(video);
		
		vv.start();
	}
}
