package com.example.bestapplication.core.network.dto;

public class TencentIdCardOcrResponse {
    public Resp Response;

    public static class Resp {
        public String Name;
        public String Sex;
        public String Nation;
        public String Birth;
        public String Address;
        public String IdNum;
        public String Authority;
        public String ValidDate;

        // 扩展信息（JSON 字符串），包含 Quality / WarnInfos / BorderCodeValue / 裁剪图等 :contentReference[oaicite:9]{index=9}
        public String AdvancedInfo;

        public String RequestId;
        public Err Error;
    }

    public static class Err {
        public String Code;
        public String Message;
    }
}
