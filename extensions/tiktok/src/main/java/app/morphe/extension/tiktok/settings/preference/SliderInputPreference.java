/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import app.morphe.extension.shared.settings.IntegerSetting;

/**
 * A number row that can also be set by dragging a slider.
 *
 * <p>It is a {@link NumberInputPreference} with a slider under the number box in its dialog, so
 * everything else about the row is that class's: the range comes from the setting, the box takes a
 * typed number, an out of range number is pulled to the nearest end with a word about it, and the
 * settings screen, backup and reset read and write it the way they do every number row. The slider
 * and the box follow each other, and Save keeps what the box says.
 */
@SuppressWarnings("deprecation")
public class SliderInputPreference extends NumberInputPreference {
    private final int low;
    private final int high;
    /** The listener the last dialog put on the box. The box outlives the dialog, so it is taken off first. */
    private TextWatcher followsBox;

    public SliderInputPreference(Context context, String title, String summary,
                                 IntegerSetting setting, String unit) {
        super(context, title, summary, setting, unit);
        this.low = setting.minimum();
        this.high = setting.maximum();
    }

    @Override
    protected View onCreateDialogView() {
        View view = super.onCreateDialogView();
        if (!(view instanceof LinearLayout)) return view;
        LinearLayout dialogView = (LinearLayout) view;
        Context context = getContext();
        EditText box = getEditText();

        SeekBar slider = new SeekBar(context);
        slider.setMax(high - low);
        slider.setProgress(clamp(parseOr(getValue(), low)) - low);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, SettingsUi.dp(context, 14), 0, 0);
        dialogView.addView(slider, params);

        // One flag rather than two comparisons: either side writes the other, and the write it
        // causes must not come back.
        final boolean[] writing = {false};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || writing[0]) return;
                writing[0] = true;
                try {
                    box.setText(String.valueOf(low + progress));
                    box.setSelection(box.getText().length());
                    SettingsUi.clearFieldError(box);
                } finally {
                    writing[0] = false;
                }
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        if (followsBox != null) box.removeTextChangedListener(followsBox);
        followsBox = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override public void afterTextChanged(Editable text) {
                if (writing[0]) return;
                Integer typed = parseTyped(text.toString());
                if (typed == null) return;
                writing[0] = true;
                try {
                    slider.setProgress(clamp(typed) - low);
                } finally {
                    writing[0] = false;
                }
            }
        };
        box.addTextChangedListener(followsBox);
        return dialogView;
    }

    private static int parseOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception unreadable) {
            return fallback;
        }
    }
}
