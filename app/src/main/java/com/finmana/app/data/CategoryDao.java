package com.finmana.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY typeStr, name")
    LiveData<List<Category>> observeAll();

    @Query("SELECT * FROM categories WHERE typeStr = :type ORDER BY name")
    LiveData<List<Category>> observeByType(String type);

    @Query("SELECT * FROM categories WHERE typeStr = :type ORDER BY name")
    List<Category> getByType(String type);

    @Query("SELECT * FROM categories ORDER BY name")
    List<Category> getAllList();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<Category> categories);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Category category);

    @Query("DELETE FROM categories WHERE name = :name AND typeStr = :type")
    void deleteByNameAndType(String name, String type);

    @Query("UPDATE categories SET synced = 1 WHERE name = :name AND typeStr = :type")
    void markSynced(String name, String type);
}
