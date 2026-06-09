package com.finmana.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
    entities = {MoneyTransaction.class, ParsePattern.class, Category.class, ExcludedApp.class},
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TransactionDao transactionDao();
    public abstract PatternDao patternDao();
    public abstract CategoryDao categoryDao();
    public abstract ExcludedAppDao excludedAppDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase create(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "finmana.db"
                    ).fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
