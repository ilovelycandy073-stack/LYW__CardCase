package com.example.bestapplication.feature.bankcard.scan;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.util.Base64;
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
import com.example.bestapplication.core.network.dto.TencentBankCardOcrResponse;
import com.example.bestapplication.core.network.tencent.TencentOcrBankCardClient;
import com.example.bestapplication.core.network.tencent.TencentSecrets;
import com.example.bestapplication.feature.bankcard.BankCardDetailActivity;
import com.example.bestapplication.feature.idcard.scan.ScanOverlayView;
import com.example.bestapplication.feature.idcard.scan.YuvToJpegConverter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.Executor;

public class BankCardScanActivity extends AppCompatActivity {

    private static final String TAG = "BankCardScan";

    // slot 与图片页保持一致
    private static final String SLOT_BANKCARD_FRONT = "BANKCARD_FRONT";
    private static final String SLOT_BANKCARD_BACK = "BANKCARD_BACK";

    private PreviewView previewView;
    private ScanOverlayView overlayView;
    private TextView tip;

    private ImageCapture imageCapture;
    private Executor mainExecutor;

    private String itemId;
    private String mode;

    private volatile boolean busy = false;

    private final ActivityResultLauncher<String> camPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, "未获得相机权限，无法拍摄银行卡", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bankcard_scan);

        itemId = getIntent().getStringExtra(BankCardDetailActivity.EXTRA_ITEM_ID);
        mode = getIntent().getStringExtra(BankCardDetailActivity.EXTRA_MODE);

        if (itemId == null || itemId.trim().isEmpty()) {
            Toast.makeText(this, "参数错误：缺少 itemId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (mode == null || mode.trim().isEmpty()) {
            mode = BankCardDetailActivity.MODE_FRONT_OCR;
        }

        previewView = findViewById(R.id.preview);
        overlayView = findViewById(R.id.overlay);
        tip = findViewById(R.id.tip);
        mainExecutor = ContextCompat.getMainExecutor(this);

        findViewById(R.id.btn_shot).setOnClickListener(v -> shoot());

        renderMode();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            camPerm.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (overlayView != null && !busy) overlayView.start();
    }

    @Override
    protected void onPause() {
        if (overlayView != null) overlayView.stop();
        super.onPause();
    }

    private void renderMode() {
        TextView title = findViewById(R.id.tv_title);
        title.setText("扫描银行卡");

        if (BankCardDetailActivity.MODE_BACK_ONLY.equals(mode)) {
            tip.setText("拍摄反面（仅保存图片）");
        } else {
            tip.setText("拍摄正面（识别并保存）");
        }
    }

    private void setUiBusy(boolean b) {
        busy = b;
        findViewById(R.id.btn_shot).setEnabled(!b);
        if (overlayView != null) {
            if (b) overlayView.stop();
            else overlayView.start();
        }
        tip.setText(b ? "处理中，请稍候…" : tip.getText());
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
        if (busy) return;

        imageCapture.takePicture(mainExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    byte[] jpeg = imageProxyToJpegBytes(image);
                    setUiBusy(true);
                    runPipeline(jpeg);
                } catch (Exception ex) {
                    Log.e(TAG, "process photo failed", ex);
                    Toast.makeText(BankCardScanActivity.this, "处理照片失败，请重试", Toast.LENGTH_LONG).show();
                    setUiBusy(false);
                } finally {
                    image.close();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "takePicture error", exception);
                Toast.makeText(BankCardScanActivity.this, "拍照失败，请重试", Toast.LENGTH_LONG).show();
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

    private void runPipeline(byte[] capturedJpeg) {
        new Thread(() -> {
            DocumentItemEntity item = null;
            try {
                if (capturedJpeg == null || capturedJpeg.length == 0) {
                    throw new IllegalStateException("image empty");
                }
                if (isEmpty(TencentSecrets.SECRET_ID) || isEmpty(TencentSecrets.SECRET_KEY)) {
                    throw new IllegalStateException("请先在 TencentSecrets.java 填写 SECRET_ID / SECRET_KEY");
                }

                AppDatabase db = AppDatabase.get(this);
                VaultDao dao = db.dao();

                item = dao.findItem(itemId);
                if (item == null) {
                    // 理论上不该发生（列表创建了 item），但做兜底
                    item = new DocumentItemEntity(itemId, "VEHICLE_LICENSE", "{}", System.currentTimeMillis(), "CREATED");
                    dao.upsertItem(item);
                }

                EncryptedFileStore store = new EncryptedFileStore(this);

                if (BankCardDetailActivity.MODE_BACK_ONLY.equals(mode)) {
                    // 反面：仅保存图片
                    item.status = "BACK_SAVED";
                    item.updatedAt = System.currentTimeMillis();
                    dao.upsertItem(item);

                    String backPath = "bankcard/" + item.itemId + "/back.jpg.enc";
                    store.saveBytes(backPath, capturedJpeg);

                    dao.upsertBlob(new BlobRefEntity(
                            item.itemId + "_BANKCARD_BACK",
                            item.itemId,
                            SLOT_BANKCARD_BACK,
                            backPath,
                            "image/jpeg",
                            System.currentTimeMillis()
                    ));

                    // 若正面已存在，则标记完成
                    if (dao.findBlobBySlot(item.itemId, SLOT_BANKCARD_FRONT) != null) {
                        item.status = "COMPLETED";
                        item.updatedAt = System.currentTimeMillis();
                        dao.upsertItem(item);
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this, "反面已保存", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // 正面：调用 OCR
                item.status = "OCR_PROCESSING";
                item.updatedAt = System.currentTimeMillis();
                dao.upsertItem(item);

                TencentOcrBankCardClient client = new TencentOcrBankCardClient(
                        TencentSecrets.SECRET_ID,
                        TencentSecrets.SECRET_KEY,
                        TencentSecrets.REGION
                );

                long ts = System.currentTimeMillis() / 1000L;
                TencentBankCardOcrResponse resp = client.bankCardOcr(capturedJpeg, ts);
                if (resp == null || resp.Response == null) throw new RuntimeException("Empty response");
                if (resp.Response.Error != null) {
                    throw new RuntimeException(resp.Response.Error.Code + ": " + resp.Response.Error.Message);
                }

                // 保存图片：优先保存 BorderCutImage（对齐裁剪图），否则保存原图
                byte[] frontToSave = capturedJpeg;
                if (resp.Response.BorderCutImage != null && !resp.Response.BorderCutImage.trim().isEmpty()) {
                    try {
                        frontToSave = Base64.decode(resp.Response.BorderCutImage, Base64.DEFAULT);
                    } catch (Exception ignored) {
                        frontToSave = capturedJpeg;
                    }
                }

                String frontPath = "bankcard/" + item.itemId + "/front.jpg.enc";
                store.saveBytes(frontPath, frontToSave);

                dao.upsertBlob(new BlobRefEntity(
                        item.itemId + "_BANKCARD_FRONT",
                        item.itemId,
                        SLOT_BANKCARD_FRONT,
                        frontPath,
                        "image/jpeg",
                        System.currentTimeMillis()
                ));

                // 合并写入 infoJson（保留已有字段）
                JsonObject obj = safeParseJsonObject(item.infoJson);
                obj.addProperty("CardNo", nvl(resp.Response.CardNo));
                obj.addProperty("BankInfo", nvl(resp.Response.BankInfo));
                obj.addProperty("ValidDate", nvl(resp.Response.ValidDate));
                obj.addProperty("CardType", nvl(resp.Response.CardType));
                obj.addProperty("CardName", nvl(resp.Response.CardName));
                obj.addProperty("CardCategory", nvl(resp.Response.CardCategory));
                obj.addProperty("QualityValue", resp.Response.QualityValue == null ? "" : String.valueOf(resp.Response.QualityValue));
                obj.addProperty("WarningCode", joinWarning(resp.Response.WarningCode));
                obj.addProperty("RequestId", nvl(resp.Response.RequestId));

                // 衍生字段（便于 UI/列表）
                String cardNo = nvl(resp.Response.CardNo);
                obj.addProperty("CardNoMasked", maskCardNo(cardNo));

                item.infoJson = new Gson().toJson(obj);
                item.status = "FRONT_OCR_DONE";
                item.updatedAt = System.currentTimeMillis();

                // 若反面已存在，则标记完成
                if (dao.findBlobBySlot(item.itemId, SLOT_BANKCARD_BACK) != null) {
                    item.status = "COMPLETED";
                }
                dao.upsertItem(item);

                runOnUiThread(() -> {
                    Toast.makeText(this, "正面识别并保存成功", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "处理失败：" + msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
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

    private static String joinWarning(java.util.List<Integer> codes) {
        if (codes == null || codes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            sb.append(codes.get(i));
            if (i != codes.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private static String maskCardNo(String cardNo) {
        if (cardNo == null) return "";
        String s = cardNo.trim();
        if (s.length() <= 8) return "****";
        return s.substring(0, 4) + " **** **** " + s.substring(s.length() - 4);
    }
}
