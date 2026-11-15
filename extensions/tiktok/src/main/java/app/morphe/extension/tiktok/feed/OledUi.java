/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The look of the OLED editor and its dialogs, in one place: dark rounded cards, small buttons, and
 * a slider whose value is also a box to type a number in.
 */
final class OledUi {
    static final int PANEL = 0xF2111319;
    static final int CARD = 0xFF1C1F29;
    static final int BUTTON = 0xFF2C303D;
    static final int ACCENT = 0xFF25D0CB;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFA9AEBB;

    interface OnValue {
        void changed(int value);
    }

    private OledUi() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static TextView text(Context context, String text, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    static Button button(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(13f);
        button.setBackground(rounded(context, BUTTON, 10));
        button.setMinHeight(0);
        button.setMinimumHeight(dp(context, 36));
        button.setMinWidth(0);
        button.setMinimumWidth(dp(context, 40));
        button.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        return button;
    }

    static LinearLayout.LayoutParams weight(Context context, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        return params;
    }

    static LinearLayout.LayoutParams wrap(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        return params;
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    /** A dialog on the dark card, without the system's own frame around it. */
    static AlertDialog dialog(Context context, View content) {
        AlertDialog dialog = new AlertDialog.Builder(context).setView(content).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dialog;
    }

    /**
     * A name with its value in a box to type in, over a slider, in two rows. Moving the slider fills
     * the box and typing a number in it moves the slider. The slider reaches {@code sliderMax}; a
     * number typed may go as far as {@code hardMax}.
     */
    static final class NumberSlider {
        final LinearLayout root;
        private final TextView label;
        private final EditText box;
        private final SeekBar bar;
        private final TextView unitView;
        private final int min;
        private final int sliderMax;
        private final int hardMax;
        private final OnValue listener;
        private int value;
        private boolean updating;
        private String name;

        NumberSlider(Context context, String name, String unit, int min, int sliderMax, int hardMax, OnValue listener) {
            this.min = min;
            this.sliderMax = Math.max(min + 1, sliderMax);
            this.hardMax = Math.max(this.sliderMax, hardMax);
            this.listener = listener;
            this.name = name;
            root = column(context);
            root.setPadding(0, dp(context, 4), 0, dp(context, 2));

            LinearLayout top = row(context);
            label = text(context, name, 13f, TEXT);
            top.addView(label, weight(context, 1f));
            box = new EditText(context);
            box.setInputType(InputType.TYPE_CLASS_NUMBER);
            box.setSingleLine(true);
            box.setImeOptions(EditorInfo.IME_ACTION_DONE);
            box.setTextColor(TEXT);
            box.setTextSize(13f);
            box.setGravity(Gravity.CENTER);
            box.setBackground(rounded(context, CARD, 8));
            box.setPadding(dp(context, 6), dp(context, 4), dp(context, 6), dp(context, 4));
            top.addView(box, new LinearLayout.LayoutParams(dp(context, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            unitView = text(context, unit.isEmpty() ? "" : " " + unit, 12f, MUTED);
            top.addView(unitView, wrap(context));
            root.addView(top);

            bar = new SeekBar(context);
            bar.setMax(this.sliderMax - min);
            root.addView(bar);

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && !updating) commit(min + progress);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            box.setOnEditorActionListener((view, action, event) -> {
                commitTyped();
                return false;
            });
            box.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) commitTyped();
            });
        }

        private void commitTyped() {
            if (updating) return;
            try {
                commit(Integer.parseInt(box.getText().toString().trim()));
            } catch (NumberFormatException ex) {
                box.setText(String.valueOf(value));
            }
        }

        private void commit(int wanted) {
            value = Math.max(min, Math.min(hardMax, wanted));
            show(value);
            listener.changed(value);
        }

        private void show(int shown) {
            updating = true;
            try {
                box.setText(String.valueOf(shown));
                bar.setProgress(Math.max(0, Math.min(sliderMax, shown) - min));
            } finally {
                updating = false;
            }
        }

        /** Puts a value in without telling the listener. */
        void set(int newValue) {
            value = newValue;
            show(newValue);
        }

        void setName(String text) {
            name = text;
            label.setText(text);
        }

        String name() {
            return name;
        }

        void setEnabled(boolean enabled) {
            box.setEnabled(enabled);
            bar.setEnabled(enabled);
            label.setEnabled(enabled);
            root.setAlpha(enabled ? 1f : 0.45f);
        }

        void setVisible(boolean visible) {
            root.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
