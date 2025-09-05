package com.cappielloantonio.tempo.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import com.cappielloantonio.tempo.util.DownloadUtil
import com.cappielloantonio.tempo.util.Preferences

@UnstableApi
class DynamicMediaSourceFactory(
    private val context: Context
) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri: Uri = mediaItem.localConfiguration?.uri ?: mediaItem.requestMetadata.mediaUri
        ?: throw IllegalArgumentException("MediaItem must contain a valid URI")

        val streamingCacheSize = Preferences.getStreamingCacheSize()
        val bypassCache = DownloadUtil.shouldBypassCache(uri)

        val useUpstream = when {
            streamingCacheSize.toInt() == 0 -> true
            streamingCacheSize > 0 && bypassCache -> true
            streamingCacheSize > 0 && !bypassCache -> false
            else -> true
        }

        val dataSourceFactory: DataSource.Factory = if (useUpstream) {
            DownloadUtil.getUpstreamDataSourceFactory(context)
        } else {
            DownloadUtil.getCacheDataSourceFactory(context)
        }

        return when {
            mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8 ||
                    uri.toString().endsWith(".m3u8") -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            else -> {
                val extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory()
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        TODO("Not yet implemented")
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        TODO("Not yet implemented")
    }

    override fun getSupportedTypes(): IntArray {
        return intArrayOf(
            C.CONTENT_TYPE_HLS,
            C.CONTENT_TYPE_OTHER
        )
    }
}