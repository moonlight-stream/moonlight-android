package com.limelight.grid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;

public abstract class GenericGridAdapter<T> extends BaseAdapter {
    protected final Context context;
    protected final int layoutId;
    protected final ArrayList<T> itemList = new ArrayList<>();
    protected final LayoutInflater inflater;

    public GenericGridAdapter(Context context, int layoutId) {
        this.context = context;
        this.layoutId = layoutId;

        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void clear() {
        itemList.clear();
    }

    @Override
    public int getCount() {
        return itemList.size();
    }

    @Override
    public Object getItem(int i) {
        return itemList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public abstract boolean populateImageView(ImageView imgView, ProgressBar prgView, T obj);
    public abstract boolean populateTextView(TextView txtView, T obj);
    public abstract boolean populateOverlayView(ImageView overlayView, T obj);

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        if (convertView == null) {
            convertView = inflater.inflate(layoutId, viewGroup, false);
        }

        ImageView imgView = convertView.findViewById(R.id.grid_image);
        ImageView overlayView = convertView.findViewById(R.id.grid_overlay);
        TextView txtView = convertView.findViewById(R.id.grid_text);
        ProgressBar prgView = convertView.findViewById(R.id.grid_spinner);

        if (imgView != null) {
            if (!populateImageView(imgView, prgView, itemList.get(i))) {
                imgView.setImageBitmap(null);
            }
        }
        if (!populateTextView(txtView, itemList.get(i))) {
            txtView.setText(itemList.get(i).toString());
        }
        if (overlayView != null) {
            if (!populateOverlayView(overlayView, itemList.get(i))) {
                overlayView.setVisibility(View.INVISIBLE);
            }
            else {
                overlayView.setVisibility(View.VISIBLE);
            }
        }

        return convertView;
    }
}
