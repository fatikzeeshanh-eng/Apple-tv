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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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

    // Aerial-style screensaver: sky colors it drifts through (deep night blues/purples)
    private static final int[] AERIAL_COLORS = {
            0xFF0B1026, 0xFF1B2A4A, 0xFF2E4370, 0xFF1F2A44, 0xFF0B1026
    };

    // Special entry: built-in tvOS-style Settings app shown as a gear tile
    private static final String SETTINGS_PACKAGE = "@@settings@@";

    private GridView appGridView;
    private List<AppInfo> appList = new ArrayList<AppInfo>();
    private PackageManager packageManager;

    private TextView headerClock;
    private TextView screensaverClock;
    private TextView screensaverDate;
    private View dimOverlay;
    private View screensaver;
    private View contentLayer;
    private View ccPanel;
    private View ccSleep;
    private View ccWifi;
    private View ccBluetooth;
    private View ccSearch;
    private View inputButton;
    private View ccButton;
    private View appsButton;

    private boolean ccOpen = false;
    private int aerialIndex = 0;
    private int selectedPosition = 0;

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
    // Drift the screensaver sky color every 8 seconds, like Aerial scenes fading
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
        packageManager = getPackageManager();
        headerClock = (TextView) findViewById(R.id.headerClock);
        screensaverClock = (TextView) findViewById(R.id.screensaverClock);
        screensaverDate = (TextView) findViewById(R.id.screensaverDate);
        dimOverlay = findViewById(R.id.dimOverlay);
        screensaver = findViewById(R.id.screensaver);
        contentLayer = findViewById(R.id.contentLayer);
        ccPanel = findViewById(R.id.ccPanel);
        ccSleep = findViewById(R.id.ccSleep);
        ccWifi = findViewById(R.id.ccWifi);
        ccBluetooth = findViewById(R.id.ccBluetooth);
        ccSearch = findViewById(R.id.ccSearch);
        inputButton = findViewById(R.id.inputButton);
        ccButton = findViewById(R.id.ccButton);
        appsButton = findViewById(R.id.appsButton);

        performAutoDebloat();
        loadInstalledApps();

        appGridView.setAdapter(new TileAdapter());
        appGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchApp(appList.get(position));
            }
        });
        appGridView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPosition = position;
                animateTileFocus(view);
                updateBackdrop(appList.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setupControlCenter();
        setupHeaderButtons();
        updateClocks();

        appGridView.post(new Runnable() {
            @Override
            public void run() {
                if (appGridView.getChildCount() > 0) {
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

    // ---------- Header buttons: Apps + Input + Control Center ----------

    private void setupHeaderButtons() {
        // Apps button opens a quick picker of every installed app
        appsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker();
            }
        });

        // Control Center button opens the same panel as the MENU key
        ccButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleControlCenter();
            }
        });

        // Input button opens the tvOS-style input picker
        inputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputPicker();
            }
        });
    }

    // Quick app picker: same list as the home grid, tap = launch (tvOS style)
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
                .setTitle(R.string.btn_apps)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        launchApp(appList.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showInputPicker() {
        // KitKat boxes have no TV input framework, so we show the standard
        // tvOS-style input list. Cheap boxes switch inputs via their own
        // hardware/remote, so we try the display settings and show a hint.
        final String[] inputs = {"HDMI 1", "HDMI 2", "AV 1", "AV 2", "TV"};

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.input_title)
                .setItems(inputs, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String chosen = inputs[which];
                        boolean opened = false;
                        // Some boxes expose a source/input settings screen
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

    // ---------- Control Center (tvOS 17 style) ----------

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
                try {
                    Intent search = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
                    search.addCategory(Intent.CATEGORY_DEFAULT);
                    startActivity(search);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Search not available on this box", Toast.LENGTH_SHORT).show();
                }
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
        if (appGridView.getChildCount() > 0) {
            appGridView.getChildAt(0).requestFocus();
        }
    }

    // ---------- Standby (Apple TV style, timing set in Settings app) ----------

    private void resetStandby() {
        handler.removeCallbacks(dimRunnable);
        handler.removeCallbacks(saverRunnable);
        handler.postDelayed(dimRunnable, DIM_AFTER_MS);
        long saverMs = LauncherPreferences.getSaverDelayMs(this);
        if (saverMs > 0) {
            // Screensaver must come after the dim stage
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
        // Start the Aerial-style sky drift
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

    // Slow breathing fade on the screensaver — Aerial vibe, API-19 safe
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
        headerClock.setText(timeFmt.format(cal.getTime()));
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
            // tvOS-style: pressing DOWN on the last grid row opens the all-apps list
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    && appGridView.hasFocus()
                    && !appList.isEmpty()
                    && isInLastRow(selectedPosition)) {
                showAppPicker();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isInLastRow(int position) {
        int columns = appGridView.getNumColumns();
        if (columns < 1) {
            columns = 1;
        }
        return appList.size() - position <= columns;
    }

    // ---------- Ambient backdrop ----------

    private void updateBackdrop(AppInfo app) {
        if (app == null || app.icon == null || contentLayer == null) {
            return;
        }
        try {
            int size = 24;
            Bitmap small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            app.icon.setBounds(0, 0, size, size);
            app.icon.draw(c);

            int[] pixels = new int[size * size];
            small.getPixels(pixels, 0, size, 0, 0, size, size);
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
            if (counted == 0) {
                return;
            }

            int wash = Color.rgb((int) (r / counted / 3), (int) (g / counted / 3), (int) (b / counted / 3));

            LayerDrawable backdrop = (LayerDrawable) contentLayer.getBackground();
            final GradientDrawable washLayer = (GradientDrawable) backdrop.findDrawableByLayerId(R.id.backdropWash);
            final View target = contentLayer;

            int from;
            Drawable current = washLayer.getCurrent();
            if (current instanceof ColorDrawable) {
                from = ((ColorDrawable) current).getColor();
            } else {
                from = Color.rgb(0, 0, 0);
            }

            ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), from, wash);
            colorAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    washLayer.setColor((Integer) animation.getAnimatedValue());
                    target.invalidate();
                }
            });
            colorAnim.setDuration(900);
            colorAnim.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- Focus animations ----------

    private void animateTileFocus(View view) {
        if (view == null) {
            return;
        }
        view.animate().scaleX(1.15f).scaleY(1.15f)
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

    private void loadInstalledApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> unfilteredApps = packageManager.queryIntentActivities(intent, 0);
        for (ResolveInfo info : unfilteredApps) {
            if (!info.activityInfo.packageName.equals(getPackageName())) {
                AppInfo app = new AppInfo();
                app.label = info.loadLabel(packageManager).toString();
                app.packageName = info.activityInfo.packageName;
                app.icon = info.loadIcon(packageManager);
                appList.add(app);
            }
        }

        Collections.sort(appList, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // Built-in Settings app: always first tile in the grid, tvOS style
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

    private class TileAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return appList.size();
        }

        @Override
        public Object getItem(int position) {
            return appList.get(position);
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

            AppInfo app = appList.get(position);
            if (app.emoji != null) {
                // Emoji tile (Settings gear)
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
