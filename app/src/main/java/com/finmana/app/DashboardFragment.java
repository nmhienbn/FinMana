package com.finmana.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.finmana.app.data.ChartPoint;
import com.finmana.app.data.MoneyTransaction;
import com.finmana.app.data.TransactionType;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {
    private MainViewModel viewModel;
    private ChipGroup chipGroup;
    private TextView textIncome, textExpense, textNet;
    private BarChartView chartView;

    private enum Period { DAY, WEEK, MONTH, QUARTER, YEAR }
    private Period selectedPeriod = Period.MONTH;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        chipGroup = view.findViewById(R.id.chipGroupPeriod);
        textIncome = view.findViewById(R.id.textIncome);
        textExpense = view.findViewById(R.id.textExpense);
        textNet = view.findViewById(R.id.textNet);
        chartView = view.findViewById(R.id.barChart);

        setupChips();
        viewModel.activeTransactions.observe(getViewLifecycleOwner(), this::refresh);
    }

    private void setupChips() {
        Period[] periods = Period.values();
        String[] labels = {"Ngày", "Tuần", "Tháng", "Quý", "Năm"};
        for (int i = 0; i < periods.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setChecked(i == selectedPeriod.ordinal());
            int idx = i;
            chip.setOnClickListener(v -> {
                selectedPeriod = periods[idx];
                for (int j = 0; j < chipGroup.getChildCount(); j++) {
                    ((Chip) chipGroup.getChildAt(j)).setChecked(j == idx);
                }
                refresh(viewModel.activeTransactions.getValue());
            });
            chipGroup.addView(chip);
        }
    }

    private void refresh(List<MoneyTransaction> all) {
        if (all == null) return;
        List<MoneyTransaction> filtered = currentPeriod(all, selectedPeriod);
        long income = 0, expense = 0;
        for (MoneyTransaction tx : filtered) {
            if (tx.type == TransactionType.INCOME) income += tx.amount;
            else expense += tx.amount;
        }
        NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        textIncome.setText("Thu: " + fmt.format(income) + " đ");
        textExpense.setText("Chi: " + fmt.format(expense) + " đ");
        textNet.setText("Dòng tiền ròng: " + fmt.format(income - expense) + " đ");
        chartView.setPoints(chartPoints(filtered, selectedPeriod));
    }

    private List<MoneyTransaction> currentPeriod(List<MoneyTransaction> items, Period period) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime start;
        switch (period) {
            case DAY: start = now.toLocalDate().atStartOfDay(now.getZone()); break;
            case WEEK: start = now.minusDays(now.getDayOfWeek().getValue() - 1).toLocalDate().atStartOfDay(now.getZone()); break;
            case MONTH: start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.getZone()); break;
            case QUARTER: start = now.withMonth(((now.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1).toLocalDate().atStartOfDay(now.getZone()); break;
            default: start = now.withDayOfYear(1).toLocalDate().atStartOfDay(now.getZone()); break;
        }
        long startMs = start.toInstant().toEpochMilli();
        List<MoneyTransaction> result = new ArrayList<>();
        for (MoneyTransaction tx : items) {
            if (tx.occurredAt >= startMs) result.add(tx);
        }
        return result;
    }

    private List<ChartPoint> chartPoints(List<MoneyTransaction> items, Period period) {
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter keyFmt;
        switch (period) {
            case DAY: keyFmt = DateTimeFormatter.ofPattern("HH'h'"); break;
            case WEEK:
            case MONTH: keyFmt = DateTimeFormatter.ofPattern("dd/MM"); break;
            default: keyFmt = DateTimeFormatter.ofPattern("MM/yy"); break;
        }
        java.util.TreeMap<ZonedDateTime, List<MoneyTransaction>> grouped = new java.util.TreeMap<>();
        for (MoneyTransaction tx : items) {
            ZonedDateTime date = Instant.ofEpochMilli(tx.occurredAt).atZone(zone);
            ZonedDateTime key;
            switch (period) {
                case DAY: key = date.truncatedTo(ChronoUnit.HOURS); break;
                case WEEK:
                case MONTH: key = date.toLocalDate().atStartOfDay(zone); break;
                default: key = date.withDayOfMonth(1).toLocalDate().atStartOfDay(zone); break;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }
        List<ChartPoint> all = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            long inc = 0, exp = 0;
            for (MoneyTransaction tx : entry.getValue()) {
                if (tx.type == TransactionType.INCOME) inc += tx.amount;
                else exp += tx.amount;
            }
            all.add(new ChartPoint(entry.getKey().format(keyFmt), inc, exp));
        }
        int from = Math.max(0, all.size() - 7);
        return all.subList(from, all.size());
    }
}
