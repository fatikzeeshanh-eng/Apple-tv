package com.example.appletvlauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class SettingsActivity extends Activity {

    // Screensaver timer options, cycling like tvOS General > Screensaver
    private static final long[] SAVER_OPTIONS_MS = {120000L, 300000L, 600000L, 0L};
    private static final String[] SAVER_OPTIONS_LABEL = {"2 Minutes", "5 Minutes", "10 Minutes", "Never"};
    private static int saverOptionIndex = 1; // default 5 minutes

    private TextView rowSaverValue;
    private TextView rowWifiValue;
    private TextView rowBluetoothValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        rowSaverValue = (TextView) findViewById(R.id.rowSaverValue);
        rowWifiValue = (TextView) findViewById(R.id.rowWifiValue);
        rowBluetoothValue = (TextView) findViewById(R.id.rowBluetoothValue);

        saverOptionIndex = LauncherPreferences.getSaverOption(this);
        updateSaverLabel();

        // Screensaver After: each press cycles 2 -> 5 -> 10 -> Never -> 2
        findViewById(R.id.rowSaver).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saverOptionIndex = (saverOptionIndex + 1) % SAVER_OPTIONS_MS.length;
                LauncherPreferences.setSaverOption(SettingsActivity.this, saverOptionIndex);
                updateSaverLabel();
                Toast.makeText(SettingsActivity.this,
                        "Screensaver after " + SAVER_OPTIONS_LABEL[saverOptionIndex], Toast.LENGTH_SHORT).show();
            }
        });

        // Wi-Fi row -> open system Wi-Fi settings
        findViewById(R.id.rowWifi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "Wi-Fi settings not available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Bluetooth row -> open system Bluetooth settings
        findViewById(R.id.rowBluetooth).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "Bluetooth settings not available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Optimize TV row -> same debloat as launcher does on boot
        findViewById(R.id.rowOptimize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performDebloat();
            }
        });

        // About row -> show about info
        findViewById(R.id.rowAbout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(R.string.settings_about)
                        .setMessage(R.string.about_body)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        updateConnectionStates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStates();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void updateSaverLabel() {
        rowSaverValue.setText(SAVER_OPTIONS_LABEL[saverOptionIndex]);
    }

    private void updateConnectionStates() {
        try {
            WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
            rowWifiValue.setText(wifi != null && wifi.isWifiEnabled()
                    ? R.string.cc_wifi_on : R.string.cc_wifi_off);
        } catch (Exception e) {
            rowWifiValue.setText(R.string.cc_wifi_off);
        }

        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            rowBluetoothValue.setText(bt != null && bt.isEnabled()
                    ? R.string.cc_bt_on : R.string.cc_bt_off);
        } catch (Exception e) {
            rowBluetoothValue.setText(R.string.cc_bt_off);
        }
    }

    private void performDebloat() {
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
            Toast.makeText(this, "TV Optimized: RAM Cleared", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Optimize not available", Toast.LENGTH_SHORT).show();
        }
    }
}
