package com.example.bestapplication.core.network.dto;

import java.util.List;

public class TencentBankCardOcrResponse {
    public Resp Response;

    public static class Resp {
        public String CardNo;
        public String BankInfo;
        public String ValidDate;
        public String CardType;
        public String CardName;

        // Base64 图片数据（可为 null）
        public String BorderCutImage;
        public String CardNoImage;

        // 告警码数组（可为 null）
        public List<Integer> WarningCode;

        // 质量分（可为 null）
        public Integer QualityValue;

        public String CardCategory;

        public String RequestId;
        public Err Error;
    }

    public static class Err {
        public String Code;
        public String Message;
    }
}
