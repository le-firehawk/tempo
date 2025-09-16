package com.cappielloantonio.tempo.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;
import com.cappielloantonio.tempo.R;

public final class WidgetViewsFactory {

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
    rv.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play);
    // Show Tempo logo when nothing is playing
    rv.setImageViewResource(R.id.album_art, R.drawable.ic_splash_logo);
    return rv;
  }

  public static RemoteViews populate(Context ctx,
                                     String title,
                                     String subtitle,
                                     Bitmap art,
                                     boolean playing) {
    return populateWithLayout(ctx, title, subtitle, art, playing, R.layout.widget_layout_compact);
  }

  public static RemoteViews populateLarge(Context ctx,
                                     String title,
                                     String subtitle,
                                     Bitmap art,
                                     boolean playing) {
    return populateWithLayout(ctx, title, subtitle, art, playing, R.layout.widget_layout_large);
  }


  private static RemoteViews populateWithLayout(Context ctx,
                                     String title,
                                     String subtitle,
                                     Bitmap art,
                                     boolean playing,
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

    return rv;
  }
}
