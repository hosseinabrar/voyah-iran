package com.abrar.voyahiran.feature.launcher

/** A delayed watch response must not overwrite a newer timeout readback. */
internal class VehicleSnapshotOrder {
    private var session=-1L
    private var revision=-1L
    fun accept(nextSession:Long,nextRevision:Long):Boolean {
        if(nextSession<session || nextSession==session && nextRevision<revision)return false
        session=nextSession;revision=nextRevision
        return true
    }
}
