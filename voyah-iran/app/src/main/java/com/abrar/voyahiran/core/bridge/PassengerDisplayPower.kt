package com.abrar.voyahiran.core.bridge

/** VoyahPolicy persists the physical passenger power button state independently of display/lift state. */
internal object PassengerDisplayPower {
    fun isOff(): Boolean = Class.forName("android.os.SystemProperties")
        .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        .invoke(null, "persist.second.screen.disable", false) as Boolean
}
