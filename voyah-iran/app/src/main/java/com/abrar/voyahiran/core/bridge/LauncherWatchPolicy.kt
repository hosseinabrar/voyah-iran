package com.abrar.voyahiran.core.bridge

/** Scope routing and restart-safe cursors for the shared CAN subscription. */
internal object LauncherWatchPolicy {
    fun scopeOf(name:String)=if(name.startsWith("FRONT_SEAT_") ||
        name in listOf("FCM_SW_REQ_FB","IVI_FRAG_TASTE_FB","IVI_FRAG_CONCERNTION_FB","FCM_DURATION_CONTROL"))
        "passenger" else "central"
    fun unchanged(after:Long?,latest:Long,relevant:Long)=
        after!=null && after>=0 && after<=latest && after>=relevant
}
