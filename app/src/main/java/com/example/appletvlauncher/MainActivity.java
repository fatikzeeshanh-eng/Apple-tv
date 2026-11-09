package com.example.appletvlauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

public class MainActivity extends Activity {

    private GridView appGridView;
    private List<AppInfo> appList = new ArrayList<AppInfo>();
    private PackageManager packageManager;

    private LinearLayout shelfRow;
    private TextView tabFeatured;
    private TextView tabApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appGridView = (GridView) findViewById(R.id.appGridView);
        packageManager = getPackageManager();
        shelfRow = (LinearLayout) findViewById(R.id.shelfRow);
        tabFeatured = (TextView) findViewById(R.id.tabFeatured);
        tabApps = (TextView) findViewById(R.id.tabApps);

        // Automatically debloat / clear background RAM clutter upon opening the launcher
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
    }

    private void launchApp(AppInfo app) {
        Intent intent = packageManager.getLaunchIntentForPackage(app.packageName);
        if (intent != null) {
            startActivity(intent);
        }
    }

    // Tabs move focus into the shelf / grid — tvOS feel on one screen
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
                if (appGridView.getChildCount() > 0 && appGridView.getChildAt(0) != null) {
                    appGridView.getChildAt(0).requestFocus();
                }
                appGridView.setSelection(0);
            }
        });

        // Start in Featured, like Apple TV
        setTabSelection(true);
        if (appGridView.getChildCount() > 0) {
            appGridView.setSelection(0);
        }
    }

    private void setTabSelection(boolean featuredSelected) {
        tabFeatured.setSelected(featuredSelected);
        tabFeatured.setTextColor(featuredSelected ? 0xFFFFFFFF : 0x88FFFFFF);
        tabApps.setSelected(!featuredSelected);
        tabApps.setTextColor(featuredSelected ? 0x88FFFFFF : 0xFFFFFFFF);
    }

    // Featured shelf: first three apps as large glass cards with reflections
    private void buildShelf() {
        shelfRow.removeAllViews();
        int count = Math.min(3, appList.size());
        for (int i = 0; i < count; i++) {
            final AppInfo app = appList.get(i);
            View card = getLayoutInflater().inflate(R.layout.shelf_item, shelfRow, false);

            ImageView icon = (ImageView) card.findViewById(R.id.shelfIcon);
            ImageView reflection = (ImageView) card.findViewById(R.id.shelfIconReflection);
            TextView label = (TextView) card.findViewById(R.id.shelfLabel);

            icon.setImageDrawable(app.icon);
            reflection.setImageDrawable(app.icon);
            label.setText(app.label);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = 28;
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

        // Empty state hint when no apps are installed
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.shelf_empty);
            empty.setTextColor(0xFFAAAAAA);
            empty.setTextSize(16);
            empty.setPadding(48, 24, 48, 24);
            shelfRow.addView(empty);
        }
    }

    private void performAutoDebloat() {
        try {
            // Kill background processes to free up RAM on resource-constrained Android 4.4 TV boxes
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
            // Filter out self to keep launcher list clean
            if (!info.activityInfo.packageName.equals(getPackageName())) {
                AppInfo app = new AppInfo();
                app.label = info.loadLabel(packageManager).toString();
                app.packageName = info.activityInfo.packageName;
                app.icon = info.loadIcon(packageManager);
                appList.add(app);
            }
        }

        // Alphabetical order like Apple TV
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
