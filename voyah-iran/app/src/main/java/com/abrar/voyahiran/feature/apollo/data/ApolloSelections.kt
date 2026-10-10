package com.abrar.voyahiran.feature.apollo.data

import android.content.Context
import com.abrar.voyahiran.feature.apollo.domain.*
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.CancellationException

internal object ApolloSelections {
    private fun prefs(c: Context) = c.getSharedPreferences("apollo_selections", Context.MODE_PRIVATE)
    fun hasSnapshot(c: Context) = ApolloFeature.displayed.all { prefs(c).contains(it.name) }
    fun local(f: ApolloFeature) = f == ApolloFeature.EMERGENCY_LANE || f == ApolloFeature.EMERGENCY_STEERING
    fun save(c: Context, f: ApolloFeature, value: Boolean) {
        check(prefs(c).edit().putBoolean(f.name, value).commit())
    }
    fun display(c: Context, f: ApolloFeature, actual: Boolean?): Boolean? {
        val p = prefs(c)
        if (local(f) && p.contains(f.name)) return p.getBoolean(f.name, false)
        if (actual != null && f in ApolloFeature.displayed) save(c, f, actual)
        return actual
    }
    fun save(c: Context, e: ApolloExperiment, value: Int) {
        if (e == ApolloExperiment.SPEED_WARNING || e == ApolloExperiment.SPEED_OFFSET)
            check(prefs(c).edit().putInt("experiment_${e.name}", value).commit())
    }
    suspend fun replay(c: Context) {
        val p = prefs(c)
        for (f in ApolloFeature.displayed) {
            if (!p.contains(f.name)) continue
            try {
                val result = ProcessCommandRunner().run(listOf("su", "0", "apollo-feature", f.name,
                    if (p.getBoolean(f.name, false)) "on" else "off"), 5)
                check(result.exitCode == 0 && !result.timedOut) { result.output }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { AppLog.error("Apollo", "Restore failed field=${f.field}", e) }
        }
        for (e in listOf(ApolloExperiment.SPEED_WARNING, ApolloExperiment.SPEED_OFFSET)) {
            if (!p.contains("experiment_${e.name}")) continue
            if (e == ApolloExperiment.SPEED_OFFSET && p.getInt("experiment_SPEED_WARNING", 1) != 2) continue
            try { ApolloExperimentPort.set(e, p.getInt("experiment_${e.name}", 1)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { AppLog.error("Apollo", "Restore failed field=${e.field}", error) }
        }
    }
}
