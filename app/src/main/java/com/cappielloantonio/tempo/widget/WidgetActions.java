package com.cappielloantonio.tempo.widget;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.service.MediaService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutionException;

public final class WidgetActions {
  public static void dispatchToMediaSession(Context ctx, String action) {
    Log.d("TempoWidget", "dispatch action=" + action);
    Context appCtx = ctx.getApplicationContext();
    SessionToken token = new SessionToken(appCtx, new ComponentName(appCtx, MediaService.class));
    ListenableFuture<MediaController> future = new MediaController.Builder(appCtx, token).buildAsync();
    future.addListener(() -> {
      try {
        if (!future.isDone()) return;
        MediaController c = future.get();
        Log.d("TempoWidget", "controller connected, isPlaying=" + c.isPlaying());
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
        }
        c.release();
      } catch (ExecutionException | InterruptedException e) {
        Log.e("TempoWidget", "dispatch failed", e);
      }
    }, MoreExecutors.directExecutor());
  }
}
