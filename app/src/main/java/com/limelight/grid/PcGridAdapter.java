package com.limelight.grid;

import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    public PcGridAdapter(Context context) {
        super(context, R.layout.generic_grid_item, R.drawable.computer);
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public boolean populateImageView(ImageView imgView, PcView.ComputerObject obj) {
        // Return false to use the default drawable
        return false;
    }

    @Override
    public boolean populateTextView(TextView txtView, PcView.ComputerObject obj) {
        // Return false to use the computer's toString method
        return false;
    }
}
