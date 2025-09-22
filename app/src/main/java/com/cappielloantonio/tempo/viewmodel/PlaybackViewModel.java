package com.cappielloantonio.tempo.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

public class PlaybackViewModel extends ViewModel {

    private final MutableLiveData<String> currentMediaId = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);

    // (Optional) expose position or other info
    // private final MutableLiveData<Long> positionMs = new MutableLiveData<>(0L);

    public LiveData<String> getCurrentMediaId() {
        return currentMediaId;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public void update(String mediaId, boolean playing) {
        if (!Objects.equals(currentMediaId.getValue(), mediaId)) {
            currentMediaId.postValue(mediaId);
        }
        if (!Objects.equals(isPlaying.getValue(), playing)) {
            isPlaying.postValue(playing);
        }
    }

    public void clear() {
        currentMediaId.postValue(null);
        isPlaying.postValue(false);
    }
}