package com.abrar.voyahiran.feature.launcher

/** Focus-only overlays do not constitute a new launcher entrance. */
internal object LauncherEntrancePolicy {
    fun keepVisible(hidden:Boolean,otherForeground:Boolean,focusOnlyWhileResumed:Boolean,avmVisible:Boolean):Boolean =
        !hidden && !otherForeground && (focusOnlyWhileResumed || avmVisible)
}
