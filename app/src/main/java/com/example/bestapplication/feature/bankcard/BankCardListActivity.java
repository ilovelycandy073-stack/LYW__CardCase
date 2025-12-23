package com.example.bestapplication.feature.bankcard;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bestapplication.R;
import com.example.bestapplication.core.auth.BiometricGate;
import com.example.bestapplication.core.db.AppDatabase;
import com.example.bestapplication.core.db.entity.BlobRefEntity;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.example.bestapplication.core.filestore.EncryptedFileStore;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankCardListActivity extends AppCompatActivity {

    public static final String TYPE_ID = "VEHICLE_LICENSE"; // 按需求：复用该 typeId
    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private RecyclerView rv;
    private View emptyView;
    private BankCardListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bankcard_list);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        tb.setOnMenuItemClickListener(this::onToolbarItemSelected);
        tb.inflateMenu(R.menu.menu_bankcard_list);

        rv = findViewById(R.id.rv);
        emptyView = findViewById(R.id.empty_view);

        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new BankCardListAdapter(new ArrayList<>(),
                item -> openDetail(item.itemId),
                (anchorView, item) -> showDeleteDialog(item)
        );
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean onToolbarItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            createNewCard();
            return true;
        }
        return false;
    }

    private void refresh() {
        new Thread(() -> {
            List<DocumentItemEntity> items = AppDatabase.get(this).dao().listItemsByType(TYPE_ID);
            runOnUiThread(() -> {
                adapter.replace(items == null ? new ArrayList<>() : items);
                updateEmpty(items == null || items.isEmpty());
            });
        }).start();
    }

    private void updateEmpty(boolean isEmpty) {
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void createNewCard() {
        new Thread(() -> {
            String itemId = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            DocumentItemEntity item = new DocumentItemEntity(itemId, TYPE_ID, "{}", now, "CREATED");
            AppDatabase.get(this).dao().upsertItem(item);

            runOnUiThread(() -> {
                refresh();
                openDetail(itemId);
            });
        }).start();
    }

    private void openDetail(String itemId) {
        Intent it = new Intent(this, BankCardDetailActivity.class);
        it.putExtra(EXTRA_ITEM_ID, itemId);
        startActivity(it);
    }

    private void showDeleteDialog(DocumentItemEntity item) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("删除银行卡")
                .setMessage("删除后无法恢复，是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> requireBiometricThenDelete(item.itemId))
                .show();
    }

    private void requireBiometricThenDelete(String itemId) {
        BiometricGate.requireUnlock(
                this,
                "确认删除",
                "用于保护你的证件信息",
                new BiometricGate.Callback() {
                    @Override public void onSuccess() {
                        deleteItemAndFiles(itemId);
                    }

                    @Override public void onFail(String msg) {
                        Toast.makeText(BankCardListActivity.this,
                                msg == null ? "未通过验证" : msg,
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void deleteItemAndFiles(String itemId) {
        new Thread(() -> {
            List<BlobRefEntity> blobs = AppDatabase.get(this).dao().listBlobsByItem(itemId);

            try {
                EncryptedFileStore fs = new EncryptedFileStore(this);
                if (blobs != null) {
                    for (BlobRefEntity b : blobs) {
                        try { fs.delete(b.relPath); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            AppDatabase.get(this).dao().deleteBlobsByItem(itemId);
            AppDatabase.get(this).dao().deleteItem(itemId);

            runOnUiThread(() -> {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                refresh();
            });
        }).start();
    }
}
