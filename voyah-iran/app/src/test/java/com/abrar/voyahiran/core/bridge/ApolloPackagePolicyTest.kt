package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import org.junit.Assert.*
import org.junit.Test

class ApolloPackagePolicyTest {
    @Test fun exactPackageIsPresent() {
        assertEquals(true, ApolloPackagePolicy.detect(CommandResult(0, "package:com.baidu.universalparking\r\n")))
    }
    @Test fun absentOrSimilarlyNamedPackageIsNotApollo() {
        assertEquals(false, ApolloPackagePolicy.detect(CommandResult(0, "")))
        assertEquals(false, ApolloPackagePolicy.detect(CommandResult(0, "package:com.baidu.universalparking.test")))
    }
    @Test fun queryFailureRemainsUnknown() {
        assertNull(ApolloPackagePolicy.detect(CommandResult(1, "")))
        assertNull(ApolloPackagePolicy.detect(CommandResult(0, "Error: service unavailable")))
    }
}
