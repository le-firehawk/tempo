package com.cappielloantonio.tempo.repository;

import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ArtistMetadataDao;
import com.cappielloantonio.tempo.model.ArtistMetadataCache;

public class ArtistMetadataRepository {
    private final ArtistMetadataDao artistMetadataDao = AppDatabase.getInstance().artistMetadataDao();

    public ArtistMetadataCache get(String artistId) {
        if (artistId == null) {
            return null;
        }
        GetArtistMetadataThreadSafe getArtistMetadataThreadSafe = new GetArtistMetadataThreadSafe(artistMetadataDao, artistId);
        Thread thread = new Thread(getArtistMetadataThreadSafe);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return getArtistMetadataThreadSafe.getCache();
    }

    public void insert(ArtistMetadataCache cache) {
        if (cache == null) {
            return;
        }
        InsertArtistMetadataThreadSafe insertArtistMetadataThreadSafe = new InsertArtistMetadataThreadSafe(artistMetadataDao, cache);
        Thread thread = new Thread(insertArtistMetadataThreadSafe);
        thread.start();
    }

    public void delete(String artistId) {
        if (artistId == null) {
            return;
        }
        DeleteArtistMetadataThreadSafe deleteArtistMetadataThreadSafe = new DeleteArtistMetadataThreadSafe(artistMetadataDao, artistId);
        Thread thread = new Thread(deleteArtistMetadataThreadSafe);
        thread.start();
    }

    public void deleteAll() {
        DeleteAllArtistMetadataThreadSafe deleteAllArtistMetadataThreadSafe = new DeleteAllArtistMetadataThreadSafe(artistMetadataDao);
        Thread thread = new Thread(deleteAllArtistMetadataThreadSafe);
        thread.start();
    }

    private static class GetArtistMetadataThreadSafe implements Runnable {
        private final ArtistMetadataDao dao;
        private final String artistId;
        private ArtistMetadataCache cache;

        private GetArtistMetadataThreadSafe(ArtistMetadataDao dao, String artistId) {
            this.dao = dao;
            this.artistId = artistId;
        }

        @Override
        public void run() {
            cache = dao.getOne(artistId);
        }

        private ArtistMetadataCache getCache() {
            return cache;
        }
    }

    private static class InsertArtistMetadataThreadSafe implements Runnable {
        private final ArtistMetadataDao dao;
        private final ArtistMetadataCache cache;

        private InsertArtistMetadataThreadSafe(ArtistMetadataDao dao, ArtistMetadataCache cache) {
            this.dao = dao;
            this.cache = cache;
        }

        @Override
        public void run() {
            dao.insert(cache);
        }
    }

    private static class DeleteArtistMetadataThreadSafe implements Runnable {
        private final ArtistMetadataDao dao;
        private final String artistId;

        private DeleteArtistMetadataThreadSafe(ArtistMetadataDao dao, String artistId) {
            this.dao = dao;
            this.artistId = artistId;
        }

        @Override
        public void run() {
            dao.delete(artistId);
        }
    }

    private static class DeleteAllArtistMetadataThreadSafe implements Runnable {
        private final ArtistMetadataDao dao;

        private DeleteAllArtistMetadataThreadSafe(ArtistMetadataDao dao) {
            this.dao = dao;
        }

        @Override
        public void run() {
            dao.deleteAll();
        }
    }
}