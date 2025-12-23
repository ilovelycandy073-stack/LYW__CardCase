package com.example.bestapplication.core.network.dto;

import java.util.Map;

public class IdCardOcrResponse {
    public String traceId;
    public Map<String, Object> frontResult;
    public Map<String, Object> backResult;
    public Map<String, Object> normalized; // 建议后端返回：name、idNumMasked、validDate 等
    public ApiError error;

    public static class ApiError {
        public String code;
        public String message;
    }
}
