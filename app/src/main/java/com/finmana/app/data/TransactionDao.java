package com.finmana.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY occurredAt DESC")
    LiveData<List<MoneyTransaction>> observeActive();

    @Query("SELECT * FROM transactions WHERE deleted = 1 ORDER BY deletedAt DESC")
    LiveData<List<MoneyTransaction>> observeDeleted();

    @Query("SELECT * FROM transactions WHERE synced = 0 ORDER BY occurredAt ASC")
    List<MoneyTransaction> unsynced();

    @Query("SELECT * FROM transactions WHERE syncId = :syncId LIMIT 1")
    MoneyTransaction getBySyncId(String syncId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(MoneyTransaction transaction);

    @Update
    void update(MoneyTransaction transaction);

    @Delete
    void delete(MoneyTransaction transaction);

    @Query("UPDATE transactions SET deleted = 1, deletedAt = :timestamp, synced = 0 WHERE id = :id")
    void softDelete(long id, long timestamp);

    @Query("UPDATE transactions SET deleted = 0, deletedAt = NULL, synced = 0 WHERE id = :id")
    void restore(long id);

    @Query("DELETE FROM transactions WHERE deleted = 1 AND deletedAt < :olderThan")
    void purgeOldDeleted(long olderThan);

    @Query("SELECT * FROM transactions WHERE deleted = 1 ORDER BY deletedAt DESC")
    List<MoneyTransaction> getDeletedList();

    @Query("SELECT * FROM transactions WHERE occurredAt >= :from AND occurredAt < :to AND deleted = 0")
    List<MoneyTransaction> getByTimeRange(long from, long to);

    @Query("UPDATE transactions SET synced = 0 WHERE synced = 1")
    void resetAllSynced();
}
