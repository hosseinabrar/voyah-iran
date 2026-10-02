package com.abrar.voyahiran.feature.apollo.domain

internal object ApolloReconcile {
    fun needsWrite(expected: Int, actual: Int?): Boolean {
        require(expected in 1..2)
        // H97C component stores state - 1; getVehicleState returns that raw cache.
        return actual == -1 || (actual in 0..1 && actual != expected - 1)
    }
}
