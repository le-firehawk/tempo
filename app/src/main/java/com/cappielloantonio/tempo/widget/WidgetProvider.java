package com.cappielloantonio.tempo.widget;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.activity.MainActivity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TempoWidget";
    public static final String ACT_PLAY_PAUSE = "tempo.widget.PLAY_PAUSE";
    public static final String ACT_NEXT = "tempo.widget.NEXT";
    public static final String ACT_PREV = "tempo.widget.PREV";
    public static final String ACT_TOGGLE_SHUFFLE = "tempo.widget.SHUFFLE";
    public static final String ACT_CYCLE_REPEAT = "tempo.widget.REPEAT";
    public static final String ACT_SEEK_TO = "tempo.widget.SEEK_TO";
    public static final String EXTRA_PROGRESS_VIEW_TYPE = "tempo.widget.EXTRA_PROGRESS_VIEW_TYPE";
    // Resolve the seek bar response setter reflectively so builds continue to compile even when
    // the method is absent from older SDK stubs.
    private static final Method SET_ON_SEEK_BAR_CHANGE_METHOD = resolveSetOnSeekBarChangeMethod();

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews rv = WidgetUpdateManager.chooseBuild(ctx, id);
            attachIntents(ctx, rv, id);
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        Log.d(TAG, "onReceive action=" + a);
        if (ACT_PLAY_PAUSE.equals(a) || ACT_NEXT.equals(a) || ACT_PREV.equals(a)
                || ACT_TOGGLE_SHUFFLE.equals(a) || ACT_CYCLE_REPEAT.equals(a)
                || ACT_SEEK_TO.equals(a)) {
            WidgetActions.dispatchToMediaSession(ctx, intent);
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)) {
            WidgetUpdateManager.refreshFromController(ctx);
        }
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        RemoteViews rv = WidgetUpdateManager.chooseBuild(context, appWidgetId);
        attachIntents(context, rv, appWidgetId);
        appWidgetManager.updateAppWidget(appWidgetId, rv);
        WidgetUpdateManager.refreshFromController(context);
    }

    public static void attachIntents(Context ctx, RemoteViews rv) {
        attachIntents(ctx, rv, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    public static void attachIntents(Context ctx, RemoteViews rv, int appWidgetId) {
        int requestCodeBase = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                ? appWidgetId
                : 0;
        PendingIntent playPause = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 0,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_PLAY_PAUSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent next = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 1,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_NEXT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent prev = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 2,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_PREV),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent shuffle = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 3,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_TOGGLE_SHUFFLE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent repeat = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 4,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_CYCLE_REPEAT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        rv.setOnClickPendingIntent(R.id.btn_play_pause, playPause);
        rv.setOnClickPendingIntent(R.id.btn_next, next);
        rv.setOnClickPendingIntent(R.id.btn_prev, prev);
        rv.setOnClickPendingIntent(R.id.btn_shuffle, shuffle);
        rv.setOnClickPendingIntent(R.id.btn_repeat, repeat);

        WidgetViewsFactory.ProgressViewType progressViewType =
                WidgetViewsFactory.getProgressViewType(ctx);
        if (progressViewType == WidgetViewsFactory.ProgressViewType.SEEK_BAR) {
            Intent seekIntent = new Intent(ctx, WidgetProvider4x1.class)
                    .setAction(ACT_SEEK_TO);
            seekIntent.putExtra(EXTRA_PROGRESS_VIEW_TYPE, progressViewType.name());
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                seekIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            }
            int seekFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            PendingIntent seek = PendingIntent.getBroadcast(
                    ctx,
                    requestCodeBase + 5,
                    seekIntent,
                    seekFlags
            );
            if (!setOnSeekBarChangeResponse(rv, R.id.progress, seek)) {
                rv.setOnClickPendingIntent(R.id.progress, seek);
            }
        }

        PendingIntent launch = TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(new Intent(ctx, MainActivity.class))
                .getPendingIntent(requestCodeBase + 10, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.root, launch);
    }

    private static boolean setOnSeekBarChangeResponse(RemoteViews rv,
                                                      int viewId,
                                                      PendingIntent pendingIntent) {
        Method method = SET_ON_SEEK_BAR_CHANGE_METHOD;
        if (method == null) {
            return false;
        }
        try {
            method.invoke(rv, viewId, RemoteViews.RemoteResponse.fromPendingIntent(pendingIntent));
            return true;
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.w(TAG, "Unable to set seek bar change response", e);
            return false;
        }
    }

    private static Method resolveSetOnSeekBarChangeMethod() {
        try {
            return RemoteViews.class.getMethod(
                    "setOnSeekBarChangeResponse",
                    int.class,
                    RemoteViews.RemoteResponse.class
            );
        } catch (NoSuchMethodException ignored) {
            try {
                return RemoteViews.class.getMethod(
                        "setOnSeekBarChangeListener",
                        int.class,
                        RemoteViews.RemoteResponse.class
                );
            } catch (NoSuchMethodException innerIgnored) {
                return null;
            }
        }
    }
}
