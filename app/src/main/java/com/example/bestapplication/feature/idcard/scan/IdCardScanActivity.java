package com.example.bestapplication.feature.idcard.scan;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.bestapplication.R;
import com.example.bestapplication.core.db.AppDatabase;
import com.example.bestapplication.core.db.VaultDao;
import com.example.bestapplication.core.db.entity.BlobRefEntity;
import com.example.bestapplication.core.db.entity.DocumentItemEntity;
import com.example.bestapplication.core.filestore.EncryptedFileStore;
import com.example.bestapplication.core.network.dto.TencentIdCardOcrResponse;
import com.example.bestapplication.core.network.tencent.TencentAdvancedInfoParser;
import com.example.bestapplication.core.network.tencent.TencentOcrIdCardClient;
import com.example.bestapplication.core.network.tencent.TencentSecrets;
import com.example.bestapplication.core.pdf.IdCardPdfService;
import com.example.bestapplication.feature.idcard.IdCardDetailActivity;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

public class IdCardScanActivity extends AppCompatActivity {

    private static final String TAG = "IdCardScan";

    private PreviewView previewView;
    private ScanOverlayView overlayView;
    private TextView tip;

    private ImageCapture imageCapture;
    private Executor mainExecutor;

    private byte[] frontJpeg;
    private byte[] backJpeg;

    private String itemId;

    private enum Step { FRONT, BACK, PROCESSING }
    private Step step = Step.FRONT;

