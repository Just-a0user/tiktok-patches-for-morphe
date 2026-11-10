/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * A dark rounded panel drawn over the window's content, for an OLED screen: the pixels under it
 * are dimmed or off. Its rounding, see-through level, width and length are settings, and it sits at
 * the top, the middle or the bottom of the window.
 *
 * <p>It is one plain view added to the content frame, with no listeners and not clickable, so a
 * touch goes through it to what's underneath. {@link BurnInGuard} asks for it on every overlay
 * pass: shown over the feed and an opened video while the switch is on, taken away anywhere else
 * and under Pause. A pass that finds it already as wanted leaves it alone.
 */
final class BurnInOverlay {
    static final String POSITION_TOP = "top";
    static final String POSITION_CENTER = "center";
    static final String POSITION_BOTTOM = "bottom";

    private static final String TAG = "hushfeed_burn_in_overlay";
    /** What each overlay view was last drawn as, so a pass that changes nothing does nothing. */
    private static final Map<View, String> DRAWN = new WeakHashMap<>();

    private BurnInOverlay() {
    }

    static void update(Activity activity, boolean show) {
        try {
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout)) return;
            FrameLayout frame = (FrameLayout) content;
            View existing = frame.findViewWithTag(TAG);
            if (!show) {
                if (existing != null) {
                    DRAWN.remove(existing);
                    frame.removeView(existing);
                }
                return;
            }

            DisplayMetrics metrics = frame.getResources().getDisplayMetrics();
            int windowWidth = frame.getWidth() > 0 ? frame.getWidth() : metrics.widthPixels;
            int windowHeight = frame.getHeight() > 0 ? frame.getHeight() : metrics.heightPixels;
            int width = Math.max(1, Math.round(windowWidth * widthPercent() / 100f));
            int height = Math.max(1, Math.round(windowHeight * lengthPercent() / 100f));
            int gravity = gravityFor(Settings.BURN_IN_OVERLAY_POSITION.get());
            int color = Color.argb(alphaFor(transparencyPercent()), 0, 0, 0);
            float radius = roundingDp() * metrics.density;
            String drawn = width + "x" + height + "@" + gravity + "#" + color + "r" + radius;

            View overlay = existing;
            if (overlay == null) {
                overlay = new View(activity);
                overlay.setTag(TAG);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                frame.addView(overlay, new FrameLayout.LayoutParams(width, height, gravity));
            }
            if (!drawn.equals(DRAWN.get(overlay))) {
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(color);
                shape.setCornerRadius(radius);
                overlay.setBackground(shape);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
                overlay.setLayoutParams(params);
                DRAWN.put(overlay, drawn);
            }
            // TikTok adds its own views to the same frame; the panel stays on top of them.
            if (frame.indexOfChild(overlay) != frame.getChildCount() - 1) overlay.bringToFront();
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not update the burn-in overlay", ex);
        }
    }

    static int gravityFor(String position) {
        if (POSITION_TOP.equals(position)) return Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        if (POSITION_CENTER.equals(position)) return Gravity.CENTER;
        return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
    }

    /** The alpha byte for a see-through level: 0% is solid (255) and 100% is invisible (0). */
    static int alphaFor(int transparencyPercent) {
        int clamped = Math.max(0, Math.min(100, transparencyPercent));
        return Math.round(255f * (100 - clamped) / 100f);
    }

    private static int roundingDp() {
        return Math.max(0, Settings.BURN_IN_OVERLAY_ROUNDING.get());
    }

    private static int transparencyPercent() {
        return Settings.BURN_IN_OVERLAY_TRANSPARENCY.get();
    }

    private static int widthPercent() {
        return Math.max(1, Math.min(100, Settings.BURN_IN_OVERLAY_WIDTH.get()));
    }

    private static int lengthPercent() {
        return Math.max(1, Math.min(100, Settings.BURN_IN_OVERLAY_LENGTH.get()));
    }
}
