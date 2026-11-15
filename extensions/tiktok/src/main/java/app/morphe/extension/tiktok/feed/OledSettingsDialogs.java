/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.List;
import java.util.Map;

/**
 * The dialogs of the OLED screen settings: a menu, the presets, the list of elements, the picture
 * to tap an element in, and the soft edges of the video. All built in code, in the editor's look.
 */
public final class OledSettingsDialogs {
    /** An element of the picture and where its middle is, as a share of the picture's width and height. */
    private static final Object[][] HOTSPOTS = {
            {"live", 0.20, 0.086}, {"tab_2", 0.40, 0.086}, {"tab_1", 0.607, 0.086}, {"search", 0.90, 0.086},
            {"avatar", 0.87, 0.478}, {"like_icon", 0.873, 0.553}, {"like_count", 0.873, 0.587},
            {"comment_icon", 0.873, 0.632}, {"comment_count", 0.872, 0.667},
            {"bookmark_icon", 0.872, 0.711}, {"bookmark_count", 0.872, 0.744},
            {"share_icon", 0.872, 0.790}, {"share_count", 0.873, 0.823}, {"music_disc", 0.872, 0.878},
            {"author", 0.22, 0.759}, {"date", 0.45, 0.759}, {"caption", 0.21, 0.795},
            {"music_line", 0.24, 0.888}, {"progress", 0.49, 0.915},
            {"nav_home_icon", 0.105, 0.950}, {"nav_home_text", 0.105, 0.974},
            {"nav_friends_icon", 0.297, 0.950}, {"nav_friends_text", 0.297, 0.974},
            {"nav_create", 0.49, 0.958},
            {"nav_inbox_icon", 0.682, 0.950}, {"nav_inbox_text", 0.682, 0.974},
            {"nav_profile_icon", 0.876, 0.950}, {"nav_profile_text", 0.876, 0.974},
    };

    private OledSettingsDialogs() {
    }

    private static void changed() {
        OledOverlays.refresh();
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = OledUi.column(context);
        int pad = OledUi.dp(context, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(OledUi.rounded(context, OledUi.PANEL | 0xFF000000, 18));
        return card;
    }

    private static View scrolled(Context context, View inner) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private static TextView title(Context context, String text) {
        TextView view = OledUi.text(context, text, 17f, OledUi.TEXT);
        view.setPadding(0, 0, 0, OledUi.dp(context, 8));
        return view;
    }

    private static Button wide(Context context, String text, View.OnClickListener listener) {
        Button button = OledUi.button(context, text);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, OledUi.dp(context, 3), 0, OledUi.dp(context, 3));
        button.setLayoutParams(params);
        return button;
    }

    /** The menu the settings row opens. */
    public static void showMenu(Context context) {
        LinearLayout card = card(context);
        card.addView(title(context, L10n.t(context, "OLED screen elements")));
        AlertDialog[] self = new AlertDialog[1];
        card.addView(wide(context, L10n.t(context, "Presets"), v -> { self[0].dismiss(); showPresets(context, true); }));
        card.addView(wide(context, L10n.t(context, "Dim single elements"), v -> { self[0].dismiss(); showElements(context); }));
        card.addView(wide(context, L10n.t(context, "Pick an element in the picture"), v -> { self[0].dismiss(); showPicture(context); }));
        card.addView(wide(context, L10n.t(context, "Soft edges of the video"), v -> { self[0].dismiss(); showEdges(context); }));
        self[0] = OledUi.dialog(context, card);
        self[0].show();
    }

    // Presets.

    private static void showPresets(Context context, boolean soft) {
        LinearLayout card = card(context);
        card.addView(title(context, L10n.t(context, "Presets")));
        card.addView(OledUi.text(context, L10n.t(context,
                "A preset is a panel that follows the real element. It opens over the feed first, where you can change it before saving."),
                12f, OledUi.MUTED));
        AlertDialog[] self = new AlertDialog[1];
        LinearLayout versions = OledUi.row(context);
        Button softButton = OledUi.button(context, L10n.t(context, "Soft edge"));
        Button sharpButton = OledUi.button(context, L10n.t(context, "Sharp edge"));
        softButton.setBackground(OledUi.rounded(context, soft ? OledUi.ACCENT : OledUi.BUTTON, 10));
        sharpButton.setBackground(OledUi.rounded(context, soft ? OledUi.BUTTON : OledUi.ACCENT, 10));
        if (soft) softButton.setTextColor(0xFF001414); else sharpButton.setTextColor(0xFF001414);
        softButton.setOnClickListener(v -> { self[0].dismiss(); showPresets(context, true); });
        sharpButton.setOnClickListener(v -> { self[0].dismiss(); showPresets(context, false); });
        versions.addView(softButton, OledUi.weight(context, 1f));
        versions.addView(sharpButton, OledUi.weight(context, 1f));
        card.addView(versions);

        card.addView(OledUi.text(context, L10n.t(context, "Packages"), 13f, OledUi.ACCENT));
        for (Map.Entry<String, List<String>> pack : OledPresets.PACKAGES.entrySet()) {
            final List<String> keys = pack.getValue();
            card.addView(wide(context, L10n.t(context, pack.getKey()), v -> {
                self[0].dismiss();
                OledOverlayEditor.requestPreview(context, OledPresets.forElements(keys, soft));
            }));
        }
        for (String group : OledElements.groups()) {
            card.addView(OledUi.text(context, L10n.t(context, group), 13f, OledUi.ACCENT));
            for (OledElements.Element element : OledElements.all()) {
                if (!element.group.equals(group)) continue;
                final String key = element.key;
                card.addView(wide(context, L10n.t(context, element.label), v -> {
                    self[0].dismiss();
                    java.util.List<OledOverlay> one = new java.util.ArrayList<>();
                    one.add(OledPresets.forElement(key, soft));
                    OledOverlayEditor.requestPreview(context, one);
                }));
            }
        }
        self[0] = OledUi.dialog(context, scrolled(context, card));
        self[0].show();
    }

