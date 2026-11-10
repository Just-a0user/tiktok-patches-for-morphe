/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Dark panels the reader places over TikTok to keep OLED pixels from staying lit, drawn by
 * {@link OledOverlayLayer} and placed with {@link OledOverlayEditor}.
 *
 * <p>{@link #pass} runs with every overlay pass of the window. It puts the layer on top of the
 * window's content while there is a panel to draw or the editor is open, shows each panel only on
 * the pages it was set for, and takes the layer away anywhere else and under Pause or without the
 * overlay patch. The layer is not clickable, so touches go through it.
 */
public final class OledOverlays {
    private static final String TAG = "oled_overlay_layer";

    private static String savedText;
    private static List<OledOverlay> saved = new java.util.ArrayList<>();
    /** The last main feed window a pass ran on, which the editor opens on. */
    private static WeakReference<Activity> lastFeed = new WeakReference<>(null);

    private OledOverlays() {
    }

    /** The saved panels, read again only when the setting's text has changed. */
    static List<OledOverlay> saved() {
        String text = Settings.OLED_OVERLAYS.get();
        if (savedText == null || !savedText.equals(text)) {
            savedText = text;
            saved = OledOverlay.parse(text);
        }
        return saved;
    }

    /** {@code allowed} is false under Pause and without the overlay patch. */
    public static void pass(Activity activity, boolean allowed) {
        try {
            if (!FeedVisibility.isFeedWindow(activity)) return;
            if (!FeedVisibility.isDetailPager(activity)) lastFeed = new WeakReference<>(activity);
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout)) return;
            FrameLayout frame = (FrameLayout) content;
            View existing = frame.findViewWithTag(TAG);

            boolean editing = allowed && OledOverlayEditor.isEditingOn(activity, frame);
            List<OledOverlay> list = editing ? OledOverlayEditor.working() : saved();
            if (!allowed || (list.isEmpty() && !editing)) {
                if (existing != null) frame.removeView(existing);
                return;
            }

            OledOverlayLayer layer = existing instanceof OledOverlayLayer ? (OledOverlayLayer) existing : null;
            if (layer == null) {
                if (existing != null) frame.removeView(existing);
                layer = new OledOverlayLayer(activity);
                layer.setTag(TAG);
                frame.addView(layer, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            int page = FeedVisibility.currentPage(activity);
            layer.update(list, page == FeedVisibility.PAGE_UNKNOWN ? 0 : pageFlag(page),
                    editing, editing ? OledOverlayEditor.selectedId() : -1L);
            // While the editor is open the layer takes the touches and moves the selected panel.
            final View windowFrame = frame;
            layer.setOnTouchListener(editing
                    ? (view, event) -> OledOverlayEditor.onLayerTouch(
                            event, windowFrame.getWidth(), windowFrame.getHeight())
                    : null);
            // TikTok adds its own views to the same frame; the panels stay above them.
            if (frame.indexOfChild(layer) != frame.getChildCount() - 1) layer.bringToFront();
            if (editing) OledOverlayEditor.keepPanelOnTop(frame);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not update the OLED overlays", ex);
        }
    }

    /** Runs a pass on the main feed window again, for a change the editor just made. */
    static void refresh() {
        Activity activity = lastFeed.get();
        if (activity != null && !activity.isFinishing()) VideoOverlayHider.applyTo(activity);
    }

    /** From the settings row: closes the settings and opens the editor over the feed. */
    public static void openEditor(android.content.Context context) {
        OledOverlayEditor.request(context);
    }

    /** Only this class's own pass, for the editor's frequent changes: a drag is many of them. */
    static void quick() {
        Activity activity = lastFeed.get();
        if (activity != null && !activity.isFinishing()) pass(activity, true);
    }

    static int pageFlag(int page) {
        switch (page) {
            case FeedVisibility.PAGE_PROFILE:
                return OledOverlay.PAGE_PROFILE;
            case FeedVisibility.PAGE_INBOX:
                return OledOverlay.PAGE_INBOX;
            case FeedVisibility.PAGE_FRIENDS:
                return OledOverlay.PAGE_FRIENDS;
            default:
                return OledOverlay.PAGE_HOME;
        }
    }
}
