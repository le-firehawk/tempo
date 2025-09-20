package com.cappielloantonio.tempo.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;

import com.cappielloantonio.tempo.R;

public final class WidgetViewsFactory {

  static final int PROGRESS_MAX = 1000;

  enum ProgressViewType {
    PROGRESS_BAR,
    SEEK_BAR
  }

  static ProgressViewType getProgressViewType() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ? ProgressViewType.SEEK_BAR
        : ProgressViewType.PROGRESS_BAR;
  }

  static boolean isInteractiveProgressSupported() {
    return getProgressViewType() == ProgressViewType.SEEK_BAR;
  }

  private WidgetViewsFactory() {}

  public static RemoteViews buildCompact(Context ctx) {
    return build(ctx, R.layout.widget_layout_compact, false, false);
  }

  public static RemoteViews buildMedium(Context ctx) {
    return build(ctx, R.layout.widget_layout_medium, false, false);
  }

  public static RemoteViews buildLarge(Context ctx) {
    return build(ctx, R.layout.widget_layout_large_short, true, true);
  }

  public static RemoteViews buildExpanded(Context ctx) {
    return build(ctx, R.layout.widget_layout_large, true, true);
  }

  private static RemoteViews build(Context ctx,
                                   int layoutRes,
                                   boolean showAlbum,
                                   boolean showSecondaryControls) {
    RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
    rv.setTextViewText(R.id.title, ctx.getString(R.string.widget_not_playing));
    rv.setTextViewText(R.id.subtitle, ctx.getString(R.string.widget_placeholder_subtitle));
    rv.setTextViewText(R.id.album, "");
    rv.setViewVisibility(R.id.album, showAlbum ? View.INVISIBLE : View.GONE);
    rv.setTextViewText(R.id.time_elapsed, ctx.getString(R.string.widget_time_elapsed_placeholder));
    rv.setTextViewText(R.id.time_total, ctx.getString(R.string.widget_time_duration_placeholder));
    rv.setProgressBar(R.id.progress, PROGRESS_MAX, 0, false);
    rv.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play);
    rv.setImageViewResource(R.id.album_art, R.drawable.ic_splash_logo);
    applySecondaryControlsDefaults(ctx, rv, showSecondaryControls);
    return rv;
  }

  private static void applySecondaryControlsDefaults(Context ctx,
                                                     RemoteViews rv,
                                                     boolean show) {
    int visibility = show ? View.VISIBLE : View.GONE;
    rv.setViewVisibility(R.id.controls_secondary, visibility);
    rv.setViewVisibility(R.id.btn_shuffle, visibility);
    rv.setViewVisibility(R.id.btn_repeat, visibility);
    if (show) {
      int defaultColor = ContextCompat.getColor(ctx, R.color.widget_icon_tint);
      rv.setImageViewResource(R.id.btn_shuffle, R.drawable.ic_shuffle);
      rv.setImageViewResource(R.id.btn_repeat, R.drawable.ic_repeat);
      rv.setInt(R.id.btn_shuffle, "setColorFilter", defaultColor);
      rv.setInt(R.id.btn_repeat, "setColorFilter", defaultColor);
    }
  }

  public static RemoteViews populateCompact(Context ctx,
                                            String title,
                                            String subtitle,
                                            String album,
                                            Bitmap art,
                                            boolean playing,
                                            String elapsedText,
                                            String totalText,
                                            int progress,
                                            boolean shuffleEnabled,
                                            int repeatMode) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_compact, false, false, shuffleEnabled, repeatMode);
  }

  public static RemoteViews populateMedium(Context ctx,
                                           String title,
                                           String subtitle,
                                           String album,
                                           Bitmap art,
                                           boolean playing,
                                           String elapsedText,
                                           String totalText,
                                           int progress,
                                           boolean shuffleEnabled,
                                           int repeatMode) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_medium, true, true, shuffleEnabled, repeatMode);
  }

  public static RemoteViews populateLarge(Context ctx,
                                          String title,
                                          String subtitle,
                                          String album,
                                          Bitmap art,
                                          boolean playing,
                                          String elapsedText,
                                          String totalText,
                                          int progress,
                                          boolean shuffleEnabled,
                                          int repeatMode) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_large_short, true, true, shuffleEnabled, repeatMode);
  }

  public static RemoteViews populateExpanded(Context ctx,
                                            String title,
                                            String subtitle,
                                            String album,
                                            Bitmap art,
                                            boolean playing,
                                            String elapsedText,
                                            String totalText,
                                            int progress,
                                            boolean shuffleEnabled,
                                            int repeatMode) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_large, true, true, shuffleEnabled, repeatMode);
  }

  private static RemoteViews populateWithLayout(Context ctx,
                                     String title,
                                            String subtitle,
                                            String album,
                                            Bitmap art,
                                            boolean playing,
                                            String elapsedText,
                                            String totalText,
                                            int progress,
                                            int layoutRes,
                                            boolean showAlbum,
                                            boolean showSecondaryControls,
                                            boolean shuffleEnabled,
                                            int repeatMode) {
    RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
    rv.setTextViewText(R.id.title, title);
    rv.setTextViewText(R.id.subtitle, subtitle);

    if (showAlbum && !TextUtils.isEmpty(album)) {
      rv.setTextViewText(R.id.album, album);
      rv.setViewVisibility(R.id.album, View.VISIBLE);
    } else {
      rv.setTextViewText(R.id.album, "");
      rv.setViewVisibility(R.id.album, View.GONE);
    }

    if (art != null) {
      rv.setImageViewBitmap(R.id.album_art, art);
    } else {
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
    if (isInteractiveProgressSupported()) {
      rv.setBoolean(R.id.progress, "setEnabled", !TextUtils.isEmpty(totalText));
    }

    applySecondaryControls(ctx, rv, showSecondaryControls, shuffleEnabled, repeatMode);

    return rv;
  }

  private static void applySecondaryControls(Context ctx,
                                            RemoteViews rv,
                                            boolean show,
                                            boolean shuffleEnabled,
                                            int repeatMode) {
    if (!show) {
      rv.setViewVisibility(R.id.controls_secondary, View.GONE);
      rv.setViewVisibility(R.id.btn_shuffle, View.GONE);
      rv.setViewVisibility(R.id.btn_repeat, View.GONE);
      return;
    }

    int inactiveColor = ContextCompat.getColor(ctx, R.color.widget_icon_tint);
    int activeColor = ContextCompat.getColor(ctx, R.color.widget_icon_tint_active);

    rv.setViewVisibility(R.id.controls_secondary, View.VISIBLE);
    rv.setViewVisibility(R.id.btn_shuffle, View.VISIBLE);
    rv.setViewVisibility(R.id.btn_repeat, View.VISIBLE);
    rv.setImageViewResource(R.id.btn_shuffle, R.drawable.ic_shuffle);
    rv.setImageViewResource(R.id.btn_repeat,
        repeatMode == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
    rv.setInt(R.id.btn_shuffle, "setColorFilter", shuffleEnabled ? activeColor : inactiveColor);
    rv.setInt(R.id.btn_repeat, "setColorFilter",
        repeatMode == Player.REPEAT_MODE_OFF ? inactiveColor : activeColor);
  }
}
