package com.abrar.voyahiran.feature.launcher

import com.abrar.voyahiran.core.bridge.BridgeClient
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** CanBusTool.setVehicleStateBack/backAfterThree: immediate UI, callback, timed readback.
 * Shared by both displays. Never replay a command whose remote outcome is unknown.
 */
internal object LauncherControlCommands {
    const val FEEDBACK_MS=3000L
    private val writes=Mutex()
    fun launch(scope:CoroutineScope,args:List<String>,pending:()->Boolean,finish:()->Unit,
               refresh:(JSONObject)->Unit,changed:()->Unit,failed:()->Unit,
               allowed:()->Boolean,dispatching:()->Unit,timeout:Long=FEEDBACK_MS) {
        scope.launch {
            val deadline=android.os.SystemClock.elapsedRealtime()+timeout
            val expiry=launch {
                delay(timeout)
                if(pending()) {
                    // One explicit OEM read at timeout, not another periodic polling loop.
                    val r=withContext(Dispatchers.IO){BridgeClient.run(listOf("launcher-vehicle","refresh"),4)}
                    if(r.exitCode==0 && !r.timedOut)runCatching{refresh(JSONObject(r.output))}
                    if(pending()) {
                        AppLog.info("LauncherControl","feedback timeout ${args.take(2)}")
                        finish();changed();failed()
                    }
                }
            }
            try {
                writes.withLock {
                    while(pending() && allowed() && android.os.SystemClock.elapsedRealtime()<deadline) {
                        dispatching()
                        val r=withContext(Dispatchers.IO){BridgeClient.run(args,12)}
                        AppLog.info("LauncherControl","${args.take(2)} exit=${r.exitCode} timeout=${r.timedOut} detail=${r.output.take(300)}")
                        // 75 explicitly guarantees no command executed. Socket errors do not.
                        if(r.exitCode==75 && !r.timedOut){delay(120);continue}
                        break
                    }
                }
                // Transport completion is not CAN confirmation; leave the overlay until
                // feedback or the OEM-style readback deadline, even if the reply was lost.
                expiry.join()
            } finally {expiry.cancel();finish();changed()}
        }
    }
}
