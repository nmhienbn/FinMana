package com.finmana.app.data;

public class AppSettings {
    public final String apiUrl;
    public final String apiKey;
    public final String aiModel;
    public final String driveFolderId;
    public final String spreadsheetId;

    public AppSettings() {
        this("", "", "", "", "");
    }

    public AppSettings(String apiUrl, String apiKey, String aiModel,
                       String driveFolderId, String spreadsheetId) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.aiModel = aiModel;
        this.driveFolderId = driveFolderId;
        this.spreadsheetId = spreadsheetId;
    }
}
