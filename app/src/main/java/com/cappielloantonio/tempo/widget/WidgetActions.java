package com.cappielloantonio.tempo.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.service.MediaService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutionException;
import java.lang.reflect.Field;

public final class WidgetActions {
  private static final String EXTRA_PROGRESS = resolveExtraConstant("EXTRA_PROGRESS",
      "android.widget.extra.PROGRESS");
  private static final String EXTRA_FROM_USER = resolveExtraConstant("EXTRA_FROM_USER",
      "android.widget.extra.FROM_USER");

  public static void dispatchToMediaSession(Context ctx, Intent intent) {
    if (intent == null) return;
    String action = intent.getAction();
    Log.d("TempoWidget", "dispatch action=" + action);
    Context appCtx = ctx.getApplicationContext();
    SessionToken token = new SessionToken(appCtx, new ComponentName(appCtx, MediaService.class));
    ListenableFuture<MediaController> future = new MediaController.Builder(appCtx, token).buildAsync();
    future.addListener(() -> {
      try {
        if (!future.isDone()) return;
        MediaController c = future.get();
        Log.d("TempoWidget", "controller connected, isPlaying=" + c.isPlaying());
        if (action == null) {
          c.release();
          return;
        }
        switch (action) {
          case WidgetProvider.ACT_PLAY_PAUSE:
            if (c.isPlaying()) c.pause(); else c.play();
            break;
          case WidgetProvider.ACT_NEXT:
            c.seekToNext();
            break;
          case WidgetProvider.ACT_PREV:
            c.seekToPrevious();
            break;
          case WidgetProvider.ACT_TOGGLE_SHUFFLE:
            c.setShuffleModeEnabled(!c.getShuffleModeEnabled());
            break;
          case WidgetProvider.ACT_CYCLE_REPEAT:
            int repeatMode = c.getRepeatMode();
            int nextMode;
            if (repeatMode == Player.REPEAT_MODE_OFF) {
              nextMode = Player.REPEAT_MODE_ALL;
            } else if (repeatMode == Player.REPEAT_MODE_ALL) {
              nextMode = Player.REPEAT_MODE_ONE;
            } else {
              nextMode = Player.REPEAT_MODE_OFF;
            }
            c.setRepeatMode(nextMode);
            break;
          case WidgetProvider.ACT_SEEK_TO:
            if (shouldHandleSeek(appCtx, intent)) {
              int progress = intent.getIntExtra(EXTRA_PROGRESS, -1);
              boolean fromUser = !intent.hasExtra(EXTRA_FROM_USER)
                  || intent.getBooleanExtra(EXTRA_FROM_USER, false);
              if (progress >= 0 && fromUser) {
                int clamped = Math.min(Math.max(progress, 0), WidgetViewsFactory.PROGRESS_MAX);
                long duration = c.getDuration();
                if (duration != C.TIME_UNSET && duration > 0) {
                  long seekPosition = (duration * clamped) / WidgetViewsFactory.PROGRESS_MAX;
                  c.seekTo(seekPosition);
                }
              }
            }
            break;
        }
        WidgetUpdateManager.refreshFromController(ctx);
        c.release();
      } catch (ExecutionException | InterruptedException e) {
        Log.e("TempoWidget", "dispatch failed", e);
      }
    }, MoreExecutors.directExecutor());
  }

  private static boolean shouldHandleSeek(Context ctx, Intent intent) {
    if (WidgetViewsFactory.isInteractiveProgressSupported(ctx)) {
      return true;
    }
    if (intent == null) {
      return false;
    }
    String typeName = intent.getStringExtra(WidgetProvider.EXTRA_PROGRESS_VIEW_TYPE);
    if (typeName == null) {
      return false;
    }
    try {
      WidgetViewsFactory.ProgressViewType type = WidgetViewsFactory.ProgressViewType.valueOf(typeName);
      return type == WidgetViewsFactory.ProgressViewType.SEEK_BAR;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static String resolveExtraConstant(String fieldName, String fallback) {
    try {
      Field field = RemoteViews.class.getField(fieldName);
      Object value = field.get(null);
      if (value instanceof String) {
        return (String) value;
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return fallback;
  }
}
