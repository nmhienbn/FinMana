package com.finmana.app.google;

import android.accounts.Account;
import android.content.Context;

import com.finmana.app.data.Category;
import com.finmana.app.data.MoneyTransaction;
import com.finmana.app.data.SettingsStore;
import com.finmana.app.data.TransactionType;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleSheetsSync {
    private static final String CATEGORIES_SHEET = "Danh mục";
    private static final String[] MONTH_LABELS = {
        "01", "02", "03", "04", "05", "06",
        "07", "08", "09", "10", "11", "12"
    };
    private static final MediaType JSON_MT = MediaType.get("application/json");
    private static final String[] SCOPES = {
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/spreadsheets"
    };
    private static final String[] HEADERS = {
        "Thời gian", "Loại", "Số tiền", "Danh mục",
        "Ghi chú", "Nguồn", "Parser", "Package", "Thông báo gốc", "Mã ĐB"
    };
    private static final int COL_TIME = 0, COL_TYPE = 1, COL_AMOUNT = 2,
        COL_CATEGORY = 3, COL_NOTE = 4, COL_SOURCE = 5, COL_PARSER = 6,
        COL_PACKAGE = 7, COL_RAW = 8, COL_SYNCID = 9;
    private static final String[] CATEGORY_HEADERS = {"Tên danh mục", "Loại"};

    private static String sheetRange(String sheetName, String cellRange) {
        return "'" + sheetName + "'!" + cellRange;
    }

    private final Context context;
    public final SettingsStore settingsStore;
    private final OkHttpClient client = new OkHttpClient();

    public GoogleSheetsSync(Context context, SettingsStore settingsStore) {
        this.context = context;
        this.settingsStore = settingsStore;
    }

    public String token() throws Exception {
        GoogleSignInAccount signedIn = GoogleSignIn.getLastSignedInAccount(context);
        if (signedIn == null) throw new IllegalStateException("Bạn cần đăng nhập Google trước.");
        Account account = signedIn.getAccount();
        if (account == null) account = new Account(signedIn.getEmail(), GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        StringBuilder scopeStr = new StringBuilder("oauth2:");
        for (int i = 0; i < SCOPES.length; i++) {
            if (i > 0) scopeStr.append(" ");
            scopeStr.append(SCOPES[i]);
        }
        return GoogleAuthUtil.getToken(context, account, scopeStr.toString());
    }

    public String ensureFolder(String token) throws Exception {
        String existing = settingsStore.get().driveFolderId;
        if (!existing.isEmpty()) {
            try {
                JSONObject f = get(token, "https://www.googleapis.com/drive/v3/files/" + existing + "?fields=id,trashed");
                if (f.optBoolean("trashed", false)) throw new IOException("trashed");
                return existing;
            } catch (IOException e) {
                settingsStore.saveGoogle("");
            }
        }
        String query = URLEncoder.encode("name='FinMana' and mimeType='application/vnd.google-apps.folder' and trashed=false", "UTF-8");
        JSONObject result = get(token, "https://www.googleapis.com/drive/v3/files?q=" + query + "&spaces=drive&fields=files(id)&pageSize=1");
        JSONArray files = result.optJSONArray("files");
        if (files != null && files.length() > 0) {
            String folderId = files.getJSONObject(0).getString("id");
            settingsStore.saveGoogle(folderId);
            return folderId;
        }
        String folderId = createDriveFile(token, new JSONObject()
            .put("name", "FinMana")
            .put("mimeType", "application/vnd.google-apps.folder"));
        settingsStore.saveGoogle(folderId);
        return folderId;
    }

    public boolean ensureYearlySpreadsheet(String token, int year) throws Exception {
        String existing = settingsStore.getSpreadsheetIdForYear(year);
        if (!existing.isEmpty()) {
            try {
                JSONObject f = get(token, "https://www.googleapis.com/drive/v3/files/" + existing + "?fields=id,trashed");
                if (f.optBoolean("trashed", false)) throw new IOException("trashed");
                ensureAllSheets(token, existing);
                return false;
            } catch (IOException e) {
                settingsStore.saveSpreadsheetIdForYear(year, "");
            }
        }

        String folderId = ensureFolder(token);
        String spreadsheetName = "FinMana " + year;
        String query = URLEncoder.encode("name='" + spreadsheetName + "' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false", "UTF-8");
        JSONObject search = get(token, "https://www.googleapis.com/drive/v3/files?q=" + query + "&spaces=drive&fields=files(id)&pageSize=1");
        JSONArray files = search.optJSONArray("files");
        if (files != null && files.length() > 0) {
            String foundId = files.getJSONObject(0).getString("id");
            settingsStore.saveSpreadsheetIdForYear(year, foundId);
            ensureAllSheets(token, foundId);
            return false;
        }

        String spreadsheetId = createDriveFile(token, new JSONObject()
            .put("name", "FinMana " + year)
            .put("mimeType", "application/vnd.google-apps.spreadsheet")
            .put("parents", new JSONArray().put(folderId)));
        settingsStore.saveSpreadsheetIdForYear(year, spreadsheetId);

        renameDefaultSheet(token, spreadsheetId, "01");

        JSONArray addRequests = new JSONArray();
        for (int m = 2; m <= 12; m++) {
            addRequests.put(new JSONObject().put("addSheet", new JSONObject().put(
                "properties", new JSONObject().put("title", MONTH_LABELS[m - 1]))));
        }
        addRequests.put(new JSONObject().put("addSheet", new JSONObject().put(
            "properties", new JSONObject().put("title", CATEGORIES_SHEET))));
        post(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
            new JSONObject().put("requests", addRequests));

        JSONArray headerRow = new JSONArray();
        for (String h : HEADERS) headerRow.put(h);
        JSONArray allHeaders = new JSONArray();
        for (int m = 1; m <= 12; m++) {
            allHeaders.put(new JSONObject()
                .put("range", sheetRange(MONTH_LABELS[m - 1], "A1:J1"))
                .put("majorDimension", "ROWS")
                .put("values", new JSONArray().put(headerRow)));
        }
        JSONArray catHeader = new JSONArray();
        for (String h : CATEGORY_HEADERS) catHeader.put(h);
        allHeaders.put(new JSONObject()
            .put("range", sheetRange(CATEGORIES_SHEET, "A1:B1"))
            .put("majorDimension", "ROWS")
            .put("values", new JSONArray().put(catHeader)));
        post(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                + "/values:batchUpdate",
            new JSONObject().put("valueInputOption", "RAW").put("data", allHeaders));

        JSONObject metadata = getSheetProperties(token, spreadsheetId);
        JSONArray sheets = metadata.getJSONArray("sheets");
        JSONArray formatRequests = new JSONArray();
        for (int i = 0; i < sheets.length(); i++) {
            JSONObject props = sheets.getJSONObject(i).getJSONObject("properties");
            String title = props.getString("title");
            int sheetId = props.getInt("sheetId");
            boolean isMonthly = false;
            for (String ml : MONTH_LABELS) {
                if (ml.equals(title)) { isMonthly = true; break; }
            }
            if (isMonthly) addMonthlyFormatRequests(formatRequests, sheetId);
            if (CATEGORIES_SHEET.equals(title)) addCategoriesFormatRequests(formatRequests, sheetId);
        }
        if (formatRequests.length() > 0) {
            post(token,
                "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
                new JSONObject().put("requests", formatRequests));
        }

        ensureCategoriesSheet(token, spreadsheetId);
        return true;
    }

    private void ensureAllSheets(String token, String spreadsheetId) throws Exception {
        JSONObject metadata = getSheetProperties(token, spreadsheetId);
        JSONArray existingSheets = metadata.getJSONArray("sheets");
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (int i = 0; i < existingSheets.length(); i++) {
            String t = existingSheets.getJSONObject(i).getJSONObject("properties").getString("title");
            existing.add(t);
        }

        JSONArray addRequests = new JSONArray();
        java.util.List<String> needHeaders = new java.util.ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String title = MONTH_LABELS[m - 1];
            if (!existing.contains(title)) {
                addRequests.put(new JSONObject().put("addSheet", new JSONObject().put(
                    "properties", new JSONObject().put("title", title))));
                needHeaders.add(title);
            }
        }
        if (!existing.contains(CATEGORIES_SHEET)) {
            addRequests.put(new JSONObject().put("addSheet", new JSONObject().put(
                "properties", new JSONObject().put("title", CATEGORIES_SHEET))));
        }

        if (addRequests.length() > 0) {
            post(token,
                "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
                new JSONObject().put("requests", addRequests));
        }

        JSONArray headerRow = new JSONArray();
        for (String h : HEADERS) headerRow.put(h);
        JSONArray allHeaders = new JSONArray();
        for (String title : needHeaders) {
            allHeaders.put(new JSONObject()
                .put("range", sheetRange(title, "A1:J1"))
                .put("majorDimension", "ROWS")
                .put("values", new JSONArray().put(headerRow)));
        }
        if (!existing.contains(CATEGORIES_SHEET)) {
            JSONArray catHeader = new JSONArray();
            for (String h : CATEGORY_HEADERS) catHeader.put(h);
            allHeaders.put(new JSONObject()
                .put("range", sheetRange(CATEGORIES_SHEET, "A1:B1"))
                .put("majorDimension", "ROWS")
                .put("values", new JSONArray().put(catHeader)));
        }
        if (allHeaders.length() > 0) {
            post(token,
                "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                    + "/values:batchUpdate",
                new JSONObject().put("valueInputOption", "RAW").put("data", allHeaders));
        }

        metadata = getSheetProperties(token, spreadsheetId);
        existingSheets = metadata.getJSONArray("sheets");
        JSONArray formatRequests = new JSONArray();
        for (int i = 0; i < existingSheets.length(); i++) {
            JSONObject props = existingSheets.getJSONObject(i).getJSONObject("properties");
            String title = props.getString("title");
            int sheetId = props.getInt("sheetId");
            boolean isMonthly = false;
            for (String ml : MONTH_LABELS) {
                if (ml.equals(title)) { isMonthly = true; break; }
            }
            if (isMonthly && needHeaders.contains(title)) {
                addMonthlyFormatRequests(formatRequests, sheetId);
            }
            if (CATEGORIES_SHEET.equals(title) && !existing.contains(CATEGORIES_SHEET)) {
                addCategoriesFormatRequests(formatRequests, sheetId);
            }
        }
        if (formatRequests.length() > 0) {
            post(token,
                "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
                new JSONObject().put("requests", formatRequests));
        }
    }

    public void ensureMonthlySheet(String token, String spreadsheetId, int month) throws Exception {
        String title = MONTH_LABELS[month - 1];
        JSONObject metadata = getSheetProperties(token, spreadsheetId);
        JSONArray sheets = metadata.getJSONArray("sheets");
        for (int i = 0; i < sheets.length(); i++) {
            String t = sheets.getJSONObject(i).getJSONObject("properties").getString("title");
            if (t.equals(title)) return;
        }
        post(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
            new JSONObject().put("requests", new JSONArray().put(
                new JSONObject().put("addSheet", new JSONObject().put(
                    "properties", new JSONObject().put("title", title))))));
        ensureHeaders(token, spreadsheetId, title);
        setupMonthlySheetRules(token, spreadsheetId, title);
    }

    public void ensureCategoriesSheet(String token, String spreadsheetId) throws Exception {
        JSONObject metadata = getSheetProperties(token, spreadsheetId);
        JSONArray sheets = metadata.getJSONArray("sheets");
        for (int i = 0; i < sheets.length(); i++) {
            String t = sheets.getJSONObject(i).getJSONObject("properties").getString("title");
            if (t.equals(CATEGORIES_SHEET)) return;
        }
        post(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
            new JSONObject().put("requests", new JSONArray().put(
                new JSONObject().put("addSheet", new JSONObject().put(
                    "properties", new JSONObject().put("title", CATEGORIES_SHEET))))));
        JSONArray headerRow = new JSONArray();
        for (String h : CATEGORY_HEADERS) headerRow.put(h);
        putValues(token, spreadsheetId, sheetRange(CATEGORIES_SHEET, "A1:B1"),
            new JSONArray().put(headerRow));
        setupCategoriesSheetRules(token, spreadsheetId);
    }

    private void addMonthlyFormatRequests(JSONArray requests, int sheetId) throws Exception {
        requests.put(new JSONObject()
            .put("updateSheetProperties", new JSONObject()
                .put("properties", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("gridProperties", new JSONObject()
                        .put("frozenRowCount", 1)))
                .put("fields", "gridProperties.frozenRowCount")));

        requests.put(makeDataValidation(sheetId, COL_TYPE, COL_TYPE + 1,
            "ONE_OF_LIST", "INCOME", "EXPENSE"));

        requests.put(makeCategoryValidation(sheetId, COL_CATEGORY, COL_CATEGORY + 1));

        JSONObject numberFormat = new JSONObject()
            .put("type", "NUMBER")
            .put("pattern", "#,##0");
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 1)
                    .put("startColumnIndex", COL_AMOUNT)
                    .put("endColumnIndex", COL_AMOUNT + 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", new JSONObject()
                        .put("numberFormat", numberFormat)))
                .put("fields", "userEnteredFormat.numberFormat")));

        JSONObject dateFormat = new JSONObject()
            .put("type", "DATE_TIME")
            .put("pattern", "yyyy-MM-dd HH:mm:ss");
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 1)
                    .put("startColumnIndex", COL_TIME)
                    .put("endColumnIndex", COL_TIME + 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", new JSONObject()
                        .put("numberFormat", dateFormat)))
                .put("fields", "userEnteredFormat.numberFormat")));

        JSONObject boldHeader = new JSONObject()
            .put("textFormat", new JSONObject().put("bold", true));
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 0)
                    .put("endRowIndex", 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", boldHeader))
                .put("fields", "userEnteredFormat.textFormat")));
    }

    private void addCategoriesFormatRequests(JSONArray requests, int sheetId) throws Exception {
        requests.put(new JSONObject()
            .put("updateSheetProperties", new JSONObject()
                .put("properties", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("gridProperties", new JSONObject()
                        .put("frozenRowCount", 1)))
                .put("fields", "gridProperties.frozenRowCount")));

        requests.put(makeDataValidation(sheetId, 1, 2,
            "ONE_OF_LIST", "INCOME", "EXPENSE"));

        JSONObject boldHeader = new JSONObject()
            .put("textFormat", new JSONObject().put("bold", true));
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 0)
                    .put("endRowIndex", 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", boldHeader))
                .put("fields", "userEnteredFormat.textFormat")));
    }

    private void setupMonthlySheetRules(String token, String spreadsheetId,
                                         String sheetTitle) throws Exception {
        int sheetId = getSheetIdByName(token, spreadsheetId, sheetTitle);

        JSONArray requests = new JSONArray();

        requests.put(new JSONObject()
            .put("updateSheetProperties", new JSONObject()
                .put("properties", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("gridProperties", new JSONObject()
                        .put("frozenRowCount", 1)))
                .put("fields", "gridProperties.frozenRowCount")));

        requests.put(makeDataValidation(sheetId, COL_TYPE, COL_TYPE + 1,
            "ONE_OF_LIST", "INCOME", "EXPENSE"));

        requests.put(makeCategoryValidation(sheetId, COL_CATEGORY, COL_CATEGORY + 1));

        JSONObject numberFormat = new JSONObject()
            .put("type", "NUMBER")
            .put("pattern", "#,##0");
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 1)
                    .put("startColumnIndex", COL_AMOUNT)
                    .put("endColumnIndex", COL_AMOUNT + 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", new JSONObject()
                        .put("numberFormat", numberFormat)))
                .put("fields", "userEnteredFormat.numberFormat")));

        JSONObject dateFormat = new JSONObject()
            .put("type", "DATE_TIME")
            .put("pattern", "yyyy-MM-dd HH:mm:ss");
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 1)
                    .put("startColumnIndex", COL_TIME)
                    .put("endColumnIndex", COL_TIME + 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", new JSONObject()
                        .put("numberFormat", dateFormat)))
                .put("fields", "userEnteredFormat.numberFormat")));

        JSONObject boldHeader = new JSONObject()
            .put("textFormat", new JSONObject().put("bold", true));
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 0)
                    .put("endRowIndex", 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", boldHeader))
                .put("fields", "userEnteredFormat.textFormat")));

        post(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
            new JSONObject().put("requests", requests));
    }

    private void setupCategoriesSheetRules(String token, String spreadsheetId) throws Exception {
        int sheetId = getSheetIdByName(token, spreadsheetId, CATEGORIES_SHEET);

        JSONArray requests = new JSONArray();

        requests.put(new JSONObject()
            .put("updateSheetProperties", new JSONObject()
                .put("properties", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("gridProperties", new JSONObject()
                        .put("frozenRowCount", 1)))
                .put("fields", "gridProperties.frozenRowCount")));

        requests.put(makeDataValidation(sheetId, 1, 2,
            "ONE_OF_LIST", "INCOME", "EXPENSE"));

        JSONObject boldHeader = new JSONObject()
            .put("textFormat", new JSONObject().put("bold", true));
        requests.put(new JSONObject()
            .put("repeatCell", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 0)
                    .put("endRowIndex", 1))
                .put("cell", new JSONObject()
                    .put("userEnteredFormat", boldHeader))
                .put("fields", "userEnteredFormat.textFormat")));

        post(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
            new JSONObject().put("requests", requests));
    }

    private JSONObject makeDataValidation(int sheetId, int startCol, int endCol,
                                           String type, String... values) throws Exception {
        JSONArray conditionValues = new JSONArray();
        for (String v : values) {
            conditionValues.put(new JSONObject().put("userEnteredValue", v));
        }
        return new JSONObject()
            .put("setDataValidation", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 1)
                    .put("startColumnIndex", startCol)
                    .put("endColumnIndex", endCol))
                .put("rule", new JSONObject()
                    .put("condition", new JSONObject()
                        .put("type", type)
                        .put("values", conditionValues))
                    .put("showCustomUi", true)
                    .put("strict", true)));
    }

    private JSONObject makeCategoryValidation(int sheetId, int startCol, int endCol) throws Exception {
        return new JSONObject()
            .put("setDataValidation", new JSONObject()
                .put("range", new JSONObject()
                    .put("sheetId", sheetId)
                    .put("startRowIndex", 1)
                    .put("startColumnIndex", startCol)
                    .put("endColumnIndex", endCol))
                .put("rule", new JSONObject()
                    .put("condition", new JSONObject()
                        .put("type", "ONE_OF_LIST")
                        .put("values", new JSONArray().put(
                            new JSONObject().put("userEnteredValue",
                                "INDIRECT(\"'Danh mục'!A2:A\"&COUNTA('Danh mục'!A:A))"))))
                    .put("showCustomUi", true)
                    .put("strict", false)));
    }

    private JSONObject getSheetProperties(String token, String spreadsheetId) throws Exception {
        return get(token,
            "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                + "?fields=sheets.properties(sheetId,title)");
    }

    private int getSheetIdByName(String token, String spreadsheetId, String name) throws Exception {
        JSONObject metadata = getSheetProperties(token, spreadsheetId);
        JSONArray sheets = metadata.getJSONArray("sheets");
        for (int i = 0; i < sheets.length(); i++) {
            JSONObject props = sheets.getJSONObject(i).getJSONObject("properties");
            if (props.getString("title").equals(name)) {
                return props.getInt("sheetId");
            }
        }
        return 0;
    }

    public void pushTransactions(String token, List<MoneyTransaction> transactions) throws Exception {
        for (MoneyTransaction tx : transactions) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(tx.occurredAt);
            int year = cal.get(java.util.Calendar.YEAR);
            int month = cal.get(java.util.Calendar.MONTH) + 1;

            ensureYearlySpreadsheet(token, year);
            String spreadsheetId = settingsStore.getSpreadsheetIdForYear(year);
            String sheetName = MONTH_LABELS[month - 1];
            ensureMonthlySheet(token, spreadsheetId, month);

            String range = URLEncoder.encode(sheetRange(sheetName, "A:J"), "UTF-8");
            String url = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                + "/values/" + range + ":append"
                + "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";

            JSONArray row = new JSONArray();
            row.put(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(tx.occurredAt)));
            row.put(tx.type.name());
            row.put(tx.amount);
            row.put(tx.category);
            row.put(tx.note);
            row.put(tx.sourceName);
            row.put(tx.parser);
            row.put(tx.sourcePackage);
            row.put(tx.rawNotification);
            row.put(tx.syncId);

            post(token, url, new JSONObject().put("values", new JSONArray().put(row)));
        }
    }

    public void updateSheetRow(String token, String spreadsheetId, String sheetName,
                                int rowIndex, MoneyTransaction tx) throws Exception {
        String range = sheetRange(sheetName, "A" + rowIndex + ":J" + rowIndex);
        JSONArray row = new JSONArray();
        row.put(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(new Date(tx.occurredAt)));
        row.put(tx.type.name());
        row.put(tx.amount);
        row.put(tx.category);
        row.put(tx.note);
        row.put(tx.sourceName);
        row.put(tx.parser);
        row.put(tx.sourcePackage);
        row.put(tx.rawNotification);
        row.put(tx.syncId);
        putValues(token, spreadsheetId, range, new JSONArray().put(row));
    }

    public List<SheetRow> pullTransactions(String token, int year) throws Exception {
        String spreadsheetId = settingsStore.getSpreadsheetIdForYear(year);
        if (spreadsheetId.isEmpty()) return new ArrayList<>();

        List<SheetRow> result = new ArrayList<>();
        for (String monthLabel : MONTH_LABELS) {
            try {
                String range = URLEncoder.encode(sheetRange(monthLabel, "A:J"), "UTF-8");
                String url = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                    + "/values/" + range;
                JSONObject resp = get(token, url);
                if (!resp.has("values")) continue;
                JSONArray values = resp.getJSONArray("values");
                for (int i = 1; i < values.length(); i++) {
                    try {
                        JSONArray row = values.getJSONArray(i);
                        String rawSyncId = row.length() > COL_SYNCID ? row.optString(COL_SYNCID, "").trim() : "";
                        SheetRow sr = parseSheetRow(row);
                        if (sr != null) {
                            sr.monthLabel = monthLabel;
                            sr.sheetRowIndex = i + 1;
                            sr.needsIdUpdate = rawSyncId.isEmpty();
                            result.add(sr);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (IOException e) {
                continue;
            } catch (Exception ignored) {}
        }
        return result;
    }

    public void writeBackSyncIds(String token, int year, List<SheetRow> rows) throws Exception {
        String spreadsheetId = settingsStore.getSpreadsheetIdForYear(year);
        if (spreadsheetId.isEmpty()) return;

        for (SheetRow sr : rows) {
            if (!sr.needsIdUpdate || sr.monthLabel == null) continue;
            String range = sheetRange(sr.monthLabel, "J" + sr.sheetRowIndex);
            JSONArray row = new JSONArray();
            row.put(sr.syncId);
            putValues(token, spreadsheetId, range, new JSONArray().put(row));
        }
    }

    public List<String[]> pullCategories(String token, int year) throws Exception {
        String spreadsheetId = settingsStore.getSpreadsheetIdForYear(year);
        if (spreadsheetId.isEmpty()) return new ArrayList<>();

        List<String[]> result = new ArrayList<>();
        try {
            String range = URLEncoder.encode(sheetRange(CATEGORIES_SHEET, "A:B"), "UTF-8");
            String url = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                + "/values/" + range;
            JSONObject resp = get(token, url);
            if (!resp.has("values")) return result;
            JSONArray values = resp.getJSONArray("values");
            for (int i = 1; i < values.length(); i++) {
                try {
                    JSONArray row = values.getJSONArray(i);
                    String name = row.length() > 0 ? row.getString(0).trim() : "";
                    String type = row.length() > 1 ? row.getString(1).trim() : "";
                    if (!name.isEmpty() && !type.isEmpty()) {
                        result.add(new String[]{name, type});
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) return result;
            throw e;
        } catch (Exception ignored) {}
        return result;
    }

    public void pushCategories(String token, List<Category> categories) throws Exception {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        ensureYearlySpreadsheet(token, year);
        String spreadsheetId = settingsStore.getSpreadsheetIdForYear(year);
        JSONArray rows = new JSONArray();
        for (Category cat : categories) {
            JSONArray row = new JSONArray();
            row.put(cat.name);
            row.put(cat.typeStr);
            rows.put(row);
        }
        String range = URLEncoder.encode(sheetRange(CATEGORIES_SHEET, "A2:B"), "UTF-8");
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
            + "/values/" + range + ":clear";
        post(token, url, new JSONObject());

        if (categories.isEmpty()) return;
        range = URLEncoder.encode(sheetRange(CATEGORIES_SHEET, "A2"), "UTF-8");
        url = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
            + "/values/" + range + ":append"
            + "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";
        post(token, url, new JSONObject().put("values", rows));
    }

    private SheetRow parseSheetRow(JSONArray row) {
        try {
            String time = row.length() > COL_TIME ? row.optString(COL_TIME, "").trim() : "";
            String typeStr = row.length() > COL_TYPE ? row.optString(COL_TYPE, "").trim() : "";
            String amountStr = row.length() > COL_AMOUNT ? row.optString(COL_AMOUNT, "").trim() : "";
            String category = row.length() > COL_CATEGORY ? row.optString(COL_CATEGORY, "Chưa phân loại").trim() : "Chưa phân loại";
            String note = row.length() > COL_NOTE ? row.optString(COL_NOTE, "").trim() : "";
            String source = row.length() > COL_SOURCE ? row.optString(COL_SOURCE, "").trim() : "";
            String parser = row.length() > COL_PARSER ? row.optString(COL_PARSER, "").trim() : "";
            String pkg = row.length() > COL_PACKAGE ? row.optString(COL_PACKAGE, "").trim() : "";
            String rawNotif = row.length() > COL_RAW ? row.optString(COL_RAW, "").trim() : "";
            String syncId = row.length() > COL_SYNCID ? row.optString(COL_SYNCID, "").trim() : "";

            if (amountStr.isEmpty()) return null;
            String digits = amountStr.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return null;
            long amount = Long.parseLong(digits);
            if (amount <= 0) return null;

            TransactionType type;
            try {
                type = TransactionType.valueOf(typeStr.toUpperCase());
            } catch (Exception e) {
                return null;
            }

            long occurredAt = 0;
            for (String fmt : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy H:mm:ss"}) {
                try {
                    occurredAt = new SimpleDateFormat(fmt, Locale.getDefault()).parse(time).getTime();
                    break;
                } catch (Exception ignored) {}
            }
            if (occurredAt == 0) occurredAt = System.currentTimeMillis();

            if (syncId.isEmpty()) {
                String seed = occurredAt + "|" + type.name() + "|" + amount + "|" + pkg;
                try {
                    byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(seed.getBytes());
                    StringBuilder sb = new StringBuilder("sheet-");
                    for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
                    syncId = sb.toString();
                } catch (Exception e) {
                    syncId = "sheet-" + seed.hashCode();
                }
            }

            return new SheetRow(syncId, occurredAt, type, amount, category, note, source,
                parser, pkg, rawNotif);
        } catch (Exception e) {
            return null;
        }
    }

    private void renameDefaultSheet(String token, String spreadsheetId, String newName) throws Exception {
        JSONObject metadata = getSheetProperties(token, spreadsheetId);
        JSONArray sheets = metadata.getJSONArray("sheets");
        if (sheets.length() > 0) {
            int sheetId = sheets.getJSONObject(0).getJSONObject("properties").getInt("sheetId");
            String title = sheets.getJSONObject(0).getJSONObject("properties").getString("title");
            if (!title.equals(newName)) {
                post(token,
                    "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + ":batchUpdate",
                    new JSONObject().put("requests", new JSONArray().put(
                        new JSONObject().put("updateSheetProperties", new JSONObject()
                            .put("properties", new JSONObject()
                                .put("sheetId", sheetId)
                                .put("title", newName))
                            .put("fields", "title")))));
            }
        }
    }

    private void ensureHeaders(String token, String spreadsheetId, String sheetName) throws Exception {
        JSONArray headerRow = new JSONArray();
        for (String h : HEADERS) headerRow.put(h);
        putValues(token, spreadsheetId, sheetRange(sheetName, "A1:J1"),
            new JSONArray().put(headerRow));
    }

    private String createDriveFile(String token, JSONObject metadata) throws Exception {
        JSONObject result = post(token,
            "https://www.googleapis.com/drive/v3/files?fields=id", metadata);
        return result.getString("id");
    }

    private void putValues(String token, String spreadsheetId, String rangeValue,
                            JSONArray values) throws Exception {
        String range = URLEncoder.encode(rangeValue, "UTF-8");
        JSONObject body = new JSONObject()
            .put("range", rangeValue)
            .put("majorDimension", "ROWS")
            .put("values", values);
        request(token, new Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId
                + "/values/" + range + "?valueInputOption=RAW")
            .put(RequestBody.create(body.toString(), JSON_MT)));
    }

    private JSONObject post(String token, String url, JSONObject body) throws Exception {
        return request(token, new Request.Builder()
            .url(url)
            .post(RequestBody.create(body.toString(), JSON_MT)));
    }

    private JSONObject get(String token, String url) throws Exception {
        return request(token, new Request.Builder().url(url).get());
    }

    private JSONObject request(String token, Request.Builder builder) throws Exception {
        int maxRetries = 3;
        for (int attempt = 0; ; attempt++) {
            Request request = builder.header("Authorization", "Bearer " + token).build();
            String url = request.url().toString();
            android.util.Log.d("FinMana-API", "Request: " + request.method() + " " + url);
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (response.code() == 429 && attempt < maxRetries) {
                    long wait = (long) Math.pow(2, attempt + 1) * 1000;
                    android.util.Log.w("FinMana-API", "429 rate limited, retry " + (attempt + 1) + " in " + wait + "ms");
                    Thread.sleep(wait);
                    continue;
                }
                if (!response.isSuccessful()) {
                    android.util.Log.e("FinMana-API", "HTTP " + response.code() + " for " + url + ": " + body);
                    throw new IOException("Google API HTTP " + response.code() + ": " + body);
                }
                return body.isEmpty() ? new JSONObject() : new JSONObject(body);
            }
        }
    }

    public static class SheetRow {
        public final String syncId;
        public final long occurredAt;
        public final TransactionType type;
        public final long amount;
        public final String category;
        public final String note;
        public final String sourceName;
        public final String parser;
        public final String sourcePackage;
        public final String rawNotification;
        public String monthLabel;
        public int sheetRowIndex;
        public boolean needsIdUpdate;

        public SheetRow(String syncId, long occurredAt, TransactionType type, long amount,
                        String category, String note, String sourceName,
                        String parser, String sourcePackage, String rawNotification) {
            this.syncId = syncId;
            this.occurredAt = occurredAt;
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.note = note;
            this.sourceName = sourceName;
            this.parser = parser;
            this.sourcePackage = sourcePackage;
            this.rawNotification = rawNotification;
        }
    }
}
