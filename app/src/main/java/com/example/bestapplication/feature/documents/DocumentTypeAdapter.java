package com.example.bestapplication.feature.documents;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bestapplication.R;
import com.example.bestapplication.core.db.entity.DocumentTypeEntity;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DocumentTypeAdapter extends RecyclerView.Adapter<DocumentTypeAdapter.VH> {

    public interface OnClick { void onClick(DocumentTypeEntity type); }

    private final List<DocumentTypeEntity> data;
    private final OnClick onClick;

    public DocumentTypeAdapter(List<DocumentTypeEntity> data, OnClick onClick) {
        this.data = (data == null) ? new ArrayList<>() : new ArrayList<>(data);
        this.onClick = onClick;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        String id = data.get(position).typeId;
        return id == null ? position : id.hashCode();
    }

    public void move(int from, int to) {
        if (from < 0 || to < 0 || from >= data.size() || to >= data.size()) return;
        DocumentTypeEntity m = data.remove(from);
        data.add(to, m);
        notifyItemMoved(from, to);
    }


    public List<DocumentTypeEntity> getItems() { return data; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_type, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DocumentTypeEntity t = data.get(position);

        String title = simplifyName(t.name);
        h.title.setText(title);

        String iconChar = (title != null && !title.isEmpty()) ? title.substring(0, 1) : "证";
        h.iconText.setText(iconChar);

        h.iconTile.setCardBackgroundColor(pickColor(t.typeId));

        h.itemView.setOnClickListener(v -> onClick.onClick(t));
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView iconTile;
        TextView iconText;
        TextView title;

        VH(@NonNull View itemView) {
            super(itemView);
            iconTile = itemView.findViewById(R.id.icon_tile);
            iconText = itemView.findViewById(R.id.icon_text);
            title = itemView.findViewById(R.id.title);
        }
    }

    private static String simplifyName(String name) {
        if (name == null) return "";
        // 去掉中文括号内容：身份证（大陆） -> 身份证
        return name.replaceAll("（.*?）", "").trim();
    }

    private static int pickColor(String typeId) {
        // 你可以按喜好换色；这里先给一个清爽的配色
        if ("IDCARD_CN".equals(typeId)) return Color.parseColor("#87CEFA");
        if ("VEHICLE_LICENSE".equals(typeId)) return Color.parseColor("#AFEEEE"); // 银行卡入口
        if ("DRIVER_LICENSE".equals(typeId)) return Color.parseColor("#FFB6C1");
        if ("STUDENT_ID".equals(typeId)) return Color.parseColor("#E6E6FA");
        if ("WORK_ID".equals(typeId)) return Color.parseColor("#C0C0C0");
        return Color.parseColor("#4F7DFF");
    }



}
