package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import com.abrar.voyahiran.core.shell.CommandResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Authenticated, preview-only input. Never falls back to the focused/central display. */
internal object LauncherMapTouchBridge {
    private var last:MotionEvent?=null
    private var display=-1
    private var touchedAt=0L
    private val managerType=Class.forName("android.hardware.input.InputManager")
    private val manager by lazy {managerType.getMethod("getInstance").invoke(null)}
    init {
        Executors.newSingleThreadScheduledExecutor { r->Thread(r,"MapTouchExpiry").apply {isDaemon=true} }
            .scheduleWithFixedDelay({ synchronized(this) { if(last!=null && SystemClock.uptimeMillis()-touchedAt>2000) cancel() } },500,500,TimeUnit.MILLISECONDS)
    }
    private fun inject(event:MotionEvent):Boolean = managerType.getMethod("injectInputEvent",InputEvent::class.java,Int::class.javaPrimitiveType)
        .invoke(manager,event,0)==true
    private fun cancel() {
        last?.let { old ->
            runCatching {MotionEvent.obtain(old).also {it.action=MotionEvent.ACTION_CANCEL;try {inject(it)} finally {it.recycle()}}}
            old.recycle()
        }
        last=null;display=-1
    }
    @Synchronized fun execute(args:List<String>):CommandResult = try {
        check(android.os.Build.VERSION.SDK_INT>=29) {"Display-targeted touch unsupported on this Android version"}
        check(BridgeSubscriptionSession.vipActive) {"Launcher inactive"}
        val target=args[2].toInt();val packet=checkNotNull(MapTouchPacket.parse(args[3]))
        require(SystemClock.uptimeMillis()-packet.time in 0..1500) {"Expired touch"}
        val global=Class.forName("android.hardware.display.DisplayManagerGlobal")
        val info=checkNotNull(global.getMethod("getDisplayInfo",Int::class.javaPrimitiveType).invoke(global.getMethod("getInstance").invoke(null),target))
        check(target>1 && info.javaClass.getField("name").get(info)=="VoyahIran.MapPreview" &&
            info.javaClass.getField("ownerPackageName").get(info)=="com.abrar.voyahiran") {"Not a launcher map display"}
        val apiClass=Class.forName("android.app.IActivityTaskManager")
        val api=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        val stacks=apiClass.getMethod("getAllStackInfos").invoke(api) as List<*>
        check(stacks.filterNotNull().any { it.javaClass.getField("displayId").getInt(it)==target &&
            it.javaClass.getField("visible").getBoolean(it) &&
            (it.javaClass.getField("topActivity").get(it) as? android.content.ComponentName)?.packageName==args[1] }) {"Map left preview"}
        if(packet.action==MotionEvent.ACTION_DOWN) cancel()
        else check(last!=null && display==target && last!!.downTime==packet.down) {"No matching gesture"}
        val width=info.javaClass.getField("logicalWidth").getInt(info)
        val height=info.javaClass.getField("logicalHeight").getInt(info)
        val properties=packet.points.map {p->MotionEvent.PointerProperties().apply {id=p.id;toolType=MotionEvent.TOOL_TYPE_FINGER}}.toTypedArray()
        val coords=packet.points.map {p->MotionEvent.PointerCoords().apply {x=p.x*(width-1);y=p.y*(height-1);pressure=1f;size=1f}}.toTypedArray()
        val event=MotionEvent.obtain(packet.down,packet.time,packet.action,properties.size,properties,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
        try {
            // Mandatory targeting: if the API is absent, fail closed, never inject on display 0.
            InputEvent::class.java.getMethod("setDisplayId",Int::class.javaPrimitiveType).invoke(event,target)
            check(inject(event)) {"Input rejected"}
            last?.recycle();last=null;display=target;touchedAt=SystemClock.uptimeMillis()
            if(event.actionMasked !in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL))last=MotionEvent.obtain(event)
        } finally {event.recycle()}
        CommandResult(0,"Map touch accepted")
    } catch(e:Exception) {cancel();CommandResult(1,"Map touch: ${e.cause?.message ?: e.message}")}
}
