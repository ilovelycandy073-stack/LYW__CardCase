package com.example.bestapplication.feature.bankcard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bestapplication.R;
import com.example.bestapplication.feature.bankcard.scan.BankCardScanActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class BankCardDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = BankCardListActivity.EXTRA_ITEM_ID;

    // 扫描页参数
    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_FRONT_OCR = "front_ocr";
    public static final String MODE_BACK_ONLY = "back_only";

    private String itemId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bankcard_detail);

        itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        if (itemId == null || itemId.trim().isEmpty()) {
            Toast.makeText(this, "参数错误：缺少 itemId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == R.id.action_scan) {
                showScanSheet();
                return true;
            }
            return false;
        });
        toolbar.inflateMenu(R.menu.menu_bankcard_detail);

        ViewPager2 pager = findViewById(R.id.pager);
        pager.setAdapter(new BankCardPagerAdapter(this, itemId));

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pager, (tab, pos) -> tab.setText(pos == 0 ? "信息" : "图片")).attach();
    }

    private void showScanSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottomsheet_bankcard_scan, null, false);

        sheet.findViewById(R.id.btn_scan_front).setOnClickListener(v -> {
            dialog.dismiss();
            startScan(MODE_FRONT_OCR);
        });

        sheet.findViewById(R.id.btn_scan_back).setOnClickListener(v -> {
            dialog.dismiss();
            startScan(MODE_BACK_ONLY);
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void startScan(String mode) {
        Intent it = new Intent(this, BankCardScanActivity.class);
        it.putExtra(EXTRA_ITEM_ID, itemId);
        it.putExtra(EXTRA_MODE, mode);
        startActivity(it);
    }
}
