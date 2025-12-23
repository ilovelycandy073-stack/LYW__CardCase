package com.example.bestapplication.feature.idcard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bestapplication.R;
import com.example.bestapplication.core.db.AppDatabase;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.example.bestapplication.feature.idcard.scan.IdCardScanActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IdCardDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private String itemId;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idcard_detail);

        itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        if (itemId == null || itemId.trim().isEmpty()) {
            Toast.makeText(this, "参数错误：缺少 itemId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == R.id.action_scan) {
                Intent it = new Intent(this, IdCardScanActivity.class);
                it.putExtra(EXTRA_ITEM_ID, itemId);
                startActivity(it);
                return true;
            }
            return false;
        });
        toolbar.inflateMenu(R.menu.menu_idcard_detail);

        ViewPager2 pager = findViewById(R.id.pager);
        pager.setAdapter(new IdCardPagerAdapter(this, itemId));

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pager, (tab, pos) -> {
            tab.setText(pos == 0 ? "信息" : pos == 1 ? "图片" : "文件");
        }).attach();

        refreshTitle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTitle();
    }

    private void refreshTitle() {
        final String id = itemId;
        new Thread(() -> {
            DocumentItemEntity item = AppDatabase.get(this).dao().findItem(id);
            String title = computeTitle(item);
            runOnUiThread(() -> toolbar.setTitle(title));
        }).start();
    }

    private static String computeTitle(DocumentItemEntity item) {
        if (item == null) return "身份证";

        JsonObject obj = safeParseJsonObject(item.infoJson);
        String custom = getString(obj, "customTitle");
        if (custom != null && !custom.trim().isEmpty()) return custom.trim();

        String name = getString(obj, "name");
        if (name != null && !name.trim().isEmpty()) return name.trim();

        return "身份证";
    }

    private static String getString(JsonObject obj, String key) {
        try {
            if (obj == null) return "";
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return "";
            return e.getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JsonObject safeParseJsonObject(String json) {
        try {
            if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) return new JsonObject();
            JsonElement el = JsonParser.parseString(json);
            if (el != null && el.isJsonObject()) return el.getAsJsonObject();
            return new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}
