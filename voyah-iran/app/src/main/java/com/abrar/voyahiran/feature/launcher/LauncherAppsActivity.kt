package com.abrar.voyahiran.feature.launcher

import android.app.ActivityOptions
import android.content.*
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/** Independent OEM + installed-app pages. No Home page participates in this pager. */
class LauncherAppsActivity : ComponentActivity() {
    private data class Entry(val id: String, val label: String, val icon: Drawable?, val component: ComponentName? = null)
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var pager: LauncherPager
    private lateinit var dots: LinearLayout
    private lateinit var status: TextView
    private var light = false
    private var entries = emptyList<Entry>()
    private var refreshJob: Job? = null
    private var guard: Job? = null
    private var opening = false
    private val pending = mutableMapOf<String, CompletableDeferred<JSONObject>>()
    private val reply = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action=="theme_loaded") {
                if(hasWindowFocus() && allowed()){applyTheme();refresh()}
                return
            }
            val nonce = intent?.getStringExtra("nonce") ?: return
            val waiting = pending.remove(nonce) ?: return
            val text = intent.getStringExtra("payload").orEmpty()
            if (text.length > 350000) { waiting.completeExceptionally(IllegalArgumentException("Catalog too large")); return }
            runCatching { JSONObject(text) }.fold(waiting::complete, waiting::completeExceptionally)
        }
    }
    private fun allowed() = LauncherPreferences.enabled(this) &&
        (application as VoyahIranApplication).subscriptions.vipAllowed && (window.decorView.display?.displayId ?: 0) == 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        content = FrameLayout(this)
        pager = LauncherPager(this)
        dots = LinearLayout(this).apply { gravity=Gravity.CENTER }
        status = TextView(this).apply { gravity=Gravity.CENTER; textSize=20f; typeface=resources.getFont(com.abrar.raha.feedback.R.font.iransansx_regular); text="در حال دریافت برنامه‌ها…"; setOnClickListener { refresh() } }
        content.addView(pager, FrameLayout.LayoutParams(-1,-1).apply { bottomMargin=28 })
        content.addView(dots, FrameLayout.LayoutParams(-1,28,Gravity.BOTTOM))
        content.addView(status,FrameLayout.LayoutParams(-1,-1))
        root.addView(content)
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutSafeArea() }
        setContentView(root)
        @Suppress("DEPRECATION")
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(reply,IntentFilter("com.abrar.voyahiran.DRAWER_RESULT").apply {addAction("theme_loaded")},Context.RECEIVER_EXPORTED)
        else registerReceiver(reply,IntentFilter("com.abrar.voyahiran.DRAWER_RESULT").apply {addAction("theme_loaded")})
        pager.onPage={ updateDots() }
        onBackPressedDispatcher.addCallback(this, object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(pager.page>0)pager.show(pager.page-1) }
        })
        applyTheme()
    }
    @Suppress("DEPRECATION") private fun layoutSafeArea() {
        val real=Point();windowManager.defaultDisplay.getRealSize(real)
        val location=IntArray(2);root.getLocationOnScreen(location)
        val left=(145f*real.x/1920-location[0]).toInt().coerceAtLeast(0)
        val top=(50f*real.y/720-location[1]).toInt().coerceAtLeast(0)
        val old=content.layoutParams as FrameLayout.LayoutParams
        if(old.leftMargin!=left || old.topMargin!=top || old.width!=-1) {
            content.layoutParams=FrameLayout.LayoutParams(-1,-1).apply {leftMargin=left;topMargin=top}
        }
    }
    override fun onResume() {
        super.onResume()
        if(!allowed()){finish();return}
        applyTheme();refresh()
        guard=lifecycleScope.launch {
            while(isActive) {
                delay(1000)
                if(!allowed()){finish();break}
                if(light!=LauncherTheme.isLight()){applyTheme();render();refresh()}
            }
        }
    }
    override fun onPause(){guard?.cancel();guard=null;super.onPause()}
    private fun applyTheme() {
        light=LauncherTheme.isLight()
        LauncherWindowTheme.apply(window,light)
        root.setBackgroundColor(LauncherWindowTheme.background(light))
        status.setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
        updateDots()
    }
    private suspend fun request(action:String,subtype:String):JSONObject {
        check(allowed()) { "Launcher unavailable" }
        val nonce=UUID.randomUUID().toString().replace("-","")
        val result=CompletableDeferred<JSONObject>();pending[nonce]=result
        try {
            val sent=withContext(Dispatchers.IO) { ProcessCommandRunner().run(listOf("su","0","launcher-drawer",action,nonce,subtype),5) }
            check(sent.exitCode==0 && !sent.timedOut){sent.output}
            return withTimeout(8000) { result.await() }.also { check(!it.has("error")){it.optString("error")} }
        } finally {pending.remove(nonce)}
    }
    private fun refresh() {
        refreshJob?.cancel()
        refreshJob=lifecycleScope.launch {
            try {
                val catalog=request("catalog","all")
                val fresh=withContext(Dispatchers.IO) {
                    val oem=catalog.getJSONArray("items")
                    val rows=mutableListOf<Entry>()
                    for(i in 0 until oem.length()) {
                        val item=oem.getJSONObject(i);val id=item.getString("subtype")
                        if(!LauncherAppsPolicy.visible(id))continue
                        val bytes=android.util.Base64.decode(item.optString("icon"),android.util.Base64.DEFAULT)
                        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                        rows+=Entry(id,item.getString("label"),bitmap?.let {BitmapDrawable(resources,it)})
                    }
                    val installed=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                        .filter { it.activityInfo.enabled && it.activityInfo.exported && it.activityInfo.applicationInfo.enabled &&
                            (it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0 }
                        .distinctBy { ComponentName(it.activityInfo.packageName,it.activityInfo.name) }
                        .map { Entry(it.activityInfo.packageName+"/"+it.activityInfo.name,it.loadLabel(packageManager).toString(),it.loadIcon(packageManager),ComponentName(it.activityInfo.packageName,it.activityInfo.name)) }
                        .sortedBy { it.label.lowercase() }
                    rows+installed
                }
                if(!allowed())return@launch
                entries=fresh;status.visibility=View.GONE;render()
                AppLog.info("LauncherApps","loaded total=${entries.size} pages=${LauncherAppsPolicy.pages(entries.size)} light=$light")
            } catch(e:CancellationException){throw e}
            catch(e:Exception){
                AppLog.error("LauncherApps","catalog failed",e)
                if(entries.isEmpty()){status.text="دریافت برنامه‌ها انجام نشد؛ برای تلاش مجدد لمس کنید";status.visibility=View.VISIBLE}
            }
        }
    }
    private fun render() {
        val selected=pager.page
        pager.removeAllViews()
        for(pageIndex in 0 until LauncherAppsPolicy.pages(entries.size)) {
            val page=object:FrameLayout(this@LauncherAppsActivity) {
                override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
                    val cellW=width.toFloat()/9;val cellH=height.toFloat()/3
                    for(i in 0 until childCount) {
                        val x=(i%9*cellW).toInt();val y=(i/9*cellH).toInt()
                        getChildAt(i).layout(x,y,(x+cellW).toInt(),(y+cellH).toInt())
                    }
                }
                override fun onMeasure(w:Int,h:Int) {
                    super.onMeasure(w,h)
                    for(i in 0 until childCount)getChildAt(i).measure(View.MeasureSpec.makeMeasureSpec(measuredWidth/9,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(measuredHeight/3,View.MeasureSpec.EXACTLY))
                }
            }
            entries.drop(pageIndex*27).take(27).forEach { entry ->
                val tile=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isFocusable=true;isClickable=true
                    contentDescription=entry.label
                    val value=android.util.TypedValue();theme.resolveAttribute(android.R.attr.selectableItemBackground,value,true);setBackgroundResource(value.resourceId)
                }
                val size=(112f*resources.displayMetrics.widthPixels/1920).toInt().coerceAtLeast(56)
                tile.addView(ImageView(this).apply {setImageDrawable(entry.icon);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(size,size))
                tile.addView(TextView(this).apply {
                    text=entry.label
                    if(entry.label.any {it in '\u0600'..'\u06ff'})typeface=resources.getFont(com.abrar.raha.feedback.R.font.iransansx_regular)
                    setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(25f*resources.displayMetrics.widthPixels/1920).coerceAtLeast(14f))
                    maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER
                    setPadding(5,14,5,0)
                },LinearLayout.LayoutParams(-1,-2))
                tile.setOnClickListener { open(entry) };page.addView(tile)
            }
            pager.addView(page,FrameLayout.LayoutParams(-1,-1))
        }
        pager.show(selected,false);updateDots()
    }
    private fun updateDots() {
        if(!::dots.isInitialized)return
        dots.removeAllViews()
        val count=LauncherAppsPolicy.pages(entries.size)
        if(count<=1)return
        repeat(count){index ->
            dots.addView(View(this).apply {
                background=android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius=5f;setColor(if(index==pager.page)0xff429bff.toInt() else if(light)0xffbac8d9.toInt() else 0xff566276.toInt())
                }
                contentDescription="Page ${index+1}";setOnClickListener {pager.show(index)}
            },LinearLayout.LayoutParams(if(index==pager.page)28 else 8,5).apply {setMargins(7,0,7,0)})
        }
    }
    private fun open(entry:Entry) {
        if(opening || !allowed())return
        opening=true
        lifecycleScope.launch {
            try {
                if(entry.component==null)request("open",entry.id)
                else {
                    val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    if(entry.component.packageName==packageName)intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent,ActivityOptions.makeBasic().apply {launchDisplayId=0}.toBundle())
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){AppLog.error("LauncherApps","open ${entry.id} failed",e);Toast.makeText(this@LauncherAppsActivity,"اجرای برنامه انجام نشد",Toast.LENGTH_SHORT).show()}
            finally {opening=false}
        }
    }
    override fun onDestroy(){unregisterReceiver(reply);pending.values.forEach {it.cancel()};pending.clear();super.onDestroy()}
}
