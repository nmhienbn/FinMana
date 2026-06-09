package com.finmana.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PatternDao {
    @Query("SELECT * FROM parse_patterns WHERE sourcePackage = :packageName OR sourcePackage = '*' ORDER BY sourcePackage DESC")
    List<ParsePattern> forPackage(String packageName);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ParsePattern pattern);
}
