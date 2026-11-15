/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ready-made panels: one that sits on an element and follows it, or a package of those for a whole
 * area. Each comes with a soft edge or with a sharp one. A preset is only a starting point: it opens
 * in the editor over the real feed, where it can be changed before it is saved.
 */
public final class OledPresets {
    /** How transparent a preset panel is: the same 40 % the element settings start at. */
    private static final int TRANSPARENCY = 40;
    private static final int SOFT_EDGE_DP = 14;

    /** The packages, each with the elements it puts a panel on. */
    public static final Map<String, List<String>> PACKAGES = new LinkedHashMap<>();

    static {
        PACKAGES.put("Right bar", Arrays.asList("avatar", "like_icon", "like_count", "comment_icon",
                "comment_count", "bookmark_icon", "bookmark_count", "share_icon", "share_count", "music_disc"));
        PACKAGES.put("Top bar", Arrays.asList("top_tabs", "search", "live"));
        PACKAGES.put("Bottom bar", Arrays.asList("nav_all"));
        PACKAGES.put("Text bottom left", Arrays.asList("caption_block"));
        List<String> everything = new ArrayList<>();
        for (List<String> keys : new ArrayList<>(PACKAGES.values())) everything.addAll(keys);
        PACKAGES.put("Everything", everything);
    }

    private OledPresets() {
    }

    /** A panel on one element. {@code soft} gives it a soft edge, otherwise a sharp one. */
    static OledOverlay forElement(String key, boolean soft) {
        OledOverlay overlay = new OledOverlay();
        overlay.follow = key;
        overlay.padding = 6;
        overlay.rounding = 16;
        overlay.alpha = 100 - TRANSPARENCY;
        overlay.softEdgeDp = soft ? SOFT_EDGE_DP : 0;
        overlay.gradient = OledOverlay.GRADIENT_OFF;
        overlay.clamp();
        return overlay;
    }

    static List<OledOverlay> forElements(List<String> keys, boolean soft) {
        List<OledOverlay> list = new ArrayList<>();
        for (String key : keys) list.add(forElement(key, soft));
        return list;
    }
}
