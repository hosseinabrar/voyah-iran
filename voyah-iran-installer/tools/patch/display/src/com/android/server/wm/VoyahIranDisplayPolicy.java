package com.android.server.wm;

import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/** Compiled into the vehicle's services DEX. No Frida or APK rewriting. */
public final class VoyahIranDisplayPolicy {
    private static final String KEY = "voyah_iran_display_policy";
    private static final String ACK = "voyah_iran_display_policy_ack";
    private static final String THEME = "voyah_iran_app_theme";
    private static volatile String themeMode = "vehicle";
    private static volatile int nightMode;
    private static volatile int mapNightMode;
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

    // OEM ThemeModels: Classic=2 / Modern=1 -> default; Speedy Dash=4 -> simple.
    static int nightFor(String mode, String vehicleTheme) {
        return "light".equals(mode) || ("vehicle".equals(mode) && "simple".equals(vehicleTheme)) ? 0x10 : 0x20;
    }
    private static int desiredNight() {
        return nightFor(themeMode, android.os.SystemProperties.get("persist.sys.qg.currenttheme", "default"));
    }
    private static final String TAG = "VI/DisplayPolicy";
    private static volatile boolean enabled;
    private static boolean scheduled;
    private static ContentObserver observer;
    private static final int LEFT = 145, TOP = 50;

