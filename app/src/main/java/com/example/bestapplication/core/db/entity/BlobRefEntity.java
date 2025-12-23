package com.example.bestapplication.core.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "blob_ref", indices = {@Index("itemId"), @Index("slot")})
public class BlobRefEntity {
    @PrimaryKey @NonNull
    public String blobId;
    public String itemId;
    public String slot;     // IDCARD_FRONT / IDCARD_BACK / IDCARD_PDF
    public String relPath;  // 加密文件相对路径
    public String mimeType;
    public long createdAt;

    public BlobRefEntity(@NonNull String blobId, String itemId, String slot, String relPath, String mimeType, long createdAt) {
        this.blobId = blobId;
        this.itemId = itemId;
        this.slot = slot;
        this.relPath = relPath;
        this.mimeType = mimeType;
        this.createdAt = createdAt;
    }
}
