package com.cappielloantonio.tempo.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.cappielloantonio.tempo.R;

public final class WidgetViewsFactory {

  static final int PROGRESS_MAX = 1000;

  public static RemoteViews buildCompact(Context ctx) {
    return build(ctx, R.layout.widget_layout_compact);
  }

  public static RemoteViews buildLarge(Context ctx) {
    return build(ctx, R.layout.widget_layout_large);
  }


  private static RemoteViews build(Context ctx, int layoutRes) {
    RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
    rv.setTextViewText(R.id.title, ctx.getString(R.string.widget_not_playing));
    rv.setTextViewText(R.id.subtitle, ctx.getString(R.string.widget_placeholder_subtitle));
    rv.setTextViewText(R.id.time_elapsed, ctx.getString(R.string.widget_time_elapsed_placeholder));
    rv.setTextViewText(R.id.time_total, ctx.getString(R.string.widget_time_duration_placeholder));
    rv.setProgressBar(R.id.progress, PROGRESS_MAX, 0, false);
    rv.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play);
    // Show Tempo logo when nothing is playing
    rv.setImageViewResource(R.id.album_art, R.drawable.ic_splash_logo);
    return rv;
  }

  public static RemoteViews populate(Context ctx,
                                     String title,
                                     String subtitle,
                                     Bitmap art,
                                     boolean playing,
                                     String elapsedText,
                                     String totalText,
                                     int progress) {
    return populateWithLayout(ctx, title, subtitle, art, playing, elapsedText, totalText, progress, R.layout.widget_layout_compact);
  }

  public static RemoteViews populateLarge(Context ctx,
                                     String title,
                                     String subtitle,
                                     Bitmap art,
                                     boolean playing,
                                     String elapsedText,
                                     String totalText,
                                     int progress) {
    return populateWithLayout(ctx, title, subtitle, art, playing, elapsedText, totalText, progress, R.layout.widget_layout_large);
  }


  private static RemoteViews populateWithLayout(Context ctx,
                                     String title,
                                     String subtitle,
                                     Bitmap art,
                                     boolean playing,
                                     String elapsedText,
                                     String totalText,
                                     int progress,
                                     int layoutRes) {
    RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
    rv.setTextViewText(R.id.title, title);
    rv.setTextViewText(R.id.subtitle, subtitle);

    if (art != null) {
      rv.setImageViewBitmap(R.id.album_art, art);
    } else {
      // Fallback to app logo when art is missing
      rv.setImageViewResource(R.id.album_art, R.drawable.ic_splash_logo);
    }

    rv.setImageViewResource(R.id.btn_play_pause,
        playing ? R.drawable.ic_pause : R.drawable.ic_play);

    String elapsed = !TextUtils.isEmpty(elapsedText)
        ? elapsedText
        : ctx.getString(R.string.widget_time_elapsed_placeholder);
    String total = !TextUtils.isEmpty(totalText)
        ? totalText
        : ctx.getString(R.string.widget_time_duration_placeholder);

    int safeProgress = progress;
    if (safeProgress < 0) safeProgress = 0;
    if (safeProgress > PROGRESS_MAX) safeProgress = PROGRESS_MAX;

    rv.setTextViewText(R.id.time_elapsed, elapsed);
    rv.setTextViewText(R.id.time_total, total);
    rv.setProgressBar(R.id.progress, PROGRESS_MAX, safeProgress, false);

    return rv;
  }
}
