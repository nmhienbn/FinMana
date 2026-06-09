package com.finmana.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "categories",
    indices = @Index(value = {"name", "typeStr"}, unique = true)
)
public class Category {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String name;
    @NonNull
    public String typeStr;
    public boolean synced;

    public Category() {
        this.name = "";
        this.typeStr = "";
        this.synced = false;
    }

    public Category(@NonNull String name, @NonNull TransactionType type) {
        this.name = name;
        this.typeStr = type.name();
        this.synced = false;
    }

    public TransactionType getType() {
        return TransactionType.valueOf(typeStr);
    }
}
