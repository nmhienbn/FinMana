package com.finmana.app.notification;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.finmana.app.FinManaApplication;
import com.finmana.app.data.ExcludedAppDao;
import com.finmana.app.data.TransactionRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BalanceNotificationListener extends NotificationListenerService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Set<String> excludedPackages = new HashSet<>();
    private long lastExcludedRefresh = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(getPackageName())) return;
        refreshExcludedIfNeeded();
        if (excludedPackages.contains(sbn.getPackageName())) return;
        var extras = sbn.getNotification().extras;
        List<String> parts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addIfNotEmpty(parts, seen, extras.getCharSequence(Notification.EXTRA_TITLE));
        addIfNotEmpty(parts, seen, extras.getCharSequence(Notification.EXTRA_TEXT));
        addIfNotEmpty(parts, seen, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String text = String.join("\n", parts).trim();
        if (text.isEmpty()) return;

        String appName;
        try {
            var info = getPackageManager().getApplicationInfo(sbn.getPackageName(), 0);
            appName = getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception e) {
            appName = sbn.getPackageName();
        }

        TransactionRepository repo = ((FinManaApplication) getApplication()).getRepository();
        String finalAppName = appName;
        executor.execute(() -> repo.ingest(sbn.getPackageName(), finalAppName, text, sbn.getPostTime()));
    }

    private void addIfNotEmpty(List<String> parts, Set<String> seen, CharSequence cs) {
        if (cs == null) return;
        String s = cs.toString().trim();
        if (!s.isEmpty() && seen.add(s)) parts.add(s);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refreshExcludedIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastExcludedRefresh < 30_000) return;
        lastExcludedRefresh = now;
        ExcludedAppDao dao = ((FinManaApplication) getApplication()).getDatabase().excludedAppDao();
        excludedPackages = new HashSet<>(dao.getExcludedPackages());
    }
}
