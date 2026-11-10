package com.example.appletvlauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final long DIM_AFTER_MS = 60000L;

    // Special entry: built-in tvOS-style Settings app shown as a gear tile
    private static final String SETTINGS_PACKAGE = "@@settings@@";

    // RAM-saver: icons are drawn once into small rounded bitmaps (see shrinkIcon)
    private static final int ICON_CACHE_PX = 160;
    // Card corner radius, tvOS 26 measured proportion
    private static final float ICON_CORNER_RATIO = 0.22f;
    // tvOS 26 measured focus scale from Apple's press screenshot
    private static final float FOCUS_SCALE = 1.2f;
    // Apps that sit in the glass dock (first row, like tvOS)
    private static final int DOCK_COUNT = 6;

    private GridView appGridView;
    private LinearLayout dockRow;
    private List<AppInfo> appList = new ArrayList<AppInfo>();
    private List<AppInfo> allApps = new ArrayList<AppInfo>();
    private PackageManager packageManager;
    private int dockCount = 0;

    private TextView screensaverClock;
    private TextView screensaverDate;
    private View dimOverlay;
    private View screensaver;
    private View ccPanel;
    private View ccSleep;
    private View ccWifi;
    private View ccBluetooth;
    private View ccSearch;
    private View ccHomeApps;
    private View ccInput;

    private boolean ccOpen = false;

    private final Handler handler = new Handler();
    private final Runnable dimRunnable = new Runnable() {
        @Override
        public void run() {
            showDim();
        }
    };
    private final Runnable saverRunnable = new Runnable() {
        @Override
        public void run() {
            showScreensaver();
        }
    };
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClocks();
            handler.postDelayed(clockTick, 30000L);
        }
    };
    private final Runnable aerialDrift = new Runnable() {
        @Override
        public void run() {
            driftAerial();
            handler.postDelayed(aerialDrift, 8000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appGridView = (GridView) findViewById(R.id.appGridView);
        dockRow = (LinearLayout) findViewById(R.id.dockRow);
        packageManager = getPackageManager();
        screensaverClock = (TextView) findViewById(R.id.screensaverClock);
        screensaverDate = (TextView) findViewById(R.id.screensaverDate);
        dimOverlay = findViewById(R.id.dimOverlay);
        screensaver = findViewById(R.id.screensaver);
        ccPanel = findViewById(R.id.ccPanel);
        ccSleep = findViewById(R.id.ccSleep);
        ccWifi = findViewById(R.id.ccWifi);
        ccBluetooth = findViewById(R.id.ccBluetooth);
        ccSearch = findViewById(R.id.ccSearch);
        ccHomeApps = findViewById(R.id.ccHomeApps);
        ccInput = findViewById(R.id.ccInput);

        performAutoDebloat();
        loadInstalledApps();
        applyHiddenFilter();
        populateDock();

        appGridView.setAdapter(new TileAdapter());
        appGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchApp(appList.get(position + dockCount));
            }
        });
        appGridView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                animateTileFocus(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setupControlCenter();
        updateClocks();

        // tvOS: focus starts on the first dock app
        dockRow.post(new Runnable() {
            @Override
            public void run() {
                if (dockRow.getChildCount() > 0) {
                    dockRow.getChildAt(0).requestFocus();
                } else if (appGridView.getChildCount() > 0) {
                    appGridView.getChildAt(0).requestFocus();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetStandby();
        handler.postDelayed(clockTick, 30000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(dimRunnable);
        handler.removeCallbacks(saverRunnable);
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(aerialDrift);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        wakeFromStandby();
        resetStandby();
    }

    // ---------- Dock: first row of apps on the glass panel, tvOS 26 style ----------

    private void populateDock() {
        dockRow.removeAllViews();
        dockCount = Math.min(DOCK_COUNT, appList.size());
        for (int i = 0; i < dockCount; i++) {
            final AppInfo app = appList.get(i);
            View tile = getLayoutInflater().inflate(R.layout.tile_item, dockRow, false);
            ImageView icon = (ImageView) tile.findViewById(R.id.tileIcon);
            TextView emoji = (TextView) tile.findViewById(R.id.tileEmoji);
            final TextView label = (TextView) tile.findViewById(R.id.tileLabel);

            if (app.emoji != null) {
                icon.setVisibility(View.GONE);
                emoji.setVisibility(View.VISIBLE);
                emoji.setText(app.emoji);
            } else {
                icon.setVisibility(View.VISIBLE);
                emoji.setVisibility(View.GONE);
                icon.setImageDrawable(app.icon);
            }
            // tvOS dock: the name appears under the icon only while it's focused
            label.setText(app.label);
            label.setAlpha(0f);

            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchApp(app);
                }
            });
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    animateDockFocus(v, hasFocus);
                    label.animate().alpha(hasFocus ? 1f : 0f).setDuration(150).start();
                }
            });
            dockRow.addView(tile);
        }
    }

    private void animateDockFocus(View tile, boolean hasFocus) {
        float scale = hasFocus ? FOCUS_SCALE : 1f;
        tile.animate().scaleX(scale).scaleY(scale)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ---------- App pickers (live in Control Center, home stays exact tvOS) ----------

    private void showAppPicker() {
        if (appList.isEmpty()) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[appList.size()];
        for (int i = 0; i < appList.size(); i++) {
            names[i] = appList.get(i).label;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.cc_search)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        launchApp(appList.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddAppsDialog() {
        if (allApps.isEmpty()) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[allApps.size()];
        final boolean[] checked = new boolean[allApps.size()];
        java.util.Set<String> hidden = LauncherPreferences.getHiddenPackages(this);
        for (int i = 0; i < allApps.size(); i++) {
            names[i] = allApps.get(i).label;
            checked[i] = !hidden.contains(allApps.get(i).packageName);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.add_apps_title)
                .setMultiChoiceItems(names, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which, boolean isChecked) {
                        LauncherPreferences.setHidden(MainActivity.this,
                                allApps.get(which).packageName, !isChecked);
                    }
                })
                .setPositiveButton(R.string.add_apps_done, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        rebuildGrid();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rebuildGrid() {
        applyHiddenFilter();
        populateDock();
        ((BaseAdapter) appGridView.getAdapter()).notifyDataSetChanged();
        appGridView.smoothScrollToPosition(0);
        dockRow.post(new Runnable() {
            @Override
            public void run() {
                if (dockRow.getChildCount() > 0) {
                    dockRow.getChildAt(0).requestFocus();
                }
            }
        });
        Toast.makeText(this, "Home Screen updated", Toast.LENGTH_SHORT).show();
    }

    private void showInputPicker() {
        final String[] inputs = {"HDMI 1", "HDMI 2", "AV 1", "AV 2", "TV"};

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.input_title)
                .setItems(inputs, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String chosen = inputs[which];
                        boolean opened = false;
                        try {
                            Intent displaySettings = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                            startActivity(displaySettings);
                            opened = true;
                        } catch (Exception e) {
                            opened = false;
                        }
                        if (opened) {
                            Toast.makeText(MainActivity.this, chosen + " " + getString(R.string.input_selected), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, getString(R.string.input_fallback_hint), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Control Center (tvOS style, opened with the MENU key) ----------

    private void setupControlCenter() {
        ccSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeControlCenter();
                showScreensaver();
            }
        });

        ccWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Wi-Fi settings not available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        ccBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Bluetooth settings not available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        ccSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker();
            }
        });

        ccHomeApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddAppsDialog();
            }
        });

        ccInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputPicker();
            }
        });
    }

    private void toggleControlCenter() {
        if (ccOpen) {
            closeControlCenter();
        } else {
            openControlCenter();
        }
    }

    private void openControlCenter() {
        ccOpen = true;
        resetStandby();
        ccPanel.setAlpha(0f);
        ccPanel.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(ccPanel, "alpha", 0f, 1f).setDuration(250).start();
        ccSleep.requestFocus();
    }

    private void closeControlCenter() {
        if (!ccOpen) {
            return;
        }
        ccOpen = false;
        ObjectAnimator.ofFloat(ccPanel, "alpha", 1f, 0f).setDuration(250).start();
        ccPanel.postDelayed(new Runnable() {
            @Override
            public void run() {
                ccPanel.setVisibility(View.GONE);
            }
        }, 250);
        if (dockRow.getChildCount() > 0) {
            dockRow.getChildAt(0).requestFocus();
        }
    }

    // ---------- Standby (Apple TV style, timing set in Settings app) ----------

    private void resetStandby() {
        handler.removeCallbacks(dimRunnable);
        handler.removeCallbacks(saverRunnable);
        handler.postDelayed(dimRunnable, DIM_AFTER_MS);
        long saverMs = LauncherPreferences.getSaverDelayMs(this);
        if (saverMs > 0) {
            handler.postDelayed(saverRunnable, Math.max(saverMs, DIM_AFTER_MS + 1000L));
        }
    }

    private void wakeFromStandby() {
        if (dimOverlay.getVisibility() == View.VISIBLE || screensaver.getVisibility() == View.VISIBLE) {
            animateWake();
        }
    }

    private void showDim() {
        dimOverlay.setAlpha(0f);
        dimOverlay.setVisibility(View.VISIBLE);
        ObjectAnimator fade = ObjectAnimator.ofFloat(dimOverlay, "alpha", 0f, 1f);
        fade.setDuration(800);
        fade.start();
    }

    private void showScreensaver() {
        updateClocks();
        screensaver.setAlpha(0f);
        screensaver.setVisibility(View.VISIBLE);
        ObjectAnimator fade = ObjectAnimator.ofFloat(screensaver, "alpha", 0f, 1f);
        fade.setDuration(1200);
        fade.start();
        handler.removeCallbacks(aerialDrift);
        handler.postDelayed(aerialDrift, 8000L);
    }

    private void animateWake() {
        handler.removeCallbacks(aerialDrift);
        ObjectAnimator saverFade = ObjectAnimator.ofFloat(screensaver, "alpha", 1f, 0f);
        saverFade.setDuration(400);
        saverFade.start();
        dimOverlay.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
            @Override
            public void run() {
                dimOverlay.setVisibility(View.GONE);
            }
        });
        screensaver.postDelayed(new Runnable() {
            @Override
            public void run() {
                screensaver.setVisibility(View.GONE);
            }
        }, 400);
    }

    private void driftAerial() {
        final View target = screensaver;
        target.animate().alpha(0.92f).setDuration(4000L).withEndAction(new Runnable() {
            @Override
            public void run() {
                target.animate().alpha(1f).setDuration(4000L).start();
            }
        }).start();
    }

    private void updateClocks() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        screensaverClock.setText(timeFmt.format(cal.getTime()));
        screensaverDate.setText(dateFmt.format(cal.getTime()));
    }

    // ---------- Remote keys ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                toggleControlCenter();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && ccOpen) {
                closeControlCenter();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ---------- Focus animations ----------

    private void animateTileFocus(View view) {
        if (view == null) {
            return;
        }
        view.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        for (int i = 0; i < appGridView.getChildCount(); i++) {
            View child = appGridView.getChildAt(i);
            if (child != null && child != view) {
                child.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
        }
    }

    // ---------- Core ----------

    private void launchApp(AppInfo app) {
        if (SETTINGS_PACKAGE.equals(app.packageName)) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        Intent intent = packageManager.getLaunchIntentForPackage(app.packageName);
        if (intent != null) {
            startActivity(intent);
        }
    }

    private void performAutoDebloat() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
                    if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        am.killBackgroundProcesses(processInfo.processName);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // RAM-saver: draw each icon ONCE into a small bitmap, rounded-corner
    // clipped like a tvOS app icon, over a dark tint of the icon's own
    // average color so the tile reads as full-bleed artwork.
    private Drawable shrinkIcon(Drawable original) {
        if (original == null) {
            return null;
        }
        try {
            int size = ICON_CACHE_PX;

            int bg;
            Bitmap probe = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_4444);
            Canvas pc = new Canvas(probe);
            original.setBounds(0, 0, 24, 24);
            original.draw(pc);
            int[] pixels = new int[24 * 24];
            probe.getPixels(pixels, 0, 24, 0, 0, 24, 24);
            probe.recycle();
            long r = 0;
            long g = 0;
            long b = 0;
            int counted = 0;
            for (int pixel : pixels) {
                if ((pixel & 0xFF000000) != 0) {
                    r += (pixel >> 16) & 0xFF;
                    g += (pixel >> 8) & 0xFF;
                    b += pixel & 0xFF;
                    counted++;
                }
            }
            if (counted > 0) {
                bg = Color.rgb((int) (r / counted / 2.5), (int) (g / counted / 2.5), (int) (b / counted / 2.5));
            } else {
                bg = Color.rgb(28, 28, 32);
            }

            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_4444);
            Canvas c = new Canvas(bmp);
            float radius = size * ICON_CORNER_RATIO;
            Path path = new Path();
            path.addRoundRect(new RectF(0, 0, size, size), radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(path);
            c.drawColor(bg);
            original.setBounds(0, 0, size, size);
            original.draw(c);
            c.restore();

            BitmapDrawable small = new BitmapDrawable(getResources(), bmp);
            small.setBounds(0, 0, size, size);
            return small;
        } catch (Exception e) {
            e.printStackTrace();
            return original;
        }
    }

    private void loadInstalledApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> unfilteredApps = packageManager.queryIntentActivities(intent, 0);
        for (ResolveInfo info : unfilteredApps) {
            if (!info.activityInfo.packageName.equals(getPackageName())) {
                AppInfo app = new AppInfo();
                app.label = info.loadLabel(packageManager).toString();
                app.packageName = info.activityInfo.packageName;
                app.icon = shrinkIcon(info.loadIcon(packageManager));
                allApps.add(app);
            }
        }

        Collections.sort(allApps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void applyHiddenFilter() {
        java.util.Set<String> hidden = LauncherPreferences.getHiddenPackages(this);
        appList.clear();
        for (AppInfo app : allApps) {
            if (!hidden.contains(app.packageName)) {
                appList.add(app);
            }
        }

        // Built-in Settings app: always first tile in the dock, tvOS style
        AppInfo settingsApp = new AppInfo();
        settingsApp.label = getString(R.string.settings_title);
        settingsApp.packageName = SETTINGS_PACKAGE;
        settingsApp.icon = null;
        settingsApp.emoji = "⚙️";
        appList.add(0, settingsApp);
    }

    static class AppInfo {
        String label;
        String packageName;
        Drawable icon;
        String emoji;
    }

    // Grid adapter: shows every app EXCEPT the dock row, above the dock
    private class TileAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return Math.max(0, appList.size() - dockCount);
        }

        @Override
        public Object getItem(int position) {
            return appList.get(position + dockCount);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            Holder holder;

            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.tile_item, parent, false);
                holder = new Holder();
                holder.icon = (ImageView) row.findViewById(R.id.tileIcon);
                holder.emoji = (TextView) row.findViewById(R.id.tileEmoji);
                holder.label = (TextView) row.findViewById(R.id.tileLabel);
                row.setTag(holder);
            } else {
                holder = (Holder) row.getTag();
            }

            AppInfo app = appList.get(position + dockCount);
            if (app.emoji != null) {
                holder.icon.setVisibility(View.GONE);
                holder.emoji.setVisibility(View.VISIBLE);
                holder.emoji.setText(app.emoji);
            } else {
                holder.icon.setVisibility(View.VISIBLE);
                holder.emoji.setVisibility(View.GONE);
                holder.icon.setImageDrawable(app.icon);
            }
            holder.label.setText(app.label);
            return row;
        }

        class Holder {
            ImageView icon;
            TextView emoji;
            TextView label;
        }
    }

    @Override
    public void onBackPressed() {
        if (ccOpen) {
            closeControlCenter();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
