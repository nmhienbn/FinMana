package com.finmana.app.data;

import androidx.lifecycle.LiveData;

import com.finmana.app.google.GoogleSheetsSync;
import com.finmana.app.parser.BalanceParser;

import java.security.MessageDigest;
import java.util.Calendar;
import java.util.List;

public class TransactionRepository {
    private final TransactionDao transactionDao;
    private final CategoryDao categoryDao;
    private final BalanceParser parser;
    private final GoogleSheetsSync sheetsSync;

    public TransactionRepository(TransactionDao transactionDao, CategoryDao categoryDao,
                                  BalanceParser parser, GoogleSheetsSync sheetsSync) {
        this.transactionDao = transactionDao;
        this.categoryDao = categoryDao;
        this.parser = parser;
        this.sheetsSync = sheetsSync;
    }

    public LiveData<List<MoneyTransaction>> getActiveTransactions() {
        return transactionDao.observeActive();
    }

    public LiveData<List<MoneyTransaction>> getDeletedTransactions() {
        return transactionDao.observeDeleted();
    }

    public LiveData<List<Category>> getCategories() {
        return categoryDao.observeAll();
    }

    public List<Category> getCategoriesByType(TransactionType type) {
        return categoryDao.getByType(type.name());
    }

    public void insertCategory(Category category) {
        categoryDao.insert(category);
    }

    public void deleteCategory(String name, String type) {
        categoryDao.deleteByNameAndType(name, type);
    }

    public void ingest(String packageName, String appName, String text, long occurredAt) {
        ParsedBalance parsed = parser.parse(packageName, appName, text);
        if (parsed == null) return;
        String fingerprint = sha256(packageName + "|" + text + "|" + (occurredAt / 60_000));
        MoneyTransaction tx = new MoneyTransaction();
        tx.amount = parsed.amount;
        tx.type = parsed.type;
        tx.occurredAt = occurredAt;
        tx.sourcePackage = packageName;
        tx.sourceName = parsed.sourceName;
        tx.rawNotification = text;
        tx.parser = parsed.parser;
        tx.fingerprint = fingerprint;
        transactionDao.insert(tx);
    }

    public void update(MoneyTransaction transaction) {
        transaction.synced = false;
        transactionDao.update(transaction);
    }

    public void softDelete(long id) {
        transactionDao.softDelete(id, System.currentTimeMillis());
    }

    public void restore(long id) {
        transactionDao.restore(id);
    }

    public void permanentDelete(MoneyTransaction transaction) {
        transactionDao.delete(transaction);
    }

    public void purgeOldDeleted(int daysOld) {
        long cutoff = System.currentTimeMillis() - (daysOld * 86_400_000L);
        transactionDao.purgeOldDeleted(cutoff);
    }

