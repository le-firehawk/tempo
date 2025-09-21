package com.cappielloantonio.tempo.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.graphics.drawable.Drawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.R;
import androidx.media3.common.C;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;

public final class WidgetUpdateManager {

  public static void updateFromState(Context ctx,
                                     String title,
                                     String artist,
                                     String album,
                                     Bitmap art,
                                     boolean playing,
                                     boolean shuffleEnabled,
                                     int repeatMode,
                                     long positionMs,
                                     long durationMs,
                                     String mediaId,
                                     String albumId,
                                     String artistId,
                                     boolean isFavorite,
                                     int userRating) {
    if (TextUtils.isEmpty(title)) title = ctx.getString(R.string.widget_not_playing);
    if (TextUtils.isEmpty(artist)) artist = ctx.getString(R.string.widget_placeholder_subtitle);
    if (TextUtils.isEmpty(album)) album = "";

    final TimingInfo timing = createTimingInfo(positionMs, durationMs);

    AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
    int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider4x1.class));
    for (int id : ids) {
      LayoutSize size = resolveLayoutSize(ctx, id);
      android.widget.RemoteViews rv = populateForSize(ctx, size, title, artist, album, art, playing,
          timing.elapsedText, timing.totalText, timing.progress, shuffleEnabled, repeatMode,
          mediaId, albumId, artistId, isFavorite, userRating);
      boolean supportsExtended = size == LayoutSize.LARGE || size == LayoutSize.EXPANDED;
      WidgetProvider.attachIntents(ctx, rv, id, mediaId, albumId, artistId, isFavorite, userRating,
          supportsExtended)
      mgr.updateAppWidget(id, rv);
    }
  }

  public static void pushNow(Context ctx) {
    AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
    int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider4x1.class));
    for (int id : ids) {
      android.widget.RemoteViews rv = chooseBuild(ctx, id);
      WidgetProvider.attachIntents(ctx, rv, id);
      mgr.updateAppWidget(id, rv);
    }
  }

  public static void updateFromState(Context ctx,
                                     String title,
                                     String artist,
                                     String album,
                                     String coverArtId,
                                     boolean playing,
                                     boolean shuffleEnabled,
                                     int repeatMode,
                                     long positionMs,
                                     long durationMs,
                                     String mediaId,
                                     String albumId,
                                     String artistId,
                                     boolean isFavorite,
                                     int userRating) {
    final Context appCtx = ctx.getApplicationContext();
    final String t = TextUtils.isEmpty(title) ? appCtx.getString(R.string.widget_not_playing) : title;
    final String a = TextUtils.isEmpty(artist) ? appCtx.getString(R.string.widget_placeholder_subtitle) : artist;
    final String alb = !TextUtils.isEmpty(album) ? album : "";
    final boolean p = playing;
    final boolean sh = shuffleEnabled;
    final int rep = repeatMode;
    final TimingInfo timing = createTimingInfo(positionMs, durationMs);
    final String media = mediaId;
    final String albId = albumId;
    final String artId = artistId;
    final boolean fav = isFavorite;
    final int rating = userRating;

    if (!TextUtils.isEmpty(coverArtId)) {
      CustomGlideRequest.loadAlbumArtBitmap(
          appCtx,
          coverArtId,
          com.cappielloantonio.tempo.util.Preferences.getImageSize(),
          new CustomTarget<Bitmap>() {
            @Override public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
              AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
              int[] ids = mgr.getAppWidgetIds(new ComponentName(appCtx, WidgetProvider4x1.class));
              for (int id : ids) {
                LayoutSize size = resolveLayoutSize(appCtx, id);
                android.widget.RemoteViews rv = populateForSize(appCtx, size, t, a, alb, resource, p,
                    timing.elapsedText, timing.totalText, timing.progress, sh, rep,
                    media, albId, artId, fav, rating);
                boolean supportsExtended = size == LayoutSize.LARGE || size == LayoutSize.EXPANDED;
                WidgetProvider.attachIntents(appCtx, rv, id, media, albId, artId, fav, rating,
                    supportsExtended);
                mgr.updateAppWidget(id, rv);
              }
            }

            @Override public void onLoadCleared(Drawable placeholder) {
              AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
              int[] ids = mgr.getAppWidgetIds(new ComponentName(appCtx, WidgetProvider4x1.class));
              for (int id : ids) {
                LayoutSize size = resolveLayoutSize(appCtx, id);
                android.widget.RemoteViews rv = populateForSize(appCtx, size, t, a, alb, null, p,
                    timing.elapsedText, timing.totalText, timing.progress, sh, rep,
                    media, albId, artId, fav, rating);
                boolean supportsExtended = size == LayoutSize.LARGE || size == LayoutSize.EXPANDED;
                WidgetProvider.attachIntents(appCtx, rv, id, media, albId, artId, fav, rating,
                    supportsExtended);
                mgr.updateAppWidget(id, rv);
              }
            }
          }
      );
    } else {
      AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
      int[] ids = mgr.getAppWidgetIds(new ComponentName(appCtx, WidgetProvider4x1.class));
      for (int id : ids) {
        LayoutSize size = resolveLayoutSize(appCtx, id);
        android.widget.RemoteViews rv = populateForSize(appCtx, size, t, a, alb, null, p,
            timing.elapsedText, timing.totalText, timing.progress, sh, rep,
            media, albId, artId, fav, rating);
        boolean supportsExtended = size == LayoutSize.LARGE || size == LayoutSize.EXPANDED;
        WidgetProvider.attachIntents(appCtx, rv, id, media, albId, artId, fav, rating,
            supportsExtended);
        mgr.updateAppWidget(id, rv);
      }
    }
  }

  public static void refreshFromController(Context ctx) {
    final Context appCtx = ctx.getApplicationContext();
    SessionToken token = new SessionToken(appCtx, new ComponentName(appCtx, MediaService.class));
    ListenableFuture<MediaController> future = new MediaController.Builder(appCtx, token).buildAsync();
    future.addListener(() -> {
      try {
        if (!future.isDone()) return;
        MediaController c = future.get();
        androidx.media3.common.MediaItem mi = c.getCurrentMediaItem();
        String title = null, artist = null, album = null, coverId = null;
        String mediaId = null, albumId = null, artistId = null;
        boolean favorite = false;
        int rating = 0;
        if (mi != null && mi.mediaMetadata != null) {
          if (mi.mediaMetadata.title != null) title = mi.mediaMetadata.title.toString();
          if (mi.mediaMetadata.artist != null) artist = mi.mediaMetadata.artist.toString();
          if (mi.mediaMetadata.albumTitle != null) album = mi.mediaMetadata.albumTitle.toString();
          if (mi.mediaMetadata.extras != null) {
            if (title == null) title = mi.mediaMetadata.extras.getString("title");
            if (artist == null) artist = mi.mediaMetadata.extras.getString("artist");
            if (album == null) album = mi.mediaMetadata.extras.getString("album");
            coverId = mi.mediaMetadata.extras.getString("coverArtId");
            mediaId = mi.mediaMetadata.extras.getString("id");
            albumId = mi.mediaMetadata.extras.getString("albumId");
            artistId = mi.mediaMetadata.extras.getString("artistId");
            long starredValue = mi.mediaMetadata.extras.getLong("starred", 0L);
            favorite = starredValue > 0L;
            rating = mi.mediaMetadata.extras.getInt("userRating", 0);
          }
        }
        if (mi != null && mi.requestMetadata != null && mi.requestMetadata.extras != null) {
          if (TextUtils.isEmpty(mediaId)) mediaId = mi.requestMetadata.extras.getString("id");
          if (TextUtils.isEmpty(albumId)) albumId = mi.requestMetadata.extras.getString("albumId");
          if (TextUtils.isEmpty(artistId)) artistId = mi.requestMetadata.extras.getString("artistId");
          if (!favorite) {
            long starredValue = mi.requestMetadata.extras.getLong("starred", 0L);
            favorite = starredValue > 0L;
          }
          if (rating <= 0) {
            rating = mi.requestMetadata.extras.getInt("userRating", 0);
          }
        }
        long position = c.getCurrentPosition();
        long duration = c.getDuration();
        if (position == C.TIME_UNSET) position = 0;
        if (duration == C.TIME_UNSET) duration = 0;
        updateFromState(appCtx,
            title != null ? title : appCtx.getString(R.string.widget_not_playing),
            artist != null ? artist : appCtx.getString(R.string.widget_placeholder_subtitle),
            album,
            coverId,
            c.isPlaying(),
            c.getShuffleModeEnabled(),
            c.getRepeatMode(),
            position,
            duration,
            mediaId,
            albumId,
            artistId,
            favorite,
            rating);
        c.release();
      } catch (ExecutionException | InterruptedException ignored) {
      }
    }, MoreExecutors.directExecutor());
  }

  private static TimingInfo createTimingInfo(long positionMs, long durationMs) {
    long safePosition = Math.max(0L, positionMs);
    long safeDuration = durationMs > 0 ? durationMs : 0L;
    if (safeDuration > 0 && safePosition > safeDuration) {
      safePosition = safeDuration;
    }

    String elapsed = (safeDuration > 0 || safePosition > 0)
        ? MusicUtil.getReadableDurationString(safePosition, true)
        : null;
    String total = safeDuration > 0
        ? MusicUtil.getReadableDurationString(safeDuration, true)
        : null;

    int progress = 0;
    if (safeDuration > 0) {
      long scaled = safePosition * WidgetViewsFactory.PROGRESS_MAX;
      long progressLong = scaled / safeDuration;
      if (progressLong < 0) {
        progress = 0;
      } else if (progressLong > WidgetViewsFactory.PROGRESS_MAX) {
        progress = WidgetViewsFactory.PROGRESS_MAX;
      } else {
        progress = (int) progressLong;
      }
    }

    return new TimingInfo(elapsed, total, progress);
  }

  public static android.widget.RemoteViews chooseBuild(Context ctx, int appWidgetId) {
    LayoutSize size = resolveLayoutSize(ctx, appWidgetId);
    switch (size) {
      case MEDIUM:
        return WidgetViewsFactory.buildMedium(ctx);
      case LARGE:
        return WidgetViewsFactory.buildLarge(ctx);
      case EXPANDED:
        return WidgetViewsFactory.buildExpanded(ctx);
      case COMPACT:
      default:
        return WidgetViewsFactory.buildCompact(ctx);
    }
  }

  private static android.widget.RemoteViews populateForSize(Context ctx,
                                                            LayoutSize size,
                                                            String title,
                                                            String artist,
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
    switch (size) {
      case MEDIUM:
        return WidgetViewsFactory.populateMedium(ctx, title, artist, album, art, playing,
            elapsedText, totalText, progress, shuffleEnabled, repeatMode,
            mediaId, albumId, artistId, isFavorite, userRating);
      case LARGE:
        return WidgetViewsFactory.populateLarge(ctx, title, artist, album, art, playing,
            elapsedText, totalText, progress, shuffleEnabled, repeatMode,
            mediaId, albumId, artistId, isFavorite, userRating);
      case EXPANDED:
        return WidgetViewsFactory.populateExpanded(ctx, title, artist, album, art, playing,
            elapsedText, totalText, progress, shuffleEnabled, repeatMode,
            mediaId, albumId, artistId, isFavorite, userRating);
      case COMPACT:
      default:
        return WidgetViewsFactory.populateCompact(ctx, title, artist, album, art, playing,
            elapsedText, totalText, progress, shuffleEnabled, repeatMode,
            mediaId, albumId, artistId, isFavorite, userRating);
    }
  }

  private static LayoutSize resolveLayoutSize(Context ctx, int appWidgetId) {
    AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
    android.os.Bundle opts = mgr.getAppWidgetOptions(appWidgetId);
    int minH = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) : 0;
    int expandedThreshold = ctx.getResources().getInteger(R.integer.widget_expanded_min_height_dp);
    int largeThreshold = ctx.getResources().getInteger(R.integer.widget_large_min_height_dp);
    int mediumThreshold = ctx.getResources().getInteger(R.integer.widget_medium_min_height_dp);
    if (minH >= expandedThreshold) return LayoutSize.EXPANDED;
    if (minH >= largeThreshold) return LayoutSize.LARGE;
    if (minH >= mediumThreshold) return LayoutSize.MEDIUM;
    return LayoutSize.COMPACT;
  }

  private enum LayoutSize {
    COMPACT,
    MEDIUM,
    LARGE,
    EXPANDED
  }

  private static final class TimingInfo {
    final String elapsedText;
    final String totalText;
    final int progress;

    TimingInfo(String elapsedText, String totalText, int progress) {
      this.elapsedText = elapsedText;
      this.totalText = totalText;
      this.progress = progress;
    }
  }

}
