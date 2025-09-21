package com.cappielloantonio.tempo.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;

import com.cappielloantonio.tempo.R;

public final class WidgetViewsFactory {

  static final int PROGRESS_MAX = 1000;
  private static final int[] RATING_TARGET_IDS = {
      R.id.rating_click_area_1,
      R.id.rating_click_area_2,
      R.id.rating_click_area_3,
      R.id.rating_click_area_4,
      R.id.rating_click_area_5
  };
  private static final int[] RATING_STAR_IDS = {
      R.id.rating_star_1,
      R.id.rating_star_2,
      R.id.rating_star_3,
      R.id.rating_star_4,
      R.id.rating_star_5
  };
  private static final float ALBUM_ART_CORNER_RADIUS_DP = 6f;

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
    applyFavoriteAndRatingDefaults(ctx, rv, layoutRes);
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
                                            int repeatMode,
                                            String mediaId,
                                            String albumId,
                                            String artistId,
                                            boolean isFavorite,
                                            int userRating) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_compact, false, false, shuffleEnabled, repeatMode,
        mediaId, albumId, artistId, isFavorite, userRating);
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
                                           int repeatMode,
                                           String mediaId,
                                           String albumId,
                                           String artistId,
                                           boolean isFavorite,
                                           int userRating) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_medium, true, true, shuffleEnabled, repeatMode,
        mediaId, albumId, artistId, isFavorite, userRating);
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
                                          int repeatMode,
                                          String mediaId,
                                          String albumId,
                                          String artistId,
                                          boolean isFavorite,
                                          int userRating) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_large_short, true, true, shuffleEnabled, repeatMode,
        mediaId, albumId, artistId, isFavorite, userRating);
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
                                            int repeatMode,
                                            String mediaId,
                                            String albumId,
                                            String artistId,
                                            boolean isFavorite,
                                            int userRating) {
    return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
        progress, R.layout.widget_layout_large, true, true, shuffleEnabled, repeatMode,
        mediaId, albumId, artistId, isFavorite, userRating);
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
                                            int repeatMode,
                                            String mediaId,
                                            String albumId,
                                            String artistId,
                                            boolean isFavorite,
                                            int userRating) {
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
      Bitmap rounded = maybeRoundBitmap(ctx, art);
      rv.setImageViewBitmap(R.id.album_art, rounded != null ? rounded : art);
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

    applySecondaryControls(ctx, rv, showSecondaryControls, shuffleEnabled, repeatMode);
    applyFavoriteAndRating(ctx, rv, layoutRes, !TextUtils.isEmpty(mediaId), isFavorite, userRating);

    return rv;
  }

  private static boolean isLargeLayout(int layoutRes) {
    return layoutRes == R.layout.widget_layout_large_short || layoutRes == R.layout.widget_layout_large;
  }

  private static void applyFavoriteAndRatingDefaults(Context ctx, RemoteViews rv, int layoutRes) {
    if (!isLargeLayout(layoutRes)) {
      return;
    }
    rv.setViewVisibility(R.id.metadata_actions, View.GONE);
    rv.setViewVisibility(R.id.rating_container, View.GONE);
    rv.setViewVisibility(R.id.rating_click_targets, View.GONE);
    rv.setBoolean(R.id.button_favorite, "setChecked", false);
    rv.setViewVisibility(R.id.button_favorite, View.GONE);
    rv.setTextViewText(R.id.rating_text, ctx.getString(R.string.widget_rating_unset));
    int inactiveColor = ContextCompat.getColor(ctx, R.color.widget_icon_tint);
    for (int id : RATING_STAR_IDS) {
      rv.setViewVisibility(id, View.VISIBLE);
      rv.setImageViewResource(id, R.drawable.ic_star_outlined);
      rv.setInt(id, "setColorFilter", inactiveColor);
    }
    for (int id : RATING_TARGET_IDS) {
      rv.setViewVisibility(id, View.GONE);
    }
  }

  private static void applyFavoriteAndRating(Context ctx,
                                             RemoteViews rv,
                                             int layoutRes,
                                             boolean hasMedia,
                                             boolean isFavorite,
                                             int userRating) {
    if (!isLargeLayout(layoutRes)) {
      return;
    }

    if (!hasMedia) {
      applyFavoriteAndRatingDefaults(ctx, rv, layoutRes);
      return;
    }

    int clampedRating = Math.max(0, Math.min(userRating, 5));
    rv.setViewVisibility(R.id.metadata_actions, View.VISIBLE);
    rv.setViewVisibility(R.id.rating_container, View.VISIBLE);
    rv.setViewVisibility(R.id.rating_click_targets, View.VISIBLE);
    rv.setViewVisibility(R.id.button_favorite, View.VISIBLE);
    rv.setBoolean(R.id.button_favorite, "setChecked", isFavorite);
    int activeColor = ContextCompat.getColor(ctx, R.color.widget_icon_tint_active);
    int inactiveColor = ContextCompat.getColor(ctx, R.color.widget_icon_tint);
    for (int i = 0; i < RATING_STAR_IDS.length; i++) {
      int starViewId = RATING_STAR_IDS[i];
      boolean filled = clampedRating > i;
      rv.setViewVisibility(starViewId, View.VISIBLE);
      rv.setImageViewResource(starViewId, filled ? R.drawable.ic_star : R.drawable.ic_star_outlined);
      rv.setInt(starViewId, "setColorFilter", filled ? activeColor : inactiveColor);
    }
    rv.setTextViewText(R.id.rating_text,
        clampedRating > 0
            ? ctx.getString(R.string.widget_rating_value, clampedRating)
            : ctx.getString(R.string.widget_rating_unset));
    rv.setContentDescription(R.id.button_favorite,
        ctx.getString(R.string.widget_content_desc_favorite));
    for (int i = 0; i < RATING_TARGET_IDS.length; i++) {
      int id = RATING_TARGET_IDS[i];
      rv.setViewVisibility(id, View.VISIBLE);
      rv.setContentDescription(id,
          ctx.getString(R.string.widget_content_desc_rate, i + 1));
    }
  }

  private static Bitmap maybeRoundBitmap(Context ctx, Bitmap source) {
    if (source == null || source.isRecycled()) {
      return null;
    }

    try {
      int width = source.getWidth();
      int height = source.getHeight();
      if (width <= 0 || height <= 0) {
        return null;
      }

      Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(output);

      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

      float radiusPx = TypedValue.applyDimension(
          TypedValue.COMPLEX_UNIT_DIP,
          ALBUM_ART_CORNER_RADIUS_DP,
          ctx.getResources().getDisplayMetrics());
      float maxRadius = Math.min(width, height) / 2f;
      float safeRadius = Math.min(radiusPx, maxRadius);

      canvas.drawRoundRect(new RectF(0f, 0f, width, height), safeRadius, safeRadius, paint);
      return output;
    } catch (RuntimeException | OutOfMemoryError e) {
      android.util.Log.w("TempoWidget", "Failed to round album art", e);
      return null;
    }
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
