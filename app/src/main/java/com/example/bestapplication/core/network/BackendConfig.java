package com.example.bestapplication.core.network;

public final class BackendConfig {
    private BackendConfig() {}

    // TODO: 改成你的后端地址，注意必须以 / 结尾
    public static final String BASE_URL = "https://YOUR_BACKEND_HOST/";

    // TODO: V1 可先写死，后续再做 /v1/auth/token
    public static final String BEARER_TOKEN = "Bearer YOUR_TOKEN";
}
