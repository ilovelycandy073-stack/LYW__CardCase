package com.example.bestapplication.core.pdf;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

public final class IdCardPdfService {

    /**
     * 仅输出身份证图片（正反两页），不输出任何文字信息。
     * - 每页 A4 尺寸（595x842，约等于 72dpi 的 A4）
     * - 图片采用等比缩放 Fit-Center，避免变形
     * - 预留边距，适合打印与盖章复印等场景
     *
     * @param frontImg 裁剪后的身份证人像面图（建议 JPEG）
     * @param backImg  裁剪后的身份证国徽面图（建议 JPEG）
     * @param normalized 参数保留以兼容旧调用，但本实现不使用
     */
    public byte[] buildPdfBytes(InputStream frontImg, InputStream backImg, Map<String, Object> normalized) throws Exception {
        Bitmap front = BitmapFactory.decodeStream(frontImg);
        Bitmap back = BitmapFactory.decodeStream(backImg);

        if (front == null && back == null) {
            throw new IllegalArgumentException("Both front and back images are null");
        }

        // A4 at 72dpi-ish: 595 x 842 points
        final int pageW = 595;
        final int pageH = 842;

        // 边距：约 18mm（可按你需求微调）
        final float margin = 50f;

        // 目标放置区域：一页只放一张图（更符合“打印件”常见要求）
        RectF box = new RectF(
                margin,
                margin,
                pageW - margin,
                pageH - margin
        );

        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        // Page 1：正面（若 front 为 null，则跳过）
        int pageNumber = 1;
        if (front != null) {
            PdfDocument.PageInfo pInfo = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create();
            PdfDocument.Page page = doc.startPage(pInfo);
            Canvas canvas = page.getCanvas();
            drawBitmapFitCenter(canvas, front, box, paint);
            doc.finishPage(page);
            pageNumber++;
        }

        // Page 2：反面（若 back 为 null，则跳过）
        if (back != null) {
            PdfDocument.PageInfo pInfo = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create();
            PdfDocument.Page page = doc.startPage(pInfo);
            Canvas canvas = page.getCanvas();
            drawBitmapFitCenter(canvas, back, box, paint);
            doc.finishPage(page);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.writeTo(baos);
        doc.close();

        // 释放 Bitmap（避免内存占用）
        if (front != null) front.recycle();
        if (back != null) back.recycle();

        return baos.toByteArray();
    }

    /**
     * 等比缩放并居中绘制，避免拉伸变形。
     */
    private static void drawBitmapFitCenter(Canvas canvas, Bitmap bmp, RectF box, Paint paint) {
        float bw = bmp.getWidth();
        float bh = bmp.getHeight();

        if (bw <= 0 || bh <= 0) return;

        float scale = Math.min(box.width() / bw, box.height() / bh);
        float w = bw * scale;
        float h = bh * scale;

        float left = box.left + (box.width() - w) / 2f;
        float top = box.top + (box.height() - h) / 2f;

        RectF dst = new RectF(left, top, left + w, top + h);
        canvas.drawBitmap(bmp, null, dst, paint);
    }
}
