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
                    observer = new ContentObserver(handler) {
                        @Override public void onChange(boolean selfChange) { refresh(service, resolver); }
                    };
                    resolver.registerContentObserver(Settings.Global.getUriFor(KEY), false, observer);
                    refresh(service, resolver);
                } catch (RuntimeException e) {
                    enabled = false;
                    scheduled = false;
                    Log.e(TAG, "Initialization failed; original layout retained", e);
                }
            }
        });
    }

    private static void refresh(final ActivityTaskManagerService service, ContentResolver resolver) {
        try {
            String request = Settings.Global.getString(resolver, KEY);
            enabled = request == null || request.equals("1") || request.matches("1:[a-f0-9]{32}");
            synchronized (service.mGlobalLock) {
                service.mRootWindowContainer.forAllActivities(new java.util.function.Consumer<ActivityRecord>() {
                    @Override public void accept(ActivityRecord a) {
                        // Include previously constrained activities on disable, too.
                        if (candidate(a) && a.getParent() != null) {
                            a.onConfigurationChanged(a.getParent().getConfiguration());
                            a.ensureActivityConfiguration(0, true);
                        }
                    }
                });
                service.mWindowManager.requestTraversal();
            }
            Settings.Global.putString(resolver, ACK, request == null ? "1:default" : request);
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

    public static boolean applies(ActivityRecord a) {
        initialize(a);
        return enabled && candidate(a);
    }

    public static int orientation(ActivityRecord a, int requested) {
        // -2 is WindowContainer's "unset/no orientation source", not an app request.
        return requested != -2 && applies(a) ? 0 : requested;
    }

    public static void configuration(ActivityRecord a, Configuration parent) {
        if (!applies(a)) return;
        Rect full = parent.windowConfiguration.getBounds();
        if (full.width() <= LEFT + 320 || full.height() <= TOP + 200 || full.width() <= full.height()) return;
        Configuration result = a.getResolvedOverrideConfiguration();
        Rect bounds = new Rect(full.left + LEFT, full.top + TOP, full.right, full.bottom);
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
        if (bounds.left < LEFT || bounds.top < TOP || bounds.isEmpty()) return null;
        return bounds;
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
