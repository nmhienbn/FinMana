package com.finmana.app;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finmana.app.data.AppSettings;
import com.finmana.app.data.Category;
import com.finmana.app.data.ExcludedApp;
import com.finmana.app.data.ExcludedAppDao;
import com.finmana.app.data.MoneyTransaction;
import com.finmana.app.data.TransactionRepository;
import com.finmana.app.data.TransactionType;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {
    private final FinManaApplication app;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public final LiveData<List<MoneyTransaction>> activeTransactions;
    public final LiveData<List<MoneyTransaction>> deletedTransactions;
    public final LiveData<List<Category>> categories;
    public final LiveData<List<ExcludedApp>> excludedApps;
    public final LiveData<AppSettings> settings;
    public final MutableLiveData<String> status = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        app = (FinManaApplication) application;
        activeTransactions = app.getRepository().getActiveTransactions();
        deletedTransactions = app.getRepository().getDeletedTransactions();
        categories = app.getRepository().getCategories();
        excludedApps = app.getDatabase().excludedAppDao().observeAll();
        settings = app.getSettingsStore().liveData;
    }

    public void update(MoneyTransaction transaction, String note, String category) {
        executor.execute(() -> {
            transaction.note = note.trim();
            transaction.category = category.trim();
            app.getRepository().update(transaction);
            status.postValue("Đã cập nhật giao dịch");
        });
    }

    public void softDelete(MoneyTransaction transaction) {
        executor.execute(() -> {
            app.getRepository().softDelete(transaction.id);
            status.postValue("Đã chuyển vào thùng rác");
        });
    }

    public void restore(MoneyTransaction transaction) {
        executor.execute(() -> {
            app.getRepository().restore(transaction.id);
            status.postValue("Đã khôi phục giao dịch");
        });
    }

    public void permanentDelete(MoneyTransaction transaction) {
        executor.execute(() -> {
            app.getRepository().permanentDelete(transaction);
            status.postValue("Đã xóa vĩnh viễn");
        });
    }

    public void saveAi(String apiUrl, String apiKey, String model) {
        executor.execute(() -> {
            app.getSettingsStore().saveAi(apiUrl, apiKey, model);
            status.postValue("Đã lưu cấu hình AI");
        });
    }

    public void sync() {
        status.postValue("Đang đồng bộ...");
        executor.execute(() -> {
            try {
                TransactionRepository.SyncResult result = app.getRepository().syncAll();
                status.postValue(result.summary());
            } catch (Exception e) {
                android.util.Log.e("FinMana-Sync", "Sync failed", e);
                String msg = e.getMessage();
                if (msg != null && msg.length() > 200) msg = msg.substring(0, 200);
                status.postValue("Lỗi đồng bộ: " + msg);
            }
        });
    }

    public void addCategory(String name, TransactionType type) {
        executor.execute(() -> {
            app.getRepository().insertCategory(new Category(name, type));
            status.postValue("Đã thêm danh mục");
        });
    }

    public void removeCategory(String name, TransactionType type) {
        executor.execute(() -> {
            app.getRepository().deleteCategory(name, type.name());
            status.postValue("Đã xóa danh mục");
        });
    }

    public void addExcludedApp(String packageName, String appName) {
        executor.execute(() -> {
            app.getDatabase().excludedAppDao().insert(new ExcludedApp(packageName, appName));
            status.postValue("Đã loại trừ " + appName);
        });
    }

    public void removeExcludedApp(String packageName) {
        executor.execute(() -> {
            app.getDatabase().excludedAppDao().delete(packageName);
            status.postValue("Đã bỏ loại trừ");
        });
    }

    public List<ExcludedAppDao.SourceAppInfo> getKnownApps() {
        return app.getDatabase().excludedAppDao().getKnownApps();
    }

    public void clearStatus() {
        status.setValue(null);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }
}
