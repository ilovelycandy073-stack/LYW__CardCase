package com.example.bestapplication.core.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "document_type")
public class DocumentTypeEntity {
    @PrimaryKey @NonNull
    public String typeId;
    public String name;
    public boolean builtin;
    public String schemaJson;

    public DocumentTypeEntity(@NonNull String typeId, String name, boolean builtin, String schemaJson) {
        this.typeId = typeId;
        this.name = name;
        this.builtin = builtin;
        this.schemaJson = schemaJson;
    }
}
