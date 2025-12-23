package com.example.bestapplication.feature.idcard.tabs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.io.OutputStream;

public class IdCardFilesFragment extends Fragment {

    private static final String ARG_ITEM_ID = "arg_item_id";

    public static IdCardFilesFragment newInstance(String itemId) {
        IdCardFilesFragment f = new IdCardFilesFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ITEM_ID, itemId);
        f.setArguments(b);
        return f;
    }

    private String itemId;

    private ActivityResultLauncher<Intent> exportLauncher;
    private String pdfRelPath;

    public IdCardFilesFragment() {
        super(R.layout.frag_idcard_files);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exportLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) return;
            Uri uri = result.getData().getData();
            if (uri == null || pdfRelPath == null) return;

            new Thread(() -> {
                try {
                    EncryptedFileStore store = new EncryptedFileStore(requireContext());
                    try (InputStream in = store.open(pdfRelPath);
                         OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("OutputStream null");
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                    }
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "已导出", Toast.LENGTH_SHORT).show()
                    );
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show()
                    );
                }
            }).start();
        });
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        itemId = getArguments() == null ? null : getArguments().getString(ARG_ITEM_ID);

        view.findViewById(R.id.btn_export_pdf).setOnClickListener(v -> {
            BiometricGate.requireUnlock(
                    requireActivity(),
                    "导出 PDF",
                    "导出到公共位置可能被其他应用读取",
                    new BiometricGate.Callback() {
                        @Override public void onSuccess() { pickExportLocation(); }
                        @Override public void onFail(String msg) {
                            Toast.makeText(requireContext(), msg == null ? "解锁失败" : msg, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        refreshPdfPath();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPdfPath();
    }

    private void refreshPdfPath() {
        new Thread(() -> {
            try {
                DocumentItemEntity item;
                if (itemId == null || itemId.trim().isEmpty()) {
                    item = AppDatabase.get(requireContext()).dao().findFirstItemByType("IDCARD_CN");
                } else {
                    item = AppDatabase.get(requireContext()).dao().findItem(itemId);
                }
                if (item == null) {
                    pdfRelPath = null;
                    return;
                }
                BlobRefEntity pdf = AppDatabase.get(requireContext()).dao().findBlobBySlot(item.itemId, "IDCARD_PDF");
                pdfRelPath = (pdf == null) ? null : pdf.relPath;
            } catch (Exception ignored) {
                pdfRelPath = null;
            }
        }).start();
    }

    private void pickExportLocation() {
        if (pdfRelPath == null) {
            Toast.makeText(requireContext(), "暂无 PDF，请先扫描生成", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "身份证扫描件.pdf");
        exportLauncher.launch(intent);
    }
}
