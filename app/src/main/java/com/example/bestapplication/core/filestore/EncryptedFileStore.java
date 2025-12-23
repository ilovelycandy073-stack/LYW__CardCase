package com.example.bestapplication.core.filestore;

import android.content.Context;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class EncryptedFileStore {

    private final Context appCtx;
    private final MasterKey masterKey;

    public EncryptedFileStore(Context ctx) throws Exception {
        this.appCtx = ctx.getApplicationContext();
        this.masterKey = new MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
    }

    private File resolve(String relPath) {
        // 如果你原来不是 filesDir，就保持你原来的根目录策略。
        // 这里增加 canonical 校验，避免 relPath 路径穿越到 filesDir 之外。
        File root = appCtx.getFilesDir();
        File candidate = new File(root, relPath);
        try {
            File rootCanon = root.getCanonicalFile();
            File candCanon = candidate.getCanonicalFile();
            String rootPath = rootCanon.getPath();
            String candPath = candCanon.getPath();
            if (!candPath.startsWith(rootPath + File.separator) && !candPath.equals(rootPath)) {
                throw new IllegalArgumentException("Invalid relPath (path traversal?): " + relPath);
            }
            return candCanon;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve path: " + relPath, e);
        }
    }

    public void saveBytes(String relPath, byte[] data) throws Exception {
        File outFile = resolve(relPath);

        // 1) 确保父目录存在
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IllegalStateException("Cannot create dir: " + parent.getAbsolutePath());
            }
        }

        // 2) EncryptedFile 不允许覆盖：存在就先删
        if (outFile.exists()) {
            if (!outFile.delete()) {
                throw new IllegalStateException("Cannot overwrite, failed to delete: " + outFile.getAbsolutePath());
            }
        }

        // 3) 写入
        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                appCtx,
                outFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        try (FileOutputStream fos = encryptedFile.openFileOutput()) {
            fos.write(data);
            fos.flush();
        }
    }

    // 你原来的 open(relPath)/readBytes(...) 保持不变
    public InputStream open(String relPath) throws Exception {
        File inFile = resolve(relPath);
        if (!inFile.exists()) {
            throw new IllegalStateException("File not found: " + inFile.getAbsolutePath());
        }

        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                appCtx,
                inFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        return encryptedFile.openFileInput();
    }

    /**
     * 删除加密文件。
     *
     * @return true 表示确实删除了一个已存在的文件；false 表示文件原本不存在。
     */
    public boolean delete(String relPath) {
        if (relPath == null || relPath.trim().isEmpty()) {
            return false;
        }
        File target = resolve(relPath);
        if (!target.exists()) return false;
        return target.delete();
    }
}
