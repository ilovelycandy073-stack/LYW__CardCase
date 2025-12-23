package com.example.bestapplication.feature.bankcard.tabs;

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
import com.example.bestapplication.core.filestore.EncryptedFileStore;

import java.io.InputStream;

public class BankCardImagesFragment extends Fragment {

    private static final String ARG_ITEM_ID = "arg_item_id";

    public static final String SLOT_BANKCARD_FRONT = "BANKCARD_FRONT";
    public static final String SLOT_BANKCARD_BACK = "BANKCARD_BACK";

    public static BankCardImagesFragment newInstance(String itemId) {
        BankCardImagesFragment f = new BankCardImagesFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ITEM_ID, itemId);
        f.setArguments(b);
        return f;
    }

    private String itemId;

    private ImageView ivFront, ivBack;
    private View btnViewImages;

    private boolean unlocked = false;

    public BankCardImagesFragment() {
        super(R.layout.frag_bankcard_images);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        itemId = getArguments() == null ? null : getArguments().getString(ARG_ITEM_ID);

        ivFront = view.findViewById(R.id.iv_front);
        ivBack = view.findViewById(R.id.iv_back);
        btnViewImages = view.findViewById(R.id.btn_view_images);

        // 每次创建 Fragment（进入详情页）默认隐藏
        unlocked = false;
        updateButtonUi();
        hideImages();

        btnViewImages.setOnClickListener(v -> {
            if (unlocked) {
                // 仅手动点击“隐藏”才隐藏
                unlocked = false;
                hideImages();
                updateButtonUi();
            } else {
                BiometricGate.requireUnlock(
                        requireActivity(),
                        "查看原图",
                        "用于保护你的证件照片",
                        new BiometricGate.Callback() {
                            @Override public void onSuccess() {
                                unlocked = true;
                                updateButtonUi();
                                loadImages();
                            }

                            @Override public void onFail(String msg) {
                                Toast.makeText(requireContext(),
                                        msg == null ? "解锁失败" : msg,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        // 关键修复：Tab 切换会触发 onPause()，不要在这里自动隐藏
        // 只有当用户退出详情页（Activity finish）才重置，保证下次进入默认隐藏
        if (getActivity() != null && getActivity().isFinishing()) {
            unlocked = false;
            hideImages();
            updateButtonUi();
        }
    }

    private void updateButtonUi() {
        if (btnViewImages instanceof TextView) {
            ((TextView) btnViewImages).setText(unlocked ? "隐藏图片" : "查看原图（需解锁）");
        }
    }

    private void hideImages() {
        if (ivFront != null) ivFront.setImageDrawable(null);
        if (ivBack != null) ivBack.setImageDrawable(null);
    }

    private void loadImages() {
        if (itemId == null) return;

        new Thread(() -> {
            try {
                BlobRefEntity front = AppDatabase.get(requireContext())
                        .dao()
                        .findBlobBySlot(itemId, SLOT_BANKCARD_FRONT);

                BlobRefEntity back = AppDatabase.get(requireContext())
                        .dao()
                        .findBlobBySlot(itemId, SLOT_BANKCARD_BACK);

                EncryptedFileStore store = new EncryptedFileStore(requireContext());

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

                Bitmap finalFront = bFront;
                Bitmap finalBack = bBack;

                requireActivity().runOnUiThread(() -> {
                    if (!unlocked) {
                        hideImages();
                        return;
                    }
                    if (finalFront != null) ivFront.setImageBitmap(finalFront);
                    if (finalBack != null) ivBack.setImageBitmap(finalBack);

                    if (finalFront == null && finalBack == null) {
                        Toast.makeText(requireContext(), "未找到图片，请先拍摄正反面", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception ignored) {
            }
        }).start();
    }
}
