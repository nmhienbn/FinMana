package com.finmana.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.finmana.app.data.AppSettings;
import com.finmana.app.data.Category;
import com.finmana.app.data.ExcludedApp;
import com.finmana.app.data.ExcludedAppDao;
import com.finmana.app.data.TransactionType;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    private MainViewModel viewModel;
    private TextInputEditText editApiUrl, editApiKey;
    private AutoCompleteTextView spinnerModel;
    private TextView textGoogleEmail, textSheetId, textNotificationStatus;
    private Button btnFetchModels, btnSignIn, btnSync, btnNotification;
    private ChipGroup chipGroupIncome, chipGroupExpense, chipGroupExcluded;
    private ActivityResultLauncher<Intent> signInLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> updateGoogleUI());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        editApiUrl = view.findViewById(R.id.editApiUrl);
        editApiKey = view.findViewById(R.id.editApiKey);
        spinnerModel = view.findViewById(R.id.spinnerModel);
        btnFetchModels = view.findViewById(R.id.btnFetchModels);
        textGoogleEmail = view.findViewById(R.id.textGoogleEmail);
        textSheetId = view.findViewById(R.id.textSheetId);
        btnSignIn = view.findViewById(R.id.btnSignIn);
        btnSync = view.findViewById(R.id.btnSync);
        chipGroupIncome = view.findViewById(R.id.chipGroupIncome);
        chipGroupExpense = view.findViewById(R.id.chipGroupExpense);
        chipGroupExcluded = view.findViewById(R.id.chipGroupExcluded);

        Button btnNotification = view.findViewById(R.id.btnNotification);
        btnNotification.setOnClickListener(v -> startActivity(
            new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        textNotificationStatus = view.findViewById(R.id.textNotificationStatus);
        updateNotificationStatus();

        btnSignIn.setOnClickListener(v -> {
            GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(
                    new Scope("https://www.googleapis.com/auth/drive.file"),
                    new Scope("https://www.googleapis.com/auth/spreadsheets"))
                .build();
            var client = GoogleSignIn.getClient(requireActivity(), options);
            client.signOut().addOnCompleteListener(t -> {
                signInLauncher.launch(client.getSignInIntent());
            });
        });

        btnSync.setOnClickListener(v -> viewModel.sync());
        btnFetchModels.setOnClickListener(v -> fetchModels());

        Button btnSaveAi = view.findViewById(R.id.btnSaveAi);
        btnSaveAi.setOnClickListener(v -> {
            String url = getText(editApiUrl);
            String key = getText(editApiKey);
            String model = getText(spinnerModel);
            viewModel.saveAi(url, key, model);
        });

        Button btnAddCategory = view.findViewById(R.id.btnAddCategory);
        btnAddCategory.setOnClickListener(v -> showAddCategoryDialog());

        Button btnAddExcluded = view.findViewById(R.id.btnAddExcluded);
        btnAddExcluded.setOnClickListener(v -> showAddExcludedDialog());

        viewModel.settings.observe(getViewLifecycleOwner(), settings -> {
            editApiUrl.setText(settings.apiUrl);
            editApiKey.setText(settings.apiKey);
            spinnerModel.setText(settings.aiModel, false);
            if (settings.driveFolderId != null && !settings.driveFolderId.isEmpty()) {
                textSheetId.setText("Đã kết nối thư mục FinMana");
                textSheetId.setVisibility(View.VISIBLE);
            } else {
                textSheetId.setVisibility(View.GONE);
            }
        });

        viewModel.categories.observe(getViewLifecycleOwner(), this::renderCategories);
        viewModel.excludedApps.observe(getViewLifecycleOwner(), this::renderExcludedApps);

        viewModel.status.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearStatus();
            }
        });

        updateGoogleUI();
    }

    private void renderCategories(List<Category> categories) {
        if (categories == null) return;
        chipGroupIncome.removeAllViews();
        chipGroupExpense.removeAllViews();
        for (Category cat : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat.name);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa danh mục?")
                    .setMessage("Xóa \"" + cat.name + "\"?")
                    .setPositiveButton("Xóa", (d, w) -> viewModel.removeCategory(cat.name, cat.getType()))
                    .setNegativeButton("Hủy", null)
                    .show();
            });
            if (cat.typeStr.equals(TransactionType.INCOME.name())) {
                chipGroupIncome.addView(chip);
            } else {
                chipGroupExpense.addView(chip);
            }
        }
    }

    private void showAddCategoryDialog() {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_category, null);
        TextInputEditText editName = dialogView.findViewById(R.id.editCategoryName);
        AutoCompleteTextView spinnerType = dialogView.findViewById(R.id.spinnerCategoryType);

        String[] types = {"INCOME", "EXPENSE"};
        String[] typeLabels = {"Thu nhập", "Chi tiêu"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, typeLabels);
        spinnerType.setAdapter(typeAdapter);
        spinnerType.setText(typeLabels[1], false);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Thêm danh mục")
            .setView(dialogView)
            .setPositiveButton("Thêm", (d, w) -> {
                String name = editName.getText() != null ? editName.getText().toString().trim() : "";
                String typeLabel = spinnerType.getText().toString();
                if (name.isEmpty()) return;
                TransactionType type = typeLabel.equals(typeLabels[0])
                    ? TransactionType.INCOME : TransactionType.EXPENSE;
                viewModel.addCategory(name, type);
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void showAddExcludedDialog() {
        executor.execute(() -> {
            List<ExcludedAppDao.SourceAppInfo> known = viewModel.getKnownApps();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                String[] items = new String[known.size()];
                boolean[] checked = new boolean[known.size()];
                for (int i = 0; i < known.size(); i++) {
                    items[i] = known.get(i).sourceName;
                    checked[i] = false;
                }
                if (items.length == 0) {
                    Toast.makeText(requireContext(), "Chưa có ứng dụng nào gửi thông báo", Toast.LENGTH_SHORT).show();
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn ứng dụng loại trừ")
                    .setMultiChoiceItems(items, checked, (d, which, isChecked) -> {})
                    .setPositiveButton("Loại trừ", (d, w) -> {
                        for (int i = 0; i < known.size(); i++) {
                            if (checked[i]) {
                                viewModel.addExcludedApp(known.get(i).sourcePackage, known.get(i).sourceName);
                            }
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
            });
        });
    }

    private void renderExcludedApps(List<ExcludedApp> apps) {
        if (apps == null) return;
        chipGroupExcluded.removeAllViews();
        for (ExcludedApp app : apps) {
            Chip chip = new Chip(requireContext());
            chip.setText(app.appName);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> viewModel.removeExcludedApp(app.packageName));
            chipGroupExcluded.addView(chip);
        }
    }

    private void fetchModels() {
        String url = getText(editApiUrl);
        String key = getText(editApiKey);
        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(requireContext(), "Nhập API URL và API Key trước", Toast.LENGTH_SHORT).show();
            return;
        }
        btnFetchModels.setEnabled(false);
        btnFetchModels.setText("Đang tải...");
        executor.execute(() -> {
            var aiParser = ((FinManaApplication) requireActivity().getApplication()).getAiParser();
            List<String> models = aiParser.fetchModels(url, key);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                btnFetchModels.setEnabled(true);
                btnFetchModels.setText("Lấy model");
                if (models == null || models.isEmpty()) {
                    Toast.makeText(requireContext(), "Không lấy được danh sách model", Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, models);
                spinnerModel.setAdapter(adapter);
                if (spinnerModel.getText().toString().isEmpty() && !models.isEmpty()) {
                    spinnerModel.setText(models.get(0), false);
                }
                spinnerModel.showDropDown();
            });
        });
    }

    private void updateGoogleUI() {
        if (getActivity() == null) return;
        var account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account != null && account.getEmail() != null) {
            textGoogleEmail.setText(account.getEmail());
            btnSignIn.setText("Đổi tài khoản");
            btnSync.setEnabled(true);
        } else {
            textGoogleEmail.setText("Chưa đăng nhập Google");
            btnSignIn.setText("Đăng nhập Google");
            btnSync.setEnabled(false);
        }
    }

    private String getText(TextView tv) {
        CharSequence cs = tv.getText();
        return cs != null ? cs.toString().trim() : "";
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoogleUI();
        updateNotificationStatus();
    }

    private void updateNotificationStatus() {
        if (getContext() == null) return;
        String pkg = getContext().getPackageName();
        String flat = android.provider.Settings.Secure.getString(
            getContext().getContentResolver(), "enabled_notification_listeners");
        boolean enabled = false;
        if (flat != null) {
            for (String name : flat.split(":")) {
                android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
                if (cn != null && pkg.equals(cn.getPackageName())) { enabled = true; break; }
            }
        }
        if (enabled) {
            textNotificationStatus.setText("✓ Đã cấp quyền đọc thông báo");
            textNotificationStatus.setTextColor(0xFF4CAF50);
        } else {
            textNotificationStatus.setText("✗ Chưa cấp quyền đọc thông báo");
            textNotificationStatus.setTextColor(0xFFF44336);
        }
    }
}
