/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of the feed screen the reader can dim one by one or put a panel on: each tab, the
 * icon of a button apart from its count, the icon of a bottom tab apart from its label, and so on.
 *
 * <p>Most are found by the id TikTok gave them, the same ids the overlay hider already uses. A few
 * have none of their own: a single tab, the pieces of a bottom tab, the username and the date. They
 * are found as parts of a container that has an id (the tabs by their order, the bottom tabs by
 * their five slots), and when that does not fit what is on screen the element is simply not found
 * and says so in the settings.
 */
public final class OledElements {
    static final String GROUP_TOP = "Top bar";
    static final String GROUP_RIGHT = "Right bar";
    static final String GROUP_LEFT = "Bottom left";
    static final String GROUP_BOTTOM = "Bottom bar";

    private static final int KIND_IDS = 0;
    private static final int KIND_TAB = 1;
    private static final int KIND_NAV_WHOLE = 2;
    private static final int KIND_NAV_ICON = 3;
    private static final int KIND_NAV_TEXT = 4;
    private static final int KIND_ANCHOR_TEXT = 5;

    private static final String[] TAB_STRIP = {"47.4.3:uwq"};
    private static final String[] BOTTOM_TABS = {"47.4.3:onp"};
    private static final String[] ANCHOR = {"47.4.3:bqr"};

    /** One part of the screen. */
    public static final class Element {
        public final String key;
        public final String group;
        public final String label;
        final int kind;
        final int index;
        final String[] ids;
        /**
         * Whether the overlay hider already sets this view's fade every pass. The dimmer leaves
         * those to it, which takes the stronger of the two.
         */
        final boolean managed;

        Element(String key, String group, String label, int kind, int index, String[] ids, boolean managed) {
            this.key = key;
            this.group = group;
            this.label = label;
            this.kind = kind;
            this.index = index;
            this.ids = ids;
            this.managed = managed;
        }
    }

    private static final Map<String, Element> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, Boolean> SEEN = new HashMap<>();

    private static void add(String key, String group, String label, int kind, int index, String[] ids,
            boolean managed) {
        BY_KEY.put(key, new Element(key, group, label, kind, index, ids, managed));
    }

