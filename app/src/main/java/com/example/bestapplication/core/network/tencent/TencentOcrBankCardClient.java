package com.example.bestapplication.core.network.tencent;

import android.util.Base64;

import com.example.bestapplication.core.network.dto.TencentBankCardOcrResponse;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class TencentOcrBankCardClient {

    private static final String HOST = "ocr.tencentcloudapi.com";
    private static final String ENDPOINT = "https://" + HOST + "/";
    private static final String SERVICE = "ocr";
    private static final String ACTION = "BankCardOCR";
    private static final String VERSION = "2018-11-19";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();

    private final String secretId;
    private final String secretKey;
    private final String regionOrEmpty;

    public TencentOcrBankCardClient(String secretId, String secretKey, String regionOrEmpty) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.regionOrEmpty = regionOrEmpty == null ? "" : regionOrEmpty.trim();
    }

    public TencentBankCardOcrResponse bankCardOcr(byte[] jpegBytes, long timestampSeconds) throws Exception {
        String imageB64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ImageBase64", imageB64);

        // 按银行卡识别能力，开启对齐裁剪 + 告警 + 质量分
        payload.put("RetBorderCutImage", true);
        payload.put("RetCardNoImage", false);
        payload.put("EnableCopyCheck", true);
        payload.put("EnableReshootCheck", true);
        payload.put("EnableBorderCheck", true);
        payload.put("EnableQualityValue", true);

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

        // Region 可选
        if (!regionOrEmpty.isEmpty()) {
            rb.header("X-TC-Region", regionOrEmpty);
        }

        try (okhttp3.Response resp = http.newCall(rb.build()).execute()) {
            String respStr = (resp.body() == null) ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                throw new RuntimeException("HTTP " + resp.code() + ": " + respStr);
            }
            return gson.fromJson(respStr, TencentBankCardOcrResponse.class);
        }
    }
}
