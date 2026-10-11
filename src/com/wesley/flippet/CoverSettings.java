package com.wesley.flippet;

import android.content.Context;
import android.content.SharedPreferences;

/** App-specific outer-display choice; separate from the creature's save. */
final class CoverSettings {
    static final String KEY_SHOW_PET = "show_pet";
    private static final String FILE = "outer_display";

    private CoverSettings() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean showPet(Context context) {
        return preferences(context).getBoolean(KEY_SHOW_PET, false);
    }

    static void setShowPet(Context context, boolean show) {
        preferences(context).edit().putBoolean(KEY_SHOW_PET, show).apply();
    }
}
