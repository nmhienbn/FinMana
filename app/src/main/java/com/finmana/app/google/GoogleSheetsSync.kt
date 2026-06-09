package com.finmana.app.google

import android.accounts.Account
import android.content.Context
import com.finmana.app.data.MoneyTransaction
import com.finmana.app.data.SettingsStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GoogleSheetsSync(
    private val context: Context,
    private val settingsStore: SettingsStore
) {
    private val transactionSheet = "Transactions"
    private val client = OkHttpClient()
    private val scopes = listOf(
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/spreadsheets"
    )

    suspend fun ensureWorkspace(): Pair<String, String> = withContext(Dispatchers.IO) {
        val current = settingsStore.get()
        val token = token()
        if (current.driveFolderId.isNotBlank() && current.spreadsheetId.isNotBlank()) {
            prepareTransactionSheet(token, current.spreadsheetId)
            return@withContext current.driveFolderId to current.spreadsheetId
        }
        val folderId = createDriveFile(
            token,
            JSONObject()
                .put("name", "FinMana")
                .put("mimeType", "application/vnd.google-apps.folder")
        )
        val sheetId = createDriveFile(
            token,
            JSONObject()
                .put("name", "Giao dịch FinMana")
                .put("mimeType", "application/vnd.google-apps.spreadsheet")
                .put("parents", JSONArray().put(folderId))
        )
        prepareTransactionSheet(token, sheetId)
        settingsStore.saveGoogle(folderId, sheetId)
        folderId to sheetId
    }

    suspend fun append(transaction: MoneyTransaction) = withContext(Dispatchers.IO) {
        val (_, sheetId) = ensureWorkspace()
        val token = token()
        val row = JSONArray(listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(transaction.occurredAt)),
            transaction.type.name,
            transaction.amount,
            transaction.category,
            transaction.note,
            transaction.sourceName,
            transaction.parser,
            transaction.sourcePackage,
            transaction.rawNotification,
            transaction.id
        ))
        val range = URLEncoder.encode("$transactionSheet!A:J", "UTF-8")
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/$range:append" +
            "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        post(token, url, JSONObject().put("values", JSONArray().put(row)))
    }

    private fun prepareTransactionSheet(token: String, sheetId: String) {
        val metadata = get(
            token,
            "https://sheets.googleapis.com/v4/spreadsheets/$sheetId?fields=sheets.properties.title"
        )
        val sheets = metadata.getJSONArray("sheets")
        val exists = (0 until sheets.length()).any { index ->
            sheets.getJSONObject(index).getJSONObject("properties").getString("title") == transactionSheet
        }
        if (!exists) {
            post(
                token,
                "https://sheets.googleapis.com/v4/spreadsheets/$sheetId:batchUpdate",
                JSONObject().put(
                    "requests",
                    JSONArray().put(
                        JSONObject().put(
                            "addSheet",
                            JSONObject().put(
                                "properties",
                                JSONObject().put("title", transactionSheet)
                            )
                        )
                    )
                )
            )
        }
        putValues(
            token,
            sheetId,
            "$transactionSheet!A1:J1",
            JSONArray().put(JSONArray(listOf(
                "Thời gian", "Loại", "Số tiền", "Danh mục", "Ghi chú",
                "Nguồn", "Parser", "Package", "Thông báo gốc", "ID"
            )))
        )
    }

    private fun token(): String {
        val signedIn = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Bạn cần đăng nhập Google trước.")
        val account = signedIn.account
            ?: Account(signedIn.email.orEmpty(), GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)
        return GoogleAuthUtil.getToken(context, account, "oauth2:${scopes.joinToString(" ")}")
    }

    private fun createDriveFile(token: String, metadata: JSONObject): String {
        val result = post(
            token,
            "https://www.googleapis.com/drive/v3/files?fields=id",
            metadata
        )
        return result.getString("id")
    }

    private fun putValues(token: String, sheetId: String, rangeValue: String, values: JSONArray) {
        val range = URLEncoder.encode(rangeValue, "UTF-8")
        val body = JSONObject()
            .put("range", rangeValue)
            .put("majorDimension", "ROWS")
            .put("values", values)
        request(
            token,
            Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/$range?valueInputOption=RAW")
                .put(body.toString().toRequestBody("application/json".toMediaType()))
        )
    }

    private fun post(token: String, url: String, body: JSONObject): JSONObject = request(
        token,
        Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType()))
    )

    private fun get(token: String, url: String): JSONObject = request(
        token,
        Request.Builder().url(url).get()
    )

    private fun request(token: String, builder: Request.Builder): JSONObject {
        val request = builder.header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Google API HTTP ${response.code}: $body")
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }
}
