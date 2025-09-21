package com.cappielloantonio.tempo.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;


import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutionException;

public final class WidgetActions {
  private static final String TAG = "TempoWidget";

  private WidgetActions() {}

  public static void dispatchToMediaSession(Context ctx, String action) {
    withController(ctx, controller -> {
      Log.d(TAG, "dispatch action=" + action);
      switch (action) {
        case WidgetProvider.ACT_PLAY_PAUSE:
          if (controller.isPlaying()) controller.pause(); else controller.play();
          break;
        case WidgetProvider.ACT_NEXT:
          controller.seekToNext();
          break;
        case WidgetProvider.ACT_PREV:
          controller.seekToPrevious();
          break;
        case WidgetProvider.ACT_TOGGLE_SHUFFLE:
          controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
          break;
        case WidgetProvider.ACT_CYCLE_REPEAT:
          int repeatMode = controller.getRepeatMode();
          int nextMode;
          if (repeatMode == Player.REPEAT_MODE_OFF) {
            nextMode = Player.REPEAT_MODE_ALL;
          } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            nextMode = Player.REPEAT_MODE_ONE;
          } else {
            nextMode = Player.REPEAT_MODE_OFF;
          }
          controller.setRepeatMode(nextMode);
          break;
        default:
          break;
      }
      WidgetUpdateManager.refreshFromController(ctx);
    });
  }

  public static void toggleFavorite(Context ctx, Intent intent) {
    if (intent == null) return;
    String mediaId = intent.getStringExtra(WidgetProvider.EXTRA_MEDIA_ID);
    String albumId = intent.getStringExtra(WidgetProvider.EXTRA_ALBUM_ID);
    String artistId = intent.getStringExtra(WidgetProvider.EXTRA_ARTIST_ID);
    boolean wasFavorite = intent.getBooleanExtra(WidgetProvider.EXTRA_IS_FAVORITE, false);
    if (TextUtils.isEmpty(mediaId) && TextUtils.isEmpty(albumId) && TextUtils.isEmpty(artistId)) {
      return;
    }

    boolean shouldFavorite = !wasFavorite;
    FavoriteRepository repository = new FavoriteRepository();
    if (NetworkUtil.isOffline()) {
      repository.starLater(mediaId, albumId, artistId, shouldFavorite);
    } else if (shouldFavorite) {
      repository.star(mediaId, albumId, artistId, new StarCallback() {
        @Override public void onError() {
          repository.starLater(mediaId, albumId, artistId, true);
        }
      });
    } else {
      repository.unstar(mediaId, albumId, artistId, new StarCallback() {
        @Override public void onError() {
          repository.starLater(mediaId, albumId, artistId, false);
        }
      });
    }

    withController(ctx, controller -> {
      updateMediaExtras(controller, mediaId, extras -> {
        extras.putLong("starred", shouldFavorite ? System.currentTimeMillis() : 0L);
        return true;
      });
      WidgetUpdateManager.refreshFromController(ctx);
    });
  }

  public static void submitRating(Context ctx, Intent intent) {
    if (intent == null) return;
    String mediaId = intent.getStringExtra(WidgetProvider.EXTRA_MEDIA_ID);
    if (TextUtils.isEmpty(mediaId)) {
      return;
    }
    int targetRating = intent.getIntExtra(WidgetProvider.EXTRA_TARGET_RATING, 0);
    int currentRating = intent.getIntExtra(WidgetProvider.EXTRA_CURRENT_RATING, 0);
    int newRating = targetRating == currentRating ? 0 : targetRating;
    if (newRating < 0) newRating = 0;
    if (newRating > 5) newRating = 5;

    SongRepository songRepository = new SongRepository();
    songRepository.setRating(mediaId, newRating);
    final int ratingToApply = newRating;

    withController(ctx, controller -> {
      updateMediaExtras(controller, mediaId, extras -> {
        extras.putInt("userRating", ratingToApply);
        return true;
      });
      WidgetUpdateManager.refreshFromController(ctx);
    });
  }

  private interface ControllerRunnable {
    void run(MediaController controller) throws ExecutionException, InterruptedException;
  }

  private interface ExtrasUpdater {
    boolean update(Bundle extras);
  }

  private static void withController(Context ctx, ControllerRunnable runnable) {
    Context appCtx = ctx.getApplicationContext();
    SessionToken token = new SessionToken(appCtx, new ComponentName(appCtx, MediaService.class));
    ListenableFuture<MediaController> future = new MediaController.Builder(appCtx, token).buildAsync();
    future.addListener(() -> {
      try {
        if (!future.isDone()) return;
        MediaController controller = future.get();
        try {
          runnable.run(controller);
        } finally {
          controller.release();
        }
      } catch (ExecutionException | InterruptedException e) {
        Log.e(TAG, "controller failure", e);
      }
    }, MoreExecutors.directExecutor());
  }

   private static void updateMediaExtras(MediaController controller,
                                        String mediaId,
                                        ExtrasUpdater updater) {
    if (controller == null || updater == null || TextUtils.isEmpty(mediaId)) {
      return;
    }
    MediaItem current = controller.getCurrentMediaItem();
    if (current == null || current.mediaMetadata == null) {
      return;
    }

    Bundle metadataExtras = current.mediaMetadata.extras;
    String currentId = metadataExtras != null ? metadataExtras.getString("id") : null;
    if (!TextUtils.equals(mediaId, currentId)) {
      return;
    }

    boolean changed = false;
    if (metadataExtras != null) {
      changed |= updater.update(metadataExtras);
    }
    Bundle requestExtras = current.requestMetadata != null ? current.requestMetadata.extras : null;
    if (requestExtras != null) {
      changed |= updater.update(requestExtras);
    }

    if (changed) {
      controller.replaceMediaItem(controller.getCurrentMediaItemIndex(), current);
    }
  }
}
