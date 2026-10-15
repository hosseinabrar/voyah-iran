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
    private static final String THEME = "voyah_iran_app_theme";
    private static volatile String themeMode = "vehicle";
    private static volatile int nightMode;
    private static final String DENSITY = "voyah_iran_app_density";
    private static volatile int appDensity = 160;
    private static final String PACKAGE_DENSITY = "voyah_iran_app_density_packages";
    private static volatile java.util.Map<String, Integer> packageDensities = java.util.Collections.emptyMap();
    static java.util.Map<String, Integer> parseDensities(String raw) {
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        if (raw != null) for (String entry : raw.split(";")) {
            String[] pair = entry.split("=");
            if (pair.length == 2 && pair[0].length() <= 255 && pair[0].matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") &&
                    (pair[1].equals("120") || pair[1].equals("140") || pair[1].equals("160") || pair[1].equals("180") || pair[1].equals("200")))
                result.put(pair[0], Integer.valueOf(pair[1]));
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    static int densityForPackage(String pkg) {
        Integer custom = packageDensities.get(pkg);
        return custom == null ? appDensity : custom;
    }
    private static int densityForActivity(ActivityRecord a) {
        return densityForPackage(a.info.applicationInfo.packageName);
    }
    static int densityFor(String value) {
        if ("120".equals(value)) return 120;
        if ("140".equals(value)) return 140;
        if ("180".equals(value)) return 180;
        if ("200".equals(value)) return 200;
        return 160;
    }
    static int nightFor(String mode, String vehicleTheme) {
        return "light".equals(mode) || ("vehicle".equals(mode) && "simple".equals(vehicleTheme)) ? 0x10 : 0x20;
    }
    private static int desiredNight() {
        return nightFor(themeMode, android.os.SystemProperties.get("persist.sys.qg.currenttheme", "default"));
    }
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
                    if (observer != null) {
                        if (nightMode != desiredNight()) refresh(service, resolver);
                        return;
                    }
                    observer = new ContentObserver(handler) {
                        public void onChange(boolean selfChange) { refresh(service, resolver); }
                    };
                    resolver.registerContentObserver(Settings.Global.getUriFor(KEY), false, observer);
                    resolver.registerContentObserver(Settings.Global.getUriFor(THEME), false, observer);
                    resolver.registerContentObserver(Settings.Global.getUriFor(DENSITY), false, observer);
                    resolver.registerContentObserver(Settings.Global.getUriFor(PACKAGE_DENSITY), false, observer);
                    refresh(service, resolver);
                } catch (RuntimeException e) {
                    enabled = false; scheduled = false;
                    Log.e("VI/DisplayPolicy9", "Initialization failed", e);
                } finally {
                    if (scheduled) handler.postDelayed(this, 2000);
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

    private static boolean densityCandidate(ActivityRecord a) {
        if (!candidate(a)) return false;
        ApplicationInfo app = a.info.applicationInfo;
        return !"com.abrar.voyahiran".equals(app.packageName) && app.uid >= 10000 &&
                (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
    }

    public static int orientation(Object a, int requested) {
        return requested != -2 && applies(a) ? 0 : requested;
    }

    public static void bounds(ActivityRecord a, Rect out) {
        boolean constrained = applies(a);
        updateTheme(a);
        if (!constrained || a.getDisplay() == null) return;
        // Even ActivityDisplay.getBounds() uses Display.getSize(), which reports
        // 1920x608 on H97A. Read real logical pixels (1920x720) instead.
        if (a.getDisplay().mDisplay == null) return;
        android.graphics.Point size = new android.graphics.Point();
        a.getDisplay().mDisplay.getRealSize(size);
        Rect full = new Rect(0, 0, size.x, size.y);
        if (full.width() <= 465 || full.height() <= 250 || full.width() <= full.height()) return;
        out.set(full.left + leftInset(a), full.top + 50, full.right, full.bottom);
    }

    public static int leftInset(Object object) {
        if (!(object instanceof ActivityRecord)) return 145;
        ActivityRecord a = (ActivityRecord) object;
        return a.getDisplayId() == 1 && a.info != null && "com.abrar.voyahiran".equals(a.info.packageName) &&
            "com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity".equals(a.info.name) ? 0 : 145;
    }

    public static void configuration(ActivityRecord a, android.content.res.Configuration config) {
        initialize(a);
        if (candidate(a) && nightMode != 0) config.uiMode = (config.uiMode & ~0x30) | nightMode;
        if (densityCandidate(a)) applyDensity(a, config);
        Rect b = new Rect(); bounds(a, b);
        if (b.isEmpty()) return;
        int dpi = densityCandidate(a) ? densityForActivity(a) : a.getDisplay().getConfiguration().densityDpi;
        if (dpi <= 0) return;
        config.windowConfiguration.setBounds(b);
        config.windowConfiguration.setAppBounds(b);
        config.screenWidthDp = b.width() * 160 / dpi;
        config.screenHeightDp = b.height() * 160 / dpi;
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        config.orientation = 2;
    }

    private static void applyDensity(ActivityRecord a, android.content.res.Configuration config) {
        int density = densityForActivity(a);
        config.densityDpi = density;
        if (a.getDisplay() == null) return;
        Rect area = config.windowConfiguration == null ? null : config.windowConfiguration.getAppBounds();
        if (area == null || area.isEmpty()) area = a.getDisplay().getConfiguration().windowConfiguration.getBounds();
        if (area == null || area.isEmpty()) return;
        config.screenWidthDp = area.width() * 160 / density;
        config.screenHeightDp = area.height() * 160 / density;
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
    }

    private static void updateTheme(ActivityRecord a) {
        if (!candidate(a) || nightMode == 0) return;
        android.content.res.Configuration previous = a.getOverrideConfiguration();
        if ((previous.uiMode & 0x30) == nightMode && (!densityCandidate(a) || previous.densityDpi == densityForActivity(a))) return;
        android.content.res.Configuration next = new android.content.res.Configuration(previous);
        next.uiMode = (next.uiMode & ~0x30) | nightMode;
        if (densityCandidate(a)) applyDensity(a, next);
        a.onOverrideConfigurationChanged(next);
    }

    private static void refresh(ActivityManagerService service, ContentResolver resolver) {
        try {
            String request = Settings.Global.getString(resolver, KEY);
            enabled = request == null || request.equals("1") || request.matches("1:[a-f0-9]{32}");
            String mode = Settings.Global.getString(resolver, THEME);
            themeMode = "dark".equals(mode) || "light".equals(mode) ? mode : "vehicle";
            nightMode = desiredNight();
            appDensity = densityFor(Settings.Global.getString(resolver, DENSITY));
            String packageRequest = Settings.Global.getString(resolver, PACKAGE_DENSITY);
            packageDensities = parseDensities(packageRequest);
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
                                    updateTheme(a);
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
            Settings.Global.putString(resolver, THEME + "_ack", themeMode);
            Settings.Global.putString(resolver, DENSITY + "_ack", Integer.toString(appDensity));
            Settings.Global.putString(resolver, PACKAGE_DENSITY + "_ack", packageRequest == null ? "-" : packageRequest);
            Log.i("VI/DisplayPolicy9", "Applied enabled=" + enabled + " displays=0,1 left=145 top=50");
        } catch (RuntimeException e) {
            Log.e("VI/DisplayPolicy9", "Reconfiguration failed; no acknowledgement", e);
        }
    }
}
