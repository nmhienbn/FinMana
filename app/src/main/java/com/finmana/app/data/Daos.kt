package com.finmana.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<MoneyTransaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: MoneyTransaction): Long

    @Update
    suspend fun update(transaction: MoneyTransaction)

    @Delete
    suspend fun delete(transaction: MoneyTransaction)

    @Query("SELECT * FROM transactions WHERE synced = 0 ORDER BY occurredAt ASC")
    suspend fun unsynced(): List<MoneyTransaction>

    @Query("UPDATE transactions SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}

@Dao
interface PatternDao {
    @Query("SELECT * FROM parse_patterns WHERE sourcePackage = :packageName OR sourcePackage = '*' ORDER BY sourcePackage DESC")
    suspend fun forPackage(packageName: String): List<ParsePattern>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pattern: ParsePattern)
}
