package com.finmana.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { INCOME, EXPENSE }

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["fingerprint"], unique = true)]
)
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val occurredAt: Long,
    val sourcePackage: String,
    val sourceName: String,
    val rawNotification: String,
    val note: String = "",
    val category: String = "Chưa phân loại",
    val parser: String,
    val fingerprint: String,
    val synced: Boolean = false
)

@Entity(
    tableName = "parse_patterns",
    indices = [Index(value = ["sourcePackage", "regex"], unique = true)]
)
data class ParsePattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val regex: String,
    val amountGroup: Int = 1,
    val directionGroup: Int? = null,
    val incomeWords: String = "cong,nhan,vao,+",
    val expenseWords: String = "tru,chi,ra,-",
    val learnedByAi: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ParsedBalance(
    val amount: Long,
    val type: TransactionType,
    val sourceName: String,
    val parser: String
)

data class ChartPoint(val label: String, val income: Long, val expense: Long)

