package com.finmana.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromType(value: TransactionType): String = value.name
    @TypeConverter fun toType(value: String): TransactionType = TransactionType.valueOf(value)
}

@Database(
    entities = [MoneyTransaction::class, ParsePattern::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun patternDao(): PatternDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "finmana.db"
        ).build()
    }
}

