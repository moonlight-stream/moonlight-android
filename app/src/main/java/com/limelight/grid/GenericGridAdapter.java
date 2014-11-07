package com.limelight.grid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;

import java.util.ArrayList;

public abstract class GenericGridAdapter<T> extends BaseAdapter {
    protected Context context;
    protected int defaultImageRes;
    protected int layoutId;
    protected ArrayList<T> itemList = new ArrayList<T>();
    protected LayoutInflater inflater;

    public GenericGridAdapter(Context context, int layoutId, int defaultImageRes) {
        this.context = context;
        this.layoutId = layoutId;
        this.defaultImageRes = defaultImageRes;

        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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

    public abstract boolean populateImageView(ImageView imgView, T obj);
    public abstract boolean populateTextView(TextView txtView, T obj);

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        if (convertView == null) {
            convertView = inflater.inflate(layoutId, null);
        }

        ImageView imgView = (ImageView) convertView.findViewById(R.id.grid_image);
        TextView txtView = (TextView) convertView.findViewById(R.id.grid_text);

        if (!populateImageView(imgView, itemList.get(i))) {
            imgView.setImageResource(defaultImageRes);
        }
        if (!populateTextView(txtView, itemList.get(i))) {
            txtView.setText(itemList.get(i).toString());
        }

        return convertView;
    }
}
