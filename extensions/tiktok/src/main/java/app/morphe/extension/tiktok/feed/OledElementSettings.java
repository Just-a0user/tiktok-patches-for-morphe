/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * What the reader set for each element of the feed screen: whether it is dimmed, and how
 * transparent it is, from 0 (as TikTok draws it) to 100 (not to be seen). One JSON object in a
 * setting, read again only when its text changes.
 */
public final class OledElementSettings {
    /** Where the number starts when an element is first switched on. */
    public static final int DEFAULT_TRANSPARENCY = 40;

    private static String cachedText;
    private static JSONObject cached = new JSONObject();

    private OledElementSettings() {
    }

    private static JSONObject read() {
        String text = Settings.OLED_ELEMENTS.get();
        if (cachedText == null || !cachedText.equals(text)) {
            cachedText = text;
            try {
                cached = new JSONObject(text == null || text.isEmpty() ? "{}" : text);
            } catch (JSONException ex) {
                cached = new JSONObject();
            }
        }
        return cached;
    }

    public static boolean isOn(String key) {
        JSONObject entry = read().optJSONObject(key);
        return entry != null && entry.optBoolean("on", false);
    }

    public static int transparency(String key) {
        JSONObject entry = read().optJSONObject(key);
        int value = entry == null ? DEFAULT_TRANSPARENCY : entry.optInt("t", DEFAULT_TRANSPARENCY);
        return Math.max(0, Math.min(100, value));
    }

    public static void set(String key, boolean on, int transparency) {
        try {
            JSONObject all = new JSONObject(read().toString());
            JSONObject entry = new JSONObject();
            entry.put("on", on);
            entry.put("t", Math.max(0, Math.min(100, transparency)));
            all.put(key, entry);
            Settings.OLED_ELEMENTS.save(all.toString());
        } catch (JSONException ignored) {
            // The text was ours a moment ago; nothing to keep if it can't be written.
        }
    }

    /** The elements that are on and dimmed, with the opacity each keeps: 100 minus the transparency. */
    static Map<String, Integer> active() {
        Map<String, Integer> active = new LinkedHashMap<>();
        for (OledElements.Element element : OledElements.all()) {
            if (isOn(element.key) && transparency(element.key) > 0) {
                active.put(element.key, 100 - transparency(element.key));
            }
        }
        return active;
    }
}
