package com.finmana.app.parser

import com.finmana.app.data.ParsePattern
import com.finmana.app.data.ParsedBalance
import com.finmana.app.data.SettingsStore
import com.finmana.app.data.TransactionType
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AiParseResult(val balance: ParsedBalance, val learnedPattern: ParsePattern?)

class AiParser(private val settingsStore: SettingsStore) {
    private val client = OkHttpClient()

    suspend fun parse(packageName: String, appName: String, notification: String): AiParseResult? =
        withContext(Dispatchers.IO) {
            val settings = settingsStore.get()
            if (settings.apiUrl.isBlank() || settings.apiKey.isBlank() || settings.aiModel.isBlank()) {
                return@withContext null
            }
            val prompt = """
                Phân tích thông báo ví/ngân hàng sau. Chỉ trả JSON thuần:
                {"isBalance":true,"amount":150000,"type":"INCOME|EXPENSE","regex":"regex có group 1 là số tiền"}
                Regex phải khái quát, Java-compatible, không chứa thông tin cá nhân. Nếu không phải biến động số dư: {"isBalance":false}.
                Package: $packageName
                App: $appName
                Thông báo: $notification
            """.trimIndent()
            val body = JSONObject()
                .put("model", settings.aiModel)
                .put("temperature", 0)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    )
                )
                .toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(settings.apiUrl)
                .header("Authorization", "Bearer ${settings.apiKey}")
                .post(body)
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("AI HTTP ${response.code}")
                    val envelope = JSONObject(response.body?.string().orEmpty())
                    val content = envelope.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                    val jsonText = content.substringAfter('{', "").substringBeforeLast('}', "")
                    if (jsonText.isBlank()) return@use null
                    val json = JSONObject("{$jsonText}")
                    if (!json.optBoolean("isBalance", false)) return@use null
                    val amount = json.optLong("amount").takeIf { it > 0 } ?: return@use null
                    val type = runCatching {
                        TransactionType.valueOf(json.getString("type").uppercase())
                    }.getOrNull() ?: return@use null
                    val regex = json.optString("regex").takeIf { it.isNotBlank() }
                    AiParseResult(
                        ParsedBalance(amount, type, appName, "ai"),
                        regex?.let {
                            ParsePattern(
                                sourcePackage = packageName,
                                regex = it,
                                amountGroup = 1,
                                learnedByAi = true
                            )
                        }
                    )
                }
            }.getOrNull()
        }
}