    static {
        add("top_tabs", GROUP_TOP, "Tabs, all together", KIND_IDS, 0, TAB_STRIP, true);
        add("tab_1", GROUP_TOP, "Tab 1, the rightmost (usually For you)", KIND_TAB, 0, null, false);
        add("tab_2", GROUP_TOP, "Tab 2", KIND_TAB, 1, null, false);
        add("tab_3", GROUP_TOP, "Tab 3", KIND_TAB, 2, null, false);
        add("tab_4", GROUP_TOP, "Tab 4", KIND_TAB, 3, null, false);
        add("tab_5", GROUP_TOP, "Tab 5", KIND_TAB, 4, null, false);
        add("search", GROUP_TOP, "Search icon", KIND_IDS, 0, new String[]{"47.4.3:ljb"}, true);
        add("live", GROUP_TOP, "LIVE button", KIND_IDS, 0, new String[]{"47.4.3:k_d"}, false);

        add("rail_all", GROUP_RIGHT, "Right bar, all together", KIND_IDS, 0, new String[]{"47.4.3:mud"}, true);
        add("avatar", GROUP_RIGHT, "Creator avatar", KIND_IDS, 0, new String[]{"47.4.3:i_t"}, false);
        add("like_icon", GROUP_RIGHT, "Like icon", KIND_IDS, 0, new String[]{"47.4.3:g8a"}, false);
        add("like_count", GROUP_RIGHT, "Like count", KIND_IDS, 0, new String[]{"47.4.3:g8b"}, false);
        add("comment_icon", GROUP_RIGHT, "Comment icon", KIND_IDS, 0, new String[]{"47.4.3:eq2"}, false);
        add("comment_count", GROUP_RIGHT, "Comment count", KIND_IDS, 0, new String[]{"47.4.3:ek3"}, false);
        add("bookmark_icon", GROUP_RIGHT, "Save icon", KIND_IDS, 0, new String[]{"47.4.3:i9a"}, false);
        add("bookmark_count", GROUP_RIGHT, "Save count", KIND_IDS, 0, new String[]{"47.4.3:i8_"}, false);
        add("share_icon", GROUP_RIGHT, "Share icon", KIND_IDS, 0, new String[]{"47.4.3:w_s"}, false);
        add("share_count", GROUP_RIGHT, "Share count", KIND_IDS, 0, new String[]{"47.4.3:w6z"}, false);
        add("music_disc", GROUP_RIGHT, "Music disc", KIND_IDS, 0, new String[]{"47.4.3:pop"}, false);

        add("caption_block", GROUP_LEFT, "Text block, all together", KIND_IDS, 0, ANCHOR, true);
        add("author", GROUP_LEFT, "Username", KIND_ANCHOR_TEXT, 0, null, false);
        add("date", GROUP_LEFT, "Date", KIND_ANCHOR_TEXT, 1, null, false);
        add("caption", GROUP_LEFT, "Caption and hashtags", KIND_IDS, 0, new String[]{"desc"}, false);
        add("music_line", GROUP_LEFT, "Song line", KIND_IDS, 0, new String[]{"47.4.3:o7a"}, false);
        add("progress", GROUP_LEFT, "Progress bar", KIND_IDS, 0, new String[]{"video_seek_bar"}, true);

        add("nav_all", GROUP_BOTTOM, "Bottom bar, all together", KIND_IDS, 0, BOTTOM_TABS, true);
        add("nav_home_icon", GROUP_BOTTOM, "Home icon", KIND_NAV_ICON, 0, null, false);
        add("nav_home_text", GROUP_BOTTOM, "Home label", KIND_NAV_TEXT, 0, null, false);
        add("nav_friends_icon", GROUP_BOTTOM, "Friends icon", KIND_NAV_ICON, 1, null, false);
        add("nav_friends_text", GROUP_BOTTOM, "Friends label", KIND_NAV_TEXT, 1, null, false);
        add("nav_create", GROUP_BOTTOM, "Plus button", KIND_NAV_WHOLE, 2, null, false);
        add("nav_inbox_icon", GROUP_BOTTOM, "Inbox icon", KIND_NAV_ICON, 3, null, false);
        add("nav_inbox_text", GROUP_BOTTOM, "Inbox label", KIND_NAV_TEXT, 3, null, false);
        add("nav_profile_icon", GROUP_BOTTOM, "Profile icon", KIND_NAV_ICON, 4, null, false);
        add("nav_profile_text", GROUP_BOTTOM, "Profile label", KIND_NAV_TEXT, 4, null, false);
    }

    private OledElements() {
    }

    public static List<Element> all() {
        return new ArrayList<>(BY_KEY.values());
    }

    public static Element byKey(String key) {
        return BY_KEY.get(key);
    }

    /** The groups in the order they are shown. */
    public static List<String> groups() {
        List<String> groups = new ArrayList<>();
        for (Element element : BY_KEY.values()) {
            if (!groups.contains(element.group)) groups.add(element.group);
        }
        return groups;
    }

    /** What the last look at the screen found: 1 yes, 0 no, -1 not looked yet. */
    public static int statusOf(String key) {
        Boolean seen = SEEN.get(key);
        return seen == null ? -1 : (seen ? 1 : 0);
    }

    static void note(String key, boolean found) {
        SEEN.put(key, found);
    }

    /** Every view of the screen that is this element, newest and oldest cells alike. */
    static List<View> find(Activity activity, Element element) {
        switch (element.kind) {
            case KIND_IDS:
                return VideoOverlayHider.viewsNamed(activity, element.ids);
            case KIND_TAB:
                return tab(activity, element.index);
            case KIND_ANCHOR_TEXT:
                return anchorText(activity, element.index);
            default:
                return navPart(activity, element);
        }
    }

