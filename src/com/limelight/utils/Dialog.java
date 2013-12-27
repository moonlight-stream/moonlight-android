package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

public class Dialog implements Runnable {
	private String title, message;
	private Activity activity;
	private boolean endAfterDismiss;
	
	AlertDialog alert;
	
	private static ArrayList<Dialog> rundownDialogs = new ArrayList<Dialog>();
	
	public Dialog(Activity activity, String title, String message, boolean endAfterDismiss)
	{
		this.activity = activity;
		this.title = title;
		this.message = message;
		this.endAfterDismiss = endAfterDismiss;
	}
	
	public static void closeDialogs()
	{
		for (Dialog d : rundownDialogs)
			d.alert.dismiss();
		
		rundownDialogs.clear();
	}
	
	public static void displayDialog(Activity activity, String title, String message, boolean endAfterDismiss)
	{
		activity.runOnUiThread(new Dialog(activity, title, message, endAfterDismiss));
	}
	
	@Override
	public void run() {
		// If we're dying, don't bother creating a dialog
		if (activity.isFinishing())
			return;
		
		alert = new AlertDialog.Builder(activity).create();

    	alert.setTitle(title);
    	alert.setMessage(message);
    	alert.setCancelable(false);
    	alert.setCanceledOnTouchOutside(false);
 
    	alert.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
    	      public void onClick(DialogInterface dialog, int which) {
    	    	  alert.dismiss();
    	    	  rundownDialogs.remove(this);
    	    	  
    	    	  if (endAfterDismiss)
    	    		  activity.finish();
    	      }
    	      });
    	
    	rundownDialogs.add(this);
    	alert.show();
	}

}
