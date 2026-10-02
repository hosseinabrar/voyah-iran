package com.abrar.voyahiran.feature.apollo.domain
import org.junit.Assert.*
import org.junit.Test
class ApolloPolicyTest {
    @Test fun onlyValidSubscriptionAndCheckedEnables() {
        assertEquals(2, ApolloPolicy.value(true, true))
        assertEquals(1, ApolloPolicy.value(true, false))
        assertEquals(1, ApolloPolicy.value(false, true))
        assertEquals(1, ApolloPolicy.value(false, false))
    }
    @Test fun exactBundleFields() {
        assertEquals(18, ApolloPolicy.fields.size)
        assertEquals(18, ApolloPolicy.fields.toSet().size)
        assertEquals(setOf("RPA_FUNC_ENABLE", "HPP_FUNC_ENABLE", "GLC_FUNC_ENABLE", "ISLC_FUNC_ENABLE", "TLC_FUNC_ENABLE", "NOA_FUNC_ENABLE", "ELK_FUNC_ENABLE", "ESA_FUNC_ENABLE", "APA_FUNC_ENABLE_SA", "RPA_FUNC_ENABLE_SA", "HAVP_FUNC_ENABLE_SA", "ACC_FUNC_ENABLE_SA", "ICA_FUNC_ENABLE_SA", "PLC_FUNC_ENABLE_SA", "HANP_FUNC_ENABLE_SA", "ISA_FUNC_ENABLE_SA", "ISLC_FUNC_ENABLE_SA", "TLA_FUNC_ENABLE_SA"), ApolloPolicy.fields.toSet())
    }
}
