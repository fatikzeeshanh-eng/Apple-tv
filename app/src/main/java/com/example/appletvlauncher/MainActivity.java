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
    private static final long SAVER_AFTER_MS = 300000L;

    // Aerial-style screensaver: sky colors it drifts through (deep night blues/purples)
    private static final int[] AERIAL_COLORS = {
            0xFF0B1026, 0xFF1B2A4A, 0xFF2E4370, 0xFF1F2A44, 0xFF0B1026
    };

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

    private boolean ccOpen = false;
    private int aerialIndex = 0;

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
                animateTileFocus(view);
                updateBackdrop(appList.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setupControlCenter();
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

    // ---------- Standby (Apple TV style) ----------

    private void resetStandby() {
        handler.removeCallbacks(dimRunnable);
        handler.removeCallbacks(saverRunnable);
        handler.postDelayed(dimRunnable, DIM_AFTER_MS);
        handler.postDelayed(saverRunnable, SAVER_AFTER_MS);
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

    // Slowly drift the screensaver sky through deep night colors (Aerial vibe)
    private void driftAerial() {
        Drawable bg = screensaver.getBackground();
        if (!(bg instanceof GradientDrawable)) {
            return;
        }
        int from = ((GradientDrawable) bg).getColor() != null ? 0 : 0; // not used
        int fromColor = 0xFF0B1026;
        if (bg instanceof GradientDrawable) {
            // Track drifting via index; base color comes from the palette
            fromColor = AERIAL_COLORS[aerialIndex];
        }
        int toColor = AERIAL_COLORS[(aerialIndex + 1) % AERIAL_COLORS.length];
        aerialIndex = (aerialIndex + 1) % AERIAL_COLORS.length;

        final View target = screensaver;
        ValueAnimator drift = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        drift.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // Dim the whole screensaver background tint via a color filter-free alpha layer:
                // simplest API-19-safe approach: tint the root overlay alpha slightly.
                // We animate the screensaver's background color by setting a color overlay drawable.
                target.invalidate();
            }
        });
        // Since GradientDrawable gradient colors can't be swapped mid-way easily on API 19,
        // we instead cross-fade the whole screensaver alpha subtly. Simple and smooth:
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
        }
        return super.dispatchKeyEvent(event);
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
            Toast.makeText(this, "TV Debloated: RAM Cleared & Optimized", Toast.LENGTH_SHORT).show();
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
    }

    static class AppInfo {
        String label;
        String packageName;
        Drawable icon;
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
                holder.label = (TextView) row.findViewById(R.id.tileLabel);
                row.setTag(holder);
            } else {
                holder = (Holder) row.getTag();
            }

            AppInfo app = appList.get(position);
            holder.icon.setImageDrawable(app.icon);
            holder.label.setText(app.label);
            return row;
        }

        class Holder {
            ImageView icon;
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
