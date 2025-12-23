package com.example.bestapplication.core.network.tencent;


import android.util.Base64;

import com.example.bestapplication.core.network.dto.TencentIdCardOcrResponse;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class TencentOcrIdCardClient {

    private static final String HOST = "ocr.tencentcloudapi.com";
    private static final String ENDPOINT = "https://" + HOST + "/";
    private static final String SERVICE = "ocr";
    private static final String ACTION = "IDCardOCR";
    private static final String VERSION = "2018-11-19";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();

    private final String secretId;
    private final String secretKey;
    private final String regionOrEmpty;

    public TencentOcrIdCardClient(String secretId, String secretKey, String regionOrEmpty) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.regionOrEmpty = regionOrEmpty == null ? "" : regionOrEmpty.trim();
    }

    /**
     * @param jpegBytes 证件照片
     * @param cardSide "FRONT" 或 "BACK"（也可不传让系统自动判断，但建议显式）:contentReference[oaicite:10]{index=10}
     * @param timestampSeconds X-TC-Timestamp（秒）
     */
    public TencentIdCardOcrResponse idCardOcr(byte[] jpegBytes, String cardSide, long timestampSeconds) throws Exception {

        // 请求体：ImageBase64 + CardSide + Config（注意 Config 是 String，内容是 JSON 字符串）:contentReference[oaicite:11]{index=11}
        String imageB64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

        Map<String, Object> config = defaultConfig(); // 你要求默认开启的项
        String configJson = gson.toJson(config);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ImageBase64", imageB64);
        payload.put("CardSide", cardSide);
        payload.put("Config", configJson);

        String payloadJson = gson.toJson(payload);

        String authorization = TencentCloudV3Signer.buildAuthorization(
                secretId, secretKey, timestampSeconds,
                SERVICE, HOST, CONTENT_TYPE, payloadJson
        );

        RequestBody body = RequestBody.create(payloadJson, MediaType.parse(CONTENT_TYPE));

        Request.Builder rb = new Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .header("Content-Type", CONTENT_TYPE)
                .header("Host", HOST)
                .header("X-TC-Action", ACTION)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", String.valueOf(timestampSeconds))
                .header("Authorization", authorization);

        // Region 可选 :contentReference[oaicite:12]{index=12}
        if (!regionOrEmpty.isEmpty()) {
            rb.header("X-TC-Region", regionOrEmpty);
        }

        try (okhttp3.Response resp = http.newCall(rb.build()).execute()) {
            String respStr = (resp.body() == null) ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                throw new RuntimeException("HTTP " + resp.code() + ": " + respStr);
            }
            return gson.fromJson(respStr, TencentIdCardOcrResponse.class);
        }
    }

    /**
     * 默认开启：CropIdCard + CropPortrait + Quality +（告警项）
     * 字段名以官方 IDCardOCR 文档为准 :contentReference[oaicite:13]{index=13}
     */
    private Map<String, Object> defaultConfig() {
        Map<String, Object> c = new HashMap<>();
        c.put("CropIdCard", true);
        c.put("CropPortrait", true);
        c.put("Quality", true);

        // 你说的“部分告警”：我按官方列出的告警能力全部开启（可按需改）
        c.put("CopyWarn", true);
        c.put("BorderCheckWarn", true);
        c.put("ReshootWarn", true);
        c.put("DetectPsWarn", true);
        c.put("TempIdWarn", true);
        c.put("InvalidDateWarn", true);
        c.put("ReflectWarn", true);
        return c;
    }
}
