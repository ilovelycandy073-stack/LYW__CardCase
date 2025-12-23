package com.example.bestapplication.feature.idcard.scan;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class ScanOverlayView extends View {

    // 身份证宽高比：85.6 / 54 ≈ 1.585
    private static final float ID_ASPECT = 85.6f / 54f;

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF frame = new RectF();
    private float lineY;
    private ValueAnimator animator;

    public ScanOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // 暗角层
        dimPaint.setStyle(Paint.Style.FILL);
        dimPaint.setAlpha(170);

        // “挖洞”清除中间框
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // 角线
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp(3));
        cornerPaint.setAlpha(230);

        // 扫描线
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setAlpha(220);

        // 为 CLEAR 生效
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 让框占屏宽的 85%，高度按身份证比例计算
        float fw = w * 0.85f;
        float fh = fw / ID_ASPECT;

        // 框居中，稍微上移一点（底部有按钮）
        float left = (w - fw) / 2f;
        float top = (h - fh) / 2f - h * 0.06f;
        frame.set(left, top, left + fw, top + fh);

        // 初始化扫描线位置
        lineY = frame.top;
        setupAnim();
    }

    private void setupAnim() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(frame.top, frame.bottom);
        animator.setDuration(1400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            lineY = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void start() { if (animator != null && !animator.isStarted()) animator.start(); }
    public void stop()  { if (animator != null) animator.cancel(); }

    /** 如果你后续想用“本地裁剪”，可用这个 rect 做映射（先不必做）。 */
    public RectF getFrameRect() { return new RectF(frame); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int sc = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);

        // 1) 画整屏暗层
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        // 2) 清除中间框区域（挖洞）
        canvas.drawRoundRect(frame, dp(8), dp(8), clearPaint);

        // 3) 画框四角
        float len = dp(22);
        float l = frame.left, t = frame.top, r = frame.right, b = frame.bottom;

        // 你可以在这里把 cornerPaint/linePaint 颜色改成“黑白灰+强调色”
        cornerPaint.setARGB(255, 255, 255, 255);
        linePaint.setARGB(255, 255, 255, 255);

        // 左上
        canvas.drawLine(l, t, l + len, t, cornerPaint);
        canvas.drawLine(l, t, l, t + len, cornerPaint);
        // 右上
        canvas.drawLine(r - len, t, r, t, cornerPaint);
        canvas.drawLine(r, t, r, t + len, cornerPaint);
        // 左下
        canvas.drawLine(l, b, l + len, b, cornerPaint);
        canvas.drawLine(l, b - len, l, b, cornerPaint);
        // 右下
        canvas.drawLine(r - len, b, r, b, cornerPaint);
        canvas.drawLine(r, b - len, r, b, cornerPaint);

        // 4) 画扫描线
        canvas.drawLine(l + dp(8), lineY, r - dp(8), lineY, linePaint);

        canvas.restoreToCount(sc);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
