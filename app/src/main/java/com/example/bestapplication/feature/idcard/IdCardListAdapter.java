package com.example.bestapplication.feature.idcard;

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

public class IdCardListAdapter extends RecyclerView.Adapter<IdCardListAdapter.VH> {

    public interface OnClick { void onClick(String itemId); }
    public interface OnLongPress { void onLongPress(View v, DocumentItemEntity item); }

    private List<DocumentItemEntity> items;
    private final OnClick onClick;
    private final OnLongPress onLongPress;

    public IdCardListAdapter(List<DocumentItemEntity> items, OnClick onClick, OnLongPress onLongPress) {
        this.items = items;
        this.onClick = onClick;
        this.onLongPress = onLongPress;
    }

    public void replace(List<DocumentItemEntity> data) {
        this.items = data;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_idcard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DocumentItemEntity item = items.get(position);
        h.title.setText(getDisplayTitle(item));

        h.itemView.setOnClickListener(v -> onClick.onClick(item.itemId));
        h.itemView.setOnLongClickListener(v -> {
            onLongPress.onLongPress(v, item);
            return true;
        });
    }

    @Override public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
        }
    }

    public static String getDisplayTitle(DocumentItemEntity item) {
        String custom = getCustomTitle(item);
        if (custom != null && !custom.trim().isEmpty()) return custom.trim();

        String name = "";
        try {
            JsonObject obj = getInfoObj(item);
            if (obj.has("name") && !obj.get("name").isJsonNull()) {
                name = obj.get("name").getAsString();
            }
        } catch (Exception ignored) {}

        if (name != null && !name.trim().isEmpty()) return name.trim();
        return "未扫描身份证";
    }

    public static String getDefaultTitle(DocumentItemEntity item) {
        // 默认：姓名（若无姓名就“身份证”）
        String name = "";
        try {
            JsonObject obj = getInfoObj(item);
            if (obj.has("name") && !obj.get("name").isJsonNull()) {
                name = obj.get("name").getAsString();
            }
        } catch (Exception ignored) {}
        return (name != null && !name.trim().isEmpty()) ? name.trim() : "身份证";
    }

    public static String getCustomTitle(DocumentItemEntity item) {
        try {
            JsonObject obj = getInfoObj(item);
            if (obj.has("customTitle") && !obj.get("customTitle").isJsonNull()) {
                return obj.get("customTitle").getAsString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static JsonObject getInfoObj(DocumentItemEntity item) {
        if (item == null || item.infoJson == null) return new JsonObject();
        String s = item.infoJson.trim();
        if (s.isEmpty() || "{}".equals(s)) return new JsonObject();
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
