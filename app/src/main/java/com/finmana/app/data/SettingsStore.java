package com.finmana.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

public class SettingsStore {
    private static final String PREFS_NAME = "settings";
    private final SharedPreferences prefs;
    public final MutableLiveData<AppSettings> liveData = new MutableLiveData<>();

    public SettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        liveData.setValue(load());
    }

    public AppSettings get() {
        return load();
    }

    public void saveAi(String apiUrl, String apiKey, String model) {
        prefs.edit()
            .putString("api_url", apiUrl.trim())
            .putString("api_key", apiKey.trim())
            .putString("ai_model", model.trim())
            .apply();
        liveData.postValue(load());
    }

    public void saveGoogle(String folderId) {
        prefs.edit()
            .putString("drive_folder_id", folderId)
            .apply();
        liveData.postValue(load());
    }

    public String getSpreadsheetIdForYear(int year) {
        return prefs.getString("sheet_" + year, "");
    }

    public void saveSpreadsheetIdForYear(int year, String sheetId) {
        prefs.edit().putString("sheet_" + year, sheetId).apply();
    }

    private AppSettings load() {
        return new AppSettings(
            prefs.getString("api_url", ""),
            prefs.getString("api_key", ""),
            prefs.getString("ai_model", ""),
            prefs.getString("drive_folder_id", ""),
            ""
        );
    }
}
