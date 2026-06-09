package com.finmana.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "excluded_apps",
    indices = @Index(value = "packageName", unique = true)
)
public class ExcludedApp {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String packageName;
    @NonNull
    public String appName;

    public ExcludedApp() {
        this.packageName = "";
        this.appName = "";
    }

    public ExcludedApp(@NonNull String packageName, @NonNull String appName) {
        this.packageName = packageName;
        this.appName = appName;
    }
}
