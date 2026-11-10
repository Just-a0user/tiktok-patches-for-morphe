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

/**
 * The row that opens the OLED overlay editor. A tap closes the settings and opens the editor over
 * the feed, where the panels are placed against the real screen.
 */
@SuppressWarnings("deprecation")
public final class OledOverlayEditorPreference extends Preference
        implements app.morphe.extension.shared.settings.preference.ImmediateAction {
    @Override public boolean actsOnTap() { return true; }

    public OledOverlayEditorPreference(Context context) {
        super(context);
        setKey("action_edit_oled_overlays");
        setTitle("Edit OLED overlays");
        setSummary("Dark panels over TikTok keep OLED pixels off. This opens an editor over the feed: add, move and "
                + "resize panels, set rounding, opacity, a soft edge and a black to gray gradient, and choose the "
                + "pages each panel shows on.");
        setOnPreferenceClickListener(preference -> {
            OledOverlays.openEditor(context);
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
