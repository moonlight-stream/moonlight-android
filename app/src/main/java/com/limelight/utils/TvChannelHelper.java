package com.limelight.utils;

import android.annotation.TargetApi;
import android.app.UiModeManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.PosterContentProvider;
import com.limelight.R;
import com.limelight.ShortcutTrampoline;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.io.IOException;
import java.io.OutputStream;

@TargetApi(Build.VERSION_CODES.O)
public class TvChannelHelper {

    private static final int ASPECT_RATIO_MOVIE_POSTER = 5;
    private static final int TYPE_GAME = 12;
    public static final String[] CHANNEL_PROJECTION = {TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID};
    public static final int INTERNAL_PROVIDER_ID_INDEX = 1;
    public static final int ID_INDEX = 0;
    private Context context;

    public TvChannelHelper(Context context) {
        this.context = context;
    }

    public boolean createTvChannel(String computerUuid, String computerName) {
        if (!isSupport()) {
            return false;
        }

        Intent i = new Intent(context, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computerName);
        i.putExtra(AppView.UUID_EXTRA, computerUuid);
        i.setAction(Intent.ACTION_DEFAULT);
        ChannelBuilder builder = new ChannelBuilder()
                .setType(TvContract.Channels.TYPE_PREVIEW)
                .setDisplayName(computerName)
                .setInternalProviderId(computerUuid)
                .setAppLinkIntent(i);

        Long channelId = getChannelId(computerUuid);


        if (channelId != null) {
            context.getContentResolver().update(TvContract.buildChannelUri(channelId),
                    builder.toContentValues(), null, null);
            return false;
        }


        Uri channelUri = context.getContentResolver().insert(
                TvContract.Channels.CONTENT_URI, builder.toContentValues());


        if (channelUri != null) {
            long id = ContentUris.parseId(channelUri);
            updateChannelIcon(id);
            return true;
        }
        return false;
    }

    private void updateChannelIcon(long id) {
        Bitmap bitmap = drawableToBitmap(context.getResources().getDrawable(R.drawable.ic_channel));
        try {
            storeChannelLogo(id, bitmap);
        } finally {
            bitmap.recycle();
        }
    }


    /**
     * Stores the given channel logo {@link Bitmap} in the system content provider and associate
     * it with the given channel ID.
     *
     * @param channelId the ID of the target channel with which the given logo should be associated
     * @param logo      the logo image to be stored
     * @return {@code true} if successfully stored the logo in the system content provider,
     * otherwise {@code false}.
     */
    private boolean storeChannelLogo(long channelId,
                                     Bitmap logo) {
        if (!isSupport()) {
            return false;
        }
        boolean result = false;
        Uri localUri = TvContract.buildChannelLogoUri(channelId);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(localUri)) {
            result = logo.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
        } catch (SQLiteException | IOException e) {
            LimeLog.warning("Failed to store the logo to the system content provider.");
            e.printStackTrace();
        }
        return result;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        int width = context.getResources().getDimensionPixelSize(R.dimen.tv_channel_logo_width);
        int height = context.getResources().getDimensionPixelSize(R.dimen.tv_channel_logo_width);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }


    public boolean addGameToChannel(String computerUuid, String computerName, String appId, String appName) {
        if (!isSupport()) {
            return false;
        }

        PreviewProgramBuilder builder = new PreviewProgramBuilder();
        Intent i = new Intent(context, ShortcutTrampoline.class);

        i.putExtra(AppView.NAME_EXTRA, computerName);
        i.putExtra(AppView.UUID_EXTRA, computerUuid);
        i.putExtra(ShortcutTrampoline.APP_ID_EXTRA, appId);
        i.setAction(Intent.ACTION_DEFAULT);

        Uri resourceURI = PosterContentProvider.createBoxArtUri(computerUuid, appId);

        Long channelId = getChannelId(computerUuid);


        if (channelId == null) {
            return false;
        }
        builder.setChannelId(channelId)
                .setType(TYPE_GAME)
                .setTitle(appName)
                .setPosterArtAspectRatio(ASPECT_RATIO_MOVIE_POSTER)
                .setPosterArtUri(resourceURI)
                .setIntent(i)
                .setInternalProviderId(appId);
        Uri programUri = context.getContentResolver().insert(TvContract.PreviewPrograms.CONTENT_URI,
                builder.toContentValues());


        TvContract.requestChannelBrowsable(context, channelId);
        return programUri != null;
    }

    public boolean deleteChannel(String computerUuid) {
        if (!isSupport()) {
            return false;
        }
        Long channelId = getChannelId(computerUuid);
        if (channelId == null) {
            return false;
        }
        Uri uri = TvContract.buildChannelUri(channelId);
        return context.getContentResolver().delete(uri, null, null) > 0;

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Long getChannelId(String computerUuid) {
        Uri uri = TvContract.Channels.CONTENT_URI;
        Cursor cursor = context.getContentResolver().query(uri,
                CHANNEL_PROJECTION,
                null,
                null,
                null);
        try {
            if (cursor == null) {
                return null;
            }
            while (cursor.moveToNext()) {
                String internalProviderId = cursor.getString(INTERNAL_PROVIDER_ID_INDEX);
                if (computerUuid.equals(internalProviderId)) {
                    return cursor.getLong(ID_INDEX);
                }
            }

            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public void addGameToChannel(ComputerDetails computer, NvApp app) {
        addGameToChannel(computer.uuid, computer.name, String.valueOf(app.getAppId()), app.getAppName());
    }

    private static <T> String toValueString(T value) {
        return value == null ? null : value.toString();
    }

    private static String toUriString(Intent intent) {
        return intent == null ? null : intent.toUri(Intent.URI_INTENT_SCHEME);
    }

    public boolean isSupport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }

        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static class PreviewProgramBuilder {

        private ContentValues mValues = new ContentValues();


        public PreviewProgramBuilder setChannelId(Long channelId) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId);
            return this;
        }

        public PreviewProgramBuilder setType(int type) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_TYPE, type);
            return this;
        }

        public PreviewProgramBuilder setTitle(String title) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_TITLE, title);
            return this;
        }

        public PreviewProgramBuilder setPosterArtAspectRatio(int aspectRatio) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, aspectRatio);
            return this;
        }

        public PreviewProgramBuilder setIntent(Intent intent) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, toUriString(intent));
            return this;
        }

        public PreviewProgramBuilder setIntentUri(Uri uri) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, toValueString(uri));
            return this;
        }

        public PreviewProgramBuilder setInternalProviderId(String id) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, id);
            return this;
        }

        public PreviewProgramBuilder setPosterArtUri(Uri uri) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, toValueString(uri));
            return this;
        }

        public ContentValues toContentValues() {
            return new ContentValues(mValues);
        }

    }

    private static class ChannelBuilder {

        private ContentValues mValues = new ContentValues();

        public ChannelBuilder setType(String type) {
            mValues.put(TvContract.Channels.COLUMN_TYPE, type);
            return this;
        }

        public ChannelBuilder setDisplayName(String displayName) {
            mValues.put(TvContract.Channels.COLUMN_DISPLAY_NAME, displayName);
            return this;
        }

        public ChannelBuilder setInternalProviderId(String internalProviderId) {
            mValues.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId);
            return this;
        }

        public ChannelBuilder setAppLinkIntent(Intent intent) {
            mValues.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, toUriString(intent));
            return this;
        }

        public ContentValues toContentValues() {
            return new ContentValues(mValues);
        }

    }
}