    private static void initialize(final ActivityRecord activity) {
        if (scheduled || activity == null || activity.mAtmService == null) return;
        scheduled = true;
        final ActivityTaskManagerService service = activity.mAtmService;
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    final ContentResolver resolver = service.mContext.getContentResolver();
                    if (observer != null) {
                        if (nightMode != desiredNight() || mapNightMode != vehicleNight()) refresh(service, resolver);
                        return;
                    }
                    observer = new ContentObserver(handler) {
                        @Override public void onChange(boolean selfChange) { refresh(service, resolver); }
                    };
                    resolver.registerContentObserver(Settings.Global.getUriFor(KEY), false, observer);
                    resolver.registerContentObserver(Settings.Global.getUriFor(THEME), false, observer);
                    resolver.registerContentObserver(Settings.Global.getUriFor(DENSITY), false, observer);
                    resolver.registerContentObserver(Settings.Global.getUriFor(PACKAGE_DENSITY), false, observer);
                    refresh(service, resolver);
                } catch (RuntimeException e) {
                    enabled = false;
                    scheduled = false;
                    Log.e(TAG, "Initialization failed; original layout retained", e);
                } finally {
                    if (scheduled) handler.postDelayed(this, 2000);
                }
            }
        });
    }

    private static void refresh(final ActivityTaskManagerService service, ContentResolver resolver) {
        try {
            String request = Settings.Global.getString(resolver, KEY);
            enabled = request == null || request.equals("1") || request.matches("1:[a-f0-9]{32}");
            String mode = Settings.Global.getString(resolver, THEME);
            themeMode = "dark".equals(mode) || "light".equals(mode) ? mode : "vehicle";
            nightMode = desiredNight();
            mapNightMode = vehicleNight();
            appDensity = densityFor(Settings.Global.getString(resolver, DENSITY));
            String packageRequest = Settings.Global.getString(resolver, PACKAGE_DENSITY);
            packageDensities = parseDensities(packageRequest);
            synchronized (service.mGlobalLock) {
                service.mRootWindowContainer.forAllActivities(new java.util.function.Consumer<ActivityRecord>() {
                    @Override public void accept(ActivityRecord a) {
                        // Include previously constrained activities on disable, too.
                        if ((candidate(a) || mapCandidate(a)) && a.getParent() != null) {
                            a.onConfigurationChanged(a.getParent().getConfiguration());
                            a.ensureActivityConfiguration(0, true);
                        }
                    }
                });
                service.mWindowManager.requestTraversal();
            }
            Settings.Global.putString(resolver, ACK, request == null ? "1:default" : request);
            Settings.Global.putString(resolver, THEME + "_ack", themeMode);
            Settings.Global.putString(resolver, DENSITY + "_ack", Integer.toString(appDensity));
            Settings.Global.putString(resolver, PACKAGE_DENSITY + "_ack", packageRequest == null ? "-" : packageRequest);
            Log.i(TAG, "Applied enabled=" + enabled + " displays=0,1 left=145 top=50");
        } catch (RuntimeException e) {
            Log.e(TAG, "Reconfiguration failed; request not acknowledged", e);
        }
    }

    private static boolean candidate(ActivityRecord a) {
        if (a == null || (a.getDisplayId() != 0 && a.getDisplayId() != 1) || a.getActivityType() != 1 ||
                a.inMultiWindowMode() || a.info == null || a.info.applicationInfo == null) return false;
        ApplicationInfo app = a.info.applicationInfo;
        return "com.abrar.voyahiran".equals(app.packageName) ||
                ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
                && app.uid >= 10000);
    }

    private static int vehicleNight() {
        return nightFor("vehicle", android.os.SystemProperties.get("persist.sys.qg.currenttheme", "default"));
    }

    // Only the launcher's owned map display. Never apply physical-screen geometry or
    // third-party theme preferences here, and never affect the cluster/other displays.
    private static boolean mapCandidate(ActivityRecord a) {
        if (a == null || a.getDisplayId() <= 1 || a.getActivityType() != 1 ||
                a.inMultiWindowMode() || a.info == null || a.info.applicationInfo == null) return false;
        ApplicationInfo app = a.info.applicationInfo;
        if (app.uid < 10000 || (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return false;
        DisplayContent display = a.getDisplayContent();
        android.view.DisplayInfo info = display == null ? null : display.getDisplayInfo();
        return info != null && "VoyahIran.MapPreview".equals(info.name) &&
                "com.abrar.voyahiran".equals(info.ownerPackageName);
    }

    public static boolean applies(ActivityRecord a) {
        initialize(a);
        // Passenger HOME owns its full-display layout and reserves the status area itself.
        // Match disabling the geometry policy for this Activity only; keep theme handling.
        return enabled && candidate(a) && !(a.getDisplayId() == 1 &&
            "com.abrar.voyahiran".equals(a.info.packageName) &&
            "com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity".equals(a.info.name));
    }

    private static boolean densityCandidate(ActivityRecord a) {
        if (!candidate(a)) return false;
        ApplicationInfo app = a.info.applicationInfo;
        return !"com.abrar.voyahiran".equals(app.packageName) && app.uid >= 10000 &&
                (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
    }

    public static int orientation(ActivityRecord a, int requested) {
        // -2 is WindowContainer's "unset/no orientation source", not an app request.
        return requested != -2 && applies(a) ? 0 : requested;
    }

    public static void configuration(ActivityRecord a, Configuration parent) {
        initialize(a);
        boolean map = mapCandidate(a);
        int selectedNight = map ? vehicleNight() : nightMode;
        if ((map || candidate(a)) && selectedNight != 0) {
            Configuration themed = a.getResolvedOverrideConfiguration();
            themed.uiMode = (themed.uiMode & ~0x30) | selectedNight;
            if (map) Log.d(TAG, "Map theme display=" + a.getDisplayId() + " package=" + a.info.packageName +
                    " source=launcher night=" + selectedNight + " uiMode=" + themed.uiMode);
        }
        if (densityCandidate(a)) {
            Configuration result = a.getResolvedOverrideConfiguration();
            int density = densityForActivity(a);
            result.densityDpi = density;
            Rect content = parent.windowConfiguration == null ? null : parent.windowConfiguration.getAppBounds();
            if ((content == null || content.isEmpty()) && parent.windowConfiguration != null) content = parent.windowConfiguration.getBounds();
            if (content != null && !content.isEmpty()) {
                result.screenWidthDp = content.width() * 160 / density;
                result.screenHeightDp = content.height() * 160 / density;
                result.smallestScreenWidthDp = Math.min(result.screenWidthDp, result.screenHeightDp);
            }
        }
        if (!applies(a)) return;
        Rect full = parent.windowConfiguration.getBounds();
        if (full.width() <= LEFT + 320 || full.height() <= TOP + 200 || full.width() <= full.height()) return;
        Configuration result = a.getResolvedOverrideConfiguration();
        Rect bounds = new Rect(full.left + leftInset(a), full.top + TOP, full.right, full.bottom);
        Rect content = new Rect(bounds);
        Rect parentContent = parent.windowConfiguration.getAppBounds();
        if (parentContent != null && !parentContent.isEmpty()) content.intersect(parentContent);
        result.windowConfiguration.setBounds(bounds);
        result.windowConfiguration.setAppBounds(content);
        result.orientation = Configuration.ORIENTATION_LANDSCAPE;
        int density = result.densityDpi > 0 ? result.densityDpi : parent.densityDpi;
        if (density <= 0) density = 160;
        result.screenWidthDp = content.width() * 160 / density;
        result.screenHeightDp = content.height() * 160 / density;
        result.smallestScreenWidthDp = Math.min(result.screenWidthDp, result.screenHeightDp);
        Log.d(TAG, "Bounds display=" + a.getDisplayId() + " " + a.info.packageName + " " + bounds + " content=" + content);
    }

    private static Rect safe(WindowState w) {
        // App windows only: never touch IME, vehicle/system bars, accessibility overlays or camera.
        if (w.mAttrs.type < 1 || w.mAttrs.type >= 2000 || !applies(w.mActivityRecord)) return null;
        Rect bounds = w.mActivityRecord.getResolvedOverrideConfiguration().windowConfiguration.getBounds();
        if (bounds.left < leftInset(w.mActivityRecord) || bounds.top < TOP || bounds.isEmpty()) return null;
        return bounds;
    }

    public static int leftInset(ActivityRecord a) {
        return a != null && a.getDisplayId() == 1 && a.info != null && "com.abrar.voyahiran".equals(a.info.packageName) &&
            "com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity".equals(a.info.name) ? 0 : LEFT;
    }

    private static void clip(Rect frame, Rect bounds) {
        if (frame != null && !frame.isEmpty()) {
            // Rect.intersect leaves the original unchanged on no overlap.
            if (!frame.intersect(bounds)) frame.set(bounds);
        }
    }

    public static void beforeFrame(WindowState w, WindowFrames frames, Rect containing, Rect display) {
        suppressLoginWelcome(w);
        Rect bounds = safe(w);
        if (bounds == null) return;
        clip(containing, bounds);
        clip(display, bounds);
        clip(frames.mParentFrame, bounds);
        clip(frames.mDisplayFrame, bounds);
        clip(frames.mContainingFrame, bounds);
        clip(frames.mContentFrame, bounds);
        clip(frames.mVisibleFrame, bounds);
        clip(frames.mStableFrame, bounds);
    }

    static boolean supportsLoginWelcomeVariant(String variant) {
        return java.util.regex.Pattern.compile("(?i)(?<![a-z0-9])h97[ac](?![a-z0-9])").matcher(variant).find();
    }

    private static void suppressLoginWelcome(WindowState w) {
        if (w.mAttrs == null || !"com.qinggan.app.login".equals(w.mAttrs.packageName) ||
                !"LoginingView".contentEquals(w.mAttrs.getTitle())) return;
        String variant = (android.os.Build.DEVICE + " " + android.os.Build.PRODUCT + " " +
                android.os.Build.MODEL + " " + android.os.Build.BOARD + " " +
                android.os.Build.FINGERPRINT).toLowerCase(java.util.Locale.ROOT);
        if (!supportsLoginWelcomeVariant(variant)) return;
        try {
            int state = w.mContext.getPackageManager().getComponentEnabledSetting(
                new android.content.ComponentName("com.qinggan.app.login", "com.qinggan.app.login.RootActivity"));
            if (state != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return;
            // Keep the view's login callbacks/timers alive, but never present its surface.
            // hideLw also updates input focus; do not merely paint a transparent window.
            if (w.hideLw(false)) Log.i(TAG, "Suppressed login welcome window");
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot read login welcome policy", e);
        }
    }

    public static void afterFrame(WindowState w, WindowFrames frames) {
        Rect bounds = safe(w);
        if (bounds != null) clip(frames.mFrame, bounds);
    }
}
