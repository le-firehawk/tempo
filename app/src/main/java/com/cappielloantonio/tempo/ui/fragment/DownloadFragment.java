package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentDownloadBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.DownloadStack;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.DownloadHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.DownloadViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.common.util.concurrent.ListenableFuture;

// Core Android
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

// Media3
import androidx.media3.common.MediaItem;

// Your utility class
import com.cappielloantonio.tempo.util.ExternalAudioWriter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class DownloadFragment extends Fragment implements ClickCallback {
    private static final String TAG = "DownloadFragment";
    private static final int REQUEST_CODE_PICK_DIRECTORY = 1002; // Line ~38
    private SharedPreferences prefs; // Line ~39

    private FragmentDownloadBinding bind;
    private MainActivity activity;
    private DownloadViewModel downloadViewModel;

    private DownloadHorizontalAdapter downloadHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private MaterialToolbar materialToolbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentDownloadBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        downloadViewModel = new ViewModelProvider(requireActivity()).get(DownloadViewModel.class);

        prefs = requireContext().getSharedPreferences("tempo_prefs", Context.MODE_PRIVATE);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initDownloadedView();
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
        activity.setBottomNavigationBarVisibility(true);
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void initDownloadedView() {
        bind.downloadedRecyclerView.setHasFixedSize(true);

        downloadHorizontalAdapter = new DownloadHorizontalAdapter(this);
        bind.downloadedRecyclerView.setAdapter(downloadHorizontalAdapter);

        downloadViewModel.getDownloadedTracks(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            if (songs != null) {
                if (songs.isEmpty()) {
                    if (bind != null) {
                        bind.emptyDownloadLayout.setVisibility(View.VISIBLE);
                        bind.fragmentDownloadNestedScrollView.setVisibility(View.GONE);
                        bind.downloadDownloadedSector.setVisibility(View.GONE);
                        bind.downloadedGroupByImageView.setVisibility(View.GONE);
                    }
                } else {
                    if (bind != null) {
                        bind.emptyDownloadLayout.setVisibility(View.GONE);
                        bind.fragmentDownloadNestedScrollView.setVisibility(View.VISIBLE);
                        bind.downloadDownloadedSector.setVisibility(View.VISIBLE);
                        bind.downloadedGroupByImageView.setVisibility(View.VISIBLE);

                        finishDownloadView(songs);
                    }
                }

                if (bind != null) bind.loadingProgressBar.setVisibility(View.GONE);
            }
        });

        bind.downloadedGroupByImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.download_popup_menu));
        bind.downloadedGoBackImageView.setOnClickListener(view -> downloadViewModel.popViewStack());
    }

    private void finishDownloadView(List<Child> songs) {
        downloadViewModel.getViewStack().observe(getViewLifecycleOwner(), stack -> {
            bind.downloadedRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

            DownloadStack lastLevel = stack.get(stack.size() - 1);

            switch (lastLevel.getId()) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), songs);
                    break;
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), songs);
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_ALBUM, lastLevel.getId(), lastLevel.getView(), songs);
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), songs);
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), songs);
                    break;
            }

            bind.downloadedGoBackImageView.setVisibility(stack.size() > 1 ? View.VISIBLE : View.GONE);

            setupBackPressing(stack.size());
            setupShuffleButton();
        });
    }

    private void setupShuffleButton() {
        bind.shuffleDownloadedTextViewClickable.setOnClickListener(view -> {
            List<Child> songs = downloadHorizontalAdapter.getShuffling();

            if (songs != null && !songs.isEmpty()) {
                Collections.shuffle(songs);
                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                activity.setBottomSheetInPeek(true);
            }
        });
    }

    private void setupBackPressing(int stackSize) {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (stackSize > 1) {
                    downloadViewModel.popViewStack();
                } else {
                    activity.navController.navigateUp();
                }

                remove();
            }
        });
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_download_group_by_track) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_TRACK, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_TRACK);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_album) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ALBUM, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_ALBUM);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_artist) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ARTIST, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_ARTIST);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_genre) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_GENRE, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_GENRE);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_year) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_YEAR, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_YEAR);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_set_directory) { 
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onYearClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_YEAR, bundle.getString(Constants.DOWNLOAD_TYPE_YEAR)));
    }

    @Override
    public void onGenreClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_GENRE, bundle.getString(Constants.DOWNLOAD_TYPE_GENRE)));
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ARTIST, bundle.getString(Constants.DOWNLOAD_TYPE_ARTIST)));
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ALBUM, bundle.getString(Constants.DOWNLOAD_TYPE_ALBUM)));
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onDownloadGroupLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.downloadBottomSheetDialog, bundle);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_DIRECTORY && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
                prefs.edit().putString("download_directory_uri", uri.toString()).apply();
                Toast.makeText(requireContext(), "Download directory set", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
