package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.*

/** Original CCC frames; decoded off the UI thread, bounded cache rather than 644 resident bitmaps. */
class LauncherSuspensionArtwork(private val context:Context,private val changed:()->Unit) {
    // One full 20-frame wheel cycle is ~14 MiB; keep it resident to avoid decoding every lap.
    private val cache=object:LruCache<String,Bitmap>(20*1024*1024) {override fun sizeOf(key:String,value:Bitmap)=value.byteCount}
    private var job:Job?=null
    var state=LauncherVehicleState()
    var light=false
    var body:Bitmap?=null;private set
    var wheels:List<Bitmap> = emptyList();private set
    private var frame:Int?=null
    private suspend fun load(name:String):Bitmap?=withContext(Dispatchers.IO) {
        cache.get(name) ?: runCatching {context.assets.open("launcher/ccc/$name.png").use(BitmapFactory::decodeStream)}.getOrNull()?.also {cache.put(name,it)}
    }
    fun start(scope:CoroutineScope) {
        if(job?.isActive==true)return
        job=scope.launch {
            var lastKey=""
            val wheelTypes=MutableList(4){"static"};val wheelSteps=IntArray(4);val lastZones=IntArray(4)
            while(isActive) {
                val start=SystemClock.elapsedRealtime()
                val s=state;val theme=if(light)"light" else "dark"
                val target=s.bodyFrame
                if(frame==null)frame=target
                val moving=target!=null && frame!=target
                if(moving) frame=frame!!+if(target!!>frame!!)1 else -1
                val driving=!moving && s.fresh && (s.speed ?: 0)>3 && s.sensorError==0 && s.height==5
                if(driving) {
                    val names=listOf("fr","fl","rl","rr").mapIndexed { i,corner ->
                        val zone=when {s.wheels[i]==null -> 0;s.wheels[i]!!>=150 -> 1;s.wheels[i]!!<=-150 -> -1;else -> 0}
                        if(zone!=lastZones[i]) {lastZones[i]=zone;wheelTypes[i]=when(zone){1->"down";-1->"up";else->"static"};wheelSteps[i]=0}
                        "suspension_${theme}_${wheelTypes[i]}_${corner}_${wheelSteps[i]}"
                    }
                    val loaded=names.map {load(it)}
                    if(loaded.all {it!=null})wheels=loaded.filterNotNull()
                    for(i in 0..3) {wheelSteps[i]++;if(wheelSteps[i]>=20){wheelSteps[i]=0;wheelTypes[i]="static"}}
                    changed()
                } else {
                    val hadWheels=wheels.isNotEmpty()
                    wheels=emptyList()
                    val key="body_${theme}_${frame ?: 60}"
                    if(key!=lastKey) {load(key)?.let {body=it};lastKey=key;changed()}
                    else if(hadWheels)changed()
                }
                delay((if(moving)10L else if(driving)50L else 100L)-(SystemClock.elapsedRealtime()-start).coerceAtMost(if(moving)9L else if(driving)49L else 99L))
            }
        }
    }
    fun stop() {job?.cancel();job=null;wheels=emptyList()}
    fun close() {stop();cache.evictAll();body=null}
}
