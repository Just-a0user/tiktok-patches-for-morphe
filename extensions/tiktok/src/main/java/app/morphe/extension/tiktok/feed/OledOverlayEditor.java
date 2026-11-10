/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
 * <p>The settings row asks for it ({@link #request}) and closes the settings screen; the next
 * overlay pass on the main window opens it. A panel is dragged with a finger and sized with the
 * sliders, or with a number typed after a tap on its label. Changes show live and are written only
 * by Save; the close button asks first when there is something unsaved.
 */
final class OledOverlayEditor {
    private interface Read {
        int get(OledOverlay overlay);
    }

    private interface Write {
        void set(OledOverlay overlay, int value);
    }

    /** A label over a slider, for one number of the selected panel. */
    private static final class SliderRow {
        final String name;
        final String unit;
        final int min;
        final Read read;
        final Write write;
        int max;
        TextView label;
        SeekBar bar;

        SliderRow(String name, String unit, int min, int max, Read read, Write write) {
            this.name = name;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.read = read;
            this.write = write;
        }
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static boolean requested;
    private static boolean open;
    private static boolean dirty;
    private static boolean panelOnTop = true;
    private static List<OledOverlay> working = new ArrayList<>();
    private static long selected = -1L;
    private static WeakReference<Activity> window = new WeakReference<>(null);
    private static WeakReference<FrameLayout> frameRef = new WeakReference<>(null);
    private static WeakReference<View> panelRef = new WeakReference<>(null);

    private static final List<SliderRow> SLIDERS = new ArrayList<>();
    private static TextView elementLabel;
    private static Button gradientButton;
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

    /** From the settings row: leave the settings and open the editor on the feed behind them. */
    static void request(Context context) {
        requested = true;
        Activity settings = activityOf(context);
        if (settings != null) settings.finish();
        HANDLER.postDelayed(OledOverlays::refresh, 400L);
        HANDLER.postDelayed(OledOverlays::refresh, 1200L);
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
    }

    private static void open(Activity activity, FrameLayout frame) {
        requested = false;
        open = true;
        dirty = false;
        working = OledOverlay.copyOf(OledOverlays.saved());
        selected = working.isEmpty() ? -1L : working.get(working.size() - 1).id;
        attachPanel(activity, frame);
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
        int margin = dp(activity, 10);
        params.setMargins(margin, margin * 3, margin, margin * 3);
        params.gravity = (panelOnTop ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        frame.addView(panel, params);
        sync();
    }

    private static void close() {
        open = false;
        requested = false;
        dirty = false;
        selected = -1L;
        View panel = panelRef.get();
        if (panel != null && panel.getParent() instanceof ViewGroup) ((ViewGroup) panel.getParent()).removeView(panel);
        panelRef = new WeakReference<>(null);
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
        SLIDERS.clear();
        PAGE_BOXES.clear();
        int windowWidth = Math.max(frame.getWidth(), activity.getResources().getDisplayMetrics().widthPixels);
        int windowHeight = Math.max(frame.getHeight(), activity.getResources().getDisplayMetrics().heightPixels);
        final int maxHeight = Math.round(windowHeight * 0.62f);

        ScrollView scroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF2161A24);
        background.setCornerRadius(dp(activity, 18));
        scroll.setBackground(background);
        scroll.setClickable(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 12);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Create element, close.
        LinearLayout top = row(activity);
        Button create = button(activity, "");
        create.setOnClickListener(v -> createElement(activity));
        create.setTag("create");
        top.addView(create, weight(activity, 1f));
        Button closeButton = button(activity, "X");
        closeButton.setOnClickListener(v -> askBeforeClosing(activity));
        top.addView(closeButton, fixed(activity, 56));
        column.addView(top);

        // Save, delete.
        LinearLayout second = row(activity);
        Button save = button(activity, L10n.t(activity, "Save"));
        save.setOnClickListener(v -> save(activity));
        second.addView(save, weight(activity, 1f));
        Button delete = button(activity, L10n.t(activity, "Delete"));
        delete.setOnClickListener(v -> deleteSelected());
        second.addView(delete, weight(activity, 0.6f));
        column.addView(second);

        // Which element, and where the panel sits.
        LinearLayout pick = row(activity);
        Button previous = button(activity, "<");
        previous.setOnClickListener(v -> step(-1));
        pick.addView(previous, fixed(activity, 56));
        elementLabel = new TextView(activity);
        elementLabel.setTextColor(Color.WHITE);
        elementLabel.setGravity(Gravity.CENTER);
        elementLabel.setTextSize(15f);
        pick.addView(elementLabel, weight(activity, 1f));
        Button next = button(activity, ">");
        next.setOnClickListener(v -> step(1));
        pick.addView(next, fixed(activity, 56));
        Button move = button(activity, L10n.t(activity, "Move panel"));
        move.setOnClickListener(v -> flipPanel());
        pick.addView(move, weight(activity, 1f));
        column.addView(pick);

        // The numbers.
        SLIDERS.add(new SliderRow(L10n.t(activity, "Width"), "px", 20, Math.min(windowWidth, OledOverlay.MAX_SIZE_PX),
                o -> o.width, (o, v) -> o.width = v));
        SLIDERS.add(new SliderRow(L10n.t(activity, "Height"), "px", 10, Math.min(windowHeight, OledOverlay.MAX_SIZE_PX),
                o -> o.height, (o, v) -> o.height = v));
        SLIDERS.add(new SliderRow(L10n.t(activity, "Rounding"), "px", 0, 300,
                o -> o.rounding, (o, v) -> o.rounding = v));
        SLIDERS.add(new SliderRow(L10n.t(activity, "Opacity"), "%", 0, 100,
                o -> o.alpha, (o, v) -> o.alpha = v));
        SLIDERS.add(new SliderRow(L10n.t(activity, "Soft edge"), "dp", 0, 60,
                o -> o.softEdgeDp, (o, v) -> o.softEdgeDp = v));
        SLIDERS.add(new SliderRow(L10n.t(activity, "Gray at the end of the gradient"), "", 0, 64,
                o -> o.gray, (o, v) -> o.gray = v));
        for (SliderRow slider : SLIDERS) {
            slider.label = new TextView(activity);
            slider.label.setTextColor(Color.WHITE);
            slider.label.setTextSize(16f);
            slider.label.setPadding(0, dp(activity, 10), 0, 0);
            slider.label.setOnClickListener(v -> typeNumber(activity, slider));
            column.addView(slider.label);
            slider.bar = new SeekBar(activity);
            slider.bar.setMax(slider.max - slider.min);
            slider.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && !syncing) apply(slider, slider.min + progress);
                }

                @Override public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override public void onStopTrackingTouch(SeekBar bar) {
                }
            });
            column.addView(slider.bar);
        }

        gradientButton = button(activity, "");
        gradientButton.setOnClickListener(v -> cycleGradient(activity));
        LinearLayout.LayoutParams gradientParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gradientParams.topMargin = dp(activity, 12);
        column.addView(gradientButton, gradientParams);

        TextView showOn = new TextView(activity);
        showOn.setText(L10n.t(activity, "Show on these pages"));
        showOn.setTextColor(Color.WHITE);
        showOn.setTextSize(16f);
        showOn.setPadding(0, dp(activity, 12), 0, 0);
        column.addView(showOn);
        LinearLayout pages = row(activity);
        String[] names = {L10n.t(activity, "Home"), L10n.t(activity, "Profile"),
                L10n.t(activity, "Inbox"), L10n.t(activity, "Friends")};
        for (int index = 0; index < PAGE_FLAGS.length; index++) {
            final int flag = PAGE_FLAGS[index];
            CheckBox box = new CheckBox(activity);
            box.setText(names[index]);
            box.setTextColor(Color.WHITE);
            box.setTextSize(13f);
            box.setOnCheckedChangeListener((button, checked) -> {
                OledOverlay overlay = current();
                if (overlay == null || syncing) return;
                overlay.pages = checked ? (overlay.pages | flag) : (overlay.pages & ~flag);
                changed();
            });
            PAGE_BOXES.add(box);
            pages.addView(box, weight(activity, 1f));
        }
        column.addView(pages);
        return scroll;
    }

    // What the panel does.

    private static OledOverlay current() {
        for (OledOverlay overlay : working) {
            if (overlay.id == selected) return overlay;
        }
        return null;
    }

    private static void createElement(Activity activity) {
        FrameLayout frame = frameRef.get();
        int windowWidth = frame != null && frame.getWidth() > 0 ? frame.getWidth()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int windowHeight = frame != null && frame.getHeight() > 0 ? frame.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;
        OledOverlay overlay = new OledOverlay();
        long id = 1L;
        for (OledOverlay other : working) id = Math.max(id, other.id + 1L);
        overlay.id = id;
        overlay.width = Math.min(overlay.width, windowWidth);
        overlay.height = Math.min(overlay.height, windowHeight);
        overlay.x = (windowWidth - overlay.width) / 2;
        overlay.y = (windowHeight - overlay.height) / 2;
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

    private static void flipPanel() {
        panelOnTop = !panelOnTop;
        View panel = panelRef.get();
        if (panel != null && panel.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            params.gravity = (panelOnTop ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
            panel.setLayoutParams(params);
        }
    }

    private static void cycleGradient(Activity activity) {
        OledOverlay overlay = current();
        if (overlay == null) return;
        switch (overlay.gradient) {
            case OledOverlay.GRADIENT_OFF:
                overlay.gradient = OledOverlay.GRADIENT_LEFT_TO_RIGHT;
                break;
            case OledOverlay.GRADIENT_LEFT_TO_RIGHT:
                overlay.gradient = OledOverlay.GRADIENT_RIGHT_TO_LEFT;
                break;
            case OledOverlay.GRADIENT_RIGHT_TO_LEFT:
                overlay.gradient = OledOverlay.GRADIENT_CENTER_OUT;
                break;
            default:
                overlay.gradient = OledOverlay.GRADIENT_OFF;
        }
        changed();
    }

    private static void apply(SliderRow slider, int value) {
        OledOverlay overlay = current();
        if (overlay == null) return;
        slider.write.set(overlay, OledOverlay.within(value, slider.min, slider.max));
        overlay.clamp();
        changed();
    }

    private static void typeNumber(Activity activity, SliderRow slider) {
        OledOverlay overlay = current();
        if (overlay == null) return;
        EditText box = new EditText(activity);
        box.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.setText(String.valueOf(slider.read.get(overlay)));
        box.setSelection(box.getText().length());
        new AlertDialog.Builder(activity)
                .setTitle(slider.name + " (" + slider.min + " - " + slider.max + ")")
                .setView(box)
                .setPositiveButton(L10n.t(activity, "OK"), (dialog, which) -> {
                    try {
                        apply(slider, Integer.parseInt(box.getText().toString().trim()));
                    } catch (NumberFormatException ignored) {
                        // Nothing typed that is a number: the value stays.
                    }
                })
                .setNegativeButton(L10n.t(activity, "Cancel"), null)
                .show();
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
        if (activity == null) return;
        OledOverlay overlay = current();
        syncing = true;
        try {
            View panel = panelRef.get();
            if (panel != null) {
                View create = panel.findViewWithTag("create");
                if (create instanceof Button) {
                    ((Button) create).setText(L10n.t(activity, "Create element") + " [" + working.size() + "]");
                }
            }
            if (elementLabel != null) {
                int index = overlay == null ? 0 : working.indexOf(overlay) + 1;
                elementLabel.setText(working.isEmpty()
                        ? L10n.t(activity, "No elements yet")
                        : L10n.t(activity, "Element") + " " + index + " / " + working.size());
            }
            for (SliderRow slider : SLIDERS) {
                if (slider.label == null || slider.bar == null) continue;
                slider.bar.setEnabled(overlay != null);
                slider.label.setEnabled(overlay != null);
                if (overlay == null) {
                    slider.label.setText(slider.name);
                    slider.bar.setProgress(0);
                    continue;
                }
                int value = slider.read.get(overlay);
                String shown = slider.unit.isEmpty()
                        ? String.format(Locale.ROOT, "#%02X%02X%02X", value, value, value)
                        : value + " " + slider.unit;
                slider.label.setText(slider.name + ": " + shown);
                slider.bar.setProgress(OledOverlay.within(value, slider.min, slider.max) - slider.min);
            }
            if (gradientButton != null) {
                gradientButton.setEnabled(overlay != null);
                String name;
                String gradient = overlay == null ? OledOverlay.GRADIENT_OFF : overlay.gradient;
                if (OledOverlay.GRADIENT_LEFT_TO_RIGHT.equals(gradient)) name = L10n.t(activity, "Left to right");
                else if (OledOverlay.GRADIENT_RIGHT_TO_LEFT.equals(gradient)) name = L10n.t(activity, "Right to left");
                else if (OledOverlay.GRADIENT_CENTER_OUT.equals(gradient)) name = L10n.t(activity, "Center outwards");
                else name = L10n.t(activity, "Off");
                gradientButton.setText(L10n.t(activity, "Gradient") + ": " + name);
            }
            for (int index = 0; index < PAGE_BOXES.size(); index++) {
                CheckBox box = PAGE_BOXES.get(index);
                box.setEnabled(overlay != null);
                box.setChecked(overlay != null && (overlay.pages & PAGE_FLAGS[index]) != 0);
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
                for (int index = working.size() - 1; index >= 0; index--) {
                    OledOverlay overlay = working.get(index);
                    if (overlay.contains(event.getX(), event.getY())) {
                        selected = overlay.id;
                        downX = event.getX();
                        downY = event.getY();
                        originX = overlay.x;
                        originY = overlay.y;
                        dragging = true;
                        sync();
                        OledOverlays.quick();
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                OledOverlay overlay = current();
                if (!dragging || overlay == null) return true;
                overlay.x = OledOverlay.within(Math.round(originX + event.getX() - downX),
                        0, Math.max(0, windowWidth - overlay.width));
                overlay.y = OledOverlay.within(Math.round(originY + event.getY() - downY),
                        0, Math.max(0, windowHeight - overlay.height));
                dirty = true;
                OledOverlays.quick();
                return true;
            }
            default:
                dragging = false;
                return true;
        }
    }

    // Small view helpers.

    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private static Button button(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFF111111);
        button.setTextSize(15f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFCFCFE0);
        background.setCornerRadius(dp(context, 14));
        button.setBackground(background);
        button.setMinHeight(0);
        button.setMinimumHeight(dp(context, 44));
        return button;
    }

    private static LinearLayout.LayoutParams weight(Context context, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
        return params;
    }

    private static LinearLayout.LayoutParams fixed(Context context, int widthDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
        return params;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
