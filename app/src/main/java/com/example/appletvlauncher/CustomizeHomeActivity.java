package com.example.appletvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * tvOS-style "Home Screen Apps" selector:
 * every installed app is listed with a checkbox —
 * uncheck to hide it from the Home tile grid.
 */
public class CustomizeHomeActivity extends Activity {

    private ListView appList;
    private PackageManager packageManager;
    private List<AppEntry> entries = new ArrayList<AppEntry>();
    private Set<String> hiddenPackages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize);

        appList = (ListView) findViewById(R.id.customizeList);
        packageManager = getPackageManager();
        hiddenPackages = LauncherPreferences.getHiddenPackages(this);

        loadApps();
        appList.setAdapter(new EntryAdapter());
        appList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                toggle(position, view);
            }
        });
    }

    private void loadApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = packageManager.queryIntentActivities(intent, 0);
        for (ResolveInfo info : apps) {
            if (info.activityInfo.packageName.equals(getPackageName())) {
                continue; // never list the launcher itself
            }
            AppEntry entry = new AppEntry();
            entry.label = info.loadLabel(packageManager).toString();
            entry.packageName = info.activityInfo.packageName;
            entry.visible = !hiddenPackages.contains(entry.packageName);
            entries.add(entry);
        }

        Collections.sort(entries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void toggle(int position, View row) {
        AppEntry entry = entries.get(position);
        entry.visible = !entry.visible;

        hiddenPackages = new HashSet<String>();
        for (AppEntry e : entries) {
            if (!e.visible) {
                hiddenPackages.add(e.packageName);
            }
        }
        LauncherPreferences.setHiddenPackages(this, hiddenPackages);
        LauncherPreferences.bumpHomeRevision(this);

        CheckBox box = (CheckBox) row.findViewById(R.id.itemCheck);
        if (box != null) {
            box.setChecked(entry.visible);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    static class AppEntry {
        String label;
        String packageName;
        boolean visible;
    }

    private class EntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
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
                row = getLayoutInflater().inflate(R.layout.customize_item, parent, false);
                holder = new Holder();
                holder.check = (CheckBox) row.findViewById(R.id.itemCheck);
                holder.label = (TextView) row.findViewById(R.id.itemLabel);
                row.setTag(holder);
            } else {
                holder = (Holder) row.getTag();
            }

            AppEntry entry = entries.get(position);
            holder.check.setChecked(entry.visible);
            holder.label.setText(entry.label);
            return row;
        }

        class Holder {
            CheckBox check;
            TextView label;
        }
    }
}
