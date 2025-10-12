package com.cappielloantonio.tempo.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cappielloantonio.tempo.subsonic.models.AlbumID3
import com.cappielloantonio.tempo.subsonic.models.ArtistID3
import com.cappielloantonio.tempo.subsonic.models.ArtistInfo2
import com.cappielloantonio.tempo.subsonic.models.Child
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.util.Date

@Keep
@Entity(tableName = "artist_metadata_cache")
data class ArtistMetadataCache(
    @PrimaryKey
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    @ColumnInfo(name = "artist_name")
    val artistName: String? = null,
    @ColumnInfo(name = "profile_json")
    val profileJson: String? = null,
    @ColumnInfo(name = "top_songs_json")
    val topSongsJson: String? = null,
    @ColumnInfo(name = "albums_json")
    val albumsJson: String? = null,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {

    fun getArtistInfo(): ArtistInfo2? = profileJson?.let { Companion.gson.fromJson(it, ArtistInfo2::class.java) }

    fun getTopSongs(): List<Child>? {
        if (topSongsJson.isNullOrEmpty()) {
            return null
        }
        val type = object : TypeToken<List<Child>>() {}.type
        return Companion.gson.fromJson(topSongsJson, type)
    }

    fun getAlbums(): List<AlbumID3>? {
        if (albumsJson.isNullOrEmpty()) {
            return null
        }
        val type = object : TypeToken<List<AlbumID3>>() {}.type
        return Companion.gson.fromJson(albumsJson, type)
    }

    companion object {
        private val gson: Gson = GsonBuilder()
            .registerTypeAdapter(
                Date::class.java,
                JsonSerializer<Date> { src, _, _ ->
                    src?.let { JsonPrimitive(it.time) } ?: JsonNull.INSTANCE
                }
            )
            .registerTypeAdapter(
                Date::class.java,
                JsonDeserializer<Date> { json, _, _ ->
                    if (json == null || json.isJsonNull) {
                        null
                    } else {
                        Date(json.asLong)
                    }
                }
            )
            .create()

        fun merge(
            existing: ArtistMetadataCache?,
            artist: ArtistID3?,
            info: ArtistInfo2?,
            topSongs: List<Child>?,
            albums: List<AlbumID3>?
        ): ArtistMetadataCache {
            val artistId = artist?.id ?: existing?.artistId
                ?: throw IllegalArgumentException("Artist id required for caching")
            val name = artist?.name ?: existing?.artistName
            val profileJson = info?.let { gson.toJson(it) } ?: existing?.profileJson
            val topSongsJson = topSongs?.let { gson.toJson(it) } ?: existing?.topSongsJson
            val albumsJson = albums?.let { gson.toJson(it) } ?: existing?.albumsJson

            return ArtistMetadataCache(
                artistId = artistId,
                artistName = name,
                profileJson = profileJson,
                topSongsJson = topSongsJson,
                albumsJson = albumsJson,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}