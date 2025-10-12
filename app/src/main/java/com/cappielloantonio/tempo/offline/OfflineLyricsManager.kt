package com.cappielloantonio.tempo.offline

import com.cappielloantonio.tempo.model.LyricsCache
import com.cappielloantonio.tempo.repository.LyricsRepository
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.LyricsList
import com.cappielloantonio.tempo.util.Preferences
import com.google.gson.Gson

object OfflineLyricsManager {
    data class CachedLyrics(
        val plainText: String?,
        val structured: LyricsList?
    )

    private fun hasStructuredLyricsInternal(lyricsList: LyricsList?): Boolean {
        val structuredLyrics = lyricsList?.structuredLyrics
        val firstStructured = structuredLyrics?.firstOrNull()
        val lines = firstStructured?.line

        return !lines.isNullOrEmpty()
    }

    @JvmStatic
    fun hasStructuredLyrics(lyricsList: LyricsList?): Boolean {
        return hasStructuredLyricsInternal(lyricsList)
    }

    @JvmStatic
    fun saveLyricsToCache(
        lyricsRepository: LyricsRepository,
        media: Child?,
        lyrics: String?,
        lyricsList: LyricsList?,
        serializer: Gson = Gson()
    ): CachedLyrics? {
        if (!Preferences.isOfflineModeEnabled() || !Preferences.isOfflineLyricsEnabled()) {
            return null
        }

        if (media == null) {
            return null
        }

        val hasStructuredLyrics = hasStructuredLyricsInternal(lyricsList)
        if (!hasStructuredLyrics && lyrics.isNullOrBlank()) {
            return null
        }

        val lyricsCache = LyricsCache(media.id)
        lyricsCache.artist = media.artist
        lyricsCache.title = media.title
        lyricsCache.updatedAt = System.currentTimeMillis()

        val cachedLyrics: CachedLyrics
        if (hasStructuredLyrics) {
            lyricsCache.structuredLyrics = serializer.toJson(lyricsList)
            lyricsCache.lyrics = null
            cachedLyrics = CachedLyrics(null, lyricsList)
        } else {
            lyricsCache.lyrics = lyrics
            lyricsCache.structuredLyrics = null
            cachedLyrics = CachedLyrics(lyrics, null)
        }

        lyricsRepository.insert(lyricsCache)
        return cachedLyrics
    }

    @JvmStatic
    fun decodeCachedLyrics(cache: LyricsCache?, serializer: Gson = Gson()): CachedLyrics? {
        if (cache == null) {
            return null
        }

        if (!cache.structuredLyrics.isNullOrBlank()) {
            return try {
                val parsed = serializer.fromJson(cache.structuredLyrics, LyricsList::class.java)
                CachedLyrics(null, parsed)
            } catch (exception: Exception) {
                CachedLyrics(cache.lyrics, null)
            }
        }

        return CachedLyrics(cache.lyrics, null)
    }
}