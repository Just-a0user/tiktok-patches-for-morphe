/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lets the top and the bottom of every feed video fade into black, so a hard edge of the picture
 * does not stay lit in one place on an OLED screen. The widths are set in pixels, apart for the
 * top and the bottom.
 *
 * <p>The fade is drawn as the foreground of the video's own view, not as a panel over the screen:
 * it is part of the video and moves with it. TikTok does not let the picture itself be masked.
 */
final class OledVideoEdges {
    private static final String[] PLAYER_VIEW = {"player_view"};
    private static final Map<View, Drawable> PUT = new WeakHashMap<>();

    private OledVideoEdges() {
    }

    /** {@code allowed} is false under Pause and without the overlay patch. */
    static void pass(Activity activity, boolean allowed) {
        try {
            int strength = Settings.OLED_VIDEO_EDGE_STRENGTH.get();
            boolean on = allowed && strength > 0;
            int top = on ? Settings.OLED_VIDEO_EDGE_TOP.get() : 0;
            int bottom = on ? Settings.OLED_VIDEO_EDGE_BOTTOM.get() : 0;
            int shape = Settings.OLED_VIDEO_EDGE_SHAPE.get();
            int rgb = parseColor(Settings.OLED_VIDEO_EDGE_COLOR.get());
            if (top <= 0 && bottom <= 0 && PUT.isEmpty()) return;
            for (View player : VideoOverlayHider.viewsNamed(activity, PLAYER_VIEW)) {
                Drawable ours = PUT.get(player);
                if (top <= 0 && bottom <= 0) {
                    if (ours != null && player.getForeground() == ours) player.setForeground(null);
                    PUT.remove(player);
                    continue;
                }
                Drawable current = player.getForeground();
                if (current != null && current != ours) continue;
                if (ours instanceof EdgeDrawable) {
                    ((EdgeDrawable) ours).set(top, bottom, strength, shape, rgb);
                } else {
                    EdgeDrawable edge = new EdgeDrawable();
                    edge.set(top, bottom, strength, shape, rgb);
                    player.setForeground(edge);
                    PUT.put(player, edge);
                }
            }
            if (top <= 0 && bottom <= 0) PUT.clear();
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not set the video edges", ex);
        }
    }

    /** Six hex digits as a colour; anything else is black. */
    static int parseColor(String text) {
        try {
            return Integer.parseInt(text.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** From the edge inwards: the colour at its set strength fading to nothing, along the chosen curve. */
    private static int[] ramp(int rgb, int strength, int shape) {
        int steps = 12;
        int[] colors = new int[steps];
        for (int i = 0; i < steps; i++) {
            float t = i / (steps - 1f);
            float keep = shape == 2 ? 1f : shape == 1 ? 1f - t * t * (3f - 2f * t) : 1f - t;
            colors[i] = (Math.round(255f * strength / 100f * keep) << 24) | rgb;
        }
        return colors;
    }

    /** The colour at the very top and bottom, fading to nothing over the set number of pixels. */
    private static final class EdgeDrawable extends Drawable {
        private GradientDrawable topFade;
        private GradientDrawable bottomFade;
        private int top;
        private int bottom;
        private int strength = -1;
        private int shape = -1;
        private int rgb = -1;

        void set(int topPx, int bottomPx, int newStrength, int newShape, int newRgb) {
            if (topPx == top && bottomPx == bottom && newStrength == strength && newShape == shape && newRgb == rgb) return;
            top = topPx;
            bottom = bottomPx;
            strength = newStrength;
            shape = newShape;
            rgb = newRgb;
            int[] colors = ramp(rgb, strength, shape);
            topFade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
            bottomFade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            if (topFade == null) return;
            int width = getBounds().width();
            int height = getBounds().height();
            if (top > 0) {
                topFade.setBounds(0, 0, width, Math.min(top, height));
                topFade.draw(canvas);
            }
            if (bottom > 0) {
                bottomFade.setBounds(0, Math.max(0, height - bottom), width, height);
                bottomFade.draw(canvas);
            }
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
