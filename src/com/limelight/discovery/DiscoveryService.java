package com.limelight.discovery;

import java.io.IOException;
import java.util.List;

import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Binder;
import android.os.IBinder;

public class DiscoveryService extends Service {
	
	private MdnsDiscoveryAgent discoveryAgent;
	private MdnsDiscoveryListener boundListener;
	private MulticastLock multicastLock;
	
	public class DiscoveryBinder extends Binder {
		public void setListener(MdnsDiscoveryListener listener) {
			boundListener = listener;
		}
		
		public void startDiscovery(int queryIntervalMs) {
			multicastLock.acquire();
			discoveryAgent.startDiscovery(queryIntervalMs);
		}
		
		public void stopDiscovery() {
			discoveryAgent.stopDiscovery();
			multicastLock.release();
		}
		
		public List<MdnsComputer> getComputerSet() {
			return discoveryAgent.getComputerSet();
		}
	}
	
	@Override
	public void onCreate() {
		WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		multicastLock = wifiMgr.createMulticastLock("Limelight mDNS");
		multicastLock.setReferenceCounted(false);
		
		// This pattern is nasty. We have to do it
		// because we can't do network on the main thread
		// even if it's non-blocking
		Thread initThread = new Thread() {
			@Override
			public void run() {
				try {
					discoveryAgent = MdnsDiscoveryAgent.createDiscoveryAgent(new MdnsDiscoveryListener() {
						@Override
						public void notifyComputerAdded(MdnsComputer computer) {
							if (boundListener != null) {
								boundListener.notifyComputerAdded(computer);
							}
						}

						@Override
						public void notifyComputerRemoved(MdnsComputer computer) {
							if (boundListener != null) {
								boundListener.notifyComputerRemoved(computer);
							}
						}

						@Override
						public void notifyDiscoveryFailure(Exception e) {
							if (boundListener != null) {
								boundListener.notifyDiscoveryFailure(e);
							}
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
					discoveryAgent = null;
				}
			}
		};
		initThread.start();
		try {
			initThread.join();
		} catch (InterruptedException e) {
			discoveryAgent = null;
		}
	}
	
	private DiscoveryBinder binder = new DiscoveryBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		// We should only be bindable if discovery can happen
		if (discoveryAgent != null) {
			return binder;
		}
		
		return null;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		// Stop any discovery session
		discoveryAgent.stopDiscovery();
		multicastLock.release();
		
		// Unbind the listener
		boundListener = null;
		return false;
	}
}
