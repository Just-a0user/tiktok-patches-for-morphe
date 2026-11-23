/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The editor for the OLED overlays, opened from the settings and drawn over the live feed, so a
 * panel is placed against the real screen. Everything is built in code here: nothing outside the
 * patch is involved.
 *
 * <p>The settings ask for it ({@link #request}, or {@link #requestPreview} with panels to try) and
 * close the settings screen; the next overlay pass on the main window opens it. A panel is dragged
 * with a finger and sized with the sliders or with a number typed in the box beside each. A panel
 * that follows an element of the app takes its place from the element, so it is not dragged and has
 * a padding instead of a size. Changes show live and are written only by Save; closing asks first
 * when something is not saved.
 */
final class OledOverlayEditor {
    private interface Edit {
        void apply(OledOverlay overlay);
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static boolean requested;
    private static boolean open;
    private static boolean dirty;
    private static List<OledOverlay> pendingPreview;
    private static List<OledOverlay> working = new ArrayList<>();
    private static long selected = -1L;
    private static int tab;
    private static final int TAB_ELEMENTS = 3;
    private static int initialTab;
    private static boolean minimized;
    private static WeakReference<View> bubbleRef = new WeakReference<>(null);
    private static String elementKey = "like_icon";
    private static boolean presetSoft = true;
    private static TextView elementName;
    private static TextView presetElementName;
    private static Switch elementSwitch;
    private static OledUi.NumberSlider elementSlider;
    private static OledUi.NumberSlider edgeTop;
    private static OledUi.NumberSlider edgeBottom;
    private static OledUi.NumberSlider edgeStrength;
    private static Button edgeShape;
    private static Button presetVersion;
    private static EditText edgeColor;
    private static WeakReference<Activity> window = new WeakReference<>(null);
    private static WeakReference<FrameLayout> frameRef = new WeakReference<>(null);
    private static WeakReference<View> panelRef = new WeakReference<>(null);

    private static OledUi.NumberSlider widthSlider;
    private static OledUi.NumberSlider heightSlider;
    private static OledUi.NumberSlider paddingSlider;
    private static OledUi.NumberSlider roundingSlider;
    private static OledUi.NumberSlider opacitySlider;
    private static OledUi.NumberSlider softSlider;
    private static OledUi.NumberSlider graySlider;
    private static OledUi.NumberSlider animSlider;
    private static TextView elementLabel;
    private static TextView followInfo;
    private static Button stopFollowing;
    private static Button gradientButton;
    private static Button softAxisButton;
    private static final List<Button> TAB_BUTTONS = new ArrayList<>();
    private static final List<View> SECTIONS = new ArrayList<>();
    private static final List<CheckBox> PAGE_BOXES = new ArrayList<>();
    private static final int[] PAGE_FLAGS = {
            OledOverlay.PAGE_HOME, OledOverlay.PAGE_PROFILE, OledOverlay.PAGE_INBOX, OledOverlay.PAGE_FRIENDS};
    private static boolean syncing;

    private static float downX;
    private static float downY;
    private static int originX;
    private static int originY;
    private static boolean dragging;

    private OledOverlayEditor() {
    }

    /** From the settings: leave the settings and open the editor on the feed behind them. */
    static void request(Context context) {
        requestAt(context, 0);
    }

    /** The same, with the editor opening on one of its tabs. */
    static void requestAt(Context context, int firstTab) {
        initialTab = firstTab;
        requested = true;
        Activity settings = activityOf(context);
        if (settings != null) settings.finish();
        HANDLER.postDelayed(OledOverlays::refresh, 400L);
        HANDLER.postDelayed(OledOverlays::refresh, 1200L);
    }

    /** The same, with panels added to what is saved, to try before they are kept. */
    static void requestPreview(Context context, List<OledOverlay> panels) {
        pendingPreview = panels;
        request(context);
    }

    /** Opens the editor on this window if it was asked for, and says whether it is open on it. */
    static boolean isEditingOn(Activity activity, FrameLayout frame) {
        if (!open && requested && !FeedVisibility.isDetailPager(activity)) {
            open(activity, frame);
        } else if (open && window.get() != activity && !FeedVisibility.isDetailPager(activity)) {
            // The window was made again (a turn of the screen): the same edit, a new panel.
            attachPanel(activity, frame);
        }
        return open && window.get() == activity;
    }

    static List<OledOverlay> working() {
        return working;
    }

    static long selectedId() {
        return selected;
    }

    static void keepPanelOnTop(FrameLayout frame) {
        View panel = panelRef.get();
        if (panel != null && frame.indexOfChild(panel) != frame.getChildCount() - 1) panel.bringToFront();
        View bubble = bubbleRef.get();
        if (bubble != null && frame.indexOfChild(bubble) != frame.getChildCount() - 1) bubble.bringToFront();
    }

    private static void open(Activity activity, FrameLayout frame) {
        requested = false;
        open = true;
        dirty = false;
        working = OledOverlay.copyOf(OledOverlays.saved());
        selected = working.isEmpty() ? -1L : working.get(working.size() - 1).id;
        if (pendingPreview != null) {
            long id = nextId();
            for (OledOverlay panel : pendingPreview) {
                panel.id = id++;
                working.add(panel);
            }
            if (!pendingPreview.isEmpty()) {
                selected = pendingPreview.get(0).id;
                dirty = true;
            }
            pendingPreview = null;
        }
        tab = initialTab;
        initialTab = 0;
        minimized = false;
        attachPanel(activity, frame);
    }

    private static long nextId() {
        long id = 1L;
        for (OledOverlay other : working) id = Math.max(id, other.id + 1L);
        return id;
    }

    private static void attachPanel(Activity activity, FrameLayout frame) {
        View old = panelRef.get();
        if (old != null && old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        window = new WeakReference<>(activity);
        frameRef = new WeakReference<>(frame);
        View panel = buildPanel(activity, frame);
        panelRef = new WeakReference<>(panel);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = OledUi.dp(activity, 8);
        params.setMargins(margin, margin * 4, margin, margin * 4);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        frame.addView(panel, params);
        View oldBubble = bubbleRef.get();
        if (oldBubble != null && oldBubble.getParent() instanceof ViewGroup) ((ViewGroup) oldBubble.getParent()).removeView(oldBubble);
        View bubble = buildBubble(activity);
        bubbleRef = new WeakReference<>(bubble);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(OledUi.dp(activity, 48), OledUi.dp(activity, 48));
        bubbleParams.gravity = Gravity.TOP | Gravity.END;
        bubbleParams.setMargins(0, OledUi.dp(activity, 120), OledUi.dp(activity, 12), 0);
        frame.addView(bubble, bubbleParams);
        panel.setVisibility(minimized ? View.GONE : View.VISIBLE);
        bubble.setVisibility(minimized ? View.VISIBLE : View.GONE);
        sync();
    }

    private static void close() {
        open = false;
        requested = false;
        dirty = false;
        selected = -1L;
        pendingPreview = null;
        View panel = panelRef.get();
        if (panel != null && panel.getParent() instanceof ViewGroup) ((ViewGroup) panel.getParent()).removeView(panel);
        panelRef = new WeakReference<>(null);
        View bubble = bubbleRef.get();
        if (bubble != null && bubble.getParent() instanceof ViewGroup) ((ViewGroup) bubble.getParent()).removeView(bubble);
        bubbleRef = new WeakReference<>(null);
        minimized = false;
        working = new ArrayList<>();
        OledOverlays.quick();
    }

    private static void save(Activity activity) {
        Settings.OLED_OVERLAYS.save(OledOverlay.serialize(working));
        dirty = false;
        Toast.makeText(activity, L10n.t(activity, "Saved"), Toast.LENGTH_SHORT).show();
    }

    private static void askBeforeClosing(Activity activity) {
        if (!dirty) {
            close();
            return;
        }
        new AlertDialog.Builder(activity)
                .setMessage(L10n.t(activity, "You have changes that are not saved."))
                .setPositiveButton(L10n.t(activity, "Save"), (dialog, which) -> {
                    save(activity);
                    close();
                })
                .setNegativeButton(L10n.t(activity, "Discard"), (dialog, which) -> close())
                .setNeutralButton(L10n.t(activity, "Cancel"), null)
                .show();
    }

    // The panel.

    private static View buildPanel(Activity activity, FrameLayout frame) {
        PAGE_BOXES.clear();
        TAB_BUTTONS.clear();
        SECTIONS.clear();
        int windowWidth = Math.max(frame.getWidth(), activity.getResources().getDisplayMetrics().widthPixels);
        int windowHeight = Math.max(frame.getHeight(), activity.getResources().getDisplayMetrics().heightPixels);
        final int maxHeight = Math.round(windowHeight * 0.55f);

        ScrollView scroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.setBackground(OledUi.rounded(activity, OledUi.PANEL, 16));
        scroll.setClickable(true);
        scroll.setElevation(OledUi.dp(activity, 6));

        LinearLayout column = OledUi.column(activity);
        int pad = OledUi.dp(activity, 8);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The top row: the handle to move the panel by, and the three things done most.
        LinearLayout header = OledUi.row(activity);
        TextView handle = OledUi.text(activity, "☰  " + L10n.t(activity, "OLED overlays"), 14f, OledUi.TEXT);
        handle.setTypeface(Typeface.DEFAULT_BOLD);
        handle.setPadding(OledUi.dp(activity, 4), OledUi.dp(activity, 8), OledUi.dp(activity, 4), OledUi.dp(activity, 8));
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private float startTx;
            private float startTy;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startTx = scroll.getTranslationX();
                        startTy = scroll.getTranslationY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        scroll.setTranslationX(startTx + event.getRawX() - startX);
                        scroll.setTranslationY(startTy + event.getRawY() - startY);
                        return true;
                    default:
                        return true;
                }
            }
        });
        header.addView(handle, OledUi.weight(activity, 1f));
        Button create = OledUi.button(activity, "+ " + L10n.t(activity, "New"));
        create.setOnClickListener(v -> createElement(activity));
        header.addView(create, OledUi.wrap(activity));
        Button save = OledUi.button(activity, L10n.t(activity, "Save"));
        save.setOnClickListener(v -> save(activity));
        header.addView(save, OledUi.wrap(activity));
        Button minimize = OledUi.button(activity, "\u2014");
        minimize.setOnClickListener(v -> setMinimized(true));
        header.addView(minimize, OledUi.wrap(activity));
        Button closeButton = OledUi.button(activity, "✕");
        closeButton.setOnClickListener(v -> askBeforeClosing(activity));
        header.addView(closeButton, OledUi.wrap(activity));
        column.addView(header);

        // Which panel.
        LinearLayout pick = OledUi.row(activity);
        Button previous = OledUi.button(activity, "◀");
        previous.setOnClickListener(v -> step(-1));
        pick.addView(previous, OledUi.wrap(activity));
        elementLabel = OledUi.text(activity, "", 13f, OledUi.MUTED);
        elementLabel.setGravity(Gravity.CENTER);
        pick.addView(elementLabel, OledUi.weight(activity, 1f));
        Button next = OledUi.button(activity, "▶");
        next.setOnClickListener(v -> step(1));
        pick.addView(next, OledUi.wrap(activity));
        Button delete = OledUi.button(activity, L10n.t(activity, "Delete"));
        delete.setOnClickListener(v -> deleteSelected());
        pick.addView(delete, OledUi.wrap(activity));
        column.addView(pick);

        // The three tabs.
        LinearLayout tabs = OledUi.row(activity);
        String[] tabNames = {L10n.t(activity, "Shape"), L10n.t(activity, "Gradient"), L10n.t(activity, "Pages"),
                L10n.t(activity, "Elements"), L10n.t(activity, "Presets"), L10n.t(activity, "Edges")};
        for (int index = 0; index < tabNames.length; index++) {
            final int shown = index;
            Button button = OledUi.button(activity, tabNames[index]);
            button.setOnClickListener(v -> setTab(shown));
            TAB_BUTTONS.add(button);
            tabs.addView(button, OledUi.wrap(activity));
        }
        HorizontalScrollView tabScroll = new HorizontalScrollView(activity);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.addView(tabs);
        column.addView(tabScroll);

        column.addView(buildShape(activity, windowWidth, windowHeight));
        column.addView(buildGradient(activity));
        column.addView(buildPages(activity));
        column.addView(buildElements(activity));
        column.addView(buildPresets(activity));
        column.addView(buildEdges(activity));
        setTab(tab);
        scroll.setTag("oled_editor_panel");
        return scroll;
    }

    private static View buildShape(Activity activity, int windowWidth, int windowHeight) {
        LinearLayout section = OledUi.column(activity);
        followInfo = OledUi.text(activity, "", 12f, OledUi.ACCENT);
        section.addView(followInfo);
        stopFollowing = OledUi.button(activity, L10n.t(activity, "Stop following"));
        stopFollowing.setOnClickListener(v -> stopFollowingElement());
        section.addView(stopFollowing, OledUi.wrap(activity));

        widthSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Width"), "px", 20,
                Math.min(windowWidth, OledOverlay.MAX_SIZE_PX), OledOverlay.MAX_SIZE_PX,
                value -> edit(o -> o.width = value));
        heightSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Height"), "px", 10,
                Math.min(windowHeight, OledOverlay.MAX_SIZE_PX), OledOverlay.MAX_SIZE_PX,
                value -> edit(o -> o.height = value));
        paddingSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Padding"), "px", 0, 100, 200,
                value -> edit(o -> o.padding = value));
        roundingSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Rounding"), "px", 0, 300,
                OledOverlay.MAX_ROUNDING_PX, value -> edit(o -> o.rounding = value));
        opacitySlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Opacity"), "%", 0, 100, 100,
                value -> edit(o -> o.alpha = value));
        softSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Soft edge"), "dp", 0, 60,
                OledOverlay.MAX_SOFT_EDGE_DP, value -> edit(o -> o.softEdgeDp = value));
        section.addView(widthSlider.root);
        section.addView(heightSlider.root);
        section.addView(paddingSlider.root);
        section.addView(roundingSlider.root);
        section.addView(opacitySlider.root);
        section.addView(softSlider.root);
        softAxisButton = OledUi.button(activity, "");
        softAxisButton.setOnClickListener(v -> edit(o -> o.softAxis = (o.softAxis + 1) % 3));
        section.addView(softAxisButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SECTIONS.add(section);
        return section;
    }

    private static View buildGradient(Activity activity) {
        LinearLayout section = OledUi.column(activity);
        gradientButton = OledUi.button(activity, "");
        gradientButton.setOnClickListener(v -> cycleGradient());
        section.addView(gradientButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        graySlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Gray at the end"), "", 0, 64,
                OledOverlay.MAX_GRAY, value -> edit(o -> {
                    o.gray = value;
                    // Moving the gray is asking for the gradient: it starts itself.
                    if (OledOverlay.GRADIENT_OFF.equals(o.gradient)) o.gradient = OledOverlay.GRADIENT_RIGHT_TO_LEFT;
                }));
        animSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Seconds per round"), "s", 0, 600, 86_400,
                value -> edit(o -> o.animSeconds = value));
        section.addView(graySlider.root);
        section.addView(animSlider.root);
        section.addView(OledUi.text(activity,
                L10n.t(activity, "Moving the gray turns the gradient on. It runs as an animation; 0 seconds keeps it still."),
                11f, OledUi.MUTED));
        SECTIONS.add(section);
        return section;
    }

    private static View buildPages(Activity activity) {
        LinearLayout section = OledUi.column(activity);
        section.addView(OledUi.text(activity, L10n.t(activity, "Show on these pages"), 13f, OledUi.TEXT));
        String[] names = {L10n.t(activity, "Home"), L10n.t(activity, "Profile"),
                L10n.t(activity, "Inbox"), L10n.t(activity, "Friends")};
        LinearLayout first = OledUi.row(activity);
        LinearLayout second = OledUi.row(activity);
        for (int index = 0; index < PAGE_FLAGS.length; index++) {
            final int flag = PAGE_FLAGS[index];
            CheckBox box = new CheckBox(activity);
            box.setText(names[index]);
            box.setTextColor(OledUi.TEXT);
            box.setTextSize(13f);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (syncing) return;
                edit(o -> o.pages = checked ? (o.pages | flag) : (o.pages & ~flag));
            });
            PAGE_BOXES.add(box);
            (index < 2 ? first : second).addView(box, OledUi.weight(activity, 1f));
        }
        section.addView(first);
        section.addView(second);
        SECTIONS.add(section);
        return section;
    }

    private static void setTab(int shown) {
        tab = shown;
        for (int index = 0; index < SECTIONS.size(); index++) {
            SECTIONS.get(index).setVisibility(index == shown ? View.VISIBLE : View.GONE);
        }
        Activity activity = window.get();
        for (int index = 0; index < TAB_BUTTONS.size() && activity != null; index++) {
            TAB_BUTTONS.get(index).setBackground(OledUi.rounded(activity,
                    index == shown ? OledUi.ACCENT : OledUi.BUTTON, 10));
            TAB_BUTTONS.get(index).setTextColor(index == shown ? 0xFF001414 : OledUi.TEXT);
        }
    }

    // The tabs that work on the real feed behind the editor.

    private static int elementIndex() {
        List<OledElements.Element> all = OledElements.all();
        for (int index = 0; index < all.size(); index++) {
            if (all.get(index).key.equals(elementKey)) return index;
        }
        return 0;
    }

    private static void stepElement(int by) {
        List<OledElements.Element> all = OledElements.all();
        elementKey = all.get((elementIndex() + by + all.size()) % all.size()).key;
        syncElement();
        OledOverlays.quick();
    }

    private static LinearLayout elementPicker(Activity activity, boolean forElements) {
        LinearLayout pick = OledUi.row(activity);
        Button previous = OledUi.button(activity, "\u25C0");
        previous.setOnClickListener(v -> stepElement(-1));
        pick.addView(previous, OledUi.wrap(activity));
        TextView name = OledUi.text(activity, "", 13f, OledUi.ACCENT);
        name.setGravity(Gravity.CENTER);
        if (forElements) elementName = name; else presetElementName = name;
        pick.addView(name, OledUi.weight(activity, 1f));
        Button next = OledUi.button(activity, "\u25B6");
        next.setOnClickListener(v -> stepElement(1));
        pick.addView(next, OledUi.wrap(activity));
        return pick;
    }

    private static View buildElements(Activity activity) {
        LinearLayout section = OledUi.column(activity);
        section.addView(OledUi.text(activity, L10n.t(activity,
                "Tap an element on the feed, or pick it with the arrows. Changes here are kept at once."), 11f, OledUi.MUTED));
        section.addView(elementPicker(activity, true));
        elementSwitch = new Switch(activity);
        elementSwitch.setText(L10n.t(activity, "Dim this element"));
        elementSwitch.setTextColor(OledUi.TEXT);
        elementSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return;
            OledElementSettings.set(elementKey, checked, OledElementSettings.transparency(elementKey));
            syncElement();
            OledOverlays.refresh();
        });
        section.addView(elementSwitch);
        elementSlider = new OledUi.NumberSlider(activity, L10n.t(activity, "Transparency (100 = invisible)"), "%",
                0, 100, 100, value -> {
                    if (syncing) return;
                    OledElementSettings.set(elementKey, true, value);
                    syncElement();
                    OledOverlays.refresh();
                });
        section.addView(elementSlider.root);
        SECTIONS.add(section);
        return section;
    }

    private static View buildPresets(Activity activity) {
        LinearLayout section = OledUi.column(activity);
        section.addView(OledUi.text(activity, L10n.t(activity,
                "A preset is a panel that follows the real element. It appears on the feed, where you can change it before saving."),
                11f, OledUi.MUTED));
        presetVersion = OledUi.button(activity, "");
        presetVersion.setOnClickListener(v -> {
            presetSoft = !presetSoft;
            syncPresetVersion();
        });
        section.addView(presetVersion, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(OledUi.text(activity, L10n.t(activity, "Packages"), 13f, OledUi.ACCENT));
        for (java.util.Map.Entry<String, List<String>> pack : OledPresets.PACKAGES.entrySet()) {
            final List<String> keys = pack.getValue();
            Button button = OledUi.button(activity, L10n.t(activity, pack.getKey()));
            button.setOnClickListener(v -> addPanels(OledPresets.forElements(keys, presetSoft)));
            section.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        section.addView(OledUi.text(activity, L10n.t(activity, "One element"), 13f, OledUi.ACCENT));
        section.addView(elementPicker(activity, false));
        Button add = OledUi.button(activity, "+ " + L10n.t(activity, "Add"));
        add.setOnClickListener(v -> {
            List<OledOverlay> one = new ArrayList<>();
            one.add(OledPresets.forElement(elementKey, presetSoft));
            addPanels(one);
        });
        section.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SECTIONS.add(section);
        return section;
    }

    private static void addPanels(List<OledOverlay> panels) {
        if (panels.isEmpty()) return;
        long id = nextId();
        for (OledOverlay panel : panels) {
            panel.id = id++;
            working.add(panel);
        }
        selected = panels.get(0).id;
        setTab(0);
        changed();
    }

    private static final String[] EDGE_SHAPES = {"Straight", "Smooth", "Hard band"};

    private static View buildEdges(Activity activity) {
        LinearLayout section = OledUi.column(activity);
        section.addView(OledUi.text(activity, L10n.t(activity,
                "The video fades at its top and bottom edge. This is drawn on the video itself, not as a panel, and only in the feed. Kept at once."),
                11f, OledUi.MUTED));
        edgeTop = new OledUi.NumberSlider(activity, L10n.t(activity, "Top edge"), "px", 0, 400, 600,
                value -> { Settings.OLED_VIDEO_EDGE_TOP.save(value); OledOverlays.refresh(); });
        edgeBottom = new OledUi.NumberSlider(activity, L10n.t(activity, "Bottom edge"), "px", 0, 400, 600,
                value -> { Settings.OLED_VIDEO_EDGE_BOTTOM.save(value); OledOverlays.refresh(); });
        edgeStrength = new OledUi.NumberSlider(activity, L10n.t(activity, "Strength"), "%", 0, 100, 100,
                value -> { Settings.OLED_VIDEO_EDGE_STRENGTH.save(value); OledOverlays.refresh(); });
        section.addView(edgeTop.root);
        section.addView(edgeBottom.root);
        section.addView(edgeStrength.root);
        edgeShape = OledUi.button(activity, "");
        edgeShape.setOnClickListener(v -> {
            Settings.OLED_VIDEO_EDGE_SHAPE.save((Settings.OLED_VIDEO_EDGE_SHAPE.get() + 1) % EDGE_SHAPES.length);
            syncEdges();
            OledOverlays.refresh();
        });
        section.addView(edgeShape, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout colorRow = OledUi.row(activity);
        colorRow.addView(OledUi.text(activity, L10n.t(activity, "Colour (hex)"), 13f, OledUi.TEXT), OledUi.weight(activity, 1f));
        edgeColor = new EditText(activity);
        edgeColor.setSingleLine(true);
        edgeColor.setTextColor(OledUi.TEXT);
        edgeColor.setTextSize(13f);
        edgeColor.setGravity(Gravity.CENTER);
        edgeColor.setBackground(OledUi.rounded(activity, OledUi.CARD, 8));
        edgeColor.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        edgeColor.setOnEditorActionListener((view, action, event) -> {
            commitEdgeColor();
            return false;
        });
        edgeColor.setOnFocusChangeListener((view, focused) -> {
            if (!focused) commitEdgeColor();
        });
        colorRow.addView(edgeColor, new LinearLayout.LayoutParams(OledUi.dp(activity, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(colorRow);
        SECTIONS.add(section);
        return section;
    }

    private static void commitEdgeColor() {
        String text = edgeColor.getText().toString().trim().replace("#", "");
        if (text.matches("[0-9a-fA-F]{6}")) {
            Settings.OLED_VIDEO_EDGE_COLOR.save(text.toUpperCase(Locale.ROOT));
            OledOverlays.refresh();
        } else {
            edgeColor.setText(Settings.OLED_VIDEO_EDGE_COLOR.get());
        }
    }

    private static void syncPresetVersion() {
        Activity activity = window.get();
        if (activity == null || presetVersion == null) return;
        presetVersion.setText(L10n.t(activity, presetSoft ? "Soft edge" : "Sharp edge"));
    }

    private static void syncEdges() {
        Activity activity = window.get();
        if (activity == null || edgeTop == null) return;
        edgeTop.set(Settings.OLED_VIDEO_EDGE_TOP.get());
        edgeBottom.set(Settings.OLED_VIDEO_EDGE_BOTTOM.get());
        edgeStrength.set(Settings.OLED_VIDEO_EDGE_STRENGTH.get());
        edgeShape.setText(L10n.t(activity, "Shape") + ": "
                + L10n.t(activity, EDGE_SHAPES[Settings.OLED_VIDEO_EDGE_SHAPE.get() % EDGE_SHAPES.length]));
        if (!edgeColor.hasFocus()) edgeColor.setText(Settings.OLED_VIDEO_EDGE_COLOR.get());
    }

    private static void syncElement() {
        Activity activity = window.get();
        OledElements.Element element = OledElements.byKey(elementKey);
        if (activity == null || element == null || elementSwitch == null) return;
        String name = L10n.t(activity, element.label);
        if (OledElements.statusOf(elementKey) == 0) name += " (" + L10n.t(activity, "not found") + ")";
        elementName.setText(name);
        presetElementName.setText(name);
        syncing = true;
        try {
            elementSwitch.setChecked(OledElementSettings.isOn(elementKey));
            elementSlider.set(OledElementSettings.transparency(elementKey));
            elementSlider.setEnabled(OledElementSettings.isOn(elementKey));
        } finally {
            syncing = false;
        }
    }

    /** The frame the layer draws around the chosen element on the Elements tab, or null. */
    static int[] highlight(Activity activity, FrameLayout frame) {
        if (!open || tab != TAB_ELEMENTS || minimized) return null;
        OledElements.Element element = OledElements.byKey(elementKey);
        if (element == null) return null;
        int[] bounds = OledElements.boundsOf(activity, element, frame);
        OledElements.note(element.key, bounds != null);
        return bounds;
    }

    static boolean minimized() {
        return open && minimized;
    }

    private static void setMinimized(boolean value) {
        minimized = value;
        View panel = panelRef.get();
        View bubble = bubbleRef.get();
        if (panel != null) panel.setVisibility(value ? View.GONE : View.VISIBLE);
        if (bubble != null) bubble.setVisibility(value ? View.VISIBLE : View.GONE);
        OledOverlays.quick();
    }

    /** The small round button the editor shrinks to: it can be dragged, and a tap opens the editor again. */
    private static View buildBubble(Activity activity) {
        TextView bubble = OledUi.text(activity, "\u2630", 20f, 0xFF001414);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(OledUi.rounded(activity, OledUi.ACCENT, 24));
        bubble.setElevation(OledUi.dp(activity, 6));
        final float slop = OledUi.dp(activity, 8);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private float startTx;
            private float startTy;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startTx = view.getTranslationX();
                        startTy = view.getTranslationY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - startX) > slop || Math.abs(event.getRawY() - startY) > slop) moved = true;
                        if (moved) {
                            view.setTranslationX(startTx + event.getRawX() - startX);
                            view.setTranslationY(startTy + event.getRawY() - startY);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) setMinimized(false);
                        return true;
                    default:
                        return true;
                }
            }
        });
        return bubble;
    }

    /** A tap on the feed on the Elements tab chooses the smallest real element under the finger. */
    private static void pickElementAt(float x, float y) {
        Activity activity = window.get();
        FrameLayout frame = frameRef.get();
        if (activity == null || frame == null) return;
        String best = null;
        long bestArea = Long.MAX_VALUE;
        for (OledElements.Element element : OledElements.all()) {
            int[] bounds = OledElements.boundsOf(activity, element, frame);
            if (bounds == null || x < bounds[0] || x > bounds[0] + bounds[2] || y < bounds[1] || y > bounds[1] + bounds[3]) continue;
            long area = (long) bounds[2] * bounds[3];
            if (area < bestArea) {
                bestArea = area;
                best = element.key;
            }
        }
        if (best != null) {
            elementKey = best;
            syncElement();
            OledOverlays.quick();
        }
    }

    // What the panel does.

    private static OledOverlay current() {
        for (OledOverlay overlay : working) {
            if (overlay.id == selected) return overlay;
        }
        return null;
    }

    private static void edit(Edit edit) {
        if (syncing) return;
        OledOverlay overlay = current();
        if (overlay == null) return;
        edit.apply(overlay);
        overlay.clamp();
        changed();
    }

    private static void createElement(Activity activity) {
        FrameLayout frame = frameRef.get();
        int windowWidth = frame != null && frame.getWidth() > 0 ? frame.getWidth()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int windowHeight = frame != null && frame.getHeight() > 0 ? frame.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;
        OledOverlay overlay = new OledOverlay();
        overlay.id = nextId();
        overlay.width = Math.min(overlay.width, windowWidth);
        overlay.height = Math.min(overlay.height, windowHeight);
        overlay.x = (windowWidth - overlay.width) / 2;
        // Low, by the bottom icons and away from the editor at the top.
        overlay.y = Math.max(0, windowHeight - overlay.height - OledUi.dp(activity, 96));
        working.add(overlay);
        selected = overlay.id;
        changed();
    }

    private static void deleteSelected() {
        OledOverlay overlay = current();
        if (overlay == null) return;
        int index = working.indexOf(overlay);
        working.remove(overlay);
        selected = working.isEmpty() ? -1L : working.get(Math.max(0, Math.min(index, working.size() - 1))).id;
        changed();
    }

    private static void step(int by) {
        if (working.isEmpty()) return;
        int index = 0;
        for (int at = 0; at < working.size(); at++) {
            if (working.get(at).id == selected) index = at;
        }
        selected = working.get((index + by + working.size()) % working.size()).id;
        changed();
    }

    private static void cycleGradient() {
        edit(overlay -> {
            switch (overlay.gradient) {
                case OledOverlay.GRADIENT_OFF:
                    overlay.gradient = OledOverlay.GRADIENT_RIGHT_TO_LEFT;
                    break;
                case OledOverlay.GRADIENT_RIGHT_TO_LEFT:
                    overlay.gradient = OledOverlay.GRADIENT_LEFT_TO_RIGHT;
                    break;
                case OledOverlay.GRADIENT_LEFT_TO_RIGHT:
                    overlay.gradient = OledOverlay.GRADIENT_CENTER_OUT;
                    break;
                default:
                    overlay.gradient = OledOverlay.GRADIENT_OFF;
            }
        });
    }

    /** A panel that followed an element becomes one that stays where the element was. */
    private static void stopFollowingElement() {
        OledOverlay overlay = current();
        if (overlay == null || !overlay.follows()) return;
        for (OledOverlay placed : OledOverlays.resolved()) {
            if (placed.id == overlay.id) {
                overlay.x = placed.x;
                overlay.y = placed.y;
                overlay.width = placed.width;
                overlay.height = placed.height;
            }
        }
        overlay.follow = "";
        overlay.clamp();
        changed();
    }

    /** Something about a panel changed: mark it, show it, and bring the panel's controls in line. */
    private static void changed() {
        dirty = true;
        sync();
        OledOverlays.quick();
    }

    /** Puts the selected panel's numbers into the sliders, labels and boxes. */
    private static void sync() {
        Activity activity = window.get();
        if (activity == null || widthSlider == null) return;
        OledOverlay overlay = current();
        boolean has = overlay != null;
        boolean follows = has && overlay.follows();
        syncing = true;
        try {
            elementLabel.setText(working.isEmpty()
                    ? L10n.t(activity, "No elements yet. Tap New to add one.")
                    : L10n.t(activity, "Element") + " " + (has ? working.indexOf(overlay) + 1 : 0) + " / " + working.size());
            OledUi.NumberSlider[] all = {widthSlider, heightSlider, paddingSlider, roundingSlider, opacitySlider,
                    softSlider, graySlider, animSlider};
            for (OledUi.NumberSlider slider : all) slider.setEnabled(has);
            widthSlider.setVisible(!follows);
            heightSlider.setVisible(!follows);
            paddingSlider.setVisible(follows);
            followInfo.setVisibility(follows ? View.VISIBLE : View.GONE);
            stopFollowing.setVisibility(follows ? View.VISIBLE : View.GONE);
            gradientButton.setEnabled(has);
            softAxisButton.setEnabled(has);
            if (has) {
                if (follows) {
                    OledElements.Element element = OledElements.byKey(overlay.follow);
                    String name = element == null ? overlay.follow : L10n.t(activity, element.label);
                    followInfo.setText(L10n.t(activity, "Follows") + ": " + name);
                }
                widthSlider.set(overlay.width);
                heightSlider.set(overlay.height);
                paddingSlider.set(overlay.padding);
                roundingSlider.set(overlay.rounding);
                opacitySlider.set(overlay.alpha);
                softSlider.set(overlay.softEdgeDp);
                softAxisButton.setText(L10n.t(activity, "Soft edge on") + ": " + L10n.t(activity,
                        overlay.softAxis == OledOverlay.AXIS_HORIZONTAL ? "Left and right"
                                : overlay.softAxis == OledOverlay.AXIS_VERTICAL ? "Top and bottom" : "All sides"));
                graySlider.set(overlay.gray);
                graySlider.setName(L10n.t(activity, "Gray at the end") + String.format(Locale.ROOT,
                        " #%02X%02X%02X", overlay.gray, overlay.gray, overlay.gray));
                animSlider.set(overlay.animSeconds);
                String name;
                if (OledOverlay.GRADIENT_LEFT_TO_RIGHT.equals(overlay.gradient)) name = L10n.t(activity, "Left to right");
                else if (OledOverlay.GRADIENT_RIGHT_TO_LEFT.equals(overlay.gradient)) name = L10n.t(activity, "Right to left");
                else if (OledOverlay.GRADIENT_CENTER_OUT.equals(overlay.gradient)) name = L10n.t(activity, "Center outwards");
                else name = L10n.t(activity, "Off");
                gradientButton.setText(L10n.t(activity, "Gradient") + ": " + name);
            } else {
                gradientButton.setText(L10n.t(activity, "Gradient"));
            }
            syncElement();
            syncEdges();
            syncPresetVersion();
            for (int index = 0; index < PAGE_BOXES.size(); index++) {
                CheckBox box = PAGE_BOXES.get(index);
                box.setEnabled(has);
                box.setChecked(has && (overlay.pages & PAGE_FLAGS[index]) != 0);
            }
        } finally {
            syncing = false;
        }
    }

    // Moving a panel with a finger. Wired to the layer by OledOverlays while the editor is open.

    static boolean onLayerTouch(MotionEvent event, int windowWidth, int windowHeight) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dragging = false;
                if (tab == TAB_ELEMENTS) return true;
                List<OledOverlay> drawn = OledOverlays.resolved();
                for (int index = drawn.size() - 1; index >= 0; index--) {
                    OledOverlay hit = drawn.get(index);
                    if (!hit.contains(event.getX(), event.getY())) continue;
                    selected = hit.id;
                    OledOverlay base = current();
                    if (base != null && !base.follows()) {
                        downX = event.getX();
                        downY = event.getY();
                        originX = base.x;
                        originY = base.y;
                        dragging = true;
                    }
                    sync();
                    OledOverlays.quick();
                    break;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                OledOverlay overlay = current();
                if (!dragging || overlay == null || overlay.follows()) return true;
                overlay.x = OledOverlay.within(Math.round(originX + event.getX() - downX),
                        0, Math.max(0, windowWidth - overlay.width));
                overlay.y = OledOverlay.within(Math.round(originY + event.getY() - downY),
                        0, Math.max(0, windowHeight - overlay.height));
                dirty = true;
                OledOverlays.quick();
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (tab == TAB_ELEMENTS) pickElementAt(event.getX(), event.getY());
                dragging = false;
                return true;
            default:
                dragging = false;
                return true;
        }
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
