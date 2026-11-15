/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.morphe.extension.tiktok.feed.OledSettingsDialogs;
import app.morphe.extension.tiktok.settings.L10n;

/** The row that opens the menu for single elements, presets, the picture and the video edges. */
@SuppressWarnings("deprecation")
public final class OledScreenPreference extends Preference
        implements app.morphe.extension.shared.settings.preference.ImmediateAction {
    @Override public boolean actsOnTap() { return true; }

    public OledScreenPreference(Context context) {
        super(context);
        setKey("action_oled_screen_elements");
        setTitle("OLED screen elements");
        setSummary("Dim single elements of the feed screen, ready-made panels, a picture to tap an element in, "
                + "and soft edges for the video.");
        setOnPreferenceClickListener(preference -> {
            OledSettingsDialogs.showMenu(context);
            return true;
        });
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        SettingsUi.styleTitleAndSummary(view);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }
}
