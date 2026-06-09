package com.finmana.app.data;

import androidx.room.TypeConverter;

public class Converters {
    @TypeConverter
    public String fromType(TransactionType value) {
        return value.name();
    }

    @TypeConverter
    public TransactionType toType(String value) {
        return TransactionType.valueOf(value);
    }
}
