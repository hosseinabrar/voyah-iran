package com.abrar.voyahiran.feature.apollo.data

import com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

internal object ApolloExperimentPort {
    private suspend fun request(args: List<String>): String {
        var result = ProcessCommandRunner().run(listOf("su", "0") + args, 5)
        var attempts = 0
        while (result.exitCode == 75 && !result.timedOut && attempts++ < 4) {
            delay(350)
            result = ProcessCommandRunner().run(listOf("su", "0") + args, 5)
        }
        check(result.exitCode == 0 && !result.timedOut) { result.output }
        return result.output
    }
    suspend fun read(): Map<ApolloExperiment, Int?> = try {
        val json = JSONObject(request(listOf("apollo-test-read")).trim())
        ApolloExperiment.entries.associateWith { if (json.isNull(it.name)) null else json.getInt(it.name) }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        AppLog.error("ApolloTest", "Read failed", e)
        emptyMap()
    }
    suspend fun set(setting: ApolloExperiment, value: Int) {
        require(setting.accepts(value))
        request(listOf("apollo-test-set", setting.name, value.toString()))
    }
}
