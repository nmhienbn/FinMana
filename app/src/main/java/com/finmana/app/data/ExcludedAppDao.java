package com.finmana.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ExcludedAppDao {
    @Query("SELECT * FROM excluded_apps ORDER BY appName")
    LiveData<List<ExcludedApp>> observeAll();

    @Query("SELECT packageName FROM excluded_apps")
    List<String> getExcludedPackages();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ExcludedApp app);

    @Query("DELETE FROM excluded_apps WHERE packageName = :packageName")
    void delete(String packageName);

    @Query("SELECT DISTINCT sourcePackage, sourceName FROM transactions ORDER BY sourceName")
    List<SourceAppInfo> getKnownApps();

    class SourceAppInfo {
        public String sourcePackage;
        public String sourceName;
    }
}
