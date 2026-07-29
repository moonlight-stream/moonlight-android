package com.limelight.grid;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class PcGridAdapter extends RecyclerView.Adapter<PcGridAdapter.HostViewHolder> {
    public interface Listener {
        void onHostClicked(PcView.ComputerObject computer, View view);
        void onHostLongClicked(PcView.ComputerObject computer, View view);
        void onHostFocused(PcView.ComputerObject computer, View view);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<PcView.ComputerObject> itemList = new ArrayList<>();
    private Listener listener;

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        setHasStableIds(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        notifyDataSetChanged();
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject left, PcView.ComputerObject right) {
                return left.details.name.compareToIgnoreCase(right.details.name);
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    public int getCount() {
        return itemList.size();
    }

    public PcView.ComputerObject getItem(int index) {
        return itemList.get(index);
    }

    public int indexOfUuid(String uuid) {
        if (uuid != null) {
            for (int index = 0; index < itemList.size(); index++) {
                if (uuid.equals(itemList.get(index).details.uuid)) {
                    return index;
                }
            }
        }
        return itemList.isEmpty() ? -1 : 0;
    }

    @Override
    public long getItemId(int position) {
        String uuid = itemList.get(position).details.uuid;
        return uuid == null ? position : uuid.hashCode();
    }

    @NonNull
    @Override
    public HostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HostViewHolder(inflater.inflate(R.layout.pc_grid_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HostViewHolder holder, int position) {
        PcView.ComputerObject computer = itemList.get(position);
        ComputerDetails details = computer.details;
        holder.itemView.setTag(computer);
        holder.avatar.setTag(computer);
        holder.name.setText(details.name);
        holder.icon.setAlpha(details.state == ComputerDetails.State.ONLINE ? 1f : 0.45f);

        if (details.state == ComputerDetails.State.ONLINE) {
            if (details.pairState != PairingManager.PairState.PAIRED) {
                holder.status.setText(R.string.console_host_unpaired);
                holder.overlay.setImageResource(R.drawable.ic_lock);
                holder.overlay.setVisibility(View.VISIBLE);
            } else if (details.runningGameId != 0) {
                holder.status.setText(R.string.console_host_running);
                holder.overlay.setImageResource(R.drawable.ic_play);
                holder.overlay.setVisibility(View.VISIBLE);
            } else {
                holder.status.setText(R.string.console_host_online);
                holder.overlay.setVisibility(View.GONE);
            }
            holder.name.setAlpha(1f);
            holder.spinner.setVisibility(View.GONE);
        } else if (details.state == ComputerDetails.State.OFFLINE) {
            holder.status.setText(R.string.console_host_offline);
            holder.name.setAlpha(0.62f);
            holder.overlay.setImageResource(R.drawable.ic_pc_offline);
            holder.overlay.setVisibility(View.VISIBLE);
            holder.spinner.setVisibility(View.GONE);
        } else {
            holder.status.setText(R.string.console_host_refreshing);
            holder.name.setAlpha(0.72f);
            holder.overlay.setVisibility(View.GONE);
            holder.spinner.setVisibility(View.VISIBLE);
        }

        holder.avatar.setContentDescription(details.name + ". " + holder.status.getText());
        holder.avatar.setOnClickListener(view -> {
            if (listener != null) {
                listener.onHostClicked(computer, view);
            }
        });
        holder.avatar.setOnLongClickListener(view -> {
            if (listener != null) {
                listener.onHostLongClicked(computer, view);
            }
            return true;
        });
        holder.avatar.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && listener != null) {
                // Pass the shelf item root so horizontal centering uses RecyclerView coordinates.
                listener.onHostFocused(computer, holder.itemView);
            }
        });
        holder.avatar.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_BUTTON_Y && listener != null) {
                listener.onHostLongClicked(computer, view);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    static final class HostViewHolder extends RecyclerView.ViewHolder {
        final View avatar;
        final ImageView icon;
        final ImageView overlay;
        final ProgressBar spinner;
        final TextView name;
        final TextView status;

        HostViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.host_profile_avatar);
            icon = itemView.findViewById(R.id.grid_image);
            overlay = itemView.findViewById(R.id.grid_overlay);
            spinner = itemView.findViewById(R.id.grid_spinner);
            name = itemView.findViewById(R.id.grid_text);
            status = itemView.findViewById(R.id.grid_status);
        }
    }
}
