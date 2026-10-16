package com.abrar.voyahiran.core.bridge

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/** Same external-provider protocol as AOSP's content command, without a process per request.
 * app_process has no registered Application: a synthetic Context resolver is not valid here.
 * Each operation releases its external reference, including on failure/provider death.
 */
internal object DowAmbientSettings {
    private const val AUTHORITY = "qinggan.settings"
    private const val KEY = "vehicle_setting_drive_mode_atm"
    private val uri = Uri.parse("content://$AUTHORITY/global")
    private val managerType by lazy { Class.forName("android.app.IActivityManager") }
    private val providerType by lazy { Class.forName("android.content.IContentProvider") }

    private fun <T> withProvider(block: (Any) -> T): T {
        check(android.os.Process.myUid() == 0) { "Ambient provider requires root bridge" }
        val manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
        val token = Binder()
        val acquire = managerType.methods.single { it.name == "getContentProviderExternal" }
        val release = managerType.methods.firstOrNull { it.name == "removeContentProviderExternalAsUser" }
            ?: managerType.methods.single { it.name == "removeContentProviderExternal" }
        val args = when (acquire.parameterCount) {
            3 -> arrayOf<Any?>(AUTHORITY, 0, token) // Android 9
            4 -> arrayOf<Any?>(AUTHORITY, 0, token, "voyah-dow") // Android 11
            else -> error("Unsupported external provider protocol")
        }
        val holder = checkNotNull(acquire.invoke(manager, *args)) { "Ambient settings provider unavailable" }
        try {
            return block(checkNotNull(holder.javaClass.getField("provider").get(holder)))
        } finally {
            if (release.parameterCount == 3) release.invoke(manager, AUTHORITY, token, 0)
            else release.invoke(manager, AUTHORITY, token)
        }
    }

    internal fun readFrom(provider: Any, api: Class<*>): Int {
        val query = api.methods.single { it.name == "query" }
        val extras = Bundle().apply { putString("android:query-arg-sql-selection", "name='$KEY'") }
        val args = when (query.parameterCount) {
            5 -> arrayOf<Any?>("root", uri, arrayOf("value"), extras, null)
            6 -> arrayOf<Any?>("root", null, uri, arrayOf("value"), extras, null)
            else -> error("Unsupported ambient query protocol")
        }
        val cursor = query.invoke(provider, *args) as? Cursor ?: error("Ambient settings query failed")
        return cursor.use {
            // Match the factory's default only for a successful query with no stored row.
            if (!it.moveToFirst()) 0
            else checkNotNull(it.getString(it.getColumnIndexOrThrow("value"))?.toIntOrNull()
                ?.takeIf { value -> value in 0..1 }) { "Invalid ambient drive mode" }
        }
    }

    fun read(): Int = withProvider { readFrom(it, providerType) }

    fun set(value: Int) {
        require(value in 0..1)
        withProvider { setOn(it, providerType, value) }
    }

    internal fun setOn(provider: Any, api: Class<*>, value: Int) {
        require(value in 0..1)
        val values = ContentValues().apply { put("name", KEY); put("value", value.toString()) }
        // Android 11 exposes BOTH the legacy default method and the modern overload.
        // Selecting by name with single() fails before any write reaches the provider.
        val insert = try {
            api.getMethod("insert", String::class.java, String::class.java,
                Uri::class.java, ContentValues::class.java, Bundle::class.java)
        } catch (_: NoSuchMethodException) {
            api.getMethod("insert", String::class.java, Uri::class.java, ContentValues::class.java)
        }
        val args = when (insert.parameterCount) {
            3 -> arrayOf<Any?>("root", uri, values)
            5 -> arrayOf<Any?>("root", null, uri, values, Bundle())
            else -> error("Unsupported ambient insert protocol")
        }
        // Dispatch only. Do not gate warning onset on a query/readback round trip.
        // Recovery still verifies the complete saved snapshot asynchronously.
        checkNotNull(insert.invoke(provider, *args)) { "Ambient settings write rejected" }
    }
}
