package com.finmana.app;

import android.app.Application;

import com.finmana.app.data.AppDatabase;
import com.finmana.app.data.Category;
import com.finmana.app.data.CategoryDao;
import com.finmana.app.data.SettingsStore;
import com.finmana.app.data.TransactionRepository;
import com.finmana.app.data.TransactionType;
import com.finmana.app.google.GoogleSheetsSync;
import com.finmana.app.parser.AiParser;
import com.finmana.app.parser.BalanceParser;

import java.util.Arrays;
import java.util.List;

public class FinManaApplication extends Application {
    private AppDatabase database;
    private SettingsStore settingsStore;
    private AiParser aiParser;
    private BalanceParser balanceParser;
    private GoogleSheetsSync sheetsSync;
    private TransactionRepository repository;

    public AppDatabase getDatabase() {
        if (database == null) database = AppDatabase.create(this);
        return database;
    }

    public SettingsStore getSettingsStore() {
        if (settingsStore == null) settingsStore = new SettingsStore(this);
        return settingsStore;
    }

    public AiParser getAiParser() {
        if (aiParser == null) aiParser = new AiParser(getSettingsStore());
        return aiParser;
    }

    public BalanceParser getBalanceParser() {
        if (balanceParser == null)
            balanceParser = new BalanceParser(getDatabase().patternDao(), getAiParser());
        return balanceParser;
    }

    public GoogleSheetsSync getSheetsSync() {
        if (sheetsSync == null)
            sheetsSync = new GoogleSheetsSync(this, getSettingsStore());
        return sheetsSync;
    }

    public TransactionRepository getRepository() {
        if (repository == null)
            repository = new TransactionRepository(
                getDatabase().transactionDao(), getDatabase().categoryDao(),
                getBalanceParser(), getSheetsSync());
        return repository;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        seedCategoriesIfNeeded();
    }

    private void seedCategoriesIfNeeded() {
        CategoryDao dao = getDatabase().categoryDao();
        if (!dao.getAllList().isEmpty()) return;
        List<Category> defaults = Arrays.asList(
            new Category("Lương", TransactionType.INCOME),
            new Category("Thưởng", TransactionType.INCOME),
            new Category("Đầu tư", TransactionType.INCOME),
            new Category("Bán hàng", TransactionType.INCOME),
            new Category("Khác (thu)", TransactionType.INCOME),
            new Category("Ăn uống", TransactionType.EXPENSE),
            new Category("Đi lại", TransactionType.EXPENSE),
            new Category("Mua sắm", TransactionType.EXPENSE),
            new Category("Giải trí", TransactionType.EXPENSE),
            new Category("Sức khỏe", TransactionType.EXPENSE),
            new Category("Giáo dục", TransactionType.EXPENSE),
            new Category("Hóa đơn", TransactionType.EXPENSE),
            new Category("Nhà cửa", TransactionType.EXPENSE),
            new Category("Chưa phân loại", TransactionType.EXPENSE),
            new Category("Khác (chi)", TransactionType.EXPENSE)
        );
        dao.insertAll(defaults);
    }
}
