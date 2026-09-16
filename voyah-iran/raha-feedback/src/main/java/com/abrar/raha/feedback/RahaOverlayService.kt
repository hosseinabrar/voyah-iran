package com.abrar.raha.feedback

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/** Window host only: no UI content retrieval, gestures or key filtering. */
class RahaOverlayService : AccessibilityService() {
    override fun onCreate() { super.onCreate(); Log.i("RahaFeedback", "overlay service created") }
    override fun onServiceConnected() { super.onServiceConnected(); instance=this; Log.i("RahaFeedback", "accessibility overlay host connected") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean { Log.w("RahaFeedback", "overlay service disconnected"); if(instance===this) { instance=null; RahaFeedback.hostDisconnected() }; return super.onUnbind(intent) }
    override fun onDestroy() { if(instance===this) { instance=null; RahaFeedback.hostDisconnected() }; super.onDestroy() }
    companion object { @Volatile var instance: RahaOverlayService? = null; private set }
}
