package com.abrar.voyahiran.core.bridge

/** Startup UX: a confirmed grant lasts this boot; a denial is persisted for the next boot. */
internal class LoginBootPolicy(initial: Boolean?) {
    @Volatile var allowed: Boolean? = initial
        private set
    @Synchronized fun update(granted: Boolean) {
        if(allowed != true) allowed=granted
    }
    companion object {
        fun cached(value: String?): Boolean? {
            val parts=value?.trim()?.split(':') ?: return null
            if(parts.size!=2)return null
            val issued=parts[0].toLongOrNull() ?: return null
            val expiry=parts[1].toLongOrNull() ?: return null
            if(issued<0 || expiry<issued)return null
            // Wall-clock corrections during vehicle boot must not reverse the last decision.
            return expiry>issued
        }
    }
}