    /**
     * Where an element is, as {x, y, width, height} in the coordinates of {@code frame}: the one of
     * its views that is most on screen. Null when there is none.
     */
    static int[] boundsOf(Activity activity, Element element, View frame) {
        int[] best = null;
        long bestArea = 0;
        int[] at = new int[2];
        int[] frameAt = new int[2];
        frame.getLocationInWindow(frameAt);
        for (View view : find(activity, element)) {
            if (!view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
            view.getLocationInWindow(at);
            int x = at[0] - frameAt[0];
            int y = at[1] - frameAt[1];
            int width = view.getWidth();
            int height = view.getHeight();
            int visibleWidth = Math.max(0, Math.min(x + width, frame.getWidth()) - Math.max(x, 0));
            int visibleHeight = Math.max(0, Math.min(y + height, frame.getHeight()) - Math.max(y, 0));
            long area = (long) visibleWidth * visibleHeight;
            if (area > bestArea) {
                bestArea = area;
                best = new int[]{x, y, width, height};
            }
        }
        return best;
    }

    // Parts that have no id of their own.

    private static List<View> tab(Activity activity, int index) {
        for (View strip : VideoOverlayHider.viewsNamed(activity, TAB_STRIP)) {
            if (!strip.isShown()) continue;
            List<TextView> texts = new ArrayList<>();
            collectTexts(strip, texts);
            sortRightToLeft(texts);
            if (texts.size() > index) return Collections.<View>singletonList(texts.get(index));
        }
        return Collections.emptyList();
    }

    private static List<View> anchorText(Activity activity, int index) {
        for (View anchor : VideoOverlayHider.viewsNamed(activity, ANCHOR)) {
            if (!anchor.isShown()) continue;
            List<TextView> texts = new ArrayList<>();
            collectTexts(anchor, texts);
            if (texts.size() > index) return Collections.<View>singletonList(texts.get(index));
        }
        return Collections.emptyList();
    }

    private static List<View> navPart(Activity activity, Element element) {
        for (View bar : VideoOverlayHider.viewsNamed(activity, BOTTOM_TABS)) {
            if (!bar.isShown()) continue;
            ViewGroup row = slotRow(bar);
            if (row == null) continue;
            List<View> slots = new ArrayList<>();
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) slots.add(child);
            }
            if (slots.size() != 5) continue;
            Collections.sort(slots, Comparator.comparingInt(OledElements::leftOnScreen));
            View slot = slots.get(element.index);
            if (element.kind == KIND_NAV_WHOLE) return Collections.singletonList(slot);
            View part = element.kind == KIND_NAV_TEXT ? firstText(slot) : firstIcon(slot);
            return part == null ? Collections.<View>emptyList() : Collections.singletonList(part);
        }
        return Collections.emptyList();
    }

    /** The first container under {@code root} with five visible children: the bottom tabs' slots. */
    private static ViewGroup slotRow(View root) {
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        int visible = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() == View.VISIBLE) visible++;
        }
        if (visible >= 5) return group;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = slotRow(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static void collectTexts(View view, List<TextView> into) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.isShown() && text.getText() != null && text.getText().length() > 0) into.add(text);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectTexts(group.getChildAt(i), into);
        }
    }

    private static View firstText(View root) {
        List<TextView> texts = new ArrayList<>();
        collectTexts(root, texts);
        return texts.isEmpty() ? null : texts.get(0);
    }

    private static View firstIcon(View root) {
        if (root instanceof ImageView) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstIcon(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void sortRightToLeft(List<TextView> texts) {
        Collections.sort(texts, (a, b) -> Integer.compare(leftOnScreen(b), leftOnScreen(a)));
    }

    private static int leftOnScreen(View view) {
        int[] at = new int[2];
        view.getLocationInWindow(at);
        return at[0];
    }
}