    public SyncResult syncAll() throws Exception {
        String token = sheetsSync.token();
        SyncResult result = new SyncResult();

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = currentYear; year >= currentYear - 1; year--) {
            boolean created = sheetsSync.ensureYearlySpreadsheet(token, year);
            if (created) transactionDao.resetAllSynced();

            List<GoogleSheetsSync.SheetRow> sheetRows = sheetsSync.pullTransactions(token, year);

            sheetsSync.writeBackSyncIds(token, year, sheetRows);

            for (GoogleSheetsSync.SheetRow sr : sheetRows) {
                MoneyTransaction existing = transactionDao.getBySyncId(sr.syncId);
                if (existing == null) {
                    existing = findMatchingLocal(sr.amount, sr.type, sr.occurredAt, sr.sourcePackage);
                }
                if (existing != null) {
                    boolean changed = false;
                    if (!existing.syncId.equals(sr.syncId)) { existing.syncId = sr.syncId; changed = true; }
                    if (existing.amount != sr.amount) { existing.amount = sr.amount; changed = true; }
                    if (existing.type != sr.type) { existing.type = sr.type; changed = true; }
                    if (!existing.category.equals(sr.category)) { existing.category = sr.category; changed = true; }
                    if (!existing.note.equals(sr.note)) { existing.note = sr.note; changed = true; }
                    if (!existing.sourceName.equals(sr.sourceName)) { existing.sourceName = sr.sourceName; changed = true; }
                    if (!existing.parser.equals(sr.parser)) { existing.parser = sr.parser; changed = true; }
                    if (!existing.sourcePackage.equals(sr.sourcePackage)) { existing.sourcePackage = sr.sourcePackage; changed = true; }
                    if (!existing.rawNotification.equals(sr.rawNotification)) { existing.rawNotification = sr.rawNotification; changed = true; }
                    if (changed) {
                        existing.synced = true;
                        transactionDao.update(existing);
                        result.updated++;
                    }
                } else {
                    MoneyTransaction tx = new MoneyTransaction();
                    tx.syncId = sr.syncId;
                    tx.amount = sr.amount;
                    tx.type = sr.type;
                    tx.occurredAt = sr.occurredAt;
                    tx.sourcePackage = sr.sourcePackage;
                    tx.sourceName = sr.sourceName;
                    tx.rawNotification = sr.rawNotification;
                    tx.note = sr.note;
                    tx.category = sr.category;
                    tx.parser = sr.parser;
                    tx.fingerprint = sha256(tx.syncId);
                    tx.synced = true;
                    transactionDao.insert(tx);
                    result.pulled++;
                }
            }

            List<String[]> sheetCats = sheetsSync.pullCategories(token, year);
            for (String[] cat : sheetCats) {
                try {
                    TransactionType catType = TransactionType.valueOf(cat[1].toUpperCase());
                    Category existing = findCategory(cat[0], catType);
                    if (existing == null) {
                        categoryDao.insert(new Category(cat[0], catType));
                        result.categoriesPulled++;
                    }
                } catch (Exception ignored) {}
            }
        }

        List<MoneyTransaction> unsynced = transactionDao.unsynced();
        List<MoneyTransaction> toPush = new java.util.ArrayList<>();
        for (MoneyTransaction tx : unsynced) {
            if (!tx.deleted) toPush.add(tx);
        }
        if (!toPush.isEmpty()) {
            sheetsSync.pushTransactions(token, toPush);
            for (MoneyTransaction tx : toPush) {
                tx.synced = true;
                transactionDao.update(tx);
            }
            result.pushed = toPush.size();
        }

        List<Category> unsyncedCats = categoryDao.getAllList();
        List<Category> catsToPush = new java.util.ArrayList<>();
        for (Category cat : unsyncedCats) {
            if (!cat.synced) catsToPush.add(cat);
        }
        if (!catsToPush.isEmpty()) {
            sheetsSync.pushCategories(token, catsToPush);
            for (Category cat : catsToPush) {
                categoryDao.markSynced(cat.name, cat.typeStr);
            }
            result.categoriesPushed = catsToPush.size();
        }

        purgeOldDeleted(30);
        return result;
    }

    private Category findCategory(String name, TransactionType type) {
        List<Category> all = categoryDao.getAllList();
        for (Category cat : all) {
            if (cat.name.equals(name) && cat.typeStr.equals(type.name())) return cat;
        }
        return null;
    }

    private MoneyTransaction findMatchingLocal(long amount, TransactionType type, long occurredAt, String sourcePackage) {
        long dayStart = occurredAt - (occurredAt % 86400000);
        long dayEnd = dayStart + 86400000;
        List<MoneyTransaction> candidates = transactionDao.getByTimeRange(dayStart, dayEnd);
        for (MoneyTransaction tx : candidates) {
            if (tx.amount == amount && tx.type == type && !tx.sourcePackage.equals("sheet")) {
                return tx;
            }
        }
        return null;
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class SyncResult {
        public int pushed = 0;
        public int pulled = 0;
        public int updated = 0;
        public int categoriesPushed = 0;
        public int categoriesPulled = 0;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (pushed > 0) sb.append("Đã đẩy ").append(pushed).append(" gd");
            if (pulled > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("Kéo ").append(pulled).append(" gd từ Sheet");
            }
            if (updated > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("Cập nhật ").append(updated).append(" gd");
            }
            if (categoriesPulled > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("Kéo ").append(categoriesPulled).append(" danh mục");
            }
            if (sb.length() == 0) sb.append("Không có thay đổi mới");
            return sb.toString();
        }
    }
}
