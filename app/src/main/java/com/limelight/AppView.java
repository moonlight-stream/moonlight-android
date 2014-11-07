package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.R;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class AppView extends Activity {
	private ListView appList;
	private ArrayAdapter<AppObject> appListAdapter;
	private InetAddress ipAddress;
	private String uniqueId;
	private boolean remote;
	
	private final static int RESUME_ID = 1;
	private final static int QUIT_ID = 2;
	private final static int CANCEL_ID = 3;
	
	public final static String ADDRESS_EXTRA = "Address";
	public final static String UNIQUEID_EXTRA = "UniqueId";
	public final static String NAME_EXTRA = "Name";
	public final static String REMOTE_EXTRA = "Remote";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_app_view);

        UiHelper.notifyNewRootView(this);
		
		byte[] address = getIntent().getByteArrayExtra(ADDRESS_EXTRA);
		uniqueId = getIntent().getStringExtra(UNIQUEID_EXTRA);
		remote = getIntent().getBooleanExtra(REMOTE_EXTRA, false);
		if (address == null || uniqueId == null) {
			return;
		}
		
		String labelText = "App List for "+getIntent().getStringExtra(NAME_EXTRA);
		TextView label = (TextView) findViewById(R.id.appListText);
		setTitle(labelText);
		label.setText(labelText);
		
		try {
			ipAddress = InetAddress.getByAddress(address);
		} catch (UnknownHostException e) {
			return;
		}
		
		// Setup the list view
		appList = (ListView)findViewById(R.id.pcListView);
		appListAdapter = new ArrayAdapter<AppObject>(this, R.layout.simplerow, R.id.rowTextView);
		appListAdapter.setNotifyOnChange(false);
		appList.setAdapter(appListAdapter);
		appList.setItemsCanFocus(true);
		appList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
					long id) {
				AppObject app = appListAdapter.getItem(pos);
				if (app == null || app.app == null) {
					return;
				}
				
				// Only open the context menu if something is running, otherwise start it
				if (getRunningAppId() != -1) {
					openContextMenu(arg1);
				}
				else {
					doStart(app.app);
				}
			}
		});
        registerForContextMenu(appList);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		SpinnerDialog.closeDialogs(this);
		Dialog.closeDialogs();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
        updateAppList();
	}
	
	private int getRunningAppId() {
        int runningAppId = -1;
        for (int i = 0; i < appListAdapter.getCount(); i++) {
        	AppObject app = appListAdapter.getItem(i);
        	if (app.app == null) {
        		continue;
        	}
        	
        	if (app.app.getIsRunning()) {
        		runningAppId = app.app.getAppId();
        		break;
        	}
        }
        return runningAppId;
	}
	
	@Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
        
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        AppObject selectedApp = appListAdapter.getItem(info.position);
        if (selectedApp == null || selectedApp.app == null) {
        	return;
        }
        
        int runningAppId = getRunningAppId();
        if (runningAppId != -1) {
        	if (runningAppId == selectedApp.app.getAppId()) {
                menu.add(Menu.NONE, RESUME_ID, 1, "Resume Session");
                menu.add(Menu.NONE, QUIT_ID, 2, "Quit Session");
        	}
        	else {
                menu.add(Menu.NONE, RESUME_ID, 1, "Quit Current Game and Start");
                menu.add(Menu.NONE, CANCEL_ID, 2, "Cancel");
        	}
        }
    }
	
	@Override
	public void onContextMenuClosed(Menu menu) {
	}

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        AppObject app = appListAdapter.getItem(info.position);
        switch (item.getItemId())
        {
        case RESUME_ID:
        	// Resume is the same as start for us
        	doStart(app.app);
        	return true;
        	
        case QUIT_ID:
        	doQuit(app.app);
        	return true;
        	
        case CANCEL_ID:
        	return true;
        	
        default:
          return super.onContextItemSelected(item);
        }
    }

    private static String generateString(NvApp app) {
    	StringBuilder str = new StringBuilder();
    	str.append(app.getAppName());
    	if (app.getIsRunning()) {
    		str.append(" - Running");
    	}
    	return str.toString();
    }
    
    private void addListPlaceholder() {
        appListAdapter.add(new AppObject("No apps found. Try rescanning for games in GeForce Experience.", null));
    }
    
    private void updateAppList() {
		final SpinnerDialog spinner = SpinnerDialog.displayDialog(this, "App List", "Refreshing app list...", true);
		new Thread() {
			@Override
			public void run() {
				NvHTTP httpConn = new NvHTTP(ipAddress, uniqueId, null,  PlatformBinding.getCryptoProvider(AppView.this));
				
				try {
					final List<NvApp> appList = httpConn.getAppList();
					
					AppView.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							appListAdapter.clear();
							if (appList.isEmpty()) {
								addListPlaceholder();
							}
							else {
								for (NvApp app : appList) {
									appListAdapter.add(new AppObject(generateString(app), app));
								}
							}
							
							appListAdapter.notifyDataSetChanged();
						}
					});
					
					// Success case
					return;
				} catch (GfeHttpResponseException ignored) {
				} catch (IOException ignored) {
				} catch (XmlPullParserException ignored) {
				} finally {
					spinner.dismiss();
				}
				
				Dialog.displayDialog(AppView.this, "Error", "Failed to get app list", true);
			}
		}.start();
    }
	
	private void doStart(NvApp app) {
		Intent intent = new Intent(this, Game.class);
		intent.putExtra(Game.EXTRA_HOST, ipAddress.getHostAddress());
		intent.putExtra(Game.EXTRA_APP, app.getAppName());
		intent.putExtra(Game.EXTRA_UNIQUEID, uniqueId);
		intent.putExtra(Game.EXTRA_STREAMING_REMOTE, remote);
		startActivity(intent);
	}
	
	private void doQuit(final NvApp app) {
		Toast.makeText(AppView.this, "Quitting "+app.getAppName()+"...", Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				NvHTTP httpConn;
				String message;
				try {
					httpConn = new NvHTTP(ipAddress, uniqueId, null,  PlatformBinding.getCryptoProvider(AppView.this));
					if (httpConn.quitApp()) {
						message = "Successfully quit "+app.getAppName();
					}
					else {
						message = "Failed to quit "+app.getAppName();
					}
					updateAppList();
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
						Toast.makeText(AppView.this, toastMessage, Toast.LENGTH_LONG).show();
					}
				});
			}
		}).start();
	}
	
	public class AppObject {
		public String text;
		public NvApp app;
		
		public AppObject(String text, NvApp app) {
			this.text = text;
			this.app = app;
		}
		
		@Override
		public String toString() {
			return text;
		}
	}
}