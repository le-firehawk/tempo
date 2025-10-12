package com.cappielloantonio.tempo.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.ArtistMetadataCache;

@Dao
public interface ArtistMetadataDao {
    @Query("SELECT * FROM artist_metadata_cache WHERE artist_id = :artistId LIMIT 1")
    ArtistMetadataCache getOne(String artistId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ArtistMetadataCache cache);

    @Query("DELETE FROM artist_metadata_cache WHERE artist_id = :artistId")
    void delete(String artistId);

    @Query("DELETE FROM artist_metadata_cache")
    void deleteAll();
}