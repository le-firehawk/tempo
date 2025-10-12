package com.cappielloantonio.tempo.ui.fragment;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.repository.ChronologyRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.repository.LyricsRepository;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.StarredAlbumSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredArtistSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredSyncDialog;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalDownloadMetadataStore;

public class OfflineSettingsFragment extends PreferenceFragmentCompat {

    private MainActivity activity;
    private final PlaylistRepository playlistRepository = new PlaylistRepository();
    private final FavoriteRepository favoriteRepository = new FavoriteRepository();
    private final ChronologyRepository chronologyRepository = new ChronologyRepository();
    private final LyricsRepository lyricsRepository = new LyricsRepository();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            activity = (MainActivity) context;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.offline_preferences, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        configureOfflineModePreference();
        configureContentPreferences();
        configureMetadataPreferences();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (activity != null) {
            activity.setBottomNavigationBarVisibility(false);
            activity.setBottomSheetVisibility(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (activity != null) {
            activity.setBottomSheetVisibility(true);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    private void configureOfflineModePreference() {
        SwitchPreference offlineMode = findPreference("offline_mode_enabled");
        if (offlineMode == null) {
            return;
        }

        offlineMode.setChecked(Preferences.isOfflineModeEnabled());
        offlineMode.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
            Preferences.setOfflineModeEnabled(enabled);
            if (activity != null && activity.isOfflineBannerVisible()) {
                activity.updateOfflineBanner(true, enabled);
            }
            updateContentAvailability(enabled);
            updateMetadataAvailability(enabled, Preferences.isOfflineGenericMetadataEnabled());
            if (!enabled) {
                clearContentCaches(true);
                clearMetadataCaches();
            }
            return true;
        });

        updateContentAvailability(Preferences.isOfflineModeEnabled());
    }

    private void configureContentPreferences() {
        SwitchPreference lyricsPreference = findPreference("offline_lyrics_enabled");
        if (lyricsPreference != null) {
            lyricsPreference.setChecked(Preferences.isOfflineLyricsEnabled());
            lyricsPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineLyricsEnabled(enabled);
                return true;
            });
        }

