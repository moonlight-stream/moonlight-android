package com.limelight.preferences;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.computers.ComputerManagerService;
import com.limelight.R;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class AddComputerManually extends Activity {
	private TextView hostText;
	private ComputerManagerService.ComputerManagerBinder managerBinder;
	private LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<String>();
	private Thread addThread;
	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, final IBinder binder) {
			managerBinder = ((ComputerManagerService.ComputerManagerBinder)binder);
			startAddThread();
		}

		public void onServiceDisconnected(ComponentName className) {
			joinAddThread();
			managerBinder = null;
		}
	};
	
	private void doAddPc(String host) {
		String msg;
		boolean finish = false;

        SpinnerDialog dialog = SpinnerDialog.displayDialog(this, getResources().getString(R.string.title_add_pc),
			getResources().getString(R.string.msg_add_pc), false);

		try {
			InetAddress addr = InetAddress.getByName(host);
			
			if (!managerBinder.addComputerBlocking(addr)){
				msg = getResources().getString(R.string.addpc_fail);
			}
			else {
				msg = getResources().getString(R.string.addpc_success);
				finish = true;
			}
		} catch (UnknownHostException e) {
			msg = getResources().getString(R.string.addpc_unknown_host);
		}

        dialog.dismiss();

        final boolean toastFinish = finish;
		final String toastMsg = msg;
		AddComputerManually.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(AddComputerManually.this, toastMsg, Toast.LENGTH_LONG).show();
				
				if (toastFinish && !isFinishing()) {
					// Close the activity
					AddComputerManually.this.finish();
				}
			}
		});
	}
	
	private void startAddThread() {
		addThread = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted()) {
					String computer;
					
					try {
						computer = computersToAdd.take();
					} catch (InterruptedException e) {
						return;
					}
					
					doAddPc(computer);
				}
			}
		};
		addThread.setName("UI - AddComputerManually");
		addThread.start();
	}
	
	private void joinAddThread() {
		if (addThread != null) {
			addThread.interrupt();
			
			try {
				addThread.join();
			} catch (InterruptedException ignored) {}
			
			addThread = null;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if (managerBinder != null) {
			joinAddThread();
			unbindService(serviceConnection);
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_add_computer_manually);

        UiHelper.notifyNewRootView(this);

		this.hostText = (TextView) findViewById(R.id.hostTextView);
        hostText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hostText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                                keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (hostText.getText().length() == 0) {
                        Toast.makeText(AddComputerManually.this, getResources().getString(R.string.addpc_enter_ip), Toast.LENGTH_LONG).show();
                        return true;
                    }

                    computersToAdd.add(hostText.getText().toString());
                }

                return false;
            }
        });
		
		// Bind to the ComputerManager service
		bindService(new Intent(AddComputerManually.this,
					ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
	}
}
