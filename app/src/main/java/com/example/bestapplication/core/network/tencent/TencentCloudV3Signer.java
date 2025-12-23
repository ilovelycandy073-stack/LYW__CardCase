package com.example.bestapplication.core.network.tencent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TencentCloudV3Signer {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String HMAC_ALGO = "HmacSHA256";

    private TencentCloudV3Signer() {}

    /**
     * 生成 Authorization 头：
     * TC3-HMAC-SHA256 Credential=SecretId/Date/service/tc3_request, SignedHeaders=content-type;host, Signature=...
     */
    public static String buildAuthorization(
            String secretId,
            String secretKey,
            long timestampSeconds,
            String service,
            String host,
            String contentType,
            String payloadJson
    ) throws Exception {

        // 1) Date 必须是 UTC 日期（yyyy-MM-dd），严格按官方说明从 X-TC-Timestamp 推导 :contentReference[oaicite:6]{index=6}
        String date = utcDate(timestampSeconds);

        // 2) CanonicalRequest
        String httpRequestMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = ""; // POST 固定空
        String canonicalHeaders =
                "content-type:" + contentType.trim().toLowerCase(Locale.ROOT) + "\n" +
                        "host:" + host.trim().toLowerCase(Locale.ROOT) + "\n";
        String signedHeaders = "content-type;host";
        String hashedRequestPayload = sha256Hex(payloadJson);

        String canonicalRequest = httpRequestMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedRequestPayload;

        // 3) StringToSign
        String credentialScope = date + "/" + service + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);

        String stringToSign = ALGORITHM + "\n"
                + timestampSeconds + "\n"
                + credentialScope + "\n"
                + hashedCanonicalRequest;

        // 4) 派生签名密钥（TC3 + SecretKey） :contentReference[oaicite:7]{index=7}
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");

        // 5) Signature
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

        // 6) Authorization
        return ALGORITHM
                + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private static String utcDate(long timestampSeconds) {
        Date d = new Date(timestampSeconds * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(d);
    }

    private static byte[] hmacSha256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key, HMAC_ALGO));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte value : b) {
            String hex = Integer.toHexString(value & 0xff);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
