/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.morphe.extension.tiktok.feed.OledOverlays;
import app.morphe.extension.tiktok.settings.L10n;

/** The row that opens the editor on its Elements tab: single elements, presets and the video edges. */
@SuppressWarnings("deprecation")
public final class OledScreenPreference extends Preference
        implements app.morphe.extension.shared.settings.preference.ImmediateAction {
    @Override public boolean actsOnTap() { return true; }

    public OledScreenPreference(Context context) {
        super(context);
        setKey("action_oled_screen_elements");
        setTitle("OLED screen elements");
        setSummary("Opens over the feed: dim single elements by tapping them, add ready-made panels, and set the soft "
                + "edges of the video.");
        setOnPreferenceClickListener(preference -> {
            OledOverlays.openEditorAt(context, 3);
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
