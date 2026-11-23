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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
        int padX;
        int padY;
        /** How much to scale the alpha by: a thin panel with a wide blur never gets fully dark otherwise. */
        float boost = 1f;
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
                bitmapPaint.setColorFilter(drawn.boost > 1.02f ? new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                        1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, drawn.boost, 0})) : null);
                canvas.drawBitmap(drawn.bitmap, overlay.x - drawn.padX, overlay.y - drawn.padY, bitmapPaint);
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
        float radius = overlay.softEdgeDp * density;
        // The blur reaches about three of its sigmas out; the bitmap has room for all of it, so the
        // fade ends in nothing instead of being cut off in a step at the bitmap's edge.
        int pad = radius > 0f ? Math.round(2f * radius + 2f) : 0;
        boolean softX = radius > 0f && overlay.softAxis != OledOverlay.AXIS_VERTICAL;
        boolean softY = radius > 0f && overlay.softAxis != OledOverlay.AXIS_HORIZONTAL;
        drawn.padX = softX ? pad : 0;
        drawn.padY = softY ? pad : 0;
        drawn.boost = 1f;
        if (radius > 0f) {
            // A panel thinner than its blur would stay pale; the centre is brought back to full.
            float sigma = 0.57735f * radius + 0.5f;
            float peak = (softX ? erf(overlay.width / (2f * sigma * 1.41421f)) : 1f)
                    * (softY ? erf(overlay.height / (2f * sigma * 1.41421f)) : 1f);
            drawn.boost = 1f / Math.max(peak, 0.25f);
        }
        drawn.bitmap = render(overlay, drawn.padX, drawn.padY, radius, step / (float) PHASE_STEPS);
        return drawn;
    }

    private static float erf(float x) {
        float t = 1f / (1f + 0.3275911f * Math.abs(x));
        float y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t
                + 0.254829592f) * t * (float) Math.exp(-x * x);
        return x >= 0f ? y : -y;
    }

    /** Where in its round an animated gradient is now, as a step of {@link #PHASE_STEPS}; 0 for a still one. */
    private static int phaseStep(OledOverlay overlay) {
        if (!overlay.animates()) return 0;
        long period = overlay.animSeconds * 1000L;
        return (int) ((SystemClock.uptimeMillis() % period) * PHASE_STEPS / period);
    }

    /**
     * The panel as a bitmap with room around it for its soft edge on the soft sides, or null if it can't
     * be made. A gradient always runs over the whole width; an animated one only swings its gray between
     * black and the set gray.
     */
    static Bitmap render(OledOverlay overlay, int padX, int padY, float blur, float phase) {
        int width = overlay.width + 2 * padX;
        int height = overlay.height + 2 * padY;
        if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setDither(true);
            paint.setStyle(Paint.Style.FILL);
            int black = Color.BLACK;
            float swing = overlay.animates() ? 0.5f - 0.5f * (float) Math.cos(2.0 * Math.PI * phase) : 1f;
            int level = Math.round(overlay.gray * swing);
            int gray = Color.rgb(level, level, level);
            float left = padX;
            float top = padY;
            float right = padX + overlay.width;
            float bottom = padY + overlay.height;
            float centerX = (left + right) / 2f;
            float centerY = (top + bottom) / 2f;
            switch (overlay.gradient) {
                case OledOverlay.GRADIENT_LEFT_TO_RIGHT:
                    paint.setShader(new LinearGradient(left, 0, right, 0, black, gray, Shader.TileMode.CLAMP));
                    break;
                case OledOverlay.GRADIENT_RIGHT_TO_LEFT:
                    paint.setShader(new LinearGradient(right, 0, left, 0, black, gray, Shader.TileMode.CLAMP));
                    break;
                case OledOverlay.GRADIENT_CENTER_OUT: {
                    RadialGradient radial = new RadialGradient(centerX, centerY, Math.max(1f, overlay.width / 2f),
                            black, gray, Shader.TileMode.CLAMP);
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
            if (blur > 0f) paint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
            // On a side that is not soft the shape runs on past the bitmap, so the blur does not fade it there.
            float reach = blur > 0f ? 3f * blur + 4f : 0f;
            float shapeLeft = padX == 0 && blur > 0f ? left - reach : left;
            float shapeRight = padX == 0 && blur > 0f ? right + reach : right;
            float shapeTop = padY == 0 && blur > 0f ? top - reach : top;
            float shapeBottom = padY == 0 && blur > 0f ? bottom + reach : bottom;
            float radius = Math.min(overlay.rounding, Math.min(overlay.width, overlay.height) / 2f);
            canvas.drawRoundRect(new RectF(shapeLeft, shapeTop, shapeRight, shapeBottom), radius, radius, paint);
            return bitmap;
        } catch (OutOfMemoryError ex) {
            return null;
        }
    }
}
