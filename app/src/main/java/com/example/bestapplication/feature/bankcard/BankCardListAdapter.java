package com.example.bestapplication.feature.bankcard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bestapplication.R;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

public class BankCardListAdapter extends RecyclerView.Adapter<BankCardListAdapter.VH> {

    public interface OnClick {
        void onClick(DocumentItemEntity item);
    }

    public interface OnLongPress {
        void onLongPress(View anchorView, DocumentItemEntity item);
    }

    private List<DocumentItemEntity> items;
    private final OnClick onClick;
    private final OnLongPress onLongPress;

    public BankCardListAdapter(@NonNull List<DocumentItemEntity> items,
                               @NonNull OnClick onClick,
                               @NonNull OnLongPress onLongPress) {
        this.items = items;
        this.onClick = onClick;
        this.onLongPress = onLongPress;
    }

    public void replace(@NonNull List<DocumentItemEntity> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bankcard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DocumentItemEntity item = items.get(position);

        CardMeta meta = parseMeta(item == null ? null : item.infoJson, item == null ? null : item.status);
        h.tvTitle.setText(meta.title);
        h.tvSubtitle.setText(meta.subtitle);
        h.tvSubtitle.setVisibility(meta.subtitle == null || meta.subtitle.trim().isEmpty() ? View.GONE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> onClick.onClick(item));
        h.itemView.setOnLongClickListener(v -> {
            onLongPress.onLongPress(v, item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvSubtitle;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.title);
            tvSubtitle = itemView.findViewById(R.id.subtitle);
        }
    }

    // -------- 解析 infoJson -> 列表显示（容错，避免字段名差异导致崩溃） --------

    private static CardMeta parseMeta(String infoJson, String status) {
        String cardName = "";
        String bankInfo = "";
        String cardNo = "";

        try {
            if (infoJson != null && !infoJson.trim().isEmpty() && !"{}".equals(infoJson.trim())) {
                JsonObject obj = JsonParser.parseString(infoJson).getAsJsonObject();

                cardName = getFirstString(obj, "CardName", "cardName", "card_name");
                bankInfo = getFirstString(obj, "BankInfo", "bankInfo", "bank_info");
                cardNo = getFirstString(obj, "CardNo", "cardNo", "card_no");
            }
        } catch (Exception ignored) {}

        String title;
        if (cardName != null && !cardName.trim().isEmpty()) {
            title = cardName.trim();
        } else {
            title = "未扫描银行卡";
        }

        String subtitle = "";
        String first4 = "";
        if (cardNo != null) {
            String cn = cardNo.trim();
            if (cn.length() >= 4) {
                first4 = cn.substring(0, 4);
            }
        }

        // 你的要求：副标题=BankInfo + “首号 1234”（CardNo 前四位）
        if (bankInfo != null && !bankInfo.trim().isEmpty()) {
            subtitle = bankInfo.trim();
            if (!first4.isEmpty()) {
                subtitle = subtitle + "  首号 " + first4;
            }
        } else if (!first4.isEmpty()) {
            subtitle = "首号 " + first4;
        }

        return new CardMeta(title, subtitle);
    }

    private static String getFirstString(JsonObject obj, String... keys) {
        for (String k : keys) {
            try {
                if (obj.has(k) && !obj.get(k).isJsonNull()) {
                    String v = obj.get(k).getAsString();
                    if (v != null && !v.trim().isEmpty()) return v;
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static class CardMeta {
        final String title;
        final String subtitle;

        CardMeta(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}
