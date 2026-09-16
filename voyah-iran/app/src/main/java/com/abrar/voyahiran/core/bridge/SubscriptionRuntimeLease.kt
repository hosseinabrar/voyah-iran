package com.abrar.voyahiran.core.bridge

class SubscriptionRuntimeLease(private val clock: () -> Long) {
    @Volatile private var until=0L
    val active: Boolean get()=clock()<until
    fun update(seconds: Int) {require(seconds in 0..90);until=clock()+seconds*1000L}
}
