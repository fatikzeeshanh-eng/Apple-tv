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

    // Standby timings (ms) — tvOS style
    private static final long DIM_AFTER_MS = 60_000;      // 1 min idle -> dim
    private static final long SAVER_AFTER_MS = 300_000;  // 5 min idle -> clock screensaver

    private GridView appGridView;
    private List<AppInfo> appList = new ArrayList<AppInfo>();
    private PackageManager packageManager;

    private LinearLayout shelfRow;
    private TextView headerClock;
    private TextView screensaverClock;
    private TextView screensaverDate;
    private View dimOverlay;
    private View screensaver;
    private View contentLayer;

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
            handler.postDelayed(clockTick, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appGridView = (GridView) findViewById(R.id.appGridView);
        packageManager = getPackageManager();
        shelfRow = (LinearLayout) findViewById(R.id.shelfRow);
        headerClock = (TextView) findViewById(R.id.headerClock);
        screensaverClock = (TextView) findViewById(R.id.screensaverClock);
        screensaverDate = (TextView) findViewById(R.id.screensaverDate);
        dimOverlay = findViewById(R.id.dimOverlay);
        screensaver = findViewById(R.id.screensaver);
        contentLayer = ((ViewGroup) appGridView.getRootView()).getChildAt(0);

        performAutoDebloat();
        loadInstalledApps();

        appGridView.setAdapter(new AppAdapter());
        appGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchApp(appList.get(position));
            }
        });
        appGridView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                animateGridFocus(view);
                updateBackdrop(appList.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        buildShelf();
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
        handler.postDelayed(clockTick, 30_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(dimRunnable);
        handler.removeCallbacks(saverRunnable);
        handler.removeCallbacks(clockTick);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        wakeFromStandby();
        resetStandby();
    }

    // ---------- Standby ----------

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
    }

    private void animateWake() {
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

    private void updateClocks() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        headerClock.setText(timeFmt.format(cal.getTime()));
        screensaverClock.setText(timeFmt.format(cal.getTime()));
        screensaverDate.setText(dateFmt.format(cal.getTime()));
    }

    // ---------- Ambient backdrop ----------

    // Tinted color wash from the focused app's icon — tvOS ambient look
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
            long r = 0, g = 0, b = 0;
            int counted = 0;
            for (int pixel : pixels) {
                if ((pixel & 0xFF000000) != 0) {
                    r += (pixel >> 16) & 0xFF;
                    g += (pixel >> 8) & 0xFF;
                    b += pixel & 0xFF;
                    counted++;
                }
            }
            if (counted == 0) return;

            // Darken and desaturate toward black for the moody tvOS feel
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

    // ---------- Focus zoom animations ----------

    private void animateGridFocus(View view) {
        if (view == null) return;
        view.animate().scaleX(1.12f).scaleY(1.12f)
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

    // ---------- Shelf ----------

    private void buildShelf() {
        shelfRow.removeAllViews();
        int count = Math.min(5, appList.size());
        for (int i = 0; i < count; i++) {
            final AppInfo app = appList.get(i);
            View card = getLayoutInflater().inflate(R.layout.shelf_item, shelfRow, false);

            ImageView icon = (ImageView) card.findViewById(R.id.shelfIcon);
            TextView label = (TextView) card.findViewById(R.id.shelfLabel);
            icon.setImageDrawable(app.icon);
            label.setText(app.label);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = 32;
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchApp(app);
                }
            });

            shelfRow.addView(card);
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_apps);
            empty.setTextColor(0xFFAAAAAA);
            empty.setTextSize(16);
            empty.setPadding(56, 24, 56, 24);
            shelfRow.addView(empty);
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

    private class AppAdapter extends BaseAdapter {
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
                row = getLayoutInflater().inflate(R.layout.app_item, parent, false);
                holder = new Holder();
                holder.icon = (ImageView) row.findViewById(R.id.appIcon);
                holder.label = (TextView) row.findViewById(R.id.appLabel);
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
        // Prevent exiting home screen via remote back button
    }
}
