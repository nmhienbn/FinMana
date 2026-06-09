package com.finmana.app.data

import com.finmana.app.google.GoogleSheetsSync
import com.finmana.app.parser.BalanceParser
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val parser: BalanceParser,
    private val sheetsSync: GoogleSheetsSync
) {
    val transactions: Flow<List<MoneyTransaction>> = transactionDao.observeAll()

    suspend fun ingest(packageName: String, appName: String, text: String, occurredAt: Long) {
        val parsed = parser.parse(packageName, appName, text) ?: return
        val fingerprint = sha256("$packageName|$text|${occurredAt / 60_000}")
        transactionDao.insert(
            MoneyTransaction(
                amount = parsed.amount,
                type = parsed.type,
                occurredAt = occurredAt,
                sourcePackage = packageName,
                sourceName = parsed.sourceName,
                rawNotification = text,
                parser = parsed.parser,
                fingerprint = fingerprint
            )
        )
    }

    suspend fun update(transaction: MoneyTransaction) = transactionDao.update(transaction.copy(synced = false))

    suspend fun delete(transaction: MoneyTransaction) = transactionDao.delete(transaction)

    suspend fun sync(): Int {
        var count = 0
        transactionDao.unsynced().forEach { transaction ->
            sheetsSync.append(transaction)
            transactionDao.markSynced(transaction.id)
            count++
        }
        return count
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
