package com.example.appletvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the user's screensaver timing choice across restarts.
 * API 19 safe — plain SharedPreferences, no dependencies.
 */
public final class LauncherPreferences {

    private static final String PREFS_NAME = "appletv_prefs";
    private static final String KEY_SAVER_OPTION = "saver_option_index";

    private LauncherPreferences() {
        // no instances
    }

    public static int getSaverOption(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int index = prefs.getInt(KEY_SAVER_OPTION, 1);
        if (index < 0 || index > 3) {
            index = 1;
        }
        return index;
    }

    public static void setSaverOption(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SAVER_OPTION, index).commit();
    }

    /** Returns the screensaver delay in ms, or 0 for "Never". */
    public static long getSaverDelayMs(Context context) {
        long[] options = {120000L, 300000L, 600000L, 0L};
        return options[getSaverOption(context)];
    }
}
