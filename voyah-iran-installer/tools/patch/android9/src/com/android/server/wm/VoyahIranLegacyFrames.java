package com.android.server.wm;

import android.graphics.Rect;
import com.android.server.am.VoyahIranLegacyDisplay;

/** Android 9 has AppWindowToken and direct frame fields, not ActivityRecord/WindowFrames. */
public final class VoyahIranLegacyFrames {
    public static void traverse(WindowManagerService service) { service.requestTraversal(); }
    private static Object activity(AppWindowToken token) {
        if (token == null) return null;
        WindowContainerController controller = token.getController();
        return controller == null ? null : controller.mListener;
    }
    public static boolean applies(AppWindowToken token) {
        return VoyahIranLegacyDisplay.applies(activity(token));
    }
    public static int orientation(AppWindowToken token, int requested) {
        return VoyahIranLegacyDisplay.orientation(activity(token), requested);
    }
    private static Rect safe(WindowState w) {
        if (w.mAttrs.type < 1 || w.mAttrs.type >= 2000 || !applies(w.mAppToken)) return null;
        Rect b = w.mAppToken.getBounds();
        return b.left >= 145 && b.top >= 50 && !b.isEmpty() ? b : null;
    }
    private static void clip(Rect frame, Rect bounds) {
        if (frame != null && !frame.isEmpty() && !frame.intersect(bounds)) frame.set(bounds);
    }
    public static void before(WindowState w, Rect containing, Rect display) {
        Rect b = safe(w);
        if (b == null) return;
        WindowState ime = w.mService == null ? null : (WindowState) w.mService.mInputMethodWindows.get(w.getDisplayId());
        // Restore only the bottom decor boundary; preserve IME resize/pan and
        // frames ending earlier (dialogs/other restrictions).
        int bottom = w.getStableFrameLw().bottom;
        if (ime == null || !ime.isVisibleLw()) {
            restoreBottom(containing, bottom, b); restoreBottom(display, bottom, b);
            restoreBottom(w.mParentFrame, bottom, b); restoreBottom(w.mDisplayFrame, bottom, b);
            restoreBottom(w.mContainingFrame, bottom, b); restoreBottom(w.getContentFrameLw(), bottom, b);
            restoreBottom(w.mVisibleFrame, bottom, b); restoreBottom(w.getStableFrameLw(), bottom, b);
        }
        clip(containing, b); clip(display, b);
        clip(w.mParentFrame, b); clip(w.mDisplayFrame, b); clip(w.mContainingFrame, b);
        clip(w.getContentFrameLw(), b); clip(w.mVisibleFrame, b); clip(w.getStableFrameLw(), b);
    }
    static void restoreBottom(Rect frame, int decorBottom, Rect bounds) {
        if (frame != null && !frame.isEmpty() && decorBottom > bounds.top &&
                decorBottom < bounds.bottom && frame.bottom == decorBottom) frame.bottom = bounds.bottom;
    }
    public static void after(WindowState w) {
        Rect b = safe(w);
        if (b != null) clip(w.mFrame, b);
    }
}