    private final ActivityResultLauncher<String> camPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, "未获得相机权限，无法扫描证件", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idcard_scan);

        itemId = getIntent().getStringExtra(IdCardDetailActivity.EXTRA_ITEM_ID);

        previewView = findViewById(R.id.preview);
        overlayView = findViewById(R.id.overlay);
        tip = findViewById(R.id.tip);
        mainExecutor = ContextCompat.getMainExecutor(this);

        findViewById(R.id.btn_shot).setOnClickListener(v -> shoot());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            camPerm.launch(Manifest.permission.CAMERA);
        }

        renderStep();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (overlayView != null && step != Step.PROCESSING) overlayView.start();
    }

    @Override
    protected void onPause() {
        if (overlayView != null) overlayView.stop();
        super.onPause();
    }

    private void renderStep() {
        if (step == Step.FRONT) {
            tip.setText("拍摄人像面");
            if (overlayView != null) overlayView.start();
        } else if (step == Step.BACK) {
            tip.setText("拍摄国徽面");
            if (overlayView != null) overlayView.start();
        } else {
            tip.setText("处理中，请稍候…");
            if (overlayView != null) overlayView.stop();
        }
    }

    private void setUiBusy(boolean busy) {
        findViewById(R.id.btn_shot).setEnabled(!busy);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(92)
                        .build();

                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "camera init failed", e);
                Toast.makeText(this, "相机初始化失败", Toast.LENGTH_LONG).show();
                finish();
            }
        }, mainExecutor);
    }

    private void shoot() {
        if (imageCapture == null) return;
        if (step == Step.PROCESSING) return;

        imageCapture.takePicture(mainExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    byte[] jpeg = imageProxyToJpegBytes(image);

                    if (step == Step.FRONT) {
                        frontJpeg = jpeg;
                        step = Step.BACK;
                        renderStep();
                        Toast.makeText(IdCardScanActivity.this, "已拍摄人像面，请拍摄国徽面", Toast.LENGTH_SHORT).show();
                    } else if (step == Step.BACK) {
                        backJpeg = jpeg;
                        step = Step.PROCESSING;
                        renderStep();
                        setUiBusy(true);
                        runPipeline();
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "process photo failed", ex);
                    Toast.makeText(IdCardScanActivity.this, "处理照片失败，请重试", Toast.LENGTH_LONG).show();
                } finally {
                    image.close();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "takePicture error", exception);
                Toast.makeText(IdCardScanActivity.this, "拍照失败，请重试", Toast.LENGTH_LONG).show();
            }
        });
    }

    private static byte[] imageProxyToJpegBytes(ImageProxy image) {
        int fmt = image.getFormat();

        if (fmt == ImageFormat.JPEG) {
            ByteBuffer buf = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        }

        if (fmt == ImageFormat.YUV_420_888) {
            return YuvToJpegConverter.toJpeg(image, 92);
        }

        throw new IllegalArgumentException("Unsupported ImageProxy format: " + fmt);
    }

    private void runPipeline() {
        Toast.makeText(this, "正在识别证件信息…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            DocumentItemEntity item = null;

            try {
                if (frontJpeg == null || backJpeg == null) {
                    throw new IllegalStateException("front/back image missing");
                }
                if (isEmpty(TencentSecrets.SECRET_ID) || isEmpty(TencentSecrets.SECRET_KEY)) {
                    throw new IllegalStateException("请先在 TencentSecrets.java 填写 SECRET_ID / SECRET_KEY");
                }

                AppDatabase db = AppDatabase.get(this);
                VaultDao dao = db.dao();

                // 1) 获取或创建条目（优先用 itemId）
                String targetId = (itemId == null) ? "" : itemId.trim();
                if (!targetId.isEmpty()) {
                    item = dao.findItem(targetId);
                    if (item == null) {
                        item = new DocumentItemEntity(
                                targetId,
                                "IDCARD_CN",
                                "{}",
                                System.currentTimeMillis(),
                                "OCR_PROCESSING"
                        );
                    } else {
                        item.status = "OCR_PROCESSING";
                        item.updatedAt = System.currentTimeMillis();
                    }
                } else {
                    item = dao.findFirstItemByType("IDCARD_CN");
                    if (item == null) {
                        item = new DocumentItemEntity(
                                UUID.randomUUID().toString(),
                                "IDCARD_CN",
                                "{}",
                                System.currentTimeMillis(),
                                "OCR_PROCESSING"
                        );
                    } else {
                        item.status = "OCR_PROCESSING";
                        item.updatedAt = System.currentTimeMillis();
                    }
                }
                dao.upsertItem(item);

                // 2) 保留自定义标题（避免 OCR 覆盖）
                String preservedCustomTitle = "";
                try {
                    JsonObject old = safeParseJsonObject(item.infoJson);
                    preservedCustomTitle = getString(old, "customTitle");
                } catch (Exception ignored) {}

                // 3) OCR 两次：FRONT/BACK
                TencentOcrIdCardClient client = new TencentOcrIdCardClient(
                        TencentSecrets.SECRET_ID,
                        TencentSecrets.SECRET_KEY,
                        TencentSecrets.REGION
                );

                long ts1 = System.currentTimeMillis() / 1000L;
                TencentIdCardOcrResponse front = client.idCardOcr(frontJpeg, "FRONT", ts1);
                if (front == null || front.Response == null) throw new RuntimeException("Empty response(front)");
                if (front.Response.Error != null) {
                    throw new RuntimeException(front.Response.Error.Code + ": " + front.Response.Error.Message);
                }

                long ts2 = System.currentTimeMillis() / 1000L;
                TencentIdCardOcrResponse back = client.idCardOcr(backJpeg, "BACK", ts2);
                if (back == null || back.Response == null) throw new RuntimeException("Empty response(back)");
                if (back.Response.Error != null) {
                    throw new RuntimeException(back.Response.Error.Code + ": " + back.Response.Error.Message);
                }

                // 4) 从 AdvancedInfo 提取裁剪图，取不到则回退原图
                byte[] frontCrop = TencentAdvancedInfoParser.extractCroppedIdCardJpeg(front.Response.AdvancedInfo);
                byte[] backCrop  = TencentAdvancedInfoParser.extractCroppedIdCardJpeg(back.Response.AdvancedInfo);

                byte[] frontToSave = (frontCrop != null) ? frontCrop : frontJpeg;
                byte[] backToSave  = (backCrop  != null) ? backCrop  : backJpeg;

                // 5) 组织 infoJson
                Map<String, Object> info = new HashMap<>();
                info.put("name", front.Response.Name);
                info.put("sex", front.Response.Sex);
                info.put("nation", front.Response.Nation);
                info.put("birth", front.Response.Birth);
                info.put("address", front.Response.Address);

                info.put("idNum", front.Response.IdNum);
                info.put("idNumMasked", maskId(front.Response.IdNum));

                info.put("authority", back.Response.Authority);
                info.put("validDate", back.Response.ValidDate);

                info.put("advancedFront", front.Response.AdvancedInfo);
                info.put("advancedBack", back.Response.AdvancedInfo);
                info.put("requestIdFront", front.Response.RequestId);
                info.put("requestIdBack", back.Response.RequestId);

                // 恢复 customTitle（非空才写回）
                if (preservedCustomTitle != null && !preservedCustomTitle.trim().isEmpty()) {
                    info.put("customTitle", preservedCustomTitle.trim());
                }

                // 6) 加密保存图片（覆盖写）
                EncryptedFileStore store = new EncryptedFileStore(this);
                String frontPath = "idcard/" + item.itemId + "/front.jpg.enc";
                String backPath  = "idcard/" + item.itemId + "/back.jpg.enc";

                store.saveBytes(frontPath, frontToSave);
                store.saveBytes(backPath, backToSave);

                dao.upsertBlob(new BlobRefEntity(
                        item.itemId + "_IDCARD_FRONT",
                        item.itemId,
                        "IDCARD_FRONT",
                        frontPath,
                        "image/jpeg",
                        System.currentTimeMillis()
                ));
                dao.upsertBlob(new BlobRefEntity(
                        item.itemId + "_IDCARD_BACK",
                        item.itemId,
                        "IDCARD_BACK",
                        backPath,
                        "image/jpeg",
                        System.currentTimeMillis()
                ));

                // 7) 生成 PDF（两页图片）
                IdCardPdfService pdfService = new IdCardPdfService();
                byte[] pdfBytes;
                try (InputStream fIn = store.open(frontPath);
                     InputStream bIn = store.open(backPath)) {
                    pdfBytes = pdfService.buildPdfBytes(fIn, bIn, info);
                }

                String pdfPath = "idcard/" + item.itemId + "/idcard.pdf.enc";
                store.saveBytes(pdfPath, pdfBytes);

                dao.upsertBlob(new BlobRefEntity(
                        item.itemId + "_IDCARD_PDF",
                        item.itemId,
                        "IDCARD_PDF",
                        pdfPath,
                        "application/pdf",
                        System.currentTimeMillis()
                ));

                // 8) 更新条目
                item.infoJson = new Gson().toJson(info);
                item.status = "COMPLETED";
                item.updatedAt = System.currentTimeMillis();
                dao.upsertItem(item);

                runOnUiThread(() -> {
                    Toast.makeText(this, "已完成保存", Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "pipeline failed", e);

                if (item != null) {
                    try {
                        AppDatabase db = AppDatabase.get(this);
                        item.status = "FAILED";
                        item.updatedAt = System.currentTimeMillis();
                        db.dao().upsertItem(item);
                    } catch (Exception ignored) {}
                }

                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    setUiBusy(false);

                    step = Step.FRONT;
                    frontJpeg = null;
                    backJpeg = null;
                    renderStep();

                    Toast.makeText(this, "处理失败：" + msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String maskId(String id) {
        if (id == null) return "";
        id = id.trim();
        if (id.length() < 8) return "****";
        return id.substring(0, 4) + "**********" + id.substring(id.length() - 4);
    }

    private static JsonObject safeParseJsonObject(String json) {
        try {
            if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) return new JsonObject();
            JsonElement el = JsonParser.parseString(json);
            if (el != null && el.isJsonObject()) return el.getAsJsonObject();
            return new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static String getString(JsonObject obj, String key) {
        try {
            if (obj == null) return "";
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return "";
            return e.getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
