package com.limelight;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.limelight.computers.ComputerManagerService;
import com.limelight.utils.Dialog;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class AddComputerManually extends Activity {
	private Button addPcButton;
	private TextView hostText;
	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, final IBinder binder) {
			new Thread() {
				@Override
				public void run() {
					String msg;
					boolean finish = false;
					try {
						InetAddress addr = InetAddress.getByName(hostText.getText().toString());
						
						if (!((ComputerManagerService.ComputerManagerBinder)binder).addComputerBlocking(addr)){
							msg = "Unable to connect to the specified computer. Make sure the required ports are allowed through the firewall.";
						}
						else {
							msg = "Successfully added computer";
							finish = true;
						}
					} catch (UnknownHostException e) {
						msg = "Unable to resolve PC address. Make sure you didn't make a typo in the address.";
					}
					
					final boolean toastFinish = finish;
					final String toastMsg = msg;
					AddComputerManually.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							// Unbind from this service
							unbindService(AddComputerManually.this.serviceConnection);
							
							Toast.makeText(AddComputerManually.this, toastMsg, Toast.LENGTH_LONG).show();
							
							if (toastFinish) {
								// Close the activity
								AddComputerManually.this.finish();
							}
						}
					});
				}
			}.start();
		}

		public void onServiceDisconnected(ComponentName className) {
		}
	};
	
	@Override
	protected void onStop() {
		super.onStop();
		
		Dialog.closeDialogs();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_add_computer_manually);
		
		this.addPcButton = (Button) findViewById(R.id.addPc);
		this.hostText = (TextView) findViewById(R.id.hostTextView);
	
		addPcButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Toast.makeText(AddComputerManually.this, "Adding PC...", Toast.LENGTH_LONG).show();
				
				// Bind to the service which will try to add the PC
				bindService(new Intent(AddComputerManually.this, ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
			}
		});
	}
}