        SwitchPreference albumArtPreference = findPreference("offline_album_art_enabled");
        if (albumArtPreference != null) {
            albumArtPreference.setChecked(Preferences.isOfflineAlbumArtEnabled());
            albumArtPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineAlbumArtEnabled(enabled);
                return true;
            });
        }

        SwitchPreference starredTracksPreference = findPreference("sync_starred_tracks_for_offline_use");
        if (starredTracksPreference != null) {
            starredTracksPreference.setChecked(Preferences.getStoredStarredTracksPreference());
            starredTracksPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setStarredSyncEnabled(enabled);
                if (enabled) {
                    StarredSyncDialog dialog = new StarredSyncDialog(() -> {
                        Preferences.setStarredSyncEnabled(false);
                        starredTracksPreference.setChecked(false);
                    });
                    dialog.show(requireActivity().getSupportFragmentManager(), null);
                }
                return true;
            });
        }

        SwitchPreference starredAlbumsPreference = findPreference("sync_starred_albums_for_offline_use");
        if (starredAlbumsPreference != null) {
            starredAlbumsPreference.setChecked(Preferences.getStoredStarredAlbumsPreference());
            starredAlbumsPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setStarredAlbumsSyncEnabled(enabled);
                if (enabled) {
                    StarredAlbumSyncDialog dialog = new StarredAlbumSyncDialog(() -> {
                        Preferences.setStarredAlbumsSyncEnabled(false);
                        starredAlbumsPreference.setChecked(false);
                    });
                    dialog.show(requireActivity().getSupportFragmentManager(), null);
                }
                return true;
            });
        }

        SwitchPreference starredArtistsPreference = findPreference("sync_starred_artists_for_offline_use");
        if (starredArtistsPreference != null) {
            starredArtistsPreference.setChecked(Preferences.getStoredStarredArtistsPreference());
            starredArtistsPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setStarredArtistsSyncEnabled(enabled);
                if (enabled) {
                    StarredArtistSyncDialog dialog = new StarredArtistSyncDialog(() -> {
                        Preferences.setStarredArtistsSyncEnabled(false);
                        starredArtistsPreference.setChecked(false);
                    });
                    dialog.show(requireActivity().getSupportFragmentManager(), null);
                }
                return true;
            });
        }
    }

    private void configureMetadataPreferences() {
        SwitchPreference metadataMaster = findPreference("offline_generic_metadata_enabled");
        if (metadataMaster != null) {
            metadataMaster.setChecked(Preferences.isOfflineGenericMetadataEnabled());
            metadataMaster.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineGenericMetadataEnabled(enabled);
                updateMetadataAvailability(Preferences.isOfflineModeEnabled(), enabled);
                if (!enabled) {
                    clearContentCaches(false);
                    clearMetadataCaches();
                }
                return true;
            });
        }

        SwitchPreference playlistsPreference = findPreference("offline_playlists_enabled");
        if (playlistsPreference != null) {
            playlistsPreference.setChecked(Preferences.isOfflinePlaylistsEnabled());
            playlistsPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflinePlaylistsEnabled(enabled);
                if (!enabled) {
                    clearContentCaches(false);
                }
                return true;
            });
        }

        SwitchPreference genresPreference = findPreference("offline_metadata_genres_enabled");
        if (genresPreference != null) {
            genresPreference.setChecked(Preferences.isOfflineGenresEnabled());
            genresPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineGenresEnabled(enabled);
                return true;
            });
        }

        SwitchPreference yearsPreference = findPreference("offline_metadata_years_enabled");
        if (yearsPreference != null) {
            yearsPreference.setChecked(Preferences.isOfflineYearsEnabled());
            yearsPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineYearsEnabled(enabled);
                return true;
            });
        }

        SwitchPreference topSongsPreference = findPreference("offline_metadata_top_songs_enabled");
        if (topSongsPreference != null) {
            topSongsPreference.setChecked(Preferences.isOfflineTopSongsEnabled());
            topSongsPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineTopSongsEnabled(enabled);
                return true;
            });
        }

        SwitchPreference mostPlayedPreference = findPreference("offline_metadata_most_played_enabled");
        if (mostPlayedPreference != null) {
            mostPlayedPreference.setChecked(Preferences.isOfflineMostPlayedEnabled());
            mostPlayedPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineMostPlayedEnabled(enabled);
                if (!enabled) {
                    purgeChronologyIfUnused();
                }
                return true;
            });
        }

        SwitchPreference lastPlayedPreference = findPreference("offline_metadata_last_played_enabled");
        if (lastPlayedPreference != null) {
            lastPlayedPreference.setChecked(Preferences.isOfflineLastPlayedEnabled());
            lastPlayedPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Preferences.setOfflineLastPlayedEnabled(enabled);
                if (!enabled) {
                    purgeChronologyIfUnused();
                }
                return true;
            });
        }

        updateMetadataAvailability(Preferences.isOfflineModeEnabled(), Preferences.isOfflineGenericMetadataEnabled());
    }

    private void updateContentAvailability(boolean offlineEnabled) {
        PreferenceCategory contentCategory = findPreference("offline_content_category");
        if (contentCategory != null) {
            contentCategory.setEnabled(offlineEnabled);
        }

        setSwitchEnabled("offline_lyrics_enabled", offlineEnabled);
        setSwitchEnabled("offline_album_art_enabled", offlineEnabled);
        setSwitchEnabled("sync_starred_tracks_for_offline_use", offlineEnabled);
        setSwitchEnabled("sync_starred_albums_for_offline_use", offlineEnabled);
        setSwitchEnabled("sync_starred_artists_for_offline_use", offlineEnabled);
    }

    private void updateMetadataAvailability(boolean offlineEnabled, boolean metadataEnabled) {
        PreferenceCategory metadataCategory = findPreference("offline_metadata_category");
        if (metadataCategory != null) {
            metadataCategory.setEnabled(offlineEnabled);
        }

        boolean enableChildren = offlineEnabled && metadataEnabled;
        setSwitchEnabled("offline_playlists_enabled", enableChildren);
        setSwitchEnabled("offline_metadata_genres_enabled", enableChildren);
        setSwitchEnabled("offline_metadata_years_enabled", enableChildren);
        setSwitchEnabled("offline_metadata_top_songs_enabled", enableChildren);
        setSwitchEnabled("offline_metadata_most_played_enabled", enableChildren);
        setSwitchEnabled("offline_metadata_last_played_enabled", enableChildren);
    }

    @UnstableApi
    private void clearContentCaches(boolean includeDownloads) {
        playlistRepository.clearPinnedPlaylists();
        if (!includeDownloads) {
            return;
        }

        DownloadUtil.getDownloadTracker(requireContext()).removeAll();
        ExternalDownloadMetadataStore.clear();
        lyricsRepository.deleteAll();
    }

    private void clearMetadataCaches() {
        favoriteRepository.deleteAll();
        chronologyRepository.deleteAll();
    }

    private void purgeChronologyIfUnused() {
        if (!Preferences.isOfflineModeEnabled() || !Preferences.isOfflineGenericMetadataEnabled()) {
            chronologyRepository.deleteAll();
            return;
        }

        if (!Preferences.isOfflineMostPlayedEnabled() && !Preferences.isOfflineLastPlayedEnabled()) {
            chronologyRepository.deleteAll();
        }
    }

    private void setSwitchEnabled(String key, boolean enabled) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }
}