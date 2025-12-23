package com.example.bestapplication.core.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

public final class KeyMaterial {
    private static final String KEY_DB_PASSPHRASE = "db_passphrase_b64";

    private KeyMaterial() {}

    /**
     * 返回一个“字符串口令”（Base64），用于 SQLCipher。
     * 该口令本身存放在 EncryptedSharedPreferences 中（仍是加密存储）。
     */
    public static String getOrCreateDbPassphraseString(Context ctx) {
        SharedPreferences sp = SecurePrefs.get(ctx);
        String b64 = sp.getString(KEY_DB_PASSPHRASE, null);
        if (b64 != null && !b64.isEmpty()) {
            return b64;
        }

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String created = Base64.encodeToString(random, Base64.NO_WRAP);

        sp.edit().putString(KEY_DB_PASSPHRASE, created).apply();
        return created;
    }
}
