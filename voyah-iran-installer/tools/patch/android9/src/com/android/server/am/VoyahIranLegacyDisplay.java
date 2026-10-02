package com.android.server.am;

import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/** Android 9 ActivityManager side. Built against verified firmware signatures. */
public final class VoyahIranLegacyDisplay {
    private static final String KEY = "voyah_iran_display_policy";
    private static volatile boolean enabled;
    private static boolean scheduled;
    private static ContentObserver observer;

    private static void initialize(ActivityRecord a) {
        if (scheduled || a.service == null) return;
        scheduled = true;
        final ActivityManagerService service = a.service;
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    final ContentResolver resolver = service.mContext.getContentResolver();
                    observer = new ContentObserver(handler) {
                        public void onChange(boolean selfChange) { refresh(service, resolver); }
                    };
                    resolver.registerContentObserver(Settings.Global.getUriFor(KEY), false, observer);
                    refresh(service, resolver);
                } catch (RuntimeException e) {
                    enabled = false; scheduled = false;
                    Log.e("VI/DisplayPolicy9", "Initialization failed", e);
                }
            }
        });
    }

    private static boolean candidate(ActivityRecord a) {
        if (a == null || (a.getDisplayId() != 0 && a.getDisplayId() != 1) ||
                a.getActivityType() != 1 || a.inMultiWindowMode() || a.info == null || a.info.applicationInfo == null) return false;
        ApplicationInfo app = a.info.applicationInfo;
        return "com.abrar.voyahiran".equals(app.packageName) ||
                (app.uid >= 10000 && (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0);
    }

    public static boolean applies(Object object) {
        if (!(object instanceof ActivityRecord)) return false;
        ActivityRecord a = (ActivityRecord) object;
        initialize(a);
        return enabled && candidate(a);
    }

    public static int orientation(Object a, int requested) {
        return requested != -2 && applies(a) ? 0 : requested;
    }

    public static void bounds(ActivityRecord a, Rect out) {
        if (!applies(a) || a.getDisplay() == null) return;
        // Even ActivityDisplay.getBounds() uses Display.getSize(), which reports
        // 1920x608 on H97A. Read real logical pixels (1920x720) instead.
        if (a.getDisplay().mDisplay == null) return;
        android.graphics.Point size = new android.graphics.Point();
        a.getDisplay().mDisplay.getRealSize(size);
        Rect full = new Rect(0, 0, size.x, size.y);
        if (full.width() <= 465 || full.height() <= 250 || full.width() <= full.height()) return;
        out.set(full.left + 145, full.top + 50, full.right, full.bottom);
    }

    public static void configuration(ActivityRecord a, android.content.res.Configuration config) {
        Rect b = new Rect(); bounds(a, b);
        if (b.isEmpty()) return;
        int dpi = a.getDisplay().getConfiguration().densityDpi;
        if (dpi <= 0) return;
        config.windowConfiguration.setBounds(b);
        config.windowConfiguration.setAppBounds(b);
        config.screenWidthDp = b.width() * 160 / dpi;
        config.screenHeightDp = b.height() * 160 / dpi;
        config.orientation = 2;
    }

    private static void refresh(ActivityManagerService service, ContentResolver resolver) {
        try {
            String request = Settings.Global.getString(resolver, KEY);
            enabled = request == null || request.equals("1") || request.matches("1:[a-f0-9]{32}");
            synchronized (service) {
                ActivityStackSupervisor supervisor = service.mStackSupervisor;
                for (int d = supervisor.getChildCount() - 1; d >= 0; d--) {
                    ActivityDisplay display = supervisor.getChildAt(d);
                    for (int s = display.getChildCount() - 1; s >= 0; s--) {
                        ActivityStack stack = display.getChildAt(s);
                        for (int t = stack.getChildCount() - 1; t >= 0; t--) {
                            TaskRecord task = (TaskRecord) stack.getChildAt(t);
                            for (int i = task.getChildCount() - 1; i >= 0; i--) {
                                ActivityRecord a = task.getChildAt(i);
                                if (candidate(a)) {
                                    Rect previous = a.getBounds();
                                    if (!enabled && previous.left == 145 && previous.top == 50) a.setBounds(new Rect());
                                    a.ensureActivityConfiguration(0, true);
                                }
                            }
                        }
                    }
                }
                com.android.server.wm.VoyahIranLegacyFrames.traverse(service.mWindowManager);
            }
            Settings.Global.putString(resolver, "voyah_iran_display_policy_ack", request == null ? "1:default" : request);
            Log.i("VI/DisplayPolicy9", "Applied enabled=" + enabled + " displays=0,1 left=145 top=50");
        } catch (RuntimeException e) {
            Log.e("VI/DisplayPolicy9", "Reconfiguration failed; no acknowledgement", e);
        }
    }
}
