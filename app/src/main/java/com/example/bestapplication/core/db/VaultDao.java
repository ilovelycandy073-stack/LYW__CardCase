package com.example.bestapplication.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.bestapplication.core.db.entity.BlobRefEntity;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.example.bestapplication.core.db.entity.DocumentTypeEntity;

import java.util.List;

@Dao
public interface VaultDao {

    // Types
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTypes(List<DocumentTypeEntity> types);

    @Query("SELECT * FROM document_type ORDER BY builtin DESC, name ASC")
    List<DocumentTypeEntity> listTypes();

    // Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItem(DocumentItemEntity item);

    @Query("SELECT * FROM document_item WHERE typeId = :typeId LIMIT 1")
    DocumentItemEntity findFirstItemByType(String typeId);

    /**
     * 列出某一类证件的所有条目（如银行卡），按最近更新时间倒序。
     */
    @Query("SELECT * FROM document_item WHERE typeId = :typeId ORDER BY updatedAt DESC")
    List<DocumentItemEntity> listItemsByType(String typeId);

    @Query("SELECT * FROM document_item WHERE itemId = :itemId LIMIT 1")
    DocumentItemEntity findItem(String itemId);

    @Update
    void updateItem(DocumentItemEntity item);

    // Blobs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertBlob(BlobRefEntity blob);

    @Query("SELECT * FROM blob_ref WHERE itemId = :itemId AND slot = :slot LIMIT 1")
    BlobRefEntity findBlobBySlot(String itemId, String slot);

    /**
     * 列出某个 item 下的所有加密文件引用（用于删除时先拿到 relPath）。
     */
    @Query("SELECT * FROM blob_ref WHERE itemId = :itemId ORDER BY createdAt DESC")
    List<BlobRefEntity> listBlobsByItem(String itemId);

    @Query("DELETE FROM blob_ref WHERE itemId = :itemId")
    void deleteBlobsByItem(String itemId);

    @Query("DELETE FROM document_item WHERE itemId = :itemId")
    void deleteItem(String itemId);
}
