/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One view over the whole window that draws every panel. Each panel is rendered once into a bitmap
 * (a blur only works on a software canvas) and drawn from it until something about it changes.
 *
 * <p>While it is not being edited it is not clickable, so a touch goes through it to the app under
 * it. The editor turns that on to take the touches itself.
 */
final class OledOverlayLayer extends View {
    private static final class Rendered {
        String signature;
        Bitmap bitmap;
        int pad;
    }

    private final Map<Long, Rendered> rendered = new HashMap<>();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private List<OledOverlay> overlays = new java.util.ArrayList<>();
    private int page = OledOverlay.PAGE_HOME;
    private boolean editing;
    private long selectedId = -1L;
    private String signature = "";
    private int[] highlight;

    /** A frame around a real element of the app, x, y, width, height, or null for none. */
    void setHighlight(int[] rect) {
        if (java.util.Arrays.equals(rect, highlight)) return;
        highlight = rect;
        invalidate();
    }

    OledOverlayLayer(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);
        borderPaint.setColor(Color.RED);
    }

    /** Takes what to draw, and redraws only when it is not what is already drawn. */
    void update(List<OledOverlay> list, int currentPage, boolean isEditing, long selected) {
        StringBuilder next = new StringBuilder();
        next.append(currentPage).append(isEditing).append(selected);
        for (OledOverlay overlay : list) {
            next.append('|').append(overlay.id).append('@').append(overlay.x).append(',').append(overlay.y)
                    .append(overlay.drawSignature()).append(overlay.pages);
        }
        String key = next.toString();
        overlays = list;
        page = currentPage;
        editing = isEditing;
        selectedId = selected;
        setClickable(isEditing);
        if (!key.equals(signature)) {
            signature = key;
            invalidate();
        }
    }

    /** How many steps one round of an animation is cut into. */
    private static final int PHASE_STEPS = 240;

    @Override
    protected void onDraw(Canvas canvas) {
        Set<Long> live = new HashSet<>();
        long slowest = 0L;
        boolean moving = false;
        for (OledOverlay overlay : overlays) {
            live.add(overlay.id);
            boolean shown = editing || overlay.showsOn(page);
            if (!shown || overlay.alpha == 0 && !editing) continue;
            Rendered drawn = renderedFor(overlay);
            if (drawn.bitmap != null) {
                canvas.drawBitmap(drawn.bitmap, overlay.x - drawn.pad, overlay.y - drawn.pad, bitmapPaint);
                if (overlay.animates()) {
                    moving = true;
                    slowest = Math.max(slowest, (long) overlay.width * overlay.height);
                }
            }
            if (editing && overlay.id == selectedId) {
                float half = borderPaint.getStrokeWidth() / 2f;
                canvas.drawRect(overlay.x + half, overlay.y + half,
                        overlay.x + overlay.width - half, overlay.y + overlay.height - half, borderPaint);
            }
        }
        if (highlight != null) {
            canvas.drawRect(highlight[0], highlight[1], highlight[0] + highlight[2], highlight[1] + highlight[3], borderPaint);
        }
        // A panel that is gone gives its bitmap back.
        for (java.util.Iterator<Map.Entry<Long, Rendered>> it = rendered.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Rendered> entry = it.next();
            if (!live.contains(entry.getKey())) {
                if (entry.getValue().bitmap != null) entry.getValue().bitmap.recycle();
                it.remove();
            }
        }
        // An animated gradient draws again a few times a second; a big panel less often.
        if (moving) postInvalidateDelayed(slowest > 1_500_000L ? 1000L : 250L);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (Rendered drawn : rendered.values()) {
            if (drawn.bitmap != null) drawn.bitmap.recycle();
        }
        rendered.clear();
        signature = "";
    }

    private Rendered renderedFor(OledOverlay overlay) {
        Rendered drawn = rendered.get(overlay.id);
        int step = phaseStep(overlay);
        String wanted = overlay.drawSignature() + "@" + step;
        if (drawn != null && wanted.equals(drawn.signature)) return drawn;
        if (drawn == null) {
            drawn = new Rendered();
            rendered.put(overlay.id, drawn);
        } else if (drawn.bitmap != null) {
            drawn.bitmap.recycle();
            drawn.bitmap = null;
        }
        drawn.signature = wanted;
        drawn.pad = Math.round(overlay.softEdgeDp * density);
        drawn.bitmap = render(overlay, drawn.pad, step / (float) PHASE_STEPS);
        return drawn;
    }

    /** Where in its round an animated gradient is now, as a step of {@link #PHASE_STEPS}; 0 for a still one. */
    private static int phaseStep(OledOverlay overlay) {
        if (!overlay.animates()) return 0;
        long period = overlay.animSeconds * 1000L;
        return (int) ((SystemClock.uptimeMillis() % period) * PHASE_STEPS / period);
    }

    /** The panel as a bitmap with {@code pad} pixels around it for its soft edge, or null if it can't be made. */
    static Bitmap render(OledOverlay overlay, int pad, float phase) {
        int width = overlay.width + 2 * pad;
        int height = overlay.height + 2 * pad;
        if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            int black = Color.BLACK;
            // An animated gradient moves along the panel and swings its gray between black and the set
            // gray; a still one keeps the set gray and stays where it is.
            boolean moving = overlay.animates();
            float swing = moving ? 0.5f - 0.5f * (float) Math.cos(2.0 * Math.PI * phase) : 1f;
            int level = Math.round(overlay.gray * swing);
            int gray = Color.rgb(level, level, level);
            float left = pad;
            float top = pad;
            float right = pad + overlay.width;
            float bottom = pad + overlay.height;
            float centerX = (left + right) / 2f;
            float centerY = (top + bottom) / 2f;
            float shift = moving ? 2f * overlay.width * phase : 0f;
            switch (overlay.gradient) {
                case OledOverlay.GRADIENT_LEFT_TO_RIGHT:
                    paint.setShader(new LinearGradient(left + shift, 0, right + shift, 0, black, gray,
                            Shader.TileMode.MIRROR));
                    break;
                case OledOverlay.GRADIENT_RIGHT_TO_LEFT:
                    paint.setShader(new LinearGradient(right - shift, 0, left - shift, 0, black, gray,
                            Shader.TileMode.MIRROR));
                    break;
                case OledOverlay.GRADIENT_CENTER_OUT: {
                    float reach = Math.max(1f, overlay.width / 2f) * (moving ? 0.6f + 0.8f * swing : 1f);
                    RadialGradient radial = new RadialGradient(centerX, centerY, reach, black, gray,
                            Shader.TileMode.MIRROR);
                    Matrix squash = new Matrix();
                    squash.setScale(1f, overlay.height / (float) Math.max(1, overlay.width), centerX, centerY);
                    radial.setLocalMatrix(squash);
                    paint.setShader(radial);
                    break;
                }
                default:
                    paint.setColor(black);
            }
            paint.setAlpha(Math.round(255f * overlay.alpha / 100f));
            if (pad > 0) paint.setMaskFilter(new BlurMaskFilter(pad, BlurMaskFilter.Blur.NORMAL));
            float radius = Math.min(overlay.rounding, Math.min(overlay.width, overlay.height) / 2f);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
            return bitmap;
        } catch (OutOfMemoryError ex) {
            return null;
        }
    }
}
