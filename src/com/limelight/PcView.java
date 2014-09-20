package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.root.R;
import com.limelight.utils.Dialog;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class PcView extends Activity {
	private Button settingsButton, addComputerButton;
	private ListView pcList;
	private ArrayAdapter<ComputerObject> pcListAdapter;
	private ComputerManagerService.ComputerManagerBinder managerBinder;
	private boolean freezeUpdates, runningPolling;
	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder) {
			final ComputerManagerService.ComputerManagerBinder localBinder =
					((ComputerManagerService.ComputerManagerBinder)binder);
			
			// Wait in a separate thread to avoid stalling the UI
			new Thread() {
				@Override
				public void run() {
					// Wait for the binder to be ready
					localBinder.waitForReady();
					
					// Now make the binder visible
					managerBinder = localBinder;
					
					// Start updates
					startComputerUpdates();
				}
			}.start();
		}

		public void onServiceDisconnected(ComponentName className) {
			managerBinder = null;
		}
	};
	
	private final static int APP_LIST_ID = 1;
	private final static int PAIR_ID = 2;
	private final static int UNPAIR_ID = 3;
	private final static int WOL_ID = 4;
	private final static int DELETE_ID = 5;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pc_view);
		
		// Bind to the computer manager service
		bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
				Service.BIND_AUTO_CREATE);
		
		// Setup the list view
		settingsButton = (Button)findViewById(R.id.settingsButton);
		addComputerButton = (Button)findViewById(R.id.manuallyAddPc);

		pcList = (ListView)findViewById(R.id.pcListView);
		pcListAdapter = new ArrayAdapter<ComputerObject>(this, R.layout.simplerow, R.id.rowTextView);
		pcListAdapter.setNotifyOnChange(false);
        pcList.setAdapter(pcListAdapter);
        pcList.setItemsCanFocus(true);
        pcList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
					long id) {
		        ComputerObject computer = (ComputerObject) pcListAdapter.getItem(pos);
		        if (computer.details == null) {
		        	// Placeholder item; no context menu for it
		        	return;
		        }
		        else if (computer.details.reachability == ComputerDetails.Reachability.OFFLINE) {
		        	// Open the context menu if a PC is offline
					openContextMenu(arg1);
		        }
		        else if (computer.details.pairState != PairState.PAIRED) {
		        	// Pair an unpaired machine by default
		            doPair(computer.details);
		        }
		        else {
		            doAppList(computer.details);
		        }
			}
        });
        registerForContextMenu(pcList);
        settingsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(PcView.this, StreamSettings.class));
			}
        });
		addComputerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(PcView.this, AddComputerManually.class);
				startActivity(i);
			}
		});
        
        addListPlaceholder();
	}
	
	private void startComputerUpdates() {
		if (managerBinder != null) {
			if (runningPolling) {
				return;
			}
			
			freezeUpdates = false;
			managerBinder.startPolling(new ComputerManagerListener() {
				@Override
				public void notifyComputerUpdated(final ComputerDetails details) {
					if (!freezeUpdates) {
						PcView.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								updateListView(details);
							}
						});
					}
				}
			});
			runningPolling = true;
		}
	}
	
	private void stopComputerUpdates(boolean wait) {
		if (managerBinder != null) {
			if (!runningPolling) {
				return;
			}
			
			freezeUpdates = true;
			
			managerBinder.stopPolling();
			
			if (wait) {
				managerBinder.waitForPollingStopped();
			}
			
			runningPolling = false;
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (managerBinder != null) {
			unbindService(serviceConnection);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		startComputerUpdates();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		stopComputerUpdates(false);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		Dialog.closeDialogs();
	}
	
	@Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		stopComputerUpdates(false);
		
		// Call superclass
		super.onCreateContextMenu(menu, v, menuInfo);
                
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        ComputerObject computer = (ComputerObject) pcListAdapter.getItem(info.position);
        if (computer == null || computer.details == null) {
        	startComputerUpdates();
        	return;
        }
        
        // Inflate the context menu
        if (computer.details.reachability == ComputerDetails.Reachability.OFFLINE) {
            menu.add(Menu.NONE, WOL_ID, 1, "Send Wake-On-LAN request");
            menu.add(Menu.NONE, DELETE_ID, 2, "Delete PC");
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID, 1, "Pair with PC");
            if (computer.details.reachability == ComputerDetails.Reachability.REMOTE) {
                menu.add(Menu.NONE, DELETE_ID, 2, "Delete PC");
            }
        }
        else {
            menu.add(Menu.NONE, APP_LIST_ID, 1, "View Game List");
            menu.add(Menu.NONE, UNPAIR_ID, 2, "Unpair");
        }
    }
	
	@Override
	public void onContextMenuClosed(Menu menu) {
		startComputerUpdates();
	}
	
	private void doPair(final ComputerDetails computer) {
		if (computer.reachability == ComputerDetails.Reachability.OFFLINE) {
			Toast.makeText(PcView.this, "Computer is offline", Toast.LENGTH_SHORT).show();
			return;
		}
		if (computer.runningGameId != 0) {
			Toast.makeText(PcView.this, "Computer is currently in a game. " +
					"You must close the game before pairing.", Toast.LENGTH_LONG).show();
			return;
		}
		if (managerBinder == null) {
			Toast.makeText(PcView.this, "The ComputerManager service is not running. " +
					"Please wait a few seconds or restart the app.", Toast.LENGTH_LONG).show();
			return;
		}
		
		Toast.makeText(PcView.this, "Pairing...", Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				NvHTTP httpConn;
				String message;
				try {
					// Stop updates and wait while pairing
					stopComputerUpdates(true);
					
					InetAddress addr = null;
					if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
						addr = computer.localIp;
					}
					else if (computer.reachability == ComputerDetails.Reachability.REMOTE) {
						addr = computer.remoteIp;
					}
					
					httpConn = new NvHTTP(addr,
							managerBinder.getUniqueId(),
							PlatformBinding.getDeviceName(), 
							PlatformBinding.getCryptoProvider(PcView.this));
					if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
						message = "Already paired";
					}
					else {
						final String pinStr = PairingManager.generatePinString();
						
						// Spin the dialog off in a thread because it blocks
						Dialog.displayDialog(PcView.this, "Pairing", "Please enter the following PIN on the target PC: "+pinStr, false);
						
						PairingManager.PairState pairState = httpConn.pair(pinStr);
						if (pairState == PairingManager.PairState.PIN_WRONG) {
							message = "Incorrect PIN";
						}
						else if (pairState == PairingManager.PairState.FAILED) {
							message = "Pairing failed";
						}
						else if (pairState == PairingManager.PairState.PAIRED) {
							message = "Paired successfully";
						}
						else {
							// Should be no other values
							message = null;
						}
					}
				} catch (UnknownHostException e) {
					message = "Failed to resolve host";
				} catch (FileNotFoundException e) {
					message = "GFE returned an HTTP 404 error. Make sure your PC is running a supported GPU. Using remote desktop software can also cause this error. "
							+ "Try rebooting your machine or reinstalling GFE.";
				} catch (Exception e) {
					message = e.getMessage();
				}
				
				Dialog.closeDialogs();
				
				final String toastMessage = message;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
					}
				});
				
				// Start polling again
				startComputerUpdates();
			}
		}).start();
	}
	
	private void doWakeOnLan(final ComputerDetails computer) {
		if (computer.reachability != ComputerDetails.Reachability.OFFLINE) {
			Toast.makeText(PcView.this, "Computer is online", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (computer.macAddress == null) {
			Toast.makeText(PcView.this, "Unable to wake PC because GFE didn't send a MAC address", Toast.LENGTH_SHORT).show();
			return;
		}
		
		Toast.makeText(PcView.this, "Waking PC...", Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				String message;
				try {
					WakeOnLanSender.sendWolPacket(computer);
					message = "It may take a few seconds for your PC to wake up. " +
							"If it doesn't, make sure it's configured properly for Wake-On-LAN.";
				} catch (IOException e) {
					message = "Failed to send Wake-On-LAN packets";
				}
				
				final String toastMessage = message;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
					}
				});
			}
		}).start();
	}
	
	private void doUnpair(final ComputerDetails computer) {
		if (computer.reachability == ComputerDetails.Reachability.OFFLINE) {
			Toast.makeText(PcView.this, "Computer is offline", Toast.LENGTH_SHORT).show();
			return;
		}
		if (managerBinder == null) {
			Toast.makeText(PcView.this, "The ComputerManager service is not running. " +
					"Please wait a few seconds or restart the app.", Toast.LENGTH_LONG).show();
			return;
		}
		
		Toast.makeText(PcView.this, "Unpairing...", Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				NvHTTP httpConn;
				String message;
				try {
					InetAddress addr = null;
					if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
						addr = computer.localIp;
					}
					else if (computer.reachability == ComputerDetails.Reachability.REMOTE) {
						addr = computer.remoteIp;
					}
					
					httpConn = new NvHTTP(addr,
							managerBinder.getUniqueId(),
							PlatformBinding.getDeviceName(), 
							PlatformBinding.getCryptoProvider(PcView.this));
					if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
						httpConn.unpair();
						if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
							message = "Unpaired successfully";
						}
						else {
							message = "Failed to unpair";
						}
					}
					else {
						message = "Device was not paired";
					}
				} catch (UnknownHostException e) {
					message = "Failed to resolve host";
				} catch (FileNotFoundException e) {
					message = "GFE returned an HTTP 404 error. Make sure your PC is running a supported GPU. Using remote desktop software can also cause this error. "
							+ "Try rebooting your machine or reinstalling GFE.";
				} catch (Exception e) {
					message = e.getMessage();
				}
								
				final String toastMessage = message;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
					}
				});
			}
		}).start();
	}
	
	private void doAppList(ComputerDetails computer) {
		if (computer.reachability == ComputerDetails.Reachability.OFFLINE) {
			Toast.makeText(PcView.this, "Computer is offline", Toast.LENGTH_SHORT).show();
			return;
		}
		if (managerBinder == null) {
			Toast.makeText(PcView.this, "The ComputerManager service is not running. " +
					"Please wait a few seconds or restart the app.", Toast.LENGTH_LONG).show();
			return;
		}
		
		Intent i = new Intent(this, AppView.class);
		i.putExtra(AppView.NAME_EXTRA, computer.name);
		i.putExtra(AppView.UNIQUEID_EXTRA, managerBinder.getUniqueId());
		
		if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
			i.putExtra(AppView.ADDRESS_EXTRA, computer.localIp.getAddress());
			i.putExtra(AppView.REMOTE_EXTRA, false);
		}
		else {
			i.putExtra(AppView.ADDRESS_EXTRA, computer.remoteIp.getAddress());
			i.putExtra(AppView.REMOTE_EXTRA, true);
		}
		startActivity(i);
	}

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        ComputerObject computer = (ComputerObject) pcListAdapter.getItem(info.position);
        switch (item.getItemId())
        {
        case PAIR_ID:
        	doPair(computer.details);
        	return true;
        	
        case UNPAIR_ID:
        	doUnpair(computer.details);
        	return true;
        	
        case WOL_ID:
        	doWakeOnLan(computer.details);
        	return true;
        	
        case DELETE_ID:
    		if (managerBinder == null) {
    			Toast.makeText(PcView.this, "The ComputerManager service is not running. " +
    					"Please wait a few seconds or restart the app.", Toast.LENGTH_LONG).show();
    			return true;
    		}
        	managerBinder.removeComputer(computer.details.name);
        	removeListView(computer.details);
        	return true;
        	
        case APP_LIST_ID:
        	doAppList(computer.details);
        	return true;
        	
        default:
          return super.onContextItemSelected(item);
        }
    }

    private static String generateString(ComputerDetails details) {
    	StringBuilder str = new StringBuilder();
    	str.append(details.name).append(" - ");
    	if (details.state == ComputerDetails.State.ONLINE) {
    		str.append("Online ");
    		if (details.reachability == ComputerDetails.Reachability.LOCAL) {
    			str.append("(Local) - ");
    		}
    		else {
    			str.append("(Remote) - ");
    		}
    		if (details.pairState == PairState.PAIRED) {
    			if (details.runningGameId == 0) {
    				str.append("Available");
    			}
    			else {
    				str.append("In Game");
    			}
    		}
    		else {
    			str.append("Not Paired");
    		}
    	}
    	else {
    		str.append("Offline");
    	}
    	return str.toString();
    }
    
    private void addListPlaceholder() {
        pcListAdapter.add(new ComputerObject("Discovery is running. No computers found yet. " +
        		"If your PC doesn't show up in about 15 seconds, " +
        		"make sure your computer is running GFE or add your PC manually using the button above.", null));
    }
    
    private void removeListView(ComputerDetails details) {
		for (int i = 0; i < pcListAdapter.getCount(); i++) {
			ComputerObject computer = pcListAdapter.getItem(i);
			
			if (details.equals(computer.details)) {
				pcListAdapter.remove(computer);
				break;
			}
		}
		
		if (pcListAdapter.getCount() == 0) {
			// Add the placeholder if we're down to 0 computers
			addListPlaceholder();
		}
    }
    
	private void updateListView(ComputerDetails details) {
		String computerString = generateString(details);
		ComputerObject existingEntry = null;
		boolean placeholderPresent = false;
		
		for (int i = 0; i < pcListAdapter.getCount(); i++) {
			ComputerObject computer = pcListAdapter.getItem(i);
			
			// If there's a placeholder, there's nothing else
			if (computer.details == null) {
				placeholderPresent = true;
				break;
			}
			
			// Check if this is the same computer
			if (details.equals(computer.details)) {
				existingEntry = computer;
				break;
			}
		}
		
		if (existingEntry != null) {
			// Replace the information in the existing entry
			existingEntry.text = computerString;
			existingEntry.details = details;
		}
		else {
			// If the placeholder is the only object, remove it
			if (placeholderPresent) {
				pcListAdapter.remove(pcListAdapter.getItem(0));
			}
			
			// Add a new entry
			pcListAdapter.add(new ComputerObject(computerString, details));
		}
		
		// Notify the view that the data has changed
		pcListAdapter.notifyDataSetChanged();
	}
	
	public class ComputerObject {
		public String text;
		public ComputerDetails details;
		
		public ComputerObject(String text, ComputerDetails details) {
			this.text = text;
			this.details = details;
		}
		
		@Override
		public String toString() {
			return text;
		}
	}
}