    // Single elements.

    private static void showElements(Context context) {
        LinearLayout card = card(context);
        card.addView(title(context, L10n.t(context, "Dim single elements")));
        AlertDialog[] self = new AlertDialog[1];
        for (String group : OledElements.groups()) {
            card.addView(OledUi.text(context, L10n.t(context, group), 13f, OledUi.ACCENT));
            for (OledElements.Element element : OledElements.all()) {
                if (!element.group.equals(group)) continue;
                final String key = element.key;
                String text = L10n.t(context, element.label);
                if (OledElementSettings.isOn(key)) text += "  •  " + OledElementSettings.transparency(key) + " %";
                if (OledElements.statusOf(key) == 0) text += "  (" + L10n.t(context, "not found") + ")";
                card.addView(wide(context, text, v -> { self[0].dismiss(); showElement(context, key, true); }));
            }
        }
        self[0] = OledUi.dialog(context, scrolled(context, card));
        self[0].show();
    }

    private static void showElement(Context context, String key, boolean backToList) {
        OledElements.Element element = OledElements.byKey(key);
        if (element == null) return;
        LinearLayout card = card(context);
        card.addView(title(context, L10n.t(context, element.label)));
        if (OledElements.statusOf(key) == 0) {
            card.addView(OledUi.text(context, L10n.t(context,
                    "Not found on the last look at the screen. It may not be on screen, or this version names it differently."),
                    12f, OledUi.MUTED));
        }
        final boolean[] on = {OledElementSettings.isOn(key)};
        final int[] transparency = {OledElementSettings.transparency(key)};
        Switch toggle = new Switch(context);
        toggle.setText(L10n.t(context, "Dim this element"));
        toggle.setTextColor(OledUi.TEXT);
        toggle.setChecked(on[0]);
        OledUi.NumberSlider slider = new OledUi.NumberSlider(context, L10n.t(context, "Transparency (100 = invisible)"),
                "%", 0, 100, 100, value -> {
                    transparency[0] = value;
                    OledElementSettings.set(key, on[0], value);
                    changed();
                });
        slider.set(transparency[0]);
        slider.setEnabled(on[0]);
        toggle.setOnCheckedChangeListener((CompoundButton button, boolean checked) -> {
            on[0] = checked;
            slider.setEnabled(checked);
            OledElementSettings.set(key, checked, transparency[0]);
            changed();
        });
        card.addView(toggle);
        card.addView(slider.root);
        AlertDialog[] self = new AlertDialog[1];
        card.addView(wide(context, L10n.t(context, "Done"), v -> {
            self[0].dismiss();
            if (backToList) showElements(context);
        }));
        self[0] = OledUi.dialog(context, card);
        self[0].show();
    }

    // The picture.

    private static void showPicture(Context context) {
        LinearLayout card = card(context);
        card.addView(title(context, L10n.t(context, "Tap an element")));
        Bitmap bitmap = OledPickerImage.bitmap();
        AlertDialog[] self = new AlertDialog[1];
        if (bitmap != null) {
            ImageView image = new ImageView(context);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
                float fx = event.getX() / Math.max(1, view.getWidth());
                float fy = event.getY() / Math.max(1, view.getHeight());
                String best = null;
                double bestDistance = 0.06;
                for (Object[] spot : HOTSPOTS) {
                    double dx = fx - (Double) spot[1];
                    double dy = (fy - (Double) spot[2]) * 0.5;
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = (String) spot[0];
                    }
                }
                if (best != null) {
                    self[0].dismiss();
                    showElement(context, best, false);
                }
                return true;
            });
            int height = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.72f);
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height));
        }
        card.addView(wide(context, L10n.t(context, "Close"), v -> self[0].dismiss()));
        self[0] = OledUi.dialog(context, card);
        self[0].show();
    }

    // The video's soft edges.

    private static void showEdges(Context context) {
        LinearLayout card = card(context);
        card.addView(title(context, L10n.t(context, "Soft edges of the video")));
        card.addView(OledUi.text(context, L10n.t(context,
                "The video fades to black at its top and bottom edge. This is drawn on the video itself, not as a panel, and only in the feed."),
                12f, OledUi.MUTED));
        OledUi.NumberSlider top = new OledUi.NumberSlider(context, L10n.t(context, "Top edge"), "px", 0, 400, 600,
                value -> { Settings.OLED_VIDEO_EDGE_TOP.save(value); changed(); });
        top.set(Settings.OLED_VIDEO_EDGE_TOP.get());
        OledUi.NumberSlider bottom = new OledUi.NumberSlider(context, L10n.t(context, "Bottom edge"), "px", 0, 400, 600,
                value -> { Settings.OLED_VIDEO_EDGE_BOTTOM.save(value); changed(); });
        bottom.set(Settings.OLED_VIDEO_EDGE_BOTTOM.get());
        card.addView(top.root);
        card.addView(bottom.root);
        AlertDialog[] self = new AlertDialog[1];
        card.addView(wide(context, L10n.t(context, "Done"), v -> self[0].dismiss()));
        self[0] = OledUi.dialog(context, card);
        self[0].show();
    }
}
