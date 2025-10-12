package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ChronologyDao;
import com.cappielloantonio.tempo.model.Chronology;

import java.util.Calendar;
import java.util.List;

public class ChronologyRepository {
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    public LiveData<List<Chronology>> getChronology(String server, long start, long end) {
        return chronologyDao.getAllFrom(start, end, server);
    }

    public void insert(Chronology item) {
        InsertThreadSafe insert = new InsertThreadSafe(chronologyDao, item);
        Thread thread = new Thread(insert);
        thread.start();
    }

    public void deleteAll() {
        DeleteAllThreadSafe deleteAll = new DeleteAllThreadSafe(chronologyDao);
        Thread thread = new Thread(deleteAll);
        thread.start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final ChronologyDao chronologyDao;
        private final Chronology item;

        public InsertThreadSafe(ChronologyDao chronologyDao, Chronology item) {
            this.chronologyDao = chronologyDao;
            this.item = item;
        }

        @Override
        public void run() {
            chronologyDao.insert(item);
        }
    }

    private static class DeleteAllThreadSafe implements Runnable {
        private final ChronologyDao chronologyDao;

        public DeleteAllThreadSafe(ChronologyDao chronologyDao) {
            this.chronologyDao = chronologyDao;
        }

        @Override
        public void run() {
            chronologyDao.deleteAll();
        }
    }
}
