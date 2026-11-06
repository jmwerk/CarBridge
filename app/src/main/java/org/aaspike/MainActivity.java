package org.aaspike;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Comparator;
import java.util.List;

/**
 * Spike for approach A: can a car-visible activity launch an unmodified third-party app onto
 * the Android Auto display? Every attempt and its outcome is logged on screen and to logcat.
 */
public class MainActivity extends Activity {
    private static final String TAG = "AASpike";

    private TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        log = new TextView(this);
        log.setTextIsSelectable(true);
        root.addView(log);

        Button rescan = new Button(this);
        rescan.setText("Rescan displays");
        rescan.setOnClickListener(v -> logDisplays());
        root.addView(rescan);

        for (ResolveInfo app : launchableApps()) {
            addAppRow(root, app);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        logDisplays();
    }

    private void logDisplays() {
        Display own = getDisplay();
        append("This activity is on display " + own.getDisplayId() + " \"" + own.getName() + "\" "
                + own.getMode().getPhysicalWidth() + "x" + own.getMode().getPhysicalHeight()
                + " flags=0x" + Integer.toHexString(own.getFlags()));
        DisplayManager dm = getSystemService(DisplayManager.class);
        for (Display d : dm.getDisplays()) {
            append("  visible display " + d.getDisplayId() + " \"" + d.getName() + "\" flags=0x"
                    + Integer.toHexString(d.getFlags()));
        }
    }

    private List<ResolveInfo> launchableApps() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(query, 0);
        apps.removeIf(r -> r.activityInfo.packageName.equals(getPackageName()));
        apps.sort(Comparator.comparing(r -> r.loadLabel(getPackageManager()).toString()));
        return apps;
    }

    private void addAppRow(LinearLayout root, ResolveInfo app) {
        String label = app.loadLabel(getPackageManager()).toString();
        ComponentName cn = new ComponentName(app.activityInfo.packageName, app.activityInfo.name);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = new TextView(this);
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button plain = new Button(this);
        plain.setText("A1 plain");
        plain.setOnClickListener(v -> launch(label, cn, false));
        row.addView(plain);

        Button targeted = new Button(this);
        targeted.setText("A2 displayId");
        targeted.setOnClickListener(v -> launch(label, cn, true));
        row.addView(targeted);

        root.addView(row);
    }

    /**
     * A1: plain startActivity from this activity (Android normally places the new task on the
     * caller's display). A2: explicitly target this activity's display via ActivityOptions.
     */
    private void launch(String label, ComponentName cn, boolean targetDisplay) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        int displayId = getDisplay().getDisplayId();
        String mode = targetDisplay ? "A2 displayId=" + displayId : "A1 plain";
        try {
            if (targetDisplay) {
                ActivityOptions opts = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
                startActivity(intent, opts.toBundle());
            } else {
                startActivity(intent);
            }
            append(mode + " -> " + label + ": startActivity returned OK (check where it appeared)");
        } catch (RuntimeException e) {
            append(mode + " -> " + label + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Log.w(TAG, "launch failed", e);
        }
    }

    private void append(String line) {
        Log.i(TAG, line);
        log.append(line + "\n");
    }
}
