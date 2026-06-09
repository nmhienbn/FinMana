package com.finmana.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finmana.app.data.Category;
import com.finmana.app.data.MoneyTransaction;
import com.finmana.app.data.TransactionType;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionsFragment extends Fragment {
    private MainViewModel viewModel;
    private TransactionAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerTransactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TransactionAdapter(tx -> showEditDialog(tx));
        recyclerView.setAdapter(adapter);

        viewModel.activeTransactions.observe(getViewLifecycleOwner(), list -> {
            if (list != null) adapter.setItems(list);
        });
    }

    private void showEditDialog(MoneyTransaction tx) {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_transaction, null);
        AutoCompleteTextView editCategory = dialogView.findViewById(R.id.editCategory);
        com.google.android.material.textfield.TextInputEditText editNote = dialogView.findViewById(R.id.editNote);

        editCategory.setText(tx.category);
        editNote.setText(tx.note);

        viewModel.categories.observe(getViewLifecycleOwner(), cats -> {
            if (cats == null) return;
            List<String> names = new ArrayList<>();
            for (Category c : cats) {
                if (c.typeStr.equals(tx.type.name())) names.add(c.name);
            }
            ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, names);
            editCategory.setAdapter(catAdapter);
        });

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ghi chú giao dịch")
            .setView(dialogView)
            .setPositiveButton("Lưu", (d, w) -> {
                String cat = editCategory.getText() != null ? editCategory.getText().toString() : "";
                String note = editNote.getText() != null ? editNote.getText().toString() : "";
                viewModel.update(tx, note, cat);
            })
            .setNeutralButton("Xóa", (d, w) -> viewModel.softDelete(tx))
            .setNegativeButton("Hủy", null)
            .show();
    }

    static class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.VH> {
        private List<MoneyTransaction> items = new ArrayList<>();
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(MoneyTransaction tx);
        }

        TransactionAdapter(OnItemClickListener listener) {
            this.listener = listener;
        }

        void setItems(List<MoneyTransaction> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MoneyTransaction tx = items.get(position);
            NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            String sign = tx.type == TransactionType.INCOME ? "+ " : "- ";
            int colorRes = tx.type == TransactionType.INCOME ? 0xFF167D5B : 0xFFC54848;

            holder.textCategory.setText(tx.category);
            holder.textNote.setText(tx.note != null && !tx.note.isEmpty() ? tx.note : tx.sourceName);
            holder.textDate.setText(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(tx.occurredAt)));
            holder.textAmount.setText(sign + fmt.format(tx.amount) + " đ");
            holder.textAmount.setTextColor(colorRes);
            holder.textSync.setText(tx.synced ? "Đã sync" : "Chưa sync");
            holder.itemView.setOnClickListener(v -> listener.onItemClick(tx));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView textCategory, textNote, textDate, textAmount, textSync;
            VH(View view) {
                super(view);
                textCategory = view.findViewById(R.id.textCategory);
                textNote = view.findViewById(R.id.textNote);
                textDate = view.findViewById(R.id.textDate);
                textAmount = view.findViewById(R.id.textAmount);
                textSync = view.findViewById(R.id.textSync);
            }
        }
    }
}
