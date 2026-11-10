/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One dark panel the reader placed over the app, for an OLED screen: where it is, how big, how
 * round, how opaque, how soft its edge is, which way it turns from black to a barely lighter gray,
 * and which pages of the app it shows on. The list of them is one JSON text in a setting.
 */
final class OledOverlay {
    static final String GRADIENT_OFF = "off";
    static final String GRADIENT_LEFT_TO_RIGHT = "ltr";
    static final String GRADIENT_RIGHT_TO_LEFT = "rtl";
    static final String GRADIENT_CENTER_OUT = "center";

    static final int PAGE_HOME = 1;
    static final int PAGE_PROFILE = 2;
    static final int PAGE_INBOX = 4;
    static final int PAGE_FRIENDS = 8;
    static final int ALL_PAGES = PAGE_HOME | PAGE_PROFILE | PAGE_INBOX | PAGE_FRIENDS;

    static final int MAX_SIZE_PX = 4000;
    static final int MAX_ROUNDING_PX = 2000;
    static final int MAX_SOFT_EDGE_DP = 100;
    static final int MAX_GRAY = 255;

    long id;
    /** The top left corner, in pixels of the window. */
    int x;
    int y;
    int width = 360;
    int height = 120;
    /** The corner radius in pixels. */
    int rounding = 24;
    /** How solid it is, 0 to 100: 100 is fully opaque. */
    int alpha = 94;
    /** How far its edge fades out, in dp. 0 is a sharp edge. */
    int softEdgeDp = 0;
    String gradient = GRADIENT_OFF;
    /**
     * The gray the gradient ends at, 0 to 255, as the value of each of red, green and blue: 6 is
     * #060606. The panel starts at black, so the smaller this is the less the panel changes.
     */
    int gray = 6;
    /** The pages it shows on, a mix of the PAGE_ flags. */
    int pages = ALL_PAGES;

    OledOverlay copy() {
        OledOverlay other = new OledOverlay();
        other.id = id;
        other.x = x;
        other.y = y;
        other.width = width;
        other.height = height;
        other.rounding = rounding;
        other.alpha = alpha;
        other.softEdgeDp = softEdgeDp;
        other.gradient = gradient;
        other.gray = gray;
        other.pages = pages;
        return other;
    }

    /** Everything that changes how it is drawn, as one string, so an unchanged panel is not redrawn. */
    String drawSignature() {
        return width + "x" + height + "r" + rounding + "a" + alpha + "s" + softEdgeDp
                + gradient + gray;
    }

    boolean showsOn(int page) {
        return (pages & page) != 0;
    }

    boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    void clamp() {
        width = within(width, 20, MAX_SIZE_PX);
        height = within(height, 10, MAX_SIZE_PX);
        rounding = within(rounding, 0, MAX_ROUNDING_PX);
        alpha = within(alpha, 0, 100);
        softEdgeDp = within(softEdgeDp, 0, MAX_SOFT_EDGE_DP);
        gray = within(gray, 0, MAX_GRAY);
        pages &= ALL_PAGES;
        if (!GRADIENT_LEFT_TO_RIGHT.equals(gradient) && !GRADIENT_RIGHT_TO_LEFT.equals(gradient)
                && !GRADIENT_CENTER_OUT.equals(gradient)) {
            gradient = GRADIENT_OFF;
        }
    }

    static int within(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("x", x);
        json.put("y", y);
        json.put("w", width);
        json.put("h", height);
        json.put("r", rounding);
        json.put("a", alpha);
        json.put("s", softEdgeDp);
        json.put("g", gradient);
        json.put("c", gray);
        json.put("p", pages);
        return json;
    }

    private static OledOverlay fromJson(JSONObject json) {
        OledOverlay overlay = new OledOverlay();
        overlay.id = json.optLong("id", 0L);
        overlay.x = json.optInt("x", 0);
        overlay.y = json.optInt("y", 0);
        overlay.width = json.optInt("w", overlay.width);
        overlay.height = json.optInt("h", overlay.height);
        overlay.rounding = json.optInt("r", overlay.rounding);
        overlay.alpha = json.optInt("a", overlay.alpha);
        overlay.softEdgeDp = json.optInt("s", overlay.softEdgeDp);
        overlay.gradient = json.optString("g", GRADIENT_OFF);
        overlay.gray = json.optInt("c", overlay.gray);
        overlay.pages = json.optInt("p", ALL_PAGES);
        overlay.clamp();
        return overlay;
    }

    /** The saved list. Text that can't be read is an empty list, never a crash. */
    static List<OledOverlay> parse(String text) {
        List<OledOverlay> list = new ArrayList<>();
        if (text == null || text.isEmpty()) return list;
        try {
            JSONArray array = new JSONArray(text);
            for (int index = 0; index < array.length(); index++) {
                JSONObject json = array.optJSONObject(index);
                if (json != null) list.add(fromJson(json));
            }
        } catch (JSONException ignored) {
            list.clear();
        }
        return list;
    }

    static String serialize(List<OledOverlay> list) {
        JSONArray array = new JSONArray();
        try {
            for (OledOverlay overlay : list) array.put(overlay.toJson());
        } catch (JSONException ignored) {
            return "[]";
        }
        return array.toString();
    }

    static List<OledOverlay> copyOf(List<OledOverlay> list) {
        List<OledOverlay> copy = new ArrayList<>(list.size());
        for (OledOverlay overlay : list) copy.add(overlay.copy());
        return copy;
    }
}
