package com.example.appletvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Remembers user choices across restarts.
 * API 19 safe — plain SharedPreferences, no StringSet (unreliable on KitKat).
 */
public final class LauncherPreferences {

    private static final String PREFS_NAME = "appletv_prefs";
    private static final String KEY_SAVER_OPTION = "saver_option_index";
    private static final String KEY_HIDDEN_PACKAGES = "hidden_packages";
    private static final String KEY_HOME_REVISION = "home_revision";

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

    // ---------- Hidden apps (Home Screen Apps customization) ----------

    /** Package names the user has unchecked — hidden from the Home grid. */
    public static Set<String> getHiddenPackages(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String joined = prefs.getString(KEY_HIDDEN_PACKAGES, "");
        Set<String> hidden = new HashSet<String>();
        if (joined != null && joined.length() > 0) {
            String[] parts = joined.split(",");
            for (String p : parts) {
                if (p != null && p.length() > 0) {
                    hidden.add(p);
                }
            }
        }
        return hidden;
    }

    /** Saves the whole hidden-apps set at once (used by CustomizeHomeActivity). */
    public static void setHiddenPackages(Context context, Set<String> hidden) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (String p : hidden) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        prefs.edit().putString(KEY_HIDDEN_PACKAGES, sb.toString()).commit();
        bumpHomeRevision(context);
    }

    /** Toggles a single app (used by the Add Apps dialog on Home). */
    public static void setHidden(Context context, String packageName, boolean hidden) {
        Set<String> set = getHiddenPackages(context);
        boolean changed = hidden ? set.add(packageName) : set.remove(packageName);
        if (changed) {
            setHiddenPackages(context, set);
        }
    }

    public static boolean isHidden(Context context, String packageName) {
        return getHiddenPackages(context).contains(packageName);
    }

    /** Bumped whenever the hidden app list changes, so Home knows to refresh. */
    public static int getHomeRevision(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_HOME_REVISION, 0);
    }

    public static void bumpHomeRevision(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int rev = prefs.getInt(KEY_HOME_REVISION, 0) + 1;
        prefs.edit().putInt(KEY_HOME_REVISION, rev).commit();
    }
}
