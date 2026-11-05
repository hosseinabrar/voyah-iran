package com.abrar.voyahiran.feature.launcher

import com.abrar.voyahiran.core.shell.CommandResult

internal object LauncherMapReleasePolicy {
    fun canDispose(display:Int,result:CommandResult):Boolean =
        result.exitCode==0 && !result.timedOut && result.output.trim()=="MAP_RELEASE_SAFE display=$display"
}
