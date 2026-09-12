package com.abrar.voyahiran.feature.apollo.domain

enum class ApolloFeature(val ordinalId: Int, val stateId: Int, val on: Int, val off: Int) {
    LANE_CHANGE(0x396, 0x46f, 2, 1),
    NAVIGATION(0x397, 0x470, 2, 1),
    TRAFFIC_LIGHT(0x3a4, 0x47d, 2, 1),
    LIGHT_CHANGE(0x3a5, 0x47e, 2, 1),
    SIGNS(0xd0, 0x115, 1, 2),
    EMERGENCY_STEERING(0x3a6, 0x47f, 1, 2),
    EMERGENCY_LANE(0x323, 0x3fc, 1, 2);

    fun checked(value: Int?): Boolean? = when(value) { on -> true; off -> false; else -> null }
}

data class ApolloReport(val subscribed: Boolean? = null, val features: Map<ApolloFeature, Boolean?> = emptyMap())

/** Provider-reported entitlement only; not a claim of server authentication or operational readiness. */
fun subscriptionReport(status: String?, expiry: String?): Boolean? = when {
    status !in listOf("0", "1") || expiry !in listOf("0", "1") -> null
    else -> status == "1" && expiry == "0"
}
