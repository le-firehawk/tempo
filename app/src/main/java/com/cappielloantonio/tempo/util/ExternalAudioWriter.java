package com.cappielloantonio.tempo.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.MediaItem;
import androidx.media3.common.C;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.ContentMetadata;

import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.activity.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExternalAudioWriter {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private ExternalAudioWriter() {
    }

    private static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\/:*?\\\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }

    private static String normalizeForComparison(String name) {
        String s = sanitizeFileName(name);
        s = Normalizer.normalize(s, Normalizer.Form.NFKD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return s.toLowerCase(Locale.ROOT);
    }

    private static DocumentFile findFile(DocumentFile dir, String fileName) {
        String normalized = normalizeForComparison(fileName);
        for (DocumentFile file : dir.listFiles()) {
            if (file.isDirectory()) continue;
            String existing = file.getName();
            if (existing != null && normalizeForComparison(existing).equals(normalized)) {
                return file;
            }
        }
        return null;
    }

    private static String generateDefaultCacheKey(Uri uri) {
        if (uri == null) {
            return null;
        }
        Uri normalized = uri.normalizeScheme();
        if (normalized != null) {
            return normalized.toString();
        }
        return uri.toString();
    }

    private static String fileNameFromUri(Uri uri) {
        if (uri == null) {
            return "download";
        }
        String path = uri.getLastPathSegment();
        if (path == null || path.isEmpty()) {
            path = uri.toString();
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            path = path.substring(slash + 1);
        }
        if (path.isEmpty()) {
            return "download";
        }
        return sanitizeFileName(path);
    }

    public static void downloadToUserDirectory(Context context, Child child) {
        if (context == null || child == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        MediaItem mediaItem = MappingUtil.mapDownload(child);
        String fallbackName = child.getTitle() != null ? child.getTitle() : child.getId();
        EXECUTOR.execute(() -> performDownload(appContext, mediaItem, fallbackName, child));
    }

    public static void persistStreamToUserDirectory(Context context, MediaItem mediaItem) {
        if (context == null || mediaItem == null) {
            return;
        }
        if (!Preferences.isStreamToDownloadEnabled()) {
            return;
        }
        if (Preferences.getDownloadDirectoryUri() == null) {
            return;
        }
        if (mediaItem.mediaMetadata == null || mediaItem.mediaMetadata.extras == null) {
            return;
        }
        String type = mediaItem.mediaMetadata.extras.getString("type");
        if (type == null || !Constants.MEDIA_TYPE_MUSIC.equals(type)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> downloadFromCache(appContext, mediaItem));
    }

    private static void downloadFromCache(Context context, MediaItem mediaItem) {
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            return;
        }

        DocumentFile directory = DocumentFile.fromTreeUri(context, Uri.parse(uriString));
        if (directory == null || !directory.canWrite()) {
            notifyFailure(context, "Cannot write to folder.");
            return;
        }

        Chronology child;
        try {
            child = new Chronology(mediaItem);
        } catch (Exception e) {
            notifyFailure(context, "Missing media metadata.");
            return;
        }

        Uri mediaUri = null;
        if (mediaItem.localConfiguration != null) {
            mediaUri = mediaItem.localConfiguration.uri;
        }
        if (mediaUri == null && mediaItem.requestMetadata != null) {
            mediaUri = mediaItem.requestMetadata.mediaUri;
        }
        if (mediaUri == null) {
            notifyFailure(context, "Invalid media URI.");
            return;
        }

        String fallbackName = child.getTitle();
        if (fallbackName == null || fallbackName.isEmpty()) {
            fallbackName = mediaItem.mediaId != null ? mediaItem.mediaId : fileNameFromUri(mediaUri);
        }

        String artist = child.getArtist() != null ? child.getArtist() : "";
        String title = child.getTitle() != null ? child.getTitle() : fallbackName;
        String album = child.getAlbum() != null ? child.getAlbum() : "";
        if (title == null || title.isEmpty()) {
            title = fallbackName != null ? fallbackName : fileNameFromUri(mediaUri);
        }
        if (album == null) {
            album = "";
        }
        if (child.getTitle() == null || child.getTitle().isEmpty()) {
            child.setTitle(title);
        }
        if (child.getArtist() == null) {
            child.setArtist(artist);
        }
        if (child.getAlbum() == null) {
            child.setAlbum(album);
        }

        String baseName = artist.isEmpty() ? title : artist + " - " + title;
        if (!album.isEmpty()) baseName += " (" + album + ")";
        if (baseName.isEmpty()) {
            baseName = fallbackName != null ? fallbackName : "download";
        }
        String metadataKey = normalizeForComparison(baseName);

        Cache cache = DownloadUtil.getDownloadCache(context);
        if (cache == null) {
            return;
        }

        String cacheKey = null;
        if (mediaItem.localConfiguration != null) {
            cacheKey = mediaItem.localConfiguration.customCacheKey;
        }
        if (cacheKey == null || cacheKey.isEmpty()) {
            cacheKey = generateDefaultCacheKey(mediaUri);
        }
        if (cacheKey == null || cacheKey.isEmpty()) {
            return;
        }

        Set<CacheSpan> spans = cache.getCachedSpans(cacheKey);
        if (spans == null || spans.isEmpty()) {
            return;
        }

        List<CacheSpan> sortedSpans = new ArrayList<>(spans);
        Collections.sort(sortedSpans, Comparator.comparingLong(span -> span.position));

        long expectedPosition = 0;
        for (CacheSpan span : sortedSpans) {
            if (!span.isCached || span.file == null) {
                return;
            }
            if (span.position != expectedPosition) {
                return;
            }
            expectedPosition += span.length;
        }

        ContentMetadata metadata = cache.getContentMetadata(cacheKey);
        long contentLength = ContentMetadata.getContentLength(metadata);
        if (contentLength == C.LENGTH_UNSET) {
            contentLength = expectedPosition;
        }
        if (contentLength <= 0) {
            return;
        }
        if (expectedPosition < contentLength) {
            return;
        }

        String mimeType = child.getTranscodedContentType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = child.getContentType();
        }
        if ((mimeType == null || mimeType.isEmpty()) && child.getSuffix() != null && !child.getSuffix().isEmpty()) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(child.getSuffix());
        }
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "application/octet-stream";
        }

        String extension = child.getTranscodedSuffix();
        if (extension == null || extension.isEmpty()) {
            extension = child.getSuffix();
        }
        if ((extension == null || extension.isEmpty()) && mimeType != null && !mimeType.isEmpty()) {
            String fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (fromMime != null && !fromMime.isEmpty()) {
                extension = fromMime;
            }
        }
        if (extension == null || extension.isEmpty()) {
            extension = "bin";
        }

        String sanitized = sanitizeFileName(baseName);
        if (sanitized.isEmpty()) sanitized = sanitizeFileName(fallbackName);
        if (sanitized.isEmpty()) sanitized = "download";
        String fileName = sanitized + "." + extension;

        DocumentFile existingFile = findFile(directory, fileName);
        Long recordedSize = ExternalDownloadMetadataStore.getSize(metadataKey);

        if (existingFile != null && existingFile.exists()) {
            long localLength = existingFile.length();
            boolean matches = false;
            if (localLength == contentLength) {
                matches = true;
            } else if (recordedSize != null && localLength == recordedSize) {
                matches = true;
            }

            if (matches) {
                ExternalDownloadMetadataStore.recordSize(metadataKey, localLength);
                recordDownload(child, existingFile.getUri());
                ExternalAudioReader.refreshCacheAsync();
                return;
            } else {
                existingFile.delete();
                ExternalDownloadMetadataStore.remove(metadataKey);
            }
        }

        DocumentFile targetFile = directory.createFile(mimeType, fileName);
        if (targetFile == null) {
            notifyFailure(context, "Failed to create file.");
            return;
        }

        Uri targetUri = targetFile.getUri();
        try (OutputStream out = context.getContentResolver().openOutputStream(targetUri)) {
            if (out == null) {
                notifyFailure(context, "Cannot open output stream.");
                targetFile.delete();
                return;
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            for (CacheSpan span : sortedSpans) {
                try (InputStream in = new FileInputStream(span.file)) {
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                        total += len;
                    }
                }
            }
            out.flush();

            if (total <= 0) {
                targetFile.delete();
                ExternalDownloadMetadataStore.remove(metadataKey);
                notifyFailure(context, "Empty download.");
                return;
            }

            if (contentLength > 0 && total != contentLength) {
                targetFile.delete();
                ExternalDownloadMetadataStore.remove(metadataKey);
                notifyFailure(context, "Incomplete download.");
                return;
            }

            ExternalDownloadMetadataStore.recordSize(metadataKey, total);
            recordDownload(child, targetUri);
            notifySuccess(context, fileName, child, targetUri);
            ExternalAudioReader.refreshCacheAsync();
        } catch (Exception e) {
            targetFile.delete();
            ExternalDownloadMetadataStore.remove(metadataKey);
            notifyFailure(context, e.getMessage() != null ? e.getMessage() : "Download failed");
        }
    }

    private static void performDownload(Context context, MediaItem mediaItem, String fallbackName, Child child) {
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            notifyUnavailable(context);
            return;
        }

        DocumentFile directory = DocumentFile.fromTreeUri(context, Uri.parse(uriString));
        if (directory == null || !directory.canWrite()) {
            notifyFailure(context, "Cannot write to folder.");
            return;
        }

        Uri mediaUri = mediaItem != null && mediaItem.requestMetadata != null
                ? mediaItem.requestMetadata.mediaUri
                : null;
        if (mediaUri == null) {
            notifyFailure(context, "Invalid media URI.");
            String keyBase = fallbackName != null ? fallbackName : child.getTitle();
            if (keyBase == null || keyBase.isEmpty()) {
                keyBase = "download";
            }
            ExternalDownloadMetadataStore.remove(normalizeForComparison(keyBase));
            return;
        }

        String artist = child.getArtist() != null ? child.getArtist() : "";
        String title = child.getTitle() != null ? child.getTitle() : fallbackName;
        String album = child.getAlbum() != null ? child.getAlbum() : "";
        if (title == null || title.isEmpty()) {
            title = fallbackName != null ? fallbackName : fileNameFromUri(mediaUri);
        }
        if (child.getTitle() == null || child.getTitle().isEmpty()) {
            child.setTitle(title);
        }
        if (child.getArtist() == null) {
            child.setArtist(artist);
        }
        if (child.getAlbum() == null) {
            child.setAlbum(album);
        }

        String baseName = artist.isEmpty() ? title : artist + " - " + title;
        if (!album.isEmpty()) baseName += " (" + album + ")";
        if (baseName.isEmpty()) {
            baseName = fallbackName != null ? fallbackName : "download";
        }
        String metadataKey = normalizeForComparison(baseName);

        String scheme = mediaUri.getScheme() != null ? mediaUri.getScheme().toLowerCase(Locale.ROOT) : "";

        HttpURLConnection connection = null;
        DocumentFile sourceDocument = null;
        File sourceFile = null;
        long remoteLength = -1;
        String mimeType = null;
        DocumentFile targetFile = null;

        try {
            if (scheme.equals("http") || scheme.equals("https")) {
                connection = (HttpURLConnection) new URL(mediaUri.toString()).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    notifyFailure(context, "Server returned " + responseCode);
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    return;
                }

                mimeType = connection.getContentType();
                remoteLength = connection.getContentLengthLong();
            } else if (scheme.equals("content")) {
                sourceDocument = DocumentFile.fromSingleUri(context, mediaUri);
                mimeType = context.getContentResolver().getType(mediaUri);
                if (sourceDocument != null) {
                    remoteLength = sourceDocument.length();
                }
            } else if (scheme.equals("file")) {
                String path = mediaUri.getPath();
                if (path != null) {
                    sourceFile = new File(path);
                    if (sourceFile.exists()) {
                        remoteLength = sourceFile.length();
                    }
                }
                String ext = MimeTypeMap.getFileExtensionFromUrl(mediaUri.toString());
                if (ext != null && !ext.isEmpty()) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                }
            } else {
                notifyFailure(context, "Unsupported media URI.");
                ExternalDownloadMetadataStore.remove(metadataKey);
                return;
            }

            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }

            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if ((extension == null || extension.isEmpty()) && sourceDocument != null && sourceDocument.getName() != null) {
                String name = sourceDocument.getName();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    extension = name.substring(dot + 1);
                }
            }
            if ((extension == null || extension.isEmpty()) && sourceFile != null) {
                String name = sourceFile.getName();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    extension = name.substring(dot + 1);
                }
            }
            if (extension == null || extension.isEmpty()) {
                String suffix = child.getSuffix();
                if (suffix != null && !suffix.isEmpty()) {
                    extension = suffix;
                } else {
                    extension = "bin";
                }
            }

            String sanitized = sanitizeFileName(baseName);
            if (sanitized.isEmpty()) sanitized = sanitizeFileName(fallbackName);
            if (sanitized.isEmpty()) sanitized = "download";
            String fileName = sanitized + "." + extension;

            DocumentFile existingFile = findFile(directory, fileName);
            Long recordedSize = ExternalDownloadMetadataStore.getSize(metadataKey);
            if (existingFile != null && existingFile.exists()) {
                long localLength = existingFile.length();
                boolean matches = false;
                if (remoteLength > 0 && localLength == remoteLength) {
                    matches = true;
                } else if (remoteLength <= 0 && recordedSize != null && localLength == recordedSize) {
                    matches = true;
                }
                if (matches) {
                    ExternalDownloadMetadataStore.recordSize(metadataKey, localLength);
                    recordDownload(child, existingFile.getUri());
                    ExternalAudioReader.refreshCacheAsync();
                    notifyExists(context, fileName);
                    return;
                } else {
                    existingFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                }
            }

            targetFile = directory.createFile(mimeType, fileName);
            if (targetFile == null) {
                notifyFailure(context, "Failed to create file.");
                return;
            }

            Uri targetUri = targetFile.getUri();
            try (InputStream in = openInputStream(context, mediaUri, scheme, connection, sourceFile);
                 OutputStream out = context.getContentResolver().openOutputStream(targetUri)) {
                if (out == null) {
                    notifyFailure(context, "Cannot open output stream.");
                    targetFile.delete();
                    return;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                long total = 0;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    total += len;
                }
                out.flush();

                if (total <= 0) {
                    targetFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    notifyFailure(context, "Empty download.");
                    return;
                }

                if (remoteLength > 0 && total != remoteLength) {
                    targetFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    notifyFailure(context, "Incomplete download.");
                    return;
                }

                ExternalDownloadMetadataStore.recordSize(metadataKey, total);
                recordDownload(child, targetUri);
                notifySuccess(context, fileName, child, targetUri);
                ExternalAudioReader.refreshCacheAsync();
            }
        } catch (Exception e) {
            if (targetFile != null) {
                targetFile.delete();
            }
            ExternalDownloadMetadataStore.remove(metadataKey);
            notifyFailure(context, e.getMessage() != null ? e.getMessage() : "Download failed");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void notifyUnavailable(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
        PendingIntent openSettings = PendingIntent.getActivity(context, 0, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("No download folder set")
                .setContentText("Tap to set one in settings")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setContentIntent(openSettings)
                .setAutoCancel(true);

        manager.notify(1011, builder.build());
    }

    private static void notifyFailure(Context context, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download failed")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static void notifySuccess(Context context, String name, Child child, Uri fileUri) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download complete")
                .setContentText(name)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true);

        PendingIntent playIntent = buildPlayIntent(context, child, fileUri);
        if (playIntent != null) {
            builder.setContentIntent(playIntent);
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static void recordDownload(Child child, Uri fileUri) {
        if (child == null) {
            return;
        }

        Download download = new Download(child);
        download.setDownloadState(1);
        if (fileUri != null) {
            download.setDownloadUri(fileUri.toString());
        }

        new DownloadRepository().insert(download);
    }

    private static void notifyExists(Context context, String name) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Already downloaded")
                .setContentText(name)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static PendingIntent buildPlayIntent(Context context, Child child, Uri fileUri) {
        if (fileUri == null) return null;
        String mediaId = child.getId();
        if (mediaId == null || mediaId.isEmpty()) {
            mediaId = fileUri.toString();
        }

        String title = child.getTitle();
        if (title == null || title.isEmpty()) {
            title = fileNameFromUri(fileUri);
        }

        String artist = child.getArtist();
        if (artist == null) {
            artist = "";
        }

        String album = child.getAlbum();
        if (album == null) {
            album = "";
        }

        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Constants.ACTION_PLAY_EXTERNAL_DOWNLOAD)
                .putExtra(Constants.EXTRA_DOWNLOAD_URI, fileUri.toString())
                .putExtra(Constants.EXTRA_DOWNLOAD_MEDIA_ID, mediaId)
                .putExtra(Constants.EXTRA_DOWNLOAD_TITLE, title)
                .putExtra(Constants.EXTRA_DOWNLOAD_ARTIST, artist)
                .putExtra(Constants.EXTRA_DOWNLOAD_ALBUM, album)
                .putExtra(Constants.EXTRA_DOWNLOAD_DURATION, child.getDuration() != null ? child.getDuration() : 0)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode;
        if (child.getId() != null) {
            requestCode = Math.abs(child.getId().hashCode());
        } else {
            requestCode = Math.abs(fileUri.toString().hashCode());
        }

        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static InputStream openInputStream(Context context,
                                               Uri mediaUri,
                                               String scheme,
                                               HttpURLConnection connection,
                                               File sourceFile) throws IOException {
        switch (scheme) {
            case "http":
            case "https":
                if (connection == null) {
                    throw new IOException("Connection not initialized");
                }
                return connection.getInputStream();
            case "content":
                InputStream contentStream = context.getContentResolver().openInputStream(mediaUri);
                if (contentStream == null) {
                    throw new IOException("Cannot open content stream");
                }
                return contentStream;
            case "file":
                if (sourceFile == null || !sourceFile.exists()) {
                    throw new IOException("Missing source file");
                }
                return new FileInputStream(sourceFile);
            default:
                throw new IOException("Unsupported scheme " + scheme);
        }
    }
}
