package com.abrar.voyahiran.feature.launcher

import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.abrar.voyahiran.R

/** Keep a full-size opaque canvas beneath the OEM content-only entrance. */
internal object LauncherEntrance {
    private const val BACKDROP="launcher-fixed-backdrop"
    fun install(activity:ComponentActivity,content:View, avmVisible: (() -> Boolean)? = null) {
        val avm = if(avmVisible==null) LauncherAvmVisibility(activity).also { it.start() } else null
        val cameraVisible = avmVisible ?: { avm?.visible() == true }
        LauncherWindowTheme.apply(activity.window,LauncherTheme.isLight())
        val backdrop=FrameLayout(activity).apply {
            tag=BACKDROP
            setBackgroundColor(LauncherWindowTheme.background(LauncherTheme.isLight()))
            addView(content,FrameLayout.LayoutParams(-1,-1))
        }
        // A stopped HOME can become visible below the exiting OEM app before resume.
        // Keep only its opaque backdrop visible until the entrance is armed.
        content.alpha=0f
        activity.setContentView(backdrop)
        var resumed=false
        var focused=content.hasWindowFocus()
        var hidden=true
        var cameraCover=false
        var otherForeground=false
        fun hide() {
            cameraCover=false
            hidden=true
            content.alpha=0f
            content.clearAnimation()
        }
        fun loseFocus() {
            // Camera overlays are transparent to an already-presented launcher.
            // A cold/hidden launcher must still perform its initial entrance.
            if(!hidden && !otherForeground && cameraVisible()) {
                cameraCover=true
                content.clearAnimation()
                content.alpha=1f
            } else hide()
        }
        fun enter() {
            if(resumed && focused && cameraCover) {
                cameraCover=false
                otherForeground=false
                return
            }
            if(!resumed || !focused || !hidden)return
            otherForeground=false
            hidden=false
            content.clearAnimation()
            if(android.animation.ValueAnimator.areAnimatorsEnabled())
                content.startAnimation(AnimationUtils.loadAnimation(activity,R.anim.launcher_c_enter))
            // Arm the zero-alpha first animation frame BEFORE exposing the content.
            content.alpha=1f
        }
        val observer=activity.window.decorView.viewTreeObserver
        val focus=android.view.ViewTreeObserver.OnWindowFocusChangeListener {
            focused=it
            if(it)enter() else loseFocus()
        }
        val foreground = object : android.content.BroadcastReceiver() {
            override fun onReceive(c:android.content.Context,intent:android.content.Intent) {
                if(intent.getIntExtra("displayId",-1)!=activity.window.decorView.display?.displayId)return
                val component=android.content.ComponentName.unflattenFromString(
                    intent.getStringExtra("android.intent.extra.COMPONENT_NAME") ?: return) ?: return
                otherForeground=component!=activity.componentName
                if(otherForeground && cameraCover)hide()
            }
        }
        activity.registerReceiver(foreground,android.content.IntentFilter("android.intent.action.TOP_ACTIVITY_CHANGED"))
        observer.addOnWindowFocusChangeListener(focus)
        activity.lifecycle.addObserver(object:DefaultLifecycleObserver {
            @Suppress("DEPRECATION")
            override fun onResume(owner:LifecycleOwner) {
                // A source application's transition must not scale our whole window.
                activity.overridePendingTransition(0,0)
                LauncherWindowTheme.apply(activity.window,LauncherTheme.isLight())
                resumed=true
                focused=focused || content.hasWindowFocus()
                enter()
            }
            override fun onPause(owner:LifecycleOwner) {resumed=false;focused=false;loseFocus()}
            override fun onStop(owner:LifecycleOwner) {
                if(!cameraCover || otherForeground || !cameraVisible())hide()
            }
            override fun onDestroy(owner:LifecycleOwner) {
                avm?.stop()
                activity.unregisterReceiver(foreground)
                if(observer.isAlive)observer.removeOnWindowFocusChangeListener(focus)
                else activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            }
        })
    }
    fun theme(window:android.view.Window,color:Int) {
        window.decorView.findViewWithTag<View>(BACKDROP)?.setBackgroundColor(color)
    }
}
