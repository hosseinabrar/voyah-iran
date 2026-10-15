package com.abrar.voyahiran.feature.launcher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.DragEvent
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
open class LauncherAppsActivity : ComponentActivity() {
    protected open val launcherDisplayId: Int = 0

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var pager: LauncherPager
    private lateinit var dots: LinearLayout
    private lateinit var status: TextView
    private lateinit var loading: LauncherLoadingView
    private var light = false
    private var entries = emptyList<LauncherAppsEntry>()
    private var refreshJob: Job? = null
    private var catalogDirty = true
    private var resumed = false
    private var unobserveCatalog:(()->Unit)?=null
    private var renderedLight:Boolean?=null
    private var renderedEntries:List<LauncherAppsEntry>?=null
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action==Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return
            catalogDirty=true
            AppLog.info("LauncherApps","catalog invalidated action=${intent?.action}")
            root.post { if(allowed())refresh() }
        }
    }
    private var guard: Job? = null
    private var opening = false
    private var actions: LauncherAppActions? = null
    private var actionBusy = false
    private val badges=mutableListOf<View>()
    private var editing = false
    private var draggedId: String? = null
    private var dropIndex = -1
    private var edgeDirection = 0
    private var deferredEntries: List<LauncherAppsEntry>? = null
    private val wobblers = mutableListOf<ObjectAnimator>()
    private lateinit var done: TextView
    private val orderPrefs by lazy { getSharedPreferences("launcher_app_order", MODE_PRIVATE) }
    private val edgeTurn = object : Runnable {
        override fun run() {
            if (draggedId == null || edgeDirection == 0) return
            pager.show(pager.page + edgeDirection)
            highlightDrop((pager.page*27+if(edgeDirection<0)8 else 0).coerceAtMost(entries.lastIndex))
            pager.postDelayed(this, 850)
        }
    }
    private val pending = mutableMapOf<String, CompletableDeferred<JSONObject>>()
    private val reply = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action=="theme_loaded") {
                catalogDirty=true
                root.post { if(allowed()){applyTheme();refresh()} }
                return
            }
            val nonce = intent?.getStringExtra("nonce") ?: return
            val waiting = pending.remove(nonce) ?: return
            val text = intent.getStringExtra("payload").orEmpty()
            if (text.length > 350000) { waiting.completeExceptionally(IllegalArgumentException("Catalog too large")); return }
            runCatching { JSONObject(text) }.fold(waiting::complete, waiting::completeExceptionally)
        }
    }
    @Suppress("DEPRECATION")
    private fun allowed() = LauncherPreferences.enabled(this) &&
        (application as VoyahIranApplication).subscriptions.vipAllowed && (window.decorView.display?.displayId ?: windowManager.defaultDisplay.displayId) == launcherDisplayId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.info("LauncherApps","create display=$launcherDisplayId instance=${System.identityHashCode(this)}")
        root = FrameLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        content = FrameLayout(this)
        pager = LauncherPager(this)
        dots = LinearLayout(this).apply { gravity=Gravity.CENTER }
        status = TextView(this).apply { gravity=Gravity.CENTER; textSize=20f; typeface=resources.getFont(com.abrar.raha.feedback.R.font.iransansx_regular); visibility=View.GONE; setOnClickListener { refresh() } }
        loading=LauncherLoadingView(this)
        content.addView(pager, FrameLayout.LayoutParams(-1,-1).apply { bottomMargin=28 })
        content.addView(dots, FrameLayout.LayoutParams(-1,28,Gravity.BOTTOM))
        content.addView(status,FrameLayout.LayoutParams(-1,-1))
        content.addView(loading,FrameLayout.LayoutParams(-1,-1))
        done = TextView(this).apply {
            text="Done"; gravity=Gravity.CENTER; visibility=View.GONE
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24f); isClickable=true; isFocusable=true
            setTextColor(0xff429bff.toInt()); setOnClickListener { finishEditing() }
        }
        content.addView(done, FrameLayout.LayoutParams(136,48,Gravity.RIGHT or Gravity.BOTTOM).apply {rightMargin=20;bottomMargin=8})
        pager.setOnDragListener { _, event -> handleDrag(event) }
        content.setOnClickListener { if(editing)finishEditing() }
        root.addView(content)
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutSafeArea() }
        setContentView(root)
        @Suppress("DEPRECATION")
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(reply,IntentFilter("com.abrar.voyahiran.DRAWER_RESULT").apply {addAction("theme_loaded")},Context.RECEIVER_EXPORTED)
        else registerReceiver(reply,IntentFilter("com.abrar.voyahiran.DRAWER_RESULT").apply {addAction("theme_loaded")})
        val packageFilter=IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED);addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED);addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(packageChanges,packageFilter,Context.RECEIVER_EXPORTED)
        else registerReceiver(packageChanges,packageFilter)
        pager.onPage={ updateDots() }
        onBackPressedDispatcher.addCallback(this, object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(editing)finishEditing() else if(pager.page>0)pager.show(pager.page-1) }
        })
        applyTheme()
        unobserveCatalog=LauncherAppsCache.observe(this) { fresh ->
            if(allowed()) {
                if(draggedId!=null)deferredEntries=fresh else {entries=ordered(fresh);render(false)}
                catalogDirty=false
                loading.visibility=View.GONE
                status.visibility=View.GONE
            } else catalogDirty=true
        }
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
        resumed=true
        if(light!=LauncherTheme.isLight()){catalogDirty=true;applyTheme()}
        if(catalogDirty && refreshJob?.isActive!=true)refresh()
        else AppLog.info("LauncherApps","reuse cached layout total=${entries.size} page=${pager.page}")
        guard?.cancel()
        guard=lifecycleScope.launch {
            while(isActive) {
                delay(1000)
                if(!allowed()){finish();break}
                if(draggedId==null && light!=LauncherTheme.isLight()){catalogDirty=true;applyTheme();refresh()}
            }
        }
    }
    override fun onPause(){resumed=false;finishEditing();super.onPause()}
    override fun onConfigurationChanged(config:android.content.res.Configuration) {
        super.onConfigurationChanged(config)
        applyTheme()
        root.post { layoutSafeArea();render() }
    }
    // Android 9 pauses one display when the other gains focus; visible drawers still need the VIP guard.
    override fun onStop(){guard?.cancel();guard=null;super.onStop()}
    private fun applyTheme() {
        actions?.dismiss()
        light=LauncherTheme.isLight()
        LauncherWindowTheme.apply(window,light)
        root.setBackgroundColor(LauncherWindowTheme.background(light))
        loading.light=light
        status.setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
        val shape=android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            if(light)intArrayOf(0xfff5f8ff.toInt(),0xffd6e2f2.toInt()) else intArrayOf(0xff465777.toInt(),0xff283650.toInt())
        ).apply {cornerRadius=14f;setStroke(1,if(light)0xffc2d2e8.toInt() else 0xff5a6c90.toInt())}
        done.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33429bff),shape,null)
        done.setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
        updateDots()
    }
    private suspend fun request(action:String,subtype:String):JSONObject {
        check(allowed()) { "Launcher unavailable" }
        val nonce=UUID.randomUUID().toString().replace("-","")
        val result=CompletableDeferred<JSONObject>();pending[nonce]=result
        try {
            val command=listOf("su","0","launcher-drawer",action,nonce,subtype)+if(launcherDisplayId==0)emptyList() else listOf(launcherDisplayId.toString())
            val sent=withContext(Dispatchers.IO) { ProcessCommandRunner().run(command,5) }
            check(sent.exitCode==0 && !sent.timedOut){sent.output}
            return withTimeout(8000) { result.await() }.also { check(!it.has("error")){it.optString("error")} }
        } finally {pending.remove(nonce)}
    }
    private fun refresh() {
        refreshJob?.cancel()
        if(entries.isEmpty()){status.visibility=View.GONE;loading.visibility=View.VISIBLE}
        catalogDirty=false
        refreshJob=lifecycleScope.launch {
            try {
                val fresh=LauncherAppsCache.get(this@LauncherAppsActivity)
                if(!allowed()){catalogDirty=true;return@launch}
                if(draggedId!=null)deferredEntries=fresh else { entries=ordered(fresh);render(false) }
                status.visibility=View.GONE
                loading.visibility=View.GONE
                AppLog.info("LauncherApps","loaded total=${entries.size} pages=${LauncherAppsPolicy.pages(entries.size)} light=$light")
            } catch(e:CancellationException){throw e}
            catch(e:Exception){
                loading.visibility=View.GONE
                catalogDirty=true
                AppLog.error("LauncherApps","catalog failed",e)
                if(entries.isEmpty()){status.text="دریافت برنامه‌ها انجام نشد؛ برای تلاش مجدد لمس کنید";status.visibility=View.VISIBLE}
            }
        }
    }
    private fun render(force:Boolean=true) {
        if(!force && renderedLight==light && renderedEntries==entries)return
        renderedLight=light;renderedEntries=entries.toList()
        val selected=pager.page
        stopWobble()
        badges.clear()
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
            page.setOnClickListener {if(editing)finishEditing()}
            entries.drop(pageIndex*27).take(27).forEach { entry ->
                val tile=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isFocusable=true;isClickable=true
                    contentDescription=entry.label
                    val value=android.util.TypedValue();theme.resolveAttribute(android.R.attr.selectableItemBackground,value,true);setBackgroundResource(value.resourceId)
                }
                val size=(112f*resources.displayMetrics.widthPixels/1920).toInt().coerceAtLeast(56)
                val iconBox=FrameLayout(this).apply {clipChildren=false;clipToPadding=false}
                iconBox.addView(ImageView(this).apply {setImageDrawable(entry.icon);scaleType=ImageView.ScaleType.FIT_CENTER},FrameLayout.LayoutParams(size,size).apply {leftMargin=22;topMargin=22})
                if(entry.component!=null && entry.component.packageName!=packageName) {
                    val badge=TextView(this).apply {
                        text="⋯";gravity=Gravity.CENTER;contentDescription="Options for ${entry.label}"
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,28f)
                        setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
                        background=android.graphics.drawable.GradientDrawable().apply {shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(if(light)0xfff2f6fc.toInt() else 0xff26364f.toInt());setStroke(1,0xff657995.toInt())}
                        visibility=if(editing)View.VISIBLE else View.GONE
                        isFocusable=true;setOnClickListener {showActions(entry,this)}
                    }
                    iconBox.addView(badge,FrameLayout.LayoutParams(44,44,Gravity.TOP or Gravity.RIGHT))
                    badges+=badge
                }
                tile.addView(iconBox,LinearLayout.LayoutParams(size+44,size+22))
                tile.addView(TextView(this).apply {
                    text=entry.label
                    if(entry.label.any {it in '\u0600'..'\u06ff'})typeface=resources.getFont(com.abrar.raha.feedback.R.font.iransansx_regular)
                    setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(25f*resources.displayMetrics.widthPixels/1920).coerceAtLeast(14f))
                    setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER
                    setPadding(5,14,5,0)
                },LinearLayout.LayoutParams(-1,-2))
                tile.tag=entry.id
                var touchedContent=false
                val beginDrag={
                    if(allowed()) {
                        editing=true;badges.forEach {it.visibility=View.VISIBLE};done.visibility=View.VISIBLE;updateEditSpacing();startWobble()
                        draggedId=entry.id
                        if(!tile.startDragAndDrop(ClipData.newPlainText("app",entry.id),View.DragShadowBuilder(tile),entry.id,0))draggedId=null
                    }
                }
                val quickDrag=Runnable { if(editing && tile.isAttachedToWindow)beginDrag() }
                var downX=0f;var downY=0f
                val slop=android.view.ViewConfiguration.get(this).scaledTouchSlop
                tile.setOnTouchListener { _, event ->
                    if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN) {
                        downX=event.x;downY=event.y
                        if(editing)tile.postDelayed(quickDrag,android.view.ViewConfiguration.getLongPressTimeout()/2L)
                        touchedContent=(0 until tile.childCount).any { index ->
                            val child=tile.getChildAt(index)
                            val halfWidth=if(child is TextView)child.paint.measureText(entry.label).coerceAtMost(child.width.toFloat())/2+8 else child.width/2f
                            event.x>=tile.width/2f-halfWidth && event.x<=tile.width/2f+halfWidth && event.y>=child.top && event.y<=child.bottom
                        }
                    }
                    if(event.actionMasked==android.view.MotionEvent.ACTION_UP || event.actionMasked==android.view.MotionEvent.ACTION_CANCEL ||
                        (event.actionMasked==android.view.MotionEvent.ACTION_MOVE && (kotlin.math.abs(event.x-downX)>slop || kotlin.math.abs(event.y-downY)>slop)))tile.removeCallbacks(quickDrag)
                    false
                }
                tile.setOnClickListener { if(!editing)open(entry) else if(!touchedContent)finishEditing() }
                tile.setOnLongClickListener {
                    tile.removeCallbacks(quickDrag)
                    if(draggedId==null)beginDrag()
                    true
                }
                page.addView(tile)
            }
            pager.addView(page,FrameLayout.LayoutParams(-1,-1))
        }
        pager.show(selected,false);updateDots();if(editing)startWobble()
    }
    private fun ordered(fresh:List<LauncherAppsEntry>):List<LauncherAppsEntry> {
        val saved=runCatching { org.json.JSONArray(orderPrefs.getString("ids","[]")) }.getOrDefault(org.json.JSONArray())
        val ids=(0 until saved.length()).map {saved.getString(it)}
        val byId=fresh.associateBy {it.id}
        return LauncherAppsOrder.merge(ids,fresh.map {it.id}).mapNotNull {byId[it]}
    }
    private fun stopWobble() {
        wobblers.forEach {it.cancel()};wobblers.clear()
        for(p in 0 until pager.childCount) {
            val group=pager.getChildAt(p) as FrameLayout
            for(i in 0 until group.childCount)group.getChildAt(i).rotation=0f
        }
    }
    private fun startWobble() {
        stopWobble()
        for(p in 0 until pager.childCount) {
            val group=pager.getChildAt(p) as FrameLayout
            for(i in 0 until group.childCount) {
                val tile=group.getChildAt(i)
                wobblers+=ObjectAnimator.ofFloat(tile,"rotation",-1.5f,1.5f).apply {
                    duration=170L+(i%3)*25;repeatCount=ValueAnimator.INFINITE
                    repeatMode=ValueAnimator.REVERSE;startDelay=(i%4)*30L;start()
                }
            }
        }
    }
    private fun highlightDrop(index:Int) {
        dropIndex=index
        for(p in 0 until pager.childCount) {
            val group=pager.getChildAt(p) as FrameLayout
            for(i in 0 until group.childCount) {
                val tile=group.getChildAt(i)
                tile.alpha=if(tile.tag==draggedId)0.35f else 1f
                tile.scaleX=if(p*27+i==index)1.06f else 1f
                tile.scaleY=tile.scaleX
            }
        }
    }
    private fun handleDrag(event:DragEvent):Boolean {
        if(event.action==DragEvent.ACTION_DRAG_STARTED)return draggedId!=null && event.localState==draggedId
        if(draggedId==null)return false
        when(event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                val direction=when {event.x<pager.width*.06f -> -1;event.x>pager.width*.94f -> 1;else -> 0}
                if(direction!=edgeDirection) {
                    pager.removeCallbacks(edgeTurn);edgeDirection=direction
                    if(direction!=0)pager.postDelayed(edgeTurn,650)
                }
                val col=(event.x/(pager.width/9f)).toInt().coerceIn(0,8)
                val row=(event.y/(pager.height/3f)).toInt().coerceIn(0,2)
                highlightDrop((pager.page*27+row*9+col).coerceAtMost(entries.lastIndex))
            }
            DragEvent.ACTION_DRAG_EXITED -> {pager.removeCallbacks(edgeTurn);edgeDirection=0;highlightDrop(-1)}
            DragEvent.ACTION_DROP -> {
                if(dropIndex<0)return false
                val ids=LauncherAppsOrder.move(entries.map {it.id},draggedId!!,dropIndex)
                val byId=entries.associateBy {it.id};entries=ids.mapNotNull {byId[it]}
                orderPrefs.edit().putString("ids",org.json.JSONArray(ids).toString()).apply()
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                pager.removeCallbacks(edgeTurn);edgeDirection=0;draggedId=null;dropIndex=-1
                deferredEntries?.let {entries=ordered(it)};deferredEntries=null
                render()
            }
        }
        return true
    }
    private fun updateEditSpacing() {
        (pager.layoutParams as FrameLayout.LayoutParams).let {it.bottomMargin=if(editing)64 else 28;pager.layoutParams=it}
    }
    private fun finishEditing() {
        if(!::pager.isInitialized)return
        if(!editing && draggedId==null && deferredEntries==null){actions?.dismiss();return}
        if(draggedId!=null)pager.cancelDragAndDrop()
        pager.removeCallbacks(edgeTurn);edgeDirection=0;draggedId=null
        actions?.dismiss();badges.forEach {it.visibility=View.GONE}
        editing=false;done.visibility=View.GONE;updateEditSpacing();stopWobble()
        deferredEntries?.let {entries=ordered(it);deferredEntries=null;render()}
        highlightDrop(-1)
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
    private fun showActions(entry:LauncherAppsEntry,anchor:View) {
        if(!editing || actionBusy || !allowed() || entry.component==null || entry.component.packageName==packageName)return
        actions?.dismiss()
        actions=LauncherAppActions(content,light).also { ui ->
            ui.menu(anchor,{performAppAction(entry,false)},{ui.confirm(entry.label) {performAppAction(entry,true)}},{
                if(allowed()) {
                    val intent=Intent(this,com.abrar.voyahiran.ui.MainActivity::class.java)
                        .putExtra("com.abrar.voyahiran.OPEN_PERMISSIONS_PACKAGE",entry.component.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent,ActivityOptions.makeBasic().apply {launchDisplayId=launcherDisplayId}.toBundle())
                }
            })
        }
    }
    private fun performAppAction(entry:LauncherAppsEntry,remove:Boolean) {
        val pkg=entry.component?.packageName ?: return
        if(actionBusy || !allowed() || pkg==packageName)return
        actionBusy=true
        lifecycleScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val info=packageManager.getApplicationInfo(pkg,0)
                    check(info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)==0)
                    ProcessCommandRunner().run(listOf("su","0",if(remove)"pm" else "am",if(remove)"uninstall" else "force-stop","--user","0",pkg),30)
                }
                check(result.exitCode==0 && !result.timedOut && (!remove || result.output.lineSequence().any {it.trim()=="Success"})) {result.output}
                if(remove) {
                    refreshJob?.cancel();deferredEntries=null
                    entries=entries.filter {it.component?.packageName!=pkg}
                    orderPrefs.edit().putString("ids",org.json.JSONArray(entries.map {it.id}).toString()).apply()
                    render();LauncherAppsCache.invalidate();refresh()
                }
                Toast.makeText(this@LauncherAppsActivity,if(remove)"App removed" else "App stopped",Toast.LENGTH_SHORT).show()
            } catch(e:CancellationException){throw e}
            catch(e:Exception) {
                AppLog.error("LauncherApps","app action remove=$remove package=$pkg failed",e)
                Toast.makeText(this@LauncherAppsActivity,"Operation failed. Please try again.",Toast.LENGTH_LONG).show()
                if(remove)refresh()
            } finally {actionBusy=false}
        }
    }
    private fun open(entry:LauncherAppsEntry) {
        if(opening || !allowed())return
        opening=true
        lifecycleScope.launch {
            try {
                if(entry.component==null)request("open",entry.id)
                else if(launcherDisplayId==0 && LauncherMapPreview.openHostedMap(entry.component.packageName)) {
                    // The retained HOME preview owns masking, task transfer and return-to-card state.
                } else {
                    val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    if(entry.component.packageName==packageName)intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent,ActivityOptions.makeBasic().apply {launchDisplayId=launcherDisplayId}.toBundle())
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){AppLog.error("LauncherApps","open ${entry.id} failed",e);Toast.makeText(this@LauncherAppsActivity,"اجرای برنامه انجام نشد",Toast.LENGTH_SHORT).show()}
            finally {opening=false}
        }
    }
    override fun onDestroy(){unobserveCatalog?.invoke();unobserveCatalog=null;finishEditing();unregisterReceiver(reply);unregisterReceiver(packageChanges);pending.values.forEach {it.cancel()};pending.clear();super.onDestroy()}
}
