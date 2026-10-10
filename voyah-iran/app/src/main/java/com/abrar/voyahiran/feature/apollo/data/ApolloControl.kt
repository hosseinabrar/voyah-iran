package com.abrar.voyahiran.feature.apollo.data

import android.content.Context
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.apollo.domain.ApolloPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

object ApolloControl {
    private val lock = Mutex()
    private var applied: Int? = null
    private fun prefs(context: Context) = context.getSharedPreferences("apollo", Context.MODE_PRIVATE)
    fun checked(context: Context) = prefs(context).getBoolean("enabled", false)
    fun save(context: Context, enabled: Boolean) { check(prefs(context).edit().putBoolean("enabled", enabled).commit()) }
    suspend fun sync(context: Context, force: Boolean = false, verify: Boolean = false) = lock.withLock {
        val app = context.applicationContext as VoyahIranApplication
        repeat(6) { attempt ->
            val value = ApolloPolicy.value(app.subscriptions.vipAllowed, checked(app))
            if (!force && !verify && applied == value) return@withLock
            // Capture the currently displayed/OEM selections before enabling the bundle.
            if (value == 2 && applied != 2 && !ApolloSelections.hasSnapshot(app)) ApolloReportReader(app).read()
            val result = ProcessCommandRunner().run(listOf("su", "0", if (verify && !force) "apollo-reconcile" else "apollo-state", value.toString()), 5)
            if (result.exitCode == 75 && !result.timedOut && attempt < 5) delay(300L + attempt * 100L)
            else {
                check(result.exitCode == 0 && !result.timedOut && result.output.contains("value=$value")) { result.output }
                applied = value
                if (value == 2 && result.output.contains("bundle dispatched")) ApolloSelections.replay(app)
                return@withLock
            }
        }
    }
}
