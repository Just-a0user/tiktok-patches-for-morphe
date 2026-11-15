/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.view.View;

import app.morphe.extension.shared.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Dims the elements the reader switched on in the OLED settings, each by its own transparency, for
 * good. It rides on the overlay hider's fade: a view it dims keeps at most the opacity set here
 * whatever else fades it, and the hider's pre-draw pass puts it back when TikTok writes an opacity
 * of its own. A dimmed view still takes touches.
 */
final class OledElementDimmer {
    private static final Map<View, Integer> OPACITY = new WeakHashMap<>();
    /** The elements dimmed by the last pass, so one switched off is let go. */
    private static Set<String> applied = new HashSet<>();

    private OledElementDimmer() {
    }

    /** The most opacity, in percent, this view may have: 100 unless it is dimmed here. */
    static int opacityOf(View view) {
        Integer opacity = OPACITY.get(view);
        return opacity == null ? 100 : opacity;
    }

    /** {@code allowed} is false under Pause and without the overlay patch. */
    static void pass(Activity activity, boolean allowed) {
        try {
            Map<String, Integer> wanted = allowed ? OledElementSettings.active() : java.util.Collections.emptyMap();
            if (wanted.isEmpty() && applied.isEmpty()) return;
            for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
                OledElements.Element element = OledElements.byKey(entry.getKey());
                if (element == null) continue;
                java.util.List<View> views = OledElements.find(activity, element);
                OledElements.note(element.key, !views.isEmpty());
                for (View view : views) {
                    OPACITY.put(view, entry.getValue());
                    if (element.managed) VideoOverlayHider.refade(view);
                    else VideoOverlayHider.setFaded(view, 100);
                }
            }
            for (String key : new HashSet<>(applied)) {
                if (wanted.containsKey(key)) continue;
                OledElements.Element element = OledElements.byKey(key);
                if (element == null) continue;
                for (View view : OledElements.find(activity, element)) {
                    OPACITY.remove(view);
                    // A view the hider fades sets itself again on its next pass; any other is put back here.
                    if (!element.managed) VideoOverlayHider.setFaded(view, 100);
                }
            }
            applied = new HashSet<>(wanted.keySet());
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not dim the OLED elements", ex);
        }
    }
}
