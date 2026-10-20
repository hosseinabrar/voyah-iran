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
    fun install(activity:ComponentActivity,content:View) {
        LauncherWindowTheme.apply(activity.window,LauncherTheme.isLight())
        val backdrop=FrameLayout(activity).apply {
            tag=BACKDROP
            setBackgroundColor(LauncherWindowTheme.background(LauncherTheme.isLight()))
            addView(content,FrameLayout.LayoutParams(-1,-1))
        }
        activity.setContentView(backdrop)
        var pending=false
        fun enter() {
            if(!pending)return
            pending=false
            content.clearAnimation()
            if(android.animation.ValueAnimator.areAnimatorsEnabled())
                content.startAnimation(AnimationUtils.loadAnimation(activity,R.anim.launcher_c_enter))
        }
        val focus=android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused->if(focused)enter() }
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        activity.lifecycle.addObserver(object:DefaultLifecycleObserver {
            @Suppress("DEPRECATION")
            override fun onResume(owner:LifecycleOwner) {
                // A source application's transition must not scale our whole window.
                activity.overridePendingTransition(0,0)
                LauncherWindowTheme.apply(activity.window,LauncherTheme.isLight())
                pending=true
                if(content.hasWindowFocus())enter()
            }
            override fun onPause(owner:LifecycleOwner) {pending=false;content.clearAnimation()}
            override fun onDestroy(owner:LifecycleOwner) {
                activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            }
        })
    }
    fun theme(window:android.view.Window,color:Int) {
        window.decorView.findViewWithTag<View>(BACKDROP)?.setBackgroundColor(color)
    }
}
