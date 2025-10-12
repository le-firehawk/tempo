package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.model.ArtistMetadataCache;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistInfo2;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.IndexID3;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArtistRepository {
    private final AlbumRepository albumRepository;
    private final ArtistMetadataRepository artistMetadataRepository;

    public ArtistRepository() {
        this.albumRepository = new AlbumRepository();
        this.artistMetadataRepository = new ArtistMetadataRepository();
    }

    private boolean isArtistMetadataCachingEnabled() {
        return Preferences.isOfflineModeEnabled()
                && Preferences.isOfflineGenericMetadataEnabled()
                && Preferences.isOfflineArtistMetadataEnabled();
    }

    private ArtistMetadataCache getCachedArtistMetadata(ArtistID3 artist) {
        if (!isArtistMetadataCachingEnabled() || artist == null || artist.getId() == null) {
            return null;
        }
        return artistMetadataRepository.get(artist.getId());
    }

    private void persistArtistMetadata(ArtistID3 artist, ArtistInfo2 info, List<Child> topSongs, List<AlbumID3> albums) {
        if (artist == null || artist.getId() == null) {
            return;
        }

        if (!isArtistMetadataCachingEnabled()) {
            artistMetadataRepository.delete(artist.getId());
            return;
        }

        List<Child> topSongsCopy = topSongs != null ? new ArrayList<>(topSongs) : null;
        List<AlbumID3> albumsCopy = albums != null ? new ArrayList<>(albums) : null;
        ArtistMetadataCache existing = artistMetadataRepository.get(artist.getId());
        ArtistMetadataCache merged = ArtistMetadataCache.Companion.merge(existing, artist, info, topSongsCopy, albumsCopy);
        artistMetadataRepository.insert(merged);
    }

    private void emitCachedArtistInfo(ArtistID3 artist, MutableLiveData<ArtistInfo2> target) {
        ArtistMetadataCache cache = getCachedArtistMetadata(artist);
        if (cache != null) {
            ArtistInfo2 cachedInfo = cache.getArtistInfo();
            if (cachedInfo != null) {
                target.postValue(cachedInfo);
            }
        }
    }

    private void emitCachedTopSongs(ArtistID3 artist, int count, MutableLiveData<List<Child>> target) {
        ArtistMetadataCache cache = getCachedArtistMetadata(artist);
        if (cache != null) {
            List<Child> songs = cache.getTopSongs();
            if (songs != null) {
                List<Child> limited = limitTopSongs(songs, count);
                if (limited != null) {
                    target.postValue(limited);
                }
            }
        }
    }

    private void emitCachedAlbums(ArtistID3 artist, MutableLiveData<List<AlbumID3>> target) {
        ArtistMetadataCache cache = getCachedArtistMetadata(artist);
        if (cache != null) {
            List<AlbumID3> albums = cache.getAlbums();
            if (albums != null) {
                target.postValue(new ArrayList<>(albums));
            }
        }
    }

    private List<Child> limitTopSongs(List<Child> songs, int count) {
        if (songs == null) {
            return null;
        }
        if (count <= 0 || songs.size() <= count) {
            return new ArrayList<>(songs);
        }
        return new ArrayList<>(songs.subList(0, count));
    }

    public void getArtistAllSongs(String artistId, ArtistSongsCallback callback) {
        Log.d("ArtistSync", "Getting albums for artist: " + artistId);

        // Get the artist info first, which contains the albums
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && 
                            response.body().getSubsonicResponse().getArtist() != null && 
                            response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();
                            Log.d("ArtistSync", "Got albums directly: " + albums.size());
                            
                            if (!albums.isEmpty()) {
                                fetchAllAlbumSongsWithCallback(albums, callback);
                            } else {
                                Log.d("ArtistSync", "No albums found in artist response");
                                callback.onSongsCollected(new ArrayList<>());
                            }
                        } else {
                            Log.d("ArtistSync", "Failed to get artist info");
                            callback.onSongsCollected(new ArrayList<>());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.d("ArtistSync", "Error getting artist info: " + t.getMessage());
                        callback.onSongsCollected(new ArrayList<>());
                    }
                });
    }

    private void fetchAllAlbumSongsWithCallback(List<AlbumID3> albums, ArtistSongsCallback callback) {
        if (albums == null || albums.isEmpty()) {
            Log.d("ArtistSync", "No albums to process");
            callback.onSongsCollected(new ArrayList<>());
            return;
        }

        List<Child> allSongs = new ArrayList<>();
        AtomicInteger remainingAlbums = new AtomicInteger(albums.size());
        Log.d("ArtistSync", "Processing " + albums.size() + " albums");
        
        for (AlbumID3 album : albums) {
            Log.d("ArtistSync", "Getting tracks for album: " + album.getName());
            MutableLiveData<List<Child>> albumTracks = albumRepository.getAlbumTracks(album.getId());
            albumTracks.observeForever(songs -> {
                Log.d("ArtistSync", "Got " + (songs != null ? songs.size() : 0) + " songs from album");
                if (songs != null) {
                    allSongs.addAll(songs);
                }
                albumTracks.removeObservers(null);
                
                int remaining = remainingAlbums.decrementAndGet();
                Log.d("ArtistSync", "Remaining albums: " + remaining);
                
                if (remaining == 0) {
                    Log.d("ArtistSync", "All albums processed. Total songs: " + allSongs.size());
                    callback.onSongsCollected(allSongs);
                }
            });
        }
    }

    public interface ArtistSongsCallback {
        void onSongsCollected(List<Child> songs);
    }

    public MutableLiveData<List<AlbumID3>> getArtistAlbums(ArtistID3 artist) {
        MutableLiveData<List<AlbumID3>> artistsAlbum = new MutableLiveData<>(new ArrayList<>());

        if (artist == null || artist.getId() == null) {
            return artistsAlbum;
        }

        emitCachedAlbums(artist, artistsAlbum);

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artist.getId())
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getArtist() != null
                                && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();
                            if (albums != null) {
                                albums.sort(Comparator.comparing(AlbumID3::getYear));
                                Collections.reverse(albums);
                                artistsAlbum.setValue(albums);
                                persistArtistMetadata(artist, null, null, albums);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artistsAlbum;
    }

    public MutableLiveData<List<ArtistID3>> getStarredArtists(boolean random, int size) {
        MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<ArtistID3> artists = response.body().getSubsonicResponse().getStarred2().getArtists();

                            if (artists != null) {
                                if (!random) {
                                    getArtistInfo(artists, starredArtists);
                                } else {
                                    Collections.shuffle(artists);
                                    getArtistInfo(artists.subList(0, Math.min(size, artists.size())), starredArtists);
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return starredArtists;
    }

    public MutableLiveData<List<ArtistID3>> getArtists(boolean random, int size) {
        MutableLiveData<List<ArtistID3>> listLiveArtists = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ArtistID3> artists = new ArrayList<>();

                            if(response.body().getSubsonicResponse().getArtists() != null && response.body().getSubsonicResponse().getArtists().getIndices() != null) {
                                for (IndexID3 index : response.body().getSubsonicResponse().getArtists().getIndices()) {
                                    if(index != null && index.getArtists() != null) {
                                        artists.addAll(index.getArtists());
                                    }
                                }
                            }

                            if (random) {
                                Collections.shuffle(artists);
                                getArtistInfo(artists.subList(0, artists.size() / size > 0 ? size : artists.size()), listLiveArtists);
                            } else {
                                listLiveArtists.setValue(artists);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                    }
                });

        return listLiveArtists;
    }

    /*
     * Method that returns essential artist information (cover, album number, etc.)
     */
    public void getArtistInfo(List<ArtistID3> artists, MutableLiveData<List<ArtistID3>> list) {
        List<ArtistID3> liveArtists = list.getValue();
        if (liveArtists == null) liveArtists = new ArrayList<>();
        list.setValue(liveArtists);

        for (ArtistID3 artist : artists) {
            App.getSubsonicClientInstance(false)
                    .getBrowsingClient()
                    .getArtist(artist.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                                addToMutableLiveData(list, response.body().getSubsonicResponse().getArtist());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                        }
                    });
        }
    }

    public MutableLiveData<ArtistID3> getArtistInfo(String id) {
        MutableLiveData<ArtistID3> artist = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                            artist.setValue(response.body().getSubsonicResponse().getArtist());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artist;
    }

    public MutableLiveData<ArtistInfo2> getArtistFullInfo(ArtistID3 artist) {
        MutableLiveData<ArtistInfo2> artistFullInfo = new MutableLiveData<>(null);

        if (artist == null || artist.getId() == null) {
            return artistFullInfo;
        }

        emitCachedArtistInfo(artist, artistFullInfo);

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtistInfo2(artist.getId())
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getArtistInfo2() != null) {
                            ArtistInfo2 info = response.body().getSubsonicResponse().getArtistInfo2();
                            artistFullInfo.setValue(info);
                            persistArtistMetadata(artist, info, null, null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artistFullInfo;
    }

    public void setRating(String id, int rating) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .setRating(id, rating)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public MutableLiveData<ArtistID3> getArtist(String id) {
        MutableLiveData<ArtistID3> artist = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                            artist.setValue(response.body().getSubsonicResponse().getArtist());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artist;
    }

    public MutableLiveData<List<Child>> getInstantMix(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(artist.getId(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null) {
                            instantMix.setValue(response.body().getSubsonicResponse().getSimilarSongs2().getSongs());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return instantMix;
    }

    public MutableLiveData<List<Child>> getRandomSong(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> randomSongs = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getTopSongs(artist.getName(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getTopSongs() != null && response.body().getSubsonicResponse().getTopSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getTopSongs().getSongs();

                            if (songs != null && !songs.isEmpty()) {
                                Collections.shuffle(songs);
                            }

                            randomSongs.setValue(songs);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return randomSongs;
    }

    public MutableLiveData<List<Child>> getTopSongs(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> topSongs = new MutableLiveData<>(new ArrayList<>());

        if (artist == null || artist.getName() == null) {
            return topSongs;
        }

        emitCachedTopSongs(artist, count, topSongs);

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getTopSongs(artist.getName(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getTopSongs() != null
                                && response.body().getSubsonicResponse().getTopSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getTopSongs().getSongs();
                            topSongs.setValue(songs);
                            persistArtistMetadata(artist, null, songs, null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return topSongs;
    }

    private void addToMutableLiveData(MutableLiveData<List<ArtistID3>> liveData, ArtistID3 artist) {
        List<ArtistID3> liveArtists = liveData.getValue();
        if (liveArtists != null) liveArtists.add(artist);
        liveData.setValue(liveArtists);
    }
}
