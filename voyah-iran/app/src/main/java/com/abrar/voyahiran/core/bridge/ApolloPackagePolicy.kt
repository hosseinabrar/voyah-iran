package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult

internal object ApolloPackagePolicy {
    const val PACKAGE = "com.baidu.universalparking"

    fun detect(result: CommandResult): Boolean? {
        if (result.exitCode != 0 || result.timedOut) return null
        val lines = result.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.any { !it.startsWith("package:") }) return null
        return lines.any { it == "package:$PACKAGE" }
    }
}
