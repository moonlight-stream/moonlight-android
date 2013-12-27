package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;

public class SpinnerDialog implements Runnable,OnCancelListener {
	private String title, message;
	private Activity activity;
	private ProgressDialog progress;
	private boolean finish;
	
	private static ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<SpinnerDialog>();
	
	public SpinnerDialog(Activity activity, String title, String message, boolean finish)
	{
		this.activity = activity;
		this.title = title;
		this.message = message;
		this.progress = null;
		this.finish = finish;
	}
	
	public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish)
	{
		SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
		activity.runOnUiThread(spinner);
		return spinner;
	}
	
	public static void closeDialogs()
	{
		for (SpinnerDialog d : rundownDialogs)
			d.progress.dismiss();
		
		rundownDialogs.clear();
	}
	
	public void dismiss()
	{
		// Running again with progress != null will destroy it
		activity.runOnUiThread(this);
	}
	
	public void setMessage(final String message)
	{
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progress.setMessage(message);
			}
		});
	}
	
	@Override
	public void run() {
		
		if (progress == null)
		{
			// If we're dying, don't bother creating a dialog
			if (activity.isFinishing())
				return;
			
	    	progress = new ProgressDialog(activity);
	    	
	    	progress.setTitle(title);
	    	progress.setMessage(message);
	    	progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	    	progress.setOnCancelListener(this);
	    	
	    	// If we want to finish the activity when this is killed, make it cancellable
	    	if (finish)
	    	{
	    		progress.setCancelable(true);
	    		progress.setCanceledOnTouchOutside(false);
	    	}
	    	else
	    	{
	    		progress.setCancelable(false);
	    	}
	    	
	    	progress.show();
		}
		else
		{
			progress.dismiss();
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		// This will only be called if finish was true, so we don't need to check again
		activity.finish();
	}
}
