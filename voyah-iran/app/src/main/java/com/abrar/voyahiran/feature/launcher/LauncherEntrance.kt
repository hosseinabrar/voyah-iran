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
        val backdrop=FrameLayout(activity).apply {
            tag=BACKDROP
            setBackgroundColor(LauncherWindowTheme.background(LauncherTheme.isLight()))
            addView(content,FrameLayout.LayoutParams(-1,-1))
        }
        activity.setContentView(backdrop)
        activity.lifecycle.addObserver(object:DefaultLifecycleObserver {
            override fun onStart(owner:LifecycleOwner) {
                content.clearAnimation()
                if(android.animation.ValueAnimator.areAnimatorsEnabled())
                    content.startAnimation(AnimationUtils.loadAnimation(activity,R.anim.launcher_c_enter))
            }
            @Suppress("DEPRECATION")
            override fun onResume(owner:LifecycleOwner) {
                // A source application's transition must not scale our whole window.
                activity.overridePendingTransition(0,0)
            }
            override fun onStop(owner:LifecycleOwner) {content.clearAnimation()}
        })
    }
    fun theme(window:android.view.Window,color:Int) {
        window.decorView.findViewWithTag<View>(BACKDROP)?.setBackgroundColor(color)
    }
}
