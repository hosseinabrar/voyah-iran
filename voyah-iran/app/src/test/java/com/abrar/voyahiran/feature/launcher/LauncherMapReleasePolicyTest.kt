package com.abrar.voyahiran.feature.launcher

import com.abrar.voyahiran.core.shell.CommandResult
import org.junit.Assert.*
import org.junit.Test

class LauncherMapReleasePolicyTest {
    @Test fun retainedMapMustNotReleaseItsDisplayEvenOnExitZero() {
        assertFalse(LauncherMapReleasePolicy.canDispose(8,CommandResult(0,"Map retained in preview; foreground=com.abrar.voyahiran")))
        assertFalse(LauncherMapReleasePolicy.canDispose(8,CommandResult(0,"Map already outside preview")))
    }
    @Test fun requiresVerifiedMatchingDisplayAndSuccessfulResponse() {
        assertTrue(LauncherMapReleasePolicy.canDispose(8,CommandResult(0,"MAP_RELEASE_SAFE display=8\n")))
        assertFalse(LauncherMapReleasePolicy.canDispose(8,CommandResult(0,"MAP_RELEASE_SAFE display=9")))
        assertFalse(LauncherMapReleasePolicy.canDispose(8,CommandResult(1,"MAP_RELEASE_SAFE display=8")))
        assertFalse(LauncherMapReleasePolicy.canDispose(8,CommandResult(0,"MAP_RELEASE_SAFE display=8",true)))
    }
}
