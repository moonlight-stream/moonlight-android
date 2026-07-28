package com.limelight.grid;

import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.AppView;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class GameShelfAdapter extends RecyclerView.Adapter<GameShelfAdapter.GameViewHolder> {
    public interface Listener {
        void onGameClicked(AppView.AppObject app, View view);
        void onGameLongClicked(AppView.AppObject app, View view);
        void onGameFocused(AppView.AppObject app, View view);
        boolean onNavigateVertical(AppView.AppObject app, View view, int direction);
    }

    private final AppGridAdapter artworkSource;
    private final List<AppView.AppObject> apps = new ArrayList<>();
    private Listener listener;

    public GameShelfAdapter(AppGridAdapter artworkSource) {
        this.artworkSource = artworkSource;
        setHasStableIds(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<AppView.AppObject> newApps) {
        List<Integer> oldIds = new ArrayList<>(apps.size());
        for (AppView.AppObject app : apps) {
            oldIds.add(app.app.getAppId());
        }
        List<Integer> newIds = new ArrayList<>(newApps.size());
        for (AppView.AppObject app : newApps) {
            newIds.add(app.app.getAppId());
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldIds.size();
            }

            @Override
            public int getNewListSize() {
                return newIds.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldIds.get(oldItemPosition).equals(newIds.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return true;
            }
        }, true);
        apps.clear();
        apps.addAll(newApps);
        diff.dispatchUpdatesTo(this);
        if (!apps.isEmpty()) {
            // AppObject state is mutable, so rebind retained IDs after dispatching the
            // structural diff without sacrificing stable-ID focus restoration.
            notifyItemRangeChanged(0, apps.size());
        }
    }

    public AppView.AppObject getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return apps.get(position).app.getAppId();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_grid_item, parent, false);
        if (PreferenceConfiguration.readPreferences(parent.getContext()).smallIconMode) {
            float density = parent.getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = (int) (112 * density);
            params.height = (int) (158 * density);
            view.setLayoutParams(params);
        }
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        AppView.AppObject app = apps.get(position);
        holder.itemView.setTag(app);
        holder.title.setText(app.app.getAppName());
        holder.focusTitle.setText(app.app.getAppName());
        artworkSource.populateArtwork(app, holder.artwork, holder.title);
        holder.focusTitle.setVisibility(View.GONE);

        holder.running.setVisibility(app.isRunning ? View.VISIBLE : View.GONE);
        if (app.isHidden) {
            holder.badge.setText(R.string.console_hidden);
            holder.badge.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(0.48f);
        }
        else if (app.isFavorite) {
            holder.badge.setText("★");
            holder.badge.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(1f);
        }
        else {
            holder.badge.setVisibility(View.GONE);
            holder.itemView.setAlpha(1f);
        }

        String state = app.isRunning ? ". " +
                holder.itemView.getContext().getString(R.string.console_running) : "";
        holder.itemView.setContentDescription(app.app.getAppName() + state);
        holder.itemView.setOnClickListener(view -> {
            if (listener != null) {
                listener.onGameClicked(app, view);
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            if (listener != null) {
                listener.onGameLongClicked(app, view);
            }
            return true;
        });
        holder.itemView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && listener != null) {
                listener.onGameFocused(app, view);
            }
        });
        holder.itemView.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && listener != null &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                            keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                return listener.onNavigateVertical(app, view, keyCode);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static final class GameViewHolder extends RecyclerView.ViewHolder {
        final ImageView artwork;
        final ImageView running;
        final TextView title;
        final TextView focusTitle;
        final TextView badge;

        GameViewHolder(View itemView) {
            super(itemView);
            artwork = itemView.findViewById(R.id.grid_image);
            running = itemView.findViewById(R.id.grid_overlay);
            title = itemView.findViewById(R.id.grid_text);
            focusTitle = itemView.findViewById(R.id.grid_focus_title);
            badge = itemView.findViewById(R.id.grid_badge);
        }
    }
}
