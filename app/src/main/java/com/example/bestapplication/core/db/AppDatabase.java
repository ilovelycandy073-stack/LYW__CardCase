package com.example.bestapplication.core.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.bestapplication.core.db.entity.BlobRefEntity;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.example.bestapplication.core.db.entity.DocumentTypeEntity;
import com.example.bestapplication.core.security.KeyMaterial;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SupportFactory;

@Database(
        entities = {DocumentTypeEntity.class, DocumentItemEntity.class, BlobRefEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract VaultDao dao();

    public static AppDatabase get(Context ctx) {
        if (instance != null) return instance;
        synchronized (AppDatabase.class) {
            if (instance == null) {
                String passphrase = KeyMaterial.getOrCreateDbPassphraseString(ctx);
                byte[] passphraseBytes = SQLiteDatabase.getBytes(passphrase.toCharArray());
                SupportFactory factory = new SupportFactory(passphraseBytes);

                instance = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "vault.db")
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration()
                        .build();

            }
        }
        return instance;
    }
}
