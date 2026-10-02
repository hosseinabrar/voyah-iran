package com.abrar.voyahiran.core.bridge

/** Binder ownership changed in Android 10; stack operations retain their signatures. */
internal data class ScreenSwapContract(val owner: String, val api: String) {
    companion object {
        fun forSdk(sdk: Int): ScreenSwapContract? = when (sdk) {
            // API 28 denies modern apps access to passenger input_method_ext;
            // moving their stack succeeds but immediately crashes the app.
            28 -> null
            30 -> ScreenSwapContract("android.app.ActivityTaskManager", "android.app.IActivityTaskManager")
            else -> null
        }
    }
}
