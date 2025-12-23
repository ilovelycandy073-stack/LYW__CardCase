package com.example.bestapplication.core.network.tencent;

/**
 临时方案：把 SecretId/SecretKey 放在客户端代码里。
 */

import com.example.bestapplication.BuildConfig;

public final class TencentSecrets {
    private TencentSecrets() {}

    public static final String SECRET_ID = BuildConfig.TENCENT_SECRET_ID;
    public static final String SECRET_KEY = BuildConfig.TENCENT_SECRET_KEY;
    public static final String REGION = BuildConfig.TENCENT_REGION;
}

