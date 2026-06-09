package com.finmana.app;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private MainViewModel viewModel;
    private DashboardFragment dashboardFragment;
    private TransactionsFragment transactionsFragment;
    private RecycleBinFragment recycleBinFragment;
    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        dashboardFragment = new DashboardFragment();
        transactionsFragment = new TransactionsFragment();
        recycleBinFragment = new RecycleBinFragment();
        settingsFragment = new SettingsFragment();

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, dashboardFragment)
            .commit();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, dashboardFragment).commit();
                return true;
            } else if (id == R.id.nav_transactions) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, transactionsFragment).commit();
                return true;
            } else if (id == R.id.nav_recycle_bin) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, recycleBinFragment).commit();
                return true;
            } else if (id == R.id.nav_settings) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, settingsFragment).commit();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Cấp quyền thông báo")
                .setMessage("FinMana cần quyền đọc thông báo để nhận biến động số dư từ ngân hàng và ví điện tử.")
                .setPositiveButton("Cấp quyền", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                })
                .setNegativeButton("Để sau", null)
                .setCancelable(false)
                .show();
        }
    }

    private boolean isNotificationListenerEnabled() {
        String pkg = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        String[] names = flat.split(":");
        for (String name : names) {
            ComponentName cn = ComponentName.unflattenFromString(name);
            if (cn != null && pkg.equals(cn.getPackageName())) return true;
        }
        return false;
    }
}
