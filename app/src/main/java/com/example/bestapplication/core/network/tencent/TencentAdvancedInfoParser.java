package com.example.bestapplication.core.network.tencent;

import android.util.Base64;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class TencentAdvancedInfoParser {
    private TencentAdvancedInfoParser() {}

    /**
     * 尝试从 AdvancedInfo 中提取“裁剪后的身份证图片”（Base64 -> bytes）。
     * 注意：不同接口/版本字段可能略有差异；这里做了容错。
     */
    public static byte[] extractCroppedIdCardJpeg(String advancedInfo) {
        if (advancedInfo == null || advancedInfo.trim().isEmpty()) return null;

        JsonObject obj;
        try {
            obj = JsonParser.parseString(advancedInfo).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }

        // 常见候选字段名（你如果在日志里看到实际字段名，可把它加进来优先）
        String[] keys = new String[] {
                "IdCard", "CropIdCard", "IdCardCrop", "IdCardImage", "CardImage"
        };

        for (String k : keys) {
            byte[] b = tryExtractBase64(obj.get(k));
            if (b != null) return b;
        }

        // 有些会是嵌套对象：{ "CropIdCard": { "Data": "...", "Type": "JPG" } }
        for (String k : keys) {
            JsonElement e = obj.get(k);
            if (e != null && e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                byte[] b = tryExtractBase64(o.get("Data"));
                if (b != null) return b;
            }
        }
        return null;
    }

    private static byte[] tryExtractBase64(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return null;
        String s = e.getAsString();
        if (s == null) return null;
        s = s.trim();
        if (s.length() < 100) return null; // 太短一般不是图
        try {
            return Base64.decode(s, Base64.DEFAULT);
        } catch (Exception ex) {
            return null;
        }
    }
}
