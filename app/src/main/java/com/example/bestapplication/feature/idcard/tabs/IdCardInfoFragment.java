package com.example.bestapplication.feature.idcard.tabs;

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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IdCardInfoFragment extends Fragment {

    private static final String ARG_ITEM_ID = "arg_item_id";

    public static IdCardInfoFragment newInstance(String itemId) {
        IdCardInfoFragment f = new IdCardInfoFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ITEM_ID, itemId);
        f.setArguments(b);
        return f;
    }

    private String itemId;

    private boolean unlocked = false; // 信息页独立状态

    private TextView tvUnlockState;
    private TextView btnUnlock;

    private LinearLayout llBasicRows;
    private LinearLayout llIdRows;
    private LinearLayout llQualityRows;

    private TextView btnCopyBasic;
    private TextView btnCopyId;
    private TextView btnCopyQuality;

    private final Gson gson = new Gson();

    public IdCardInfoFragment() {
        super(R.layout.fragment_idcard_info);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        itemId = getArguments() == null ? null : getArguments().getString(ARG_ITEM_ID);

        tvUnlockState = view.findViewById(R.id.tvUnlockState);
        btnUnlock = view.findViewById(R.id.btn_unlock);

        llBasicRows = view.findViewById(R.id.llBasicRows);
        llIdRows = view.findViewById(R.id.llIdRows);
        llQualityRows = view.findViewById(R.id.llQualityRows);

        btnCopyBasic = view.findViewById(R.id.btnCopyBasic);
        btnCopyId = view.findViewById(R.id.btnCopyId);
        btnCopyQuality = view.findViewById(R.id.btnCopyQuality);

        btnUnlock.setOnClickListener(v -> {
            if (unlocked) {
                unlocked = false;
                updateUnlockUi();
                refresh();
            } else {
                BiometricGate.requireUnlock(
                        requireActivity(),
                        "显示完整信息",
                        "用于保护你的证件信息",
                        new BiometricGate.Callback() {
                            @Override public void onSuccess() {
                                unlocked = true;
                                updateUnlockUi();
                                refresh();
                            }

                            @Override public void onFail(String msg) {
                                Toast.makeText(requireContext(), msg == null ? "解锁失败" : msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
        });

        updateUnlockUi();
        refresh();
    }

    private void updateUnlockUi() {
        if (tvUnlockState != null) {
            tvUnlockState.setText(unlocked
                    ? getString(R.string.unlock_state_full)
                    : getString(R.string.unlock_state_masked));
        }
        if (btnUnlock != null) {
            btnUnlock.setText(unlocked ? getString(R.string.action_hide) : getString(R.string.action_unlock));
        }
    }

    private void refresh() {
        final Context ctx = getContext();
        if (ctx == null) return;

        new Thread(() -> {
            DocumentItemEntity item;
            if (itemId == null || itemId.trim().isEmpty()) {
                item = AppDatabase.get(ctx).dao().findFirstItemByType("IDCARD_CN");
            } else {
                item = AppDatabase.get(ctx).dao().findItem(itemId);
            }

            if (!isAdded()) return;

            if (item == null || item.infoJson == null || item.infoJson.trim().isEmpty() || "{}".equals(item.infoJson.trim())) {
                requireActivity().runOnUiThread(() -> {
                    llBasicRows.removeAllViews();
                    llIdRows.removeAllViews();
                    llQualityRows.removeAllViews();
                    Toast.makeText(requireContext(), "暂无信息，请先完成扫描", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            Type t = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> map = gson.fromJson(item.infoJson, t);

            Section basic = buildBasicSection(map, unlocked);
            Section id = buildIdSection(map, unlocked);
            Section quality = buildQualitySection(map);

            requireActivity().runOnUiThread(() -> {
                renderSection(llBasicRows, basic);
                renderSection(llIdRows, id);
                renderSection(llQualityRows, quality);

                btnCopyBasic.setOnClickListener(v -> copySectionToClipboard(basic));
                btnCopyId.setOnClickListener(v -> copySectionToClipboard(id));
                btnCopyQuality.setOnClickListener(v -> copySectionToClipboard(quality));
            });
        }).start();
    }

    private void renderSection(LinearLayout container, Section section) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (int i = 0; i < section.items.size(); i++) {
            KV kv = section.items.get(i);

            View row = inflater.inflate(R.layout.item_kv_copy, container, false);
            TextView tvKey = row.findViewById(R.id.tvKey);
            TextView tvValue = row.findViewById(R.id.tvValue);
            ImageButton btnCopy = row.findViewById(R.id.btnCopy);

            tvKey.setText(kv.key);
            tvValue.setText(kv.displayValue);

            row.setContentDescription(kv.key + "：" + kv.displayValue);
            tvKey.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tvValue.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            btnCopy.setOnClickListener(v -> {
                copyToClipboard(kv.key, kv.copyValue);
                Toast.makeText(v.getContext(),
                        v.getContext().getString(R.string.msg_copied_key, kv.key),
                        Toast.LENGTH_SHORT).show();
            });

            tvValue.setOnLongClickListener(v -> {
                copyToClipboard(kv.key, kv.copyValue);
                Toast.makeText(v.getContext(),
                        v.getContext().getString(R.string.msg_copied_key, kv.key),
                        Toast.LENGTH_SHORT).show();
                return true;
            });

            container.addView(row);

            if (i != section.items.size() - 1) {
                View divider = new View(container.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(container, 1)
                );
                lp.setMargins(dp(container, 16), 0, dp(container, 12), 0);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(0xFFE5E7EB);
                container.addView(divider);
            }
        }
    }

    private void copySectionToClipboard(Section section) {
        StringBuilder sb = new StringBuilder();
        for (KV kv : section.items) {
            sb.append(kv.key).append("：").append(kv.copyValue).append("\n");
        }
        copyToClipboard(section.title, sb.toString().trim());
        Toast.makeText(requireContext(),
                getString(R.string.msg_copied_section, section.title),
                Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        String v = text == null ? "" : text;
        cm.setPrimaryClip(ClipData.newPlainText(label, v));

        // 隐私加固：60 秒后清空剪贴板
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

    private Section buildBasicSection(Map<String, Object> info, boolean unlocked) {
        Section s = new Section(getString(R.string.section_basic));
        s.add("姓名", safe(info.get("name")));
        s.add("性别", safe(info.get("sex")));
        s.add("民族", safe(info.get("nation")));
        s.add("出生", safe(info.get("birth")));

        String addr = safe(info.get("address"));
        String addrMasked = addr.isEmpty() ? "" : maskAddress(addr);
        s.add("住址", unlocked ? addr : addrMasked, unlocked ? addr : addrMasked);

        return s;
    }

    private Section buildIdSection(Map<String, Object> info, boolean unlocked) {
        Section s = new Section(getString(R.string.section_id));

        String masked = safe(info.get("idNumMasked"));
        String full = safe(info.get("idNum"));

        if (!unlocked) {
            s.add("身份证号", nonEmpty(masked, "****"), nonEmpty(masked, "****"));
        } else {
            String display = !full.isEmpty() ? full : nonEmpty(masked, "****");
            String copy = !full.isEmpty() ? full : nonEmpty(masked, "****");
            s.add("身份证号", display, copy);
        }

        s.add("签发机关", safe(info.get("authority")));
        s.add("有效期", safe(info.get("validDate")));
        return s;
    }

    private Section buildQualitySection(Map<String, Object> info) {
        Section s = new Section(getString(R.string.section_quality));
        String advFront = safe(info.get("advancedFront"));
        String advBack = safe(info.get("advancedBack"));

        s.add("人像面质量", extractQuality(advFront));
        s.add("人像面告警", extractWarnInfos(advFront));
        s.add("国徽面质量", extractQuality(advBack));
        s.add("国徽面告警", extractWarnInfos(advBack));
        return s;
    }

    private String extractQuality(String advancedInfo) {
        if (advancedInfo == null || advancedInfo.trim().isEmpty()) return "—";
        try {
            JsonObject obj = JsonParser.parseString(advancedInfo).getAsJsonObject();
            JsonElement e = obj.get("Quality");
            if (e == null) return "—";
            if (e.isJsonPrimitive()) return e.getAsString();
            if (e.isJsonObject()) {
                JsonObject q = e.getAsJsonObject();
                if (q.get("Score") != null) return q.get("Score").getAsString();
                if (q.get("Value") != null) return q.get("Value").getAsString();
                if (q.get("Quality") != null) return q.get("Quality").getAsString();
            }
            return "—";
        } catch (Exception ex) {
            return "—";
        }
    }

    private String extractWarnInfos(String advancedInfo) {
        if (advancedInfo == null || advancedInfo.trim().isEmpty()) return "—";
        try {
            JsonObject obj = JsonParser.parseString(advancedInfo).getAsJsonObject();
            JsonElement e = obj.get("WarnInfos");
            if (e == null) return "无";
            if (e.isJsonArray()) {
                List<String> ids = new ArrayList<>();
                for (JsonElement it : e.getAsJsonArray()) {
                    if (it.isJsonPrimitive()) ids.add(it.getAsString());
                    else if (it.isJsonObject()) {
                        JsonObject o = it.getAsJsonObject();
                        if (o.get("WarnId") != null) ids.add(o.get("WarnId").getAsString());
                        else if (o.get("Id") != null) ids.add(o.get("Id").getAsString());
                    }
                }
                return ids.isEmpty() ? "无" : join(ids, "、");
            }
            return e.isJsonPrimitive() ? e.getAsString() : "无";
        } catch (Exception ex) {
            return "—";
        }
    }

    private static String join(List<String> xs, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(xs.get(i));
        }
        return sb.toString();
    }

    private static int dp(View v, int dp) {
        return (int) (dp * v.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String safe(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String nonEmpty(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }

    private static String maskAddress(String addr) {
        if (addr == null) return "";
        addr = addr.trim();
        if (addr.length() <= 6) return "***";
        return addr.substring(0, 2) + "****" + addr.substring(addr.length() - 2);
    }

    private static final class KV {
        final String key;
        final String displayValue;
        final String copyValue;
        KV(String key, String displayValue, String copyValue) {
            this.key = key;
            this.displayValue = displayValue == null ? "" : displayValue;
            this.copyValue = copyValue == null ? "" : copyValue;
        }
    }

    private static final class Section {
        final String title;
        final List<KV> items = new ArrayList<>();
        Section(String title) { this.title = title; }
        void add(String key, String value) { items.add(new KV(key, value, value)); }
        void add(String key, String displayValue, String copyValue) { items.add(new KV(key, displayValue, copyValue)); }
    }
}
