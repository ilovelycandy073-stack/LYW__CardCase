package com.example.bestapplication.core.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SecurePrefs {
    private static final String FILE_NAME = "secure_prefs";
    private static volatile SharedPreferences instance;

    private SecurePrefs() {}

    public static SharedPreferences get(Context ctx) {
        if (instance != null) return instance;
        synchronized (SecurePrefs.class) {
            if (instance == null) {
                try {
                    MasterKey masterKey = new MasterKey.Builder(ctx)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build();

                    instance = EncryptedSharedPreferences.create(
                            ctx,
                            FILE_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );
                } catch (Exception e) {
                    throw new RuntimeException("SecurePrefs init failed", e);
                }
            }
        }
        return instance;
    }
}
