package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.binding.PlatformBinding;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class AppView extends Activity {
    private AppGridAdapter appGridAdapter;
	private InetAddress ipAddress;
	private String uniqueId;
	private boolean remote;
    private boolean firstLoad = true;
	
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
            finish();
			return;
		}
		
		String labelText = getResources().getString(R.string.title_applist)+" "+getIntent().getStringExtra(NAME_EXTRA);
		TextView label = (TextView) findViewById(R.id.appListText);
		setTitle(labelText);
		label.setText(labelText);
		
		try {
			ipAddress = InetAddress.getByAddress(address);
		} catch (UnknownHostException e) {
            e.printStackTrace();
            finish();
            return;
		}
		
		// Setup the list view
        GridView appGrid = (GridView) findViewById(R.id.appGridView);
        try {
            appGridAdapter = new AppGridAdapter(this, ipAddress, uniqueId);
        } catch (Exception e) {
            e.printStackTrace();
            finish();
            return;
        }
        appGrid.setAdapter(appGridAdapter);
        appGrid.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                AppObject app = (AppObject) appGridAdapter.getItem(pos);
                if (app == null || app.app == null) {
                    return;
                }

                // Only open the context menu if something is running, otherwise start it
                if (getRunningAppId() != -1) {
                    openContextMenu(arg1);
                } else {
                    doStart(app.app);
                }
            }
        });
        registerForContextMenu(appGrid);
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

        // Display the error message if it was the
        // first load, but just kill the activity
        // on subsequent errors
        updateAppList(firstLoad);
        firstLoad = false;
	}
	
	private int getRunningAppId() {
        int runningAppId = -1;
        for (int i = 0; i < appGridAdapter.getCount(); i++) {
        	AppObject app = (AppObject) appGridAdapter.getItem(i);
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
        AppObject selectedApp = (AppObject) appGridAdapter.getItem(info.position);
        if (selectedApp == null || selectedApp.app == null) {
        	return;
        }
        
        int runningAppId = getRunningAppId();
        if (runningAppId != -1) {
        	if (runningAppId == selectedApp.app.getAppId()) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
        	}
        	else {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_quit_and_start));
                menu.add(Menu.NONE, CANCEL_ID, 2, getResources().getString(R.string.applist_menu_cancel));
        	}
        }
    }
	
	@Override
	public void onContextMenuClosed(Menu menu) {
	}

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        AppObject app = (AppObject) appGridAdapter.getItem(info.position);
        switch (item.getItemId()) {
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
    
    private void updateAppList(final boolean displayError) {
		final SpinnerDialog spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.applist_refresh_title),
				getResources().getString(R.string.applist_refresh_msg), true);
		new Thread() {
			@Override
			public void run() {
				NvHTTP httpConn = new NvHTTP(ipAddress, uniqueId, null,  PlatformBinding.getCryptoProvider(AppView.this));
				
				try {
					final List<NvApp> appList = httpConn.getAppList();
					
					AppView.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
                            appGridAdapter.clear();
                            for (NvApp app : appList) {
                                appGridAdapter.addApp(new AppObject(app));
                            }

                            appGridAdapter.notifyDataSetChanged();
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

                if (displayError) {
                    Dialog.displayDialog(AppView.this, getResources().getString(R.string.applist_refresh_error_title),
                            getResources().getString(R.string.applist_refresh_error_msg), true);
                }
                else {
                    // Just finish the activity immediately
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                }
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
		Toast.makeText(AppView.this, getResources().getString(R.string.applist_quit_app)+" "+app.getAppName()+"...", Toast.LENGTH_SHORT).show();
		new Thread(new Runnable() {
			@Override
			public void run() {
				NvHTTP httpConn;
				String message;
				try {
					httpConn = new NvHTTP(ipAddress, uniqueId, null,  PlatformBinding.getCryptoProvider(AppView.this));
					if (httpConn.quitApp()) {
						message = getResources().getString(R.string.applist_quit_success)+" "+app.getAppName();
					}
					else {
						message = getResources().getString(R.string.applist_quit_fail)+" "+app.getAppName();
					}
					updateAppList(true);
				} catch (UnknownHostException e) {
					message = getResources().getString(R.string.error_unknown_host);
				} catch (FileNotFoundException e) {
					message = getResources().getString(R.string.error_404);
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
		public NvApp app;
		
		public AppObject(NvApp app) {
			this.app = app;
		}
		
		@Override
		public String toString() {
			return app.getAppName();
		}
	}
}
