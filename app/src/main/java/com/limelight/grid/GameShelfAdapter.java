package com.limelight.grid;

import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.AppView;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class GameShelfAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onGameClicked(AppView.AppObject app, View view);
        void onGameLongClicked(AppView.AppObject app, View view);
        void onGameFocused(AppView.AppObject app, View view);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CARD = 1;

    private final AppGridAdapter artworkSource;
    private final List<Object> items = new ArrayList<>();
    private int continueSectionEnd;
    private Listener listener;

    public GameShelfAdapter(AppGridAdapter artworkSource) {
        this.artworkSource = artworkSource;
        setHasStableIds(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<AppView.AppObject> continuePlaying,
                           List<AppView.AppObject> allGames) {
        items.clear();
        if (!continuePlaying.isEmpty()) {
            items.add(Header.CONTINUE_PLAYING);
            items.addAll(continuePlaying);
            items.add(Header.ALL_GAMES);
            continueSectionEnd = 1 + continuePlaying.size();
        }
        else {
            continueSectionEnd = 0;
        }
        items.addAll(allGames);
        notifyDataSetChanged();
    }

    public AppView.AppObject getAppAt(int position) {
        Object item = items.get(position);
        return item instanceof AppView.AppObject ? (AppView.AppObject) item : null;
    }

    public int findAppPosition(int appId) {
        for (int index = 0; index < items.size(); index++) {
            AppView.AppObject app = getAppAt(index);
            if (app != null && app.app.getAppId() == appId) {
                return index;
            }
        }
        return -1;
    }

    public int firstCardPosition() {
        for (int index = 0; index < items.size(); index++) {
            if (getAppAt(index) != null) {
                return index;
            }
        }
        return -1;
    }

    public boolean isHeader(int position) {
        return getItemViewType(position) == TYPE_HEADER;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof Header ? TYPE_HEADER : TYPE_CARD;
    }

    @Override
    public long getItemId(int position) {
        Object item = items.get(position);
        if (item instanceof Header) {
            return item == Header.CONTINUE_PLAYING ? -1 : -2;
        }
        int appId = ((AppView.AppObject) item).app.getAppId();
        // Continue-playing cards duplicate entries from the all-games section,
        // so they need distinct stable IDs.
        return position < continueSectionEnd ? -3L - appId : appId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(
                    inflater.inflate(R.layout.shelf_header_item, parent, false));
        }
        View view = inflater.inflate(R.layout.app_grid_item, parent, false);
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
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            Header header = (Header) items.get(position);
            ((HeaderViewHolder) holder).title.setText(header == Header.CONTINUE_PLAYING ?
                    R.string.console_continue_playing : R.string.console_all_games);
            return;
        }

        GameViewHolder gameHolder = (GameViewHolder) holder;
        AppView.AppObject app = (AppView.AppObject) items.get(position);
        gameHolder.itemView.setTag(app);
        gameHolder.title.setText(app.app.getAppName());
        gameHolder.focusTitle.setText(app.app.getAppName());
        artworkSource.populateArtwork(app, gameHolder.artwork, gameHolder.title);
        gameHolder.focusTitle.setVisibility(View.GONE);

        gameHolder.running.setVisibility(app.isRunning ? View.VISIBLE : View.GONE);
        if (app.isHidden) {
            gameHolder.badge.setText(R.string.console_hidden);
            gameHolder.badge.setVisibility(View.VISIBLE);
            gameHolder.itemView.setAlpha(0.48f);
        }
        else if (app.isFavorite) {
            gameHolder.badge.setText("★");
            gameHolder.badge.setVisibility(View.VISIBLE);
            gameHolder.itemView.setAlpha(1f);
        }
        else {
            gameHolder.badge.setVisibility(View.GONE);
            gameHolder.itemView.setAlpha(1f);
        }

        String state = app.isRunning ? ". " +
                gameHolder.itemView.getContext().getString(R.string.console_running) : "";
        gameHolder.itemView.setContentDescription(app.app.getAppName() + state);
        gameHolder.itemView.setOnClickListener(view -> {
            if (listener != null) {
                listener.onGameClicked(app, view);
            }
        });
        gameHolder.itemView.setOnLongClickListener(view -> {
            if (listener != null) {
                listener.onGameLongClicked(app, view);
            }
            return true;
        });
        gameHolder.itemView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && listener != null) {
                listener.onGameFocused(app, view);
            }
        });
        gameHolder.itemView.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_BUTTON_Y && listener != null) {
                listener.onGameLongClicked(app, view);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private enum Header {
        CONTINUE_PLAYING,
        ALL_GAMES
    }

    static final class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView;
        }
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
