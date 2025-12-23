package com.example.bestapplication;

import android.app.Application;

import com.example.bestapplication.core.db.AppDatabase;
import com.example.bestapplication.core.db.entity.DocumentTypeEntity;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // SQLCipher 必须加载 native libs
        SQLiteDatabase.loadLibs(this);

        // 初始化默认“证件类型”（幂等）
        new Thread(() -> {
            AppDatabase db = AppDatabase.get(this);
            db.dao().upsertTypes(Arrays.asList(
                    new DocumentTypeEntity("IDCARD_CN", "身份证（大陆）", true, "{}"),
                    new DocumentTypeEntity("DRIVER_LICENSE", "驾驶证", true, "{}"),
                    // 复用 VEHICLE_LICENSE 作为“银行卡”入口（按需求：最少改动）
                    new DocumentTypeEntity("VEHICLE_LICENSE", "银行卡", true, "{}"),
                    new DocumentTypeEntity("STUDENT_ID", "学生证", true, "{}"),
                    new DocumentTypeEntity("WORK_ID", "工作证", true, "{}")
            ));
        }).start();
    }
}
