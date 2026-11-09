package com.example.appletvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Remembers user choices across restarts.
 * API 19 safe — plain SharedPreferences, no dependencies.
 */
public final class LauncherPreferences {

    private static final String PREFS_NAME = "appletv_prefs";
    private static final String KEY_SAVER_OPTION = "saver_option_index";
    private static final String KEY_HIDDEN_PACKAGES = "hidden_packages";

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

    // ---------- Hidden apps (Add Apps home screen customization) ----------

    /** Packages the user unticked in the Add Apps dialog — not shown on the home grid. */
    public static Set<String> getHiddenPackages(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_HIDDEN_PACKAGES, "");
        Set<String> hidden = new HashSet<String>();
        if (raw != null && raw.length() > 0) {
            String[] parts = raw.split(",");
            for (String p : parts) {
                if (p.length() > 0) {
                    hidden.add(p);
                }
            }
        }
        return hidden;
    }

    private static void saveHiddenPackages(Context context, Set<String> hidden) {
        StringBuilder sb = new StringBuilder();
        for (String p : hidden) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_HIDDEN_PACKAGES, sb.toString()).commit();
    }

    public static void setHidden(Context context, String packageName, boolean hidden) {
        Set<String> set = getHiddenPackages(context);
        boolean changed = hidden ? set.add(packageName) : set.remove(packageName);
        if (changed) {
            saveHiddenPackages(context, set);
        }
    }

    public static boolean isHidden(Context context, String packageName) {
        return getHiddenPackages(context).contains(packageName);
    }
}
