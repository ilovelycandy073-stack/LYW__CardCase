package com.example.bestapplication.feature.idcard.scan;

import android.graphics.ImageFormat;

import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public final class ImageProxyBytes {
    private ImageProxyBytes() {}

    public static byte[] toJpegBytes(ImageProxy image) {
        int fmt = image.getFormat();

        // 情况 1：相机直接返回 JPEG（最常见）
        if (fmt == ImageFormat.JPEG) {
            ByteBuffer buf = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        }

        // 情况 2：返回 YUV，需要自行转 JPEG
        if (fmt == ImageFormat.YUV_420_888) {
            return YuvToJpegConverter.toJpeg(image, 92);
        }

        throw new IllegalArgumentException("Unsupported ImageProxy format: " + fmt);
    }
}
