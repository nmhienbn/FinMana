package com.finmana.app

import android.app.Application
import com.finmana.app.data.AppDatabase
import com.finmana.app.data.SettingsStore
import com.finmana.app.data.TransactionRepository
import com.finmana.app.google.GoogleSheetsSync
import com.finmana.app.parser.AiParser
import com.finmana.app.parser.BalanceParser

class FinManaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsStore(this) }
    val aiParser by lazy { AiParser(settings) }
    val balanceParser by lazy { BalanceParser(database.patternDao(), aiParser) }
    val sheetsSync by lazy { GoogleSheetsSync(this, settings) }
    val repository by lazy {
        TransactionRepository(database.transactionDao(), balanceParser, sheetsSync)
    }
}

