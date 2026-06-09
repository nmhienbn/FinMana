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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finmana.app.data.MoneyTransaction;
import com.finmana.app.data.TransactionType;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecycleBinFragment extends Fragment {
    private MainViewModel viewModel;
    private RecycleBinAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recycle_bin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerRecycleBin);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecycleBinAdapter(new RecycleBinAdapter.Listener() {
            @Override
            public void onRestore(MoneyTransaction tx) {
                viewModel.restore(tx);
            }

            @Override
            public void onPermanentDelete(MoneyTransaction tx) {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa vĩnh viễn?")
                    .setMessage("Không thể khôi phục giao dịch này.")
                    .setPositiveButton("Xóa", (d, w) -> viewModel.permanentDelete(tx))
                    .setNegativeButton("Hủy", null)
                    .show();
            }
        });
        recyclerView.setAdapter(adapter);

        viewModel.deletedTransactions.observe(getViewLifecycleOwner(), list -> {
            if (list != null) adapter.setItems(list);
        });
    }

    static class RecycleBinAdapter extends RecyclerView.Adapter<RecycleBinAdapter.VH> {
        private List<MoneyTransaction> items = new java.util.ArrayList<>();
        private final Listener listener;

        interface Listener {
            void onRestore(MoneyTransaction tx);
            void onPermanentDelete(MoneyTransaction tx);
        }

        RecycleBinAdapter(Listener listener) {
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
            holder.textSync.setText("Đã xóa");

            holder.itemView.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(holder.itemView.getContext())
                    .setTitle(tx.category)
                    .setMessage("Khôi phục hay xóa vĩnh viễn?")
                    .setPositiveButton("Khôi phục", (d, w) -> listener.onRestore(tx))
                    .setNegativeButton("Xóa vĩnh viễn", (d, w) -> listener.onPermanentDelete(tx))
                    .setNeutralButton("Hủy", null)
                    .show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

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
