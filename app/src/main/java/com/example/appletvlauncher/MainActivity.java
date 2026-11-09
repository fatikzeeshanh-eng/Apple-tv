package com.example.appletvlauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {

    private static final long CLOCK_TICK_MS = 30000L; // 30s
    private static final long DIM_AFTER_MS = 60000L;   // 1 min idle -> dim
    private static final long CLOCK_SAVE_AFTER_MS = 300000L; // 5 min idle -> clock screensaver

    private GridView appGridView;
    private List<AppInfo> appList = new ArrayList<AppInfo>();
    private PackageManager packageManager;

    private LinearLayout shelfRow;
    private TextView tabFeatured;
    private TextView tabApps;
    private TextView heroTitle;
    private TextView topClock;
    private ImageView heroBackdrop;

    private RenderScript renderScript;
    private Handler handler;
    private long lastInteractionTime;
    private boolean dimmed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appGridView = (GridView) findViewById(R.id.appGridView);
        packageManager = getPackageManager();
        shelfRow = (LinearLayout) findViewById(R.id.shelfRow);
        tabFeatured = (TextView) findViewById(R.id.tabFeatured);
        tabApps = (TextView) findViewById(R.id.tabApps);
        heroTitle = (TextView) findViewById(R.id.heroTitle);
        topClock = (TextView) findViewById(R.id.topClock);
        heroBackdrop = (ImageView) findViewById(R.id.heroBackdrop);

        renderScript = RenderScript.create(this);
        handler = new Handler();
        lastInteractionTime = System.currentTimeMillis();

        performAutoDebloat();
        loadInstalledApps();

        AppAdapter adapter = new AppAdapter();
        appGridView.setAdapter(adapter);
        appGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = appList.get(position);
                launchApp(app);
            }
        });

        buildShelf();
        setupTabs();
        startClock();
        startIdleWatcher();
    }

    // ---------- Hero backdrop (blurred featured icon, tvOS style) ----------

    private void showHeroBackdrop(AppInfo app) {
        if (app == null || app.icon == null) return;
        try {
            Bitmap src = drawableToBitmap(app.icon);
            Bitmap blurred = blurBitmap(src, 25f);
            heroBackdrop.setImageBitmap(blurred);
            heroTitle.setText(app.label);
        } catch (Throwable t) {
            heroBackdrop.setImageDrawable(app.icon);
            heroTitle.setText(app.label);
        }
    }

    private Bitmap drawableToBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) d).getBitmap();
            if (bm != null) return bm;
        }
        int w = Math.max(2, d.getIntrinsicWidth());
        int h = Math.max(2, d.getIntrinsicHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return out;
    }

    private Bitmap blurBitmap(Bitmap src, float radius) {
        Bitmap out = Bitmap.createScaledBitmap(src, 64, 64, true);
        Allocation in = Allocation.createFromBitmap(renderScript, out);
        Allocation res = Allocation.createTyped(renderScript, in.getType());
        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        script.setRadius(radius);
        script.setInput(in);
        script.forEach(res);
        res.copyTo(out);
        return out;
    }

    // ---------- Live clock (tvOS top-right clock) ----------

    private void startClock() {
        topClock.setText(DateFormat.getTimeFormat(this).format(new Date()));
        handler.postDelayed(clockTick, CLOCK_TICK_MS);
    }

    private Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            topClock.setText(DateFormat.getTimeFormat(this) == null ? null
                    : android.text.format.DateFormat.getTimeFormat(MainActivity.this).format(new Date()));
            handler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    // ---------- Idle dim + clock screensaver (Apple TV standby) ----------

    private void startIdleWatcher() {
        handler.postDelayed(idleCheck, 5000);
    }

    private Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            long idle = System.currentTimeMillis() - lastInteractionTime;
            if (!dimmed && idle >= DIM_AFTER_MS) {
                dimmed = true;
                View root = getWindow().getDecorView();
                root.animate() != null ? root.animate().alpha(0.15f) : null;
            }
            if (idle >= CLOCK_SAVE_AFTER_MS) {
                getWindow().getDecorView().setAlpha(0.15f);
                topClock.setTextSize(72);
                topClock.setTextColor(0xFFFFFFFF);
                // Full clock screensaver mode: giant time, dim UI
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastInteractionTime = System.currentTimeMillis();
        if (dimmed) {
            dimmed = false;
            View root = getWindow().getDecorView();
            root.animate().alpha(1f);
            topClock.setTextSize(16);
            topClock.setTextColor(0xAAFFFFFF);
        }
    }

    // ---------- Tabs, shelf, grid ----------

    private void launchApp(AppInfo app) {
        Intent intent = packageManager.getLaunchIntentForPackage(app.packageName);
        if (intent != null) {
            startActivity(intent);
        }
    }

    private void setupTabs() {
        tabFeatured.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTabSelection(true);
                if (shelfRow.getChildCount() > 0 && shelfRow.getChildAt(0) != null) {
                    shelfRow.getChildAt(0).requestFocus();
                }
            }
        });

        tabApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTabSelection(false);
                appGridView.setSelection(0);
                if (appGridView.getChildCount() > 0) {
                    appGridView.getChildAt(0).requestFocus();
                }
            }
        });

        setTabSelection(true);
        appGridView.setSelection(0);
    }

    private void setTabSelection(boolean featuredSelected) {
        tabFeatured.setSelected(featuredSelected);
        tabFeatured.setTextColor(featuredSelected ? 0xFFFFFFFF : 0x88FFFFFF);
        tabApps.setSelected(!featuredSelected);
        tabApps.setTextColor(featuredSelected ? 0x88FFFFFF : 0xFFFFFFFF);
    }

    // Featured shelf: first three apps as large glass cards
    private void buildShelf() {
        shelfRow.removeAllViews();
        int count = Math.min(3, appList.size());
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
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    zoomOnFocus(v, hasFocus);
                    if (hasFocus) {
                        showHeroBackdrop(app);
                    }
                }
            });

            shelfRow.addView(card);
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.shelf_empty);
            empty.setTextColor(0xFFAAAAAA);
            empty.setTextSize(16);
            empty.setPadding(48, 24, 48, 24);
            shelfRow.addView(empty);
        }

        // Initial backdrop from first featured app
        if (appList.size() > 0) {
            showHeroBackdrop(appList.get(0));
        }
    }

    // tvOS-style focus zoom
    private void zoomOnFocus(View v, boolean hasFocus) {
        float target = hasFocus ? 1.08f : 1.0f;
        ScaleAnimation zoom = new ScaleAnimation(1f, target, 1f, target,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        zoom.setDuration(150);
        zoom.setFillAfter(true);
        v.startAnimation(zoom);
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
        android.graphics.drawable.Drawable icon;
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
                row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        zoomOnFocus(v, hasFocus);
                    }
                });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (renderScript != null) {
            renderScript.destroy();
        }
    }
}
