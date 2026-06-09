package com.finmana.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finmana.app.data.AppSettings
import com.finmana.app.data.MoneyTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinManaApplication

    val transactions = app.repository.transactions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val settings: StateFlow<AppSettings> = app.settings.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )
    val status = MutableStateFlow<String?>(null)

    fun update(transaction: MoneyTransaction, note: String, category: String) {
        viewModelScope.launch {
            app.repository.update(transaction.copy(note = note.trim(), category = category.trim()))
            status.value = "Đã cập nhật giao dịch"
        }
    }

    fun delete(transaction: MoneyTransaction) {
        viewModelScope.launch {
            app.repository.delete(transaction)
            status.value = "Đã xóa giao dịch"
        }
    }

    fun saveAi(apiUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            app.settings.saveAi(apiUrl, apiKey, model)
            status.value = "Đã lưu cấu hình AI"
        }
    }

    fun sync() {
        viewModelScope.launch {
            status.value = "Đang đồng bộ..."
            status.value = runCatching {
                val count = app.repository.sync()
                "Đã đồng bộ $count giao dịch"
            }.getOrElse { "Không thể đồng bộ: ${it.message}" }
        }
    }

    fun clearStatus() {
        status.value = null
    }
}
