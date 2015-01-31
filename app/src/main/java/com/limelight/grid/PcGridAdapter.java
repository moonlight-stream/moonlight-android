package com.limelight.grid;

import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    public PcGridAdapter(Context context, boolean listMode, boolean small) {
        super(context, listMode ? R.layout.simple_row : (small ? R.layout.pc_grid_item_small : R.layout.pc_grid_item), R.drawable.computer);
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.compareTo(rhs.details.name);
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public boolean populateImageView(ImageView imgView, PcView.ComputerObject obj) {
        if (obj.details.reachability != ComputerDetails.Reachability.OFFLINE) {
            imgView.setAlpha(1.0f);
        }
        else {
            imgView.setAlpha(0.4f);
        }

        // Return false to use the default drawable
        return false;
    }

    @Override
    public boolean populateTextView(TextView txtView, PcView.ComputerObject obj) {
        if (obj.details.reachability != ComputerDetails.Reachability.OFFLINE) {
            txtView.setAlpha(1.0f);
        }
        else {
            txtView.setAlpha(0.4f);
        }

        // Return false to use the computer's toString method
        return false;
    }

    @Override
    public boolean populateOverlayView(ImageView overlayView, PcView.ComputerObject obj) {
        if (obj.details.reachability == ComputerDetails.Reachability.UNKNOWN) {
            // Still refreshing this PC so display the overlay
            overlayView.setImageResource(R.drawable.image_loading);
            return true;
        }

        // No overlay
        return false;
    }
}
