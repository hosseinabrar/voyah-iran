package com.abrar.voyahiran.feature.launcher

import android.graphics.Bitmap

/** MediaSession can parcel the same artwork into a fresh Bitmap after resubscription. */
internal object LauncherArtworkIdentity {
    fun same(first: Bitmap?, second: Bitmap?): Boolean {
        if (first === second) return true
        if (first == null || second == null || first.isRecycled || second.isRecycled) return false
        if (first.width != second.width || first.height != second.height || first.config != second.config) return false
        return runCatching { first.sameAs(second) }.getOrDefault(false)
    }
}
