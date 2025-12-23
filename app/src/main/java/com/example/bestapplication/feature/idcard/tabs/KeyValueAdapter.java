package com.example.bestapplication.feature.idcard.tabs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bestapplication.R;

import java.util.List;

public class KeyValueAdapter extends RecyclerView.Adapter<KeyValueAdapter.VH> {

    public static class Item {
        public final String key;
        public final String value;
        public Item(String k, String v) { key = k; value = v; }
    }

    private final List<Item> data;

    public KeyValueAdapter(List<Item> data) { this.data = data; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_kv_copy, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = data.get(position);
        h.tvKey.setText(it.key);
        h.tvValue.setText(it.value == null ? "" : it.value);

        h.btnCopy.setOnClickListener(v -> {
            Context ctx = v.getContext();
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            String text = it.value == null ? "" : it.value;
            cm.setPrimaryClip(ClipData.newPlainText(it.key, text));
            Toast.makeText(ctx, "已复制：" + it.key, Toast.LENGTH_SHORT).show();

            // 隐私加固：60 秒后自动清空（课程作业也可先默认开启）
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    ClipData cur = cm.getPrimaryClip();
                    if (cur != null && cur.getItemCount() > 0) {
                        CharSequence cs = cur.getItemAt(0).coerceToText(ctx);
                        if (cs != null && cs.toString().equals(text)) {
                            cm.setPrimaryClip(ClipData.newPlainText("", ""));
                        }
                    }
                } catch (Exception ignored) {}
            }, 60_000);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvKey, tvValue;
        ImageButton btnCopy;
        VH(@NonNull View itemView) {
            super(itemView);
            tvKey = itemView.findViewById(R.id.tvKey);
            tvValue = itemView.findViewById(R.id.tvValue);
            btnCopy = itemView.findViewById(R.id.btnCopy);
        }
    }
}
