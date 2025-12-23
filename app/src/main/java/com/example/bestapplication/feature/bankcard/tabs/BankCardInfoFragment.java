package com.example.bestapplication.feature.bankcard.tabs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bestapplication.R;
import com.example.bestapplication.core.auth.BiometricGate;
import com.example.bestapplication.core.db.AppDatabase;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BankCardInfoFragment extends Fragment {

    private static final String ARG_ITEM_ID = "arg_item_id";

    public static BankCardInfoFragment newInstance(String itemId) {
        BankCardInfoFragment f = new BankCardInfoFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ITEM_ID, itemId);
        f.setArguments(b);
        return f;
    }

    private String itemId;

    private boolean unlocked = false;

    private TextView tvUnlockState;
    private TextView btnUnlock;

    private View lockedPlaceholder;
    private LinearLayout llRows;
    private TextView btnCopyAll;

    public BankCardInfoFragment() {
        super(R.layout.fragment_bankcard_info);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        itemId = getArguments() == null ? null : getArguments().getString(ARG_ITEM_ID);

        tvUnlockState = view.findViewById(R.id.tvUnlockState);
        btnUnlock = view.findViewById(R.id.btn_unlock);

        lockedPlaceholder = view.findViewById(R.id.locked_placeholder);
        llRows = view.findViewById(R.id.llRows);
        btnCopyAll = view.findViewById(R.id.btnCopyAll);

        btnUnlock.setOnClickListener(v -> {
            if (unlocked) {
                // 仅手动点击“隐藏”才隐藏
                unlocked = false;
                updateUnlockUi();
                renderLocked();
            } else {
                BiometricGate.requireUnlock(
                        requireActivity(),
                        "显示银行卡信息",
                        "用于保护你的证件信息",
                        new BiometricGate.Callback() {
                            @Override public void onSuccess() {
                                unlocked = true;
                                updateUnlockUi();
                                refresh();
                            }

                            @Override public void onFail(String msg) {
                                Toast.makeText(requireContext(),
                                        msg == null ? "解锁失败" : msg,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
        });

        // 每次创建 Fragment（进入详情页）默认隐藏
        unlocked = false;
        updateUnlockUi();
        renderLocked();
    }

    @Override
    public void onPause() {
        super.onPause();
        // 关键修复：Tab 切换也会触发 onPause()，不要在这里自动隐藏
        // 只有当用户“退出详情页”（Activity finish）时才重置，保证下次进入默认隐藏
        if (getActivity() != null && getActivity().isFinishing()) {
            unlocked = false;
            updateUnlockUi();
            renderLocked();
        }
    }

    private void updateUnlockUi() {
        if (tvUnlockState != null) {
            tvUnlockState.setText(unlocked ? "已解锁（显示内容）" : "内容已隐藏");
        }
        if (btnUnlock != null) {
            btnUnlock.setText(unlocked ? getString(R.string.action_hide) : getString(R.string.action_unlock));
        }
    }

    private void renderLocked() {
        lockedPlaceholder.setVisibility(View.VISIBLE);
        llRows.setVisibility(View.GONE);
        btnCopyAll.setEnabled(false);
        llRows.removeAllViews();
    }

    private void refresh() {
        final Context ctx = getContext();
        if (ctx == null || itemId == null) return;

        new Thread(() -> {
            DocumentItemEntity item = AppDatabase.get(ctx).dao().findItem(itemId);
            if (!isAdded()) return;

            if (item == null || item.infoJson == null || item.infoJson.trim().isEmpty() || "{}".equals(item.infoJson.trim())) {
                requireActivity().runOnUiThread(() -> {
                    if (!unlocked) { renderLocked(); return; }
                    lockedPlaceholder.setVisibility(View.GONE);
                    llRows.setVisibility(View.VISIBLE);
                    llRows.removeAllViews();
                    btnCopyAll.setEnabled(false);
                    Toast.makeText(requireContext(), "暂无信息，请先扫描正面", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(item.infoJson).getAsJsonObject();
            } catch (Exception e) {
                obj = new JsonObject();
            }

            final JsonObject finalObj = obj;

            requireActivity().runOnUiThread(() -> {
                if (!unlocked) {
                    renderLocked();
                    return;
                }

                lockedPlaceholder.setVisibility(View.GONE);
                llRows.setVisibility(View.VISIBLE);
                llRows.removeAllViews();

                addRow("银行卡名", getStringSafe(finalObj, "CardName"));
                addRow("银行信息", getStringSafe(finalObj, "BankInfo"));
                addRow("卡类型", getStringSafe(finalObj, "CardType"));
                addRow("卡种类", getStringSafe(finalObj, "CardCategory"));
                addRow("有效期", getStringSafe(finalObj, "ValidDate"));
                addRow("卡号", getStringSafe(finalObj, "CardNo"));

                String q = getStringSafe(finalObj, "QualityValue");
                if (!q.isEmpty()) addRow("质量分", q);
                String w = getStringSafe(finalObj, "WarningCode");
                if (!w.isEmpty()) addRow("告警码", w);

                btnCopyAll.setEnabled(true);
                btnCopyAll.setOnClickListener(v -> copyAll(finalObj));
            });
        }).start();
    }

    private void addRow(String key, String value) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View row = inflater.inflate(R.layout.item_kv_copy, llRows, false);

        TextView tvKey = row.findViewById(R.id.tvKey);
        TextView tvValue = row.findViewById(R.id.tvValue);
        ImageButton btnCopy = row.findViewById(R.id.btnCopy);

        tvKey.setText(key);
        tvValue.setText(value == null ? "" : value);

        btnCopy.setOnClickListener(v -> {
            copyToClipboard(key, value);
            Toast.makeText(requireContext(), getString(R.string.msg_copied_key, key), Toast.LENGTH_SHORT).show();
        });

        tvValue.setOnLongClickListener(v -> {
            copyToClipboard(key, value);
            Toast.makeText(requireContext(), getString(R.string.msg_copied_key, key), Toast.LENGTH_SHORT).show();
            return true;
        });

        llRows.addView(row);

        View divider = new View(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (requireContext().getResources().getDisplayMetrics().density * 1)
        );
        lp.setMargins(dp(16), 0, dp(12), 0);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(0xFFE5E7EB);
        llRows.addView(divider);
    }

    private int dp(int v) {
        float d = requireContext().getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    private void copyAll(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "银行卡名", getStringSafe(obj, "CardName"));
        appendIfPresent(sb, "银行信息", getStringSafe(obj, "BankInfo"));
        appendIfPresent(sb, "卡类型", getStringSafe(obj, "CardType"));
        appendIfPresent(sb, "卡种类", getStringSafe(obj, "CardCategory"));
        appendIfPresent(sb, "有效期", getStringSafe(obj, "ValidDate"));
        appendIfPresent(sb, "卡号", getStringSafe(obj, "CardNo"));
        appendIfPresent(sb, "质量分", getStringSafe(obj, "QualityValue"));
        appendIfPresent(sb, "告警码", getStringSafe(obj, "WarningCode"));

        copyToClipboard("银行卡信息", sb.toString().trim());
        Toast.makeText(requireContext(), getString(R.string.msg_copied_section, "银行卡信息"), Toast.LENGTH_SHORT).show();
    }

    private void appendIfPresent(StringBuilder sb, String k, String v) {
        if (v != null && !v.trim().isEmpty()) {
            sb.append(k).append("：").append(v.trim()).append("\n");
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        String v = text == null ? "" : text;
        cm.setPrimaryClip(ClipData.newPlainText(label, v));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                ClipData cur = cm.getPrimaryClip();
                if (cur != null && cur.getItemCount() > 0) {
                    CharSequence cs = cur.getItemAt(0).coerceToText(requireContext());
                    if (cs != null && cs.toString().equals(v)) {
                        cm.setPrimaryClip(ClipData.newPlainText("", ""));
                    }
                }
            } catch (Exception ignored) {}
        }, 60_000);
    }

    private String getStringSafe(JsonObject obj, String key) {
        try {
            if (obj == null) return "";
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return "";
            return e.getAsString();
        } catch (Exception ex) {
            return "";
        }
    }
}
