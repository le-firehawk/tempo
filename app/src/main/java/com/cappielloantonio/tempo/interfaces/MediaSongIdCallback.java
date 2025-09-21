package com.cappielloantonio.tempo.interfaces;

import androidx.annotation.Keep;

@Keep
public interface MediaSongIdCallback {
    default void onRecovery(String id) {}
}
