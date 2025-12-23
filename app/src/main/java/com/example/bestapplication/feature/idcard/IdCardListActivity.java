package com.example.bestapplication.feature.idcard;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IdCardListActivity extends AppCompatActivity {

    public static final String TYPE_ID = "IDCARD_CN";
    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private RecyclerView rv;
    private View emptyView;
    private IdCardListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idcard_list);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        tb.setOnMenuItemClickListener(this::onToolbarItemSelected);
        tb.inflateMenu(R.menu.menu_idcard_list);

        rv = findViewById(R.id.rv);
        emptyView = findViewById(R.id.empty_view);

        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new IdCardListAdapter(
                new ArrayList<>(),
                this::openDetail,
                (v, item) -> showLongPressMenu(item)
        );
        rv.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean onToolbarItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            createNewIdCard();
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

    private void createNewIdCard() {
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
        Intent it = new Intent(this, IdCardDetailActivity.class);
        it.putExtra(EXTRA_ITEM_ID, itemId);
        startActivity(it);
    }

    private void showLongPressMenu(DocumentItemEntity item) {
        String[] actions = new String[]{"编辑标题", "删除"};
        new MaterialAlertDialogBuilder(this)
                .setItems(actions, (d, which) -> {
                    if (which == 0) showEditTitleDialog(item);
                    else showDeleteDialog(item);
                })
                .show();
    }

    private void showEditTitleDialog(DocumentItemEntity item) {
        // 交互要求：
        // - 有 customTitle：输入框=customTitle
        // - 没有 customTitle：输入框为空；hint="默认：xxx"
        String currentCustom = IdCardListAdapter.getCustomTitle(item);
        String defaultTitle = IdCardListAdapter.getDefaultTitle(item);

        TextInputLayout til = new TextInputLayout(this);
        til.setPadding(dp(20), dp(8), dp(20), 0);
        til.setHint("标题");

        TextInputEditText et = new TextInputEditText(this);
        et.setText(currentCustom == null ? "" : currentCustom);
        et.setHint("默认：" + defaultTitle);
        til.addView(et);

        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑标题")
                .setView(til)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String input = et.getText() == null ? "" : et.getText().toString().trim();
                    saveCustomTitle(item.itemId, input);
                })
                .show();
    }

    private void saveCustomTitle(String itemId, String input) {
        new Thread(() -> {
            DocumentItemEntity item = AppDatabase.get(this).dao().findItem(itemId);
            if (item == null) return;

            JsonObject obj = new JsonObject();
            try {
                if (item.infoJson != null && !item.infoJson.trim().isEmpty() && !"{}".equals(item.infoJson.trim())) {
                    obj = JsonParser.parseString(item.infoJson).getAsJsonObject();
                }
            } catch (Exception ignored) {}

            if (input.isEmpty()) obj.remove("customTitle");     // 清空=恢复默认
            else obj.addProperty("customTitle", input);

            item.infoJson = obj.toString();
            item.updatedAt = System.currentTimeMillis();
            AppDatabase.get(this).dao().upsertItem(item);

            runOnUiThread(() -> {
                Toast.makeText(this, "已更新标题", Toast.LENGTH_SHORT).show();
                refresh();
            });
        }).start();
    }

    private void showDeleteDialog(DocumentItemEntity item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除身份证")
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
                    @Override
                    public void onSuccess() {
                        deleteItemAndFiles(itemId);
                    }

                    @Override
                    public void onFail(String msg) {
                        // 可选：给用户一个提示
                        // msg 可能为空
                        Toast.makeText(IdCardListActivity.this,
                                (msg == null || msg.trim().isEmpty()) ? "验证失败" : msg,
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

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }
}
