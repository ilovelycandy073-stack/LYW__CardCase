package com.example.bestapplication.feature.idcard.tabs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bestapplication.R;
import com.example.bestapplication.core.auth.BiometricGate;
import com.example.bestapplication.core.db.AppDatabase;
import com.example.bestapplication.core.db.entity.BlobRefEntity;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.example.bestapplication.core.filestore.EncryptedFileStore;

import java.io.InputStream;

public class IdCardImagesFragment extends Fragment {

    private static final String ARG_ITEM_ID = "arg_item_id";

    public static IdCardImagesFragment newInstance(String itemId) {
        IdCardImagesFragment f = new IdCardImagesFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ITEM_ID, itemId);
        f.setArguments(b);
        return f;
    }

    private String itemId;

    private ImageView ivFront, ivBack;
    private View btnViewImages;

    private boolean unlocked = false;

    public IdCardImagesFragment() {
        super(R.layout.frag_idcard_images);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        itemId = getArguments() == null ? null : getArguments().getString(ARG_ITEM_ID);

        ivFront = view.findViewById(R.id.iv_front);
        ivBack = view.findViewById(R.id.iv_back);
        btnViewImages = view.findViewById(R.id.btn_view_images);

        updateButtonUi();

        btnViewImages.setOnClickListener(v -> {
            if (unlocked) {
                unlocked = false;
                hideImages();
                updateButtonUi();
            } else {
                BiometricGate.requireUnlock(
                        requireActivity(),
                        "查看原图",
                        "用于保护你的证件照片",
                        new BiometricGate.Callback() {
                            @Override
                            public void onSuccess() {
                                unlocked = true;
                                updateButtonUi();
                                loadImages();
                            }

                            @Override
                            public void onFail(String msg) {
                                Toast.makeText(requireContext(),
                                        msg == null ? "解锁失败" : msg,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        hideImages();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!unlocked) hideImages();
    }

    private void updateButtonUi() {
        if (btnViewImages instanceof TextView) {
            ((TextView) btnViewImages).setText(unlocked ? "隐藏图片" : "查看原图");
        }
    }

    private void hideImages() {
        if (ivFront != null) ivFront.setImageDrawable(null);
        if (ivBack != null) ivBack.setImageDrawable(null);
    }

    private void loadImages() {
        final Context ctx = getContext();
        if (ctx == null) return;

        new Thread(() -> {
            try {
                DocumentItemEntity item;
                if (itemId == null || itemId.trim().isEmpty()) {
                    item = AppDatabase.get(ctx).dao().findFirstItemByType("IDCARD_CN");
                } else {
                    item = AppDatabase.get(ctx).dao().findItem(itemId);
                }
                if (item == null) return;

                BlobRefEntity front = AppDatabase.get(ctx).dao().findBlobBySlot(item.itemId, "IDCARD_FRONT");
                BlobRefEntity back = AppDatabase.get(ctx).dao().findBlobBySlot(item.itemId, "IDCARD_BACK");

                EncryptedFileStore store = new EncryptedFileStore(ctx);

                Bitmap bFront = null, bBack = null;
                if (front != null) {
                    try (InputStream in = store.open(front.relPath)) {
                        bFront = BitmapFactory.decodeStream(in);
                    }
                }
                if (back != null) {
                    try (InputStream in = store.open(back.relPath)) {
                        bBack = BitmapFactory.decodeStream(in);
                    }
                }

                Bitmap finalBFront = bFront;
                Bitmap finalBBack = bBack;

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!unlocked) {
                        hideImages();
                        return;
                    }
                    if (finalBFront != null) ivFront.setImageBitmap(finalBFront);
                    if (finalBBack != null) ivBack.setImageBitmap(finalBBack);

                    if (finalBFront == null && finalBBack == null) {
                        Toast.makeText(requireContext(), "未找到图片，请先完成扫描", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception ignored) {
            }
        }).start();
    }
}
