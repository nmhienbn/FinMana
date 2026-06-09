package com.finmana.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "transactions",
    indices = {
        @Index(value = "fingerprint", unique = true),
        @Index(value = "syncId", unique = true)
    }
)
public class MoneyTransaction {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String syncId;
    public long amount;
    public TransactionType type;
    public long occurredAt;
    @NonNull
    public String sourcePackage;
    @NonNull
    public String sourceName;
    @NonNull
    public String rawNotification;
    @NonNull
    public String note;
    @NonNull
    public String category;
    @NonNull
    public String parser;
    @NonNull
    public String fingerprint;
    public boolean synced;
    public boolean deleted;
    @Nullable
    public Long deletedAt;

    public MoneyTransaction() {
        this.syncId = java.util.UUID.randomUUID().toString();
        this.sourcePackage = "";
        this.sourceName = "";
        this.rawNotification = "";
        this.note = "";
        this.category = "Chưa phân loại";
        this.parser = "";
        this.fingerprint = "";
        this.synced = false;
        this.deleted = false;
        this.deletedAt = null;
    }
}
