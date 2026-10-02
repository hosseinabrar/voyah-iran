package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.abrar.voyahiran.core.logging.AppLog
import java.util.concurrent.Executors

/** On-demand list, no background package scanning and no launcher replacement. */
class LauncherDrawerActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    private var generation = 0
    private data class Entry(val label: String, val component: ComponentName)
    @Suppress("DEPRECATION")
    private fun currentDisplayId() = windowManager.defaultDisplay.displayId
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 42))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            // Preserve factory navigation and status area even without the framework patch.
            setPadding(145 + dp(16), 50 + dp(12), dp(16), dp(16))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "برنامه‌ها"; textSize = 24f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(Button(this).apply {
            text = "فهرست اصلی خودرو"
            setOnClickListener {
                // Some OEM tiles are internal functions, not launchable Android activities.
                // Keep the original drawer available without generating another click event.
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(ComponentName(LauncherFallbackPolicy.PACKAGE, if (currentDisplayId() == 1) "com.qinggan.secondlauncher.activity.SecondMainActivity" else "com.qinggan.app.launcher.activity.MainActivity"))
                        .putExtra("showAllApp", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic().apply { launchDisplayId = currentDisplayId() }.toBundle())
                    finish()
                } catch (e: Exception) { failure(e) }
            }
        })
        root.addView(header)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.post {
            val location = IntArray(2); root.getLocationOnScreen(location)
            // A patched framework may already have inset the activity window.
            root.setPadding((145 - location[0]).coerceAtLeast(0) + dp(16),
                (50 - location[1]).coerceAtLeast(0) + dp(12), dp(16), dp(16))
        }
    }
    override fun onResume() {
        super.onResume()
        val request = ++generation
        body.removeAllViews(); body.addView(ProgressBar(this))
        worker.execute {
            try {
                val entries = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.enabled && it.activityInfo.exported && it.activityInfo.applicationInfo.enabled && it.activityInfo.packageName != LauncherFallbackPolicy.PACKAGE &&
                        (it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0 }
                    .map { Entry(it.loadLabel(packageManager).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
                    .distinctBy { it.component }.sortedBy { it.label }
                runOnUiThread { if (!isFinishing && !isDestroyed && generation == request) show(entries) }
            } catch (e: Exception) { runOnUiThread { if (!isFinishing && !isDestroyed && generation == request) { body.removeAllViews(); failure(e) } } }
        }
    }
    private fun show(entries: List<Entry>) {
        body.removeAllViews()
        var row: LinearLayout? = null
        entries.forEachIndexed { index, entry ->
            if (index % 5 == 0) { row = LinearLayout(this); body.addView(row) }
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundColor(Color.rgb(30, 41, 59)); isFocusable = true; contentDescription = entry.label
            }
            tile.addView(ImageView(this).apply { setImageDrawable(runCatching { packageManager.getActivityIcon(entry.component) }.getOrNull()) }, LinearLayout.LayoutParams(dp(52), dp(52)))
            tile.addView(TextView(this).apply { text = entry.label; textSize = 17f; maxLines = 2; gravity = Gravity.CENTER; setTextColor(Color.WHITE) })
            tile.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                        ActivityOptions.makeBasic().apply { launchDisplayId = currentDisplayId() }.toBundle())
                    finish()
                } catch (e: Exception) { failure(e) }
            }
            row!!.addView(tile, LinearLayout.LayoutParams(0, dp(125), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            if (index == entries.lastIndex) repeat((5 - entries.size % 5) % 5) { row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
        }
        AppLog.info("LauncherDrawer", "loaded user=${entries.size}")
    }
    private fun failure(e: Exception) { AppLog.error("LauncherDrawer", "operation failed", e); Toast.makeText(this, "اجرای برنامه امکان‌پذیر نشد", Toast.LENGTH_SHORT).show() }
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
}
