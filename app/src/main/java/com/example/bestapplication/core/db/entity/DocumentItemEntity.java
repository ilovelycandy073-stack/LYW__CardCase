package com.example.bestapplication.core.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "document_item", indices = {@Index("typeId")})
public class DocumentItemEntity {
    @PrimaryKey @NonNull
    public String itemId;
    public String typeId;
    public String infoJson;
    public long updatedAt;
    public String status;

    public DocumentItemEntity(@NonNull String itemId, String typeId, String infoJson, long updatedAt, String status) {
        this.itemId = itemId;
        this.typeId = typeId;
        this.infoJson = infoJson;
        this.updatedAt = updatedAt;
        this.status = status;
    }
}
