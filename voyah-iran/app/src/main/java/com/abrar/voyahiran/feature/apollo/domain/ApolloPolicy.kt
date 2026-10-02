package com.abrar.voyahiran.feature.apollo.domain

object ApolloPolicy {
    fun value(subscriptionValid: Boolean, checked: Boolean) = if (subscriptionValid && checked) 2 else 1
    val fields = listOf(
        "RPA_FUNC_ENABLE",
        "HPP_FUNC_ENABLE",
        "GLC_FUNC_ENABLE",
        "ISLC_FUNC_ENABLE",
        "TLC_FUNC_ENABLE",
        "NOA_FUNC_ENABLE",
        "ELK_FUNC_ENABLE",
        "ESA_FUNC_ENABLE",
        "APA_FUNC_ENABLE_SA",
        "RPA_FUNC_ENABLE_SA",
        "HAVP_FUNC_ENABLE_SA",
        "ACC_FUNC_ENABLE_SA",
        "ICA_FUNC_ENABLE_SA",
        "PLC_FUNC_ENABLE_SA",
        "HANP_FUNC_ENABLE_SA",
        "ISA_FUNC_ENABLE_SA",
        "ISLC_FUNC_ENABLE_SA",
        "TLA_FUNC_ENABLE_SA"
    )
}
