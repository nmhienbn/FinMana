package com.finmana.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val apiUrl: String = "",
    val apiKey: String = "",
    val aiModel: String = "",
    val driveFolderId: String = "",
    val spreadsheetId: String = ""
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val apiUrl = stringPreferencesKey("api_url")
        val apiKey = stringPreferencesKey("api_key")
        val aiModel = stringPreferencesKey("ai_model")
        val folderId = stringPreferencesKey("drive_folder_id")
        val spreadsheetId = stringPreferencesKey("spreadsheet_id")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiUrl = p[Keys.apiUrl].orEmpty(),
            apiKey = p[Keys.apiKey].orEmpty(),
            aiModel = p[Keys.aiModel].orEmpty(),
            driveFolderId = p[Keys.folderId].orEmpty(),
            spreadsheetId = p[Keys.spreadsheetId].orEmpty()
        )
    }

    suspend fun get(): AppSettings = flow.first()

    suspend fun saveAi(apiUrl: String, apiKey: String, model: String) {
        context.dataStore.edit {
            it[Keys.apiUrl] = apiUrl.trim()
            it[Keys.apiKey] = apiKey.trim()
            it[Keys.aiModel] = model.trim()
        }
    }

    suspend fun saveGoogle(folderId: String, spreadsheetId: String) {
        context.dataStore.edit {
            it[Keys.folderId] = folderId
            it[Keys.spreadsheetId] = spreadsheetId
        }
    }
}

