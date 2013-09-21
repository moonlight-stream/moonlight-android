package com.limelight;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.NvController;
import com.limelight.nvstream.input.NvInputPacket;

import tv.ouya.console.api.OuyaController;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.MediaController;
import android.widget.VideoView;


public class Game extends Activity {
	private VideoView vv;
	private MediaController mc;
	private short inputMap = 0x0000;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		OuyaController.init(this);
		
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
		
		setContentView(R.layout.activity_game);

		/*vv = (VideoView) findViewById(R.id.videoView);
		
		mc = new MediaController(this);
		mc.setAnchorView(vv);
		
		Uri video = Uri.parse("rtsp://141.213.191.236:47902/nvstream");
		vv.setMediaController(mc);
		vv.setVideoURI(video);
		
		vv.start();*/
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
//		int player = OuyaController.getPlayerNumByDeviceId(event.getDeviceId());
		
		switch (keyCode) {
		case OuyaController.BUTTON_MENU:
			System.out.println("Pressed Menu Button");
			inputMap |= NvInputPacket.PLAY_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_LEFT:
			System.out.println("Pressed Dpad Left");
			inputMap |= NvInputPacket.LEFT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_RIGHT:
			System.out.println("Pressed Dpad Right");
			inputMap |= NvInputPacket.RIGHT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_UP:
			System.out.println("Pressed Dpad Up");
			inputMap |= NvInputPacket.UP_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_DOWN:
			System.out.println("Pressed Dpad Down");
			inputMap |= NvInputPacket.DOWN_FLAG;
			break;
		case OuyaController.BUTTON_A: 
			System.out.println("Pressed A");
			inputMap |= NvInputPacket.B_FLAG;
			break;
		case OuyaController.BUTTON_O:
			System.out.println("Pressed O");
			inputMap |= NvInputPacket.A_FLAG;
			break;
		case OuyaController.BUTTON_U:
			System.out.println("Pressed U");
			inputMap |= NvInputPacket.X_FLAG;
			break;
		case OuyaController.BUTTON_Y:
			System.out.println("Pressed Y");
			inputMap |= NvInputPacket.Y_FLAG;
			break;
		case OuyaController.BUTTON_L1:
			System.out.println("Pressed L1");
			inputMap |= NvInputPacket.LB_FLAG;
			break;
		case OuyaController.BUTTON_R1:
			System.out.println("Pressed R1");
			inputMap |= NvInputPacket.RB_FLAG;
			break;
		case OuyaController.BUTTON_L3:
			System.out.println("Pressed L3");
			inputMap |= NvInputPacket.LS_CLK_FLAG;
			break;
		case OuyaController.BUTTON_R3:
			System.out.println("Pressed R3");
			inputMap |= NvInputPacket.RS_CLK_FLAG;
			break;
		default:
			System.out.println("Pressed some button: " + keyCode);
			return super.onKeyDown(keyCode, event);
		}
		sendInputPacket();
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case OuyaController.BUTTON_MENU:
			System.out.println("Released Menu Button");
			inputMap &= ~NvInputPacket.PLAY_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_LEFT:
			System.out.println("Released Dpad Left");
			inputMap &= ~NvInputPacket.LEFT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_RIGHT:
			System.out.println("Released Dpad Right");
			inputMap &= ~NvInputPacket.RIGHT_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_UP:
			System.out.println("Released Dpad Up");
			inputMap &= ~NvInputPacket.UP_FLAG;
			break;
		case OuyaController.BUTTON_DPAD_DOWN:
			System.out.println("Released Dpad Down");
			inputMap &= ~NvInputPacket.DOWN_FLAG;
			break;
		case OuyaController.BUTTON_A: 
			System.out.println("Released A");
			inputMap &= ~NvInputPacket.B_FLAG;
			break;
		case OuyaController.BUTTON_O:
			System.out.println("Released O");
			inputMap &= ~NvInputPacket.A_FLAG;
			break;
		case OuyaController.BUTTON_U:
			System.out.println("Released U");
			inputMap &= ~NvInputPacket.X_FLAG;
			break;
		case OuyaController.BUTTON_Y:
			System.out.println("Released Y");
			inputMap &= ~NvInputPacket.Y_FLAG;
			break;
		case OuyaController.BUTTON_L1:
			System.out.println("Released L1");
			inputMap &= ~NvInputPacket.LB_FLAG;
			break;
		case OuyaController.BUTTON_R1:
			System.out.println("Released R1");
			inputMap &= ~NvInputPacket.RB_FLAG;
			break;
		case OuyaController.BUTTON_L3:
			System.out.println("Released L3");
			inputMap &= ~NvInputPacket.LS_CLK_FLAG;
			break;
		case OuyaController.BUTTON_R3:
			System.out.println("Released R3");
			inputMap &= ~NvInputPacket.RS_CLK_FLAG;
			break;
		default:
			System.out.println("Released some button: " + keyCode);
			return super.onKeyUp(keyCode, event);
		}
		sendInputPacket();
		return true;	
	}
	
	private void sendInputPacket() {
		NvInputPacket inputPacket = new NvInputPacket(inputMap, (byte)0, (byte)0, (byte)0, (byte)0);
		
	}
}
