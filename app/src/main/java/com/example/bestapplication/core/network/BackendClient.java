package com.example.bestapplication.core.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class BackendClient {
    private static volatile BackendApi api;

    private BackendClient() {}

    public static BackendApi api() {
        if (api != null) return api;
        synchronized (BackendClient.class) {
            if (api == null) {
                HttpLoggingInterceptor log = new HttpLoggingInterceptor();
                log.setLevel(HttpLoggingInterceptor.Level.BASIC);

                OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(log)
                        .build();

                Retrofit r = new Retrofit.Builder()
                        .baseUrl(BackendConfig.BASE_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();

                api = r.create(BackendApi.class);
            }
        }
        return api;
    }
}
