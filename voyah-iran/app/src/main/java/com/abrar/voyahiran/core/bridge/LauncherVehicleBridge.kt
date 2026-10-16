package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.Binder
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.drivememory.data.OemDriveModeWriter
import com.abrar.voyahiran.feature.voice.EnergyStrategyPolicy
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File

/** Bounded, symbolic operations; the factory drive service retains its ECU interlocks and mode bundle. */
internal object LauncherVehicleBridge {
    private var cached: Session? = null
    private val leaseTimer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r,"LauncherCanLease").apply {isDaemon=true} }
    init { leaseTimer.scheduleWithFixedDelay({
        synchronized(this) {
            cached?.takeIf {SystemClock.elapsedRealtime()-it.lastRead>10000}?.let {it.close();cached=null}
        }
    },5,5,java.util.concurrent.TimeUnit.SECONDS) }
    private fun session(): Session = synchronized(this) {cached?.takeIf { it.binder.isBinderAlive } ?: run {
            cached?.close();cached=null
            Session().also { it.start();cached=it }
        }}
    /** Share the existing CAN subscription; no second speed reader or full dashboard poll. */
    internal fun autoMapSpeed(): Int? = try {
        check(BridgeSubscriptionSession.vipActive) { "VIP session required" }
        session().cachedSpeed()
    } catch(e:Exception) {
        AppLog.changed("ClusterMapAuto", "Speed unavailable: ${e.message}")
        null
    }
    fun execute(op: String, value: String = ""): CommandResult = try {
        val s=session()
        if(op in listOf("read","watch","refresh")) {
            if(op=="refresh")s.refresh()
            CommandResult(0,s.readAll(if(op=="watch")value.toLongOrNull() else null).toString())
        } else {
            check(BridgeSubscriptionSession.active) { "Subscription inactive" }
            when(op) {
                "energy" -> OemEnergyBinder.dispatchStrategy(value)
                "recovery" -> {
                    check(s.read("HUM_ENERGY_PTREGEN_LEVL_FB") in 2..4) { "Recovery unavailable" }
                    s.write("HUM_ENERGY_PTREGEN_LEVL",value.toInt().also { require(it in 2..4) })
                }
                "height", "drive" -> {
                    check(s.read("DRIVING_MODE_SET") in 1..6) { "Driving mode unavailable" }
                    if(op == "height") {
                        check(s.flag("isSupportAsc")) { "Air suspension not equipped" }
                        check(s.read("ASC_MODE_SELECT") in 1..9 && s.read("ASC_MAINTAIN_SWITCH") in 0..1 && s.read("ASC_ADJUST_SUSTEMP") in listOf(0,2)) { "Suspension adjustment unavailable" }
                    } else check(s.read("ASC_MAINTAIN_SWITCH") != 2) { "Suspension maintenance active" }
                    if(op == "height" && value == "0") {
                        check(s.read("MANUAL_EASY_ENTER_SET") in 1..2) { "Easy entry unavailable" }
                        s.write("MANUAL_EASY_ENTER_SET",2)
                    } else {
                        val mode=if(op == "drive") value.toInt().also { require(it in 1..6) } else when(value) {
                            "1" -> 3; "2" -> s.read("DRIVING_MODE_SET").takeIf { it in listOf(1,2,5,6) } ?: 2
                            "3" -> 4; else -> error("Invalid height")
                        }
                        check(mode != 4 || s.speed() in 0..39) { "Outing requires speed below 40 km/h" }
                        check(mode != 4 || s.flag("isSupportOffload")) { "Outing not supported" }
                        val r=ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/am")+OemDriveModeWriter.arguments(mode).drop(1),8)
                        check(r.exitCode==0 && !r.timedOut && !r.output.contains("Error",true)) { r.output }
                    }
                }
                else -> error("Unknown launcher operation")
            }
            AppLog.info("LauncherVehicle","requested $op=$value; awaiting vehicle feedback")
            CommandResult(0,"DISPATCHED")
        }
    } catch(e:Exception) {
        synchronized(this) {cached?.close();cached=null}
        AppLog.error("LauncherVehicle","$op failed",e)
        CommandResult(1,(e.cause ?: e).toString())
    }
    private class Session {
        private val generation=SystemClock.elapsedRealtimeNanos()
        private val apk=File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        private val loader=object:PathClassLoader(apk.path,"/system/lib64:/system/lib",javaClass.classLoader) {
            override fun loadClass(name:String,resolve:Boolean):Class<*> {
                if(!name.startsWith("com.qinggan.canbus.") && !name.startsWith("com.qinggan.vehicle.")) return super.loadClass(name,resolve)
                synchronized(this) { val c=findLoadedClass(name) ?: findClass(name);if(resolve)resolveClass(c);return c }
            }
        }
        private val type=loader.loadClass("com.qinggan.canbus.VehicleState")
        private val facade=loader.loadClass("com.qinggan.canbus.DFVehicleState")
        private val api=loader.loadClass("com.qinggan.canbus.ICanBusService")
        val binder=Class.forName("android.os.ServiceManager").getMethod("getService",String::class.java).invoke(null,"qg.canbus") as IBinder
        private val remote=loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub").getMethod("asInterface",IBinder::class.java).invoke(null,binder)
        private val get=api.getMethod("getVehicleState",type)
        private val fields=HashMap<String,Any>()
        private val configType by lazy {loader.loadClass("com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceConfigs")}
        private val config by lazy {configType.getConstructor().newInstance()}
        fun flag(name:String)=configType.getMethod(name).invoke(config) as Boolean
        init { check(apk.isFile && binder.interfaceDescriptor==api.name) }
        private fun field(name:String)=synchronized(fields){fields.getOrPut(name) { (try {facade.getField(name)} catch(_:NoSuchFieldException){type.getField(name)}).get(null)!! }}
        fun read(name:String)=(get.invoke(remote,field(name)) as Number).toInt()
        fun speed()=(api.getMethod("getVehicleSpeed").invoke(remote) as Number).toInt()
        fun write(name:String,value:Int) {
            val result=api.getMethod("setVehicleState",type,Int::class.javaPrimitiveType).invoke(remote,field(name),value)
            check(result !is Number || result.toInt()>=0) { "Vehicle rejected $name" }
        }
        private val modes by lazy {
            runCatching {
                val t=configType
                if(t.methods.none { it.name=="isDoFengH97acd" }) EnergyStrategyPolicy.legacyModes(flag("isSupportExtends"))
                else EnergyStrategyPolicy.modes(flag("isSupportExtends"),flag("isDoFengH97acd"),
                    if(t.methods.any {it.name=="is97XOversea"}) flag("is97XOversea") else false)
            }.getOrElse { AppLog.error("LauncherVehicle","Energy capabilities unavailable",it);emptyMap() }
        }
        private val names=listOf("DRIVING_MODE_SET","ASC_MODE_SELECT","ASC_ADJUST_DIREACT","ASC_MAINTAIN_SWITCH","ASC_ADJUST_SUSTEMP",
            "HUM_ENERGY_PTREGEN_LEVL_FB","IVI_SOC_MODESET","AMBIENT_AIR_PRESSURE","ASC_SENSOR_ERROR_STS",
            "ASC_RF_HEIGHT","ASC_LF_HEIGHT","ASC_LR_HEIGHT","ASC_RR_HEIGHT","MANUAL_EASY_ENTER_SET") +
            listOf("FCM_SW_REQ_FB","IVI_FRAG_TASTE_FB","IVI_FRAG_CONCERNTION_FB","FCM_DURATION_CONTROL") +
            listOf("LEFT","RIGHT").flatMap { side -> listOf("HEATING_COMMAND","VENTILATION_COMMAND","MASS_INTEN","MASS_COMMAND",
                "HEATING_SWITCH","VENTILATION_SWITCH","MASS_SWITCH").map { "FRONT_SEAT_${it}_$side" } }
        private val airGetters=listOf("getAirLeftTemperature","getAirRightTemperature","getAirWindSpeed","getAirSWStatus",
            "getAirACStatus","getAirMode","getAirDUALStatus","getAirFrontWindowDefogger","getAirRearWindowDefogger","getAirCirculationMode","getAirSupplyStatus")
        private fun acceptAir(air:Any?, initial:Boolean=false) {
            if(air==null)return
            val snapshot=HashMap<String,Int>()
            for(name in airGetters) {
                val value=runCatching {val n=air.javaClass.getMethod(name).invoke(air) as Number
                    if(name.endsWith("Temperature"))(n.toDouble()*10).toInt() else n.toInt()}.getOrNull() ?: continue
                snapshot[name]=value
            }
            synchronized(lock) {
                var changed=false
                for((name,value) in snapshot)if((!initial || !values.containsKey(name)) && values[name]!=value){values[name]=value;changed=true}
                if(changed){revision++;lock.notifyAll()}
            }
        }
        private val values=HashMap<String,Int?>()
        private val lock=java.lang.Object()
        private var revision=0L
        private fun update(name:String,value:Int) {synchronized(lock) {if(values[name]!=value || !values.containsKey(name)){values[name]=value;revision++;lock.notifyAll()}}}
        @Volatile var lastRead=SystemClock.elapsedRealtime();private set
        private val callbackType=loader.loadClass("com.qinggan.canbus.ICanBusServiceCallback")
        private val callbackStub=loader.loadClass("com.qinggan.canbus.ICanBusServiceCallback\$Stub")
        private fun transaction(name:String)=callbackStub.getDeclaredField("TRANSACTION_$name").apply {isAccessible=true}.getInt(null)
        private val stateCode=transaction("onVehicleStateChanged")
        private val speedCode=transaction("onVehicleSpeedChanged")
        private val airCode=runCatching {transaction("onAirConditionChanged")}.getOrDefault(-1)
        private val airCreator=runCatching {loader.loadClass("com.qinggan.canbus.AirCondition").getField("CREATOR").get(null) as Parcelable.Creator<*>}.getOrNull()
        private val creator=type.getField("CREATOR").get(null) as Parcelable.Creator<*>
        private val watched by lazy {names.mapNotNull { n -> runCatching {field(n) to n}.getOrNull() }.toMap()}
        @Volatile private var listening=false
        private val death=IBinder.DeathRecipient {listening=false;synchronized(lock){lock.notifyAll()}}
        private val callbackBinder=object:Binder() {
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                if(code==IBinder.INTERFACE_TRANSACTION) {reply?.writeString(callbackType.name);return true}
                data.enforceInterface(callbackType.name)
                if(listening) when(code) {
                    stateCode -> {
                        val state=if(data.readInt()!=0)creator.createFromParcel(data) else null
                        val value=data.readInt()
                        watched[state]?.let {name->update(name,value)}
                    }
                    speedCode -> {val value=data.readInt();update("speed",value)}
                    airCode -> acceptAir(if(data.readInt()!=0)airCreator?.createFromParcel(data) else null)
                }
                // Other OEM callbacks are intentionally ignored; never query or write from Binder threads.
                reply?.writeNoException()
                return true
            }
        }
        private val callback=callbackStub.getMethod("asInterface",IBinder::class.java).invoke(null,callbackBinder)
        fun start() {
            watched // Resolve symbols before accepting callbacks.
            listening=true
            try {
                binder.linkToDeath(death,0)
                check(api.getMethod("addCallback",callbackType).invoke(remote,callback) == true) {"CAN callback registration rejected"}
                for(n in names+"speed") {
                    val value=runCatching {if(n=="speed")speed() else read(n)}.getOrNull()
                    // A callback received during the initial read wins over an older snapshot.
                    synchronized(lock){if(!values.containsKey(n))values[n]=value}
                }
                runCatching {acceptAir(api.getMethod("getAirCondition").invoke(remote),true)}
                AppLog.info("LauncherVehicle","CAN listener attached; initial snapshot loaded")
            } catch(e:Exception) {close();throw e}
        }
        fun close() {
            val wasListening=listening
            listening=false
            synchronized(lock){lock.notifyAll()}
            runCatching {binder.unlinkToDeath(death,0)}
            if(wasListening)runCatching {api.getMethod("removeCallback",callbackType).invoke(remote,callback)}
        }
        fun cachedSpeed():Int? = synchronized(lock) {
            check(binder.isBinderAlive && listening) {"CAN listener disconnected"}
            lastRead=SystemClock.elapsedRealtime()
            values["speed"]?.takeIf {it in 0..300}
        }
        /** One-shot readback for the OEM three-second fallback, never a refresh loop. */
        fun refresh() {
            for(n in names+"speed") {
                val before=synchronized(lock){revision}
                val value=runCatching{if(n=="speed")speed() else read(n)}.getOrNull() ?: continue
                synchronized(lock){
                    // A callback during the binder read is newer and must win.
                    if(revision==before && values[n]!=value){values[n]=value;revision++;lock.notifyAll()}
                }
            }
            val before=synchronized(lock){revision}
            val air=runCatching{api.getMethod("getAirCondition").invoke(remote)}.getOrNull()
            synchronized(lock){if(revision==before)acceptAir(air)}
        }
        fun readAll(after:Long?=null):JSONObject {
            check(binder.isBinderAlive && listening) {"CAN listener disconnected"}
            lastRead=SystemClock.elapsedRealtime()
            return JSONObject().apply {
                synchronized(lock) {
                    if(after!=null && after==revision)lock.wait(2000)
                    check(listening && binder.isBinderAlive) {"CAN listener disconnected"}
                    for(n in names+airGetters+"speed")put(n,values[n] ?: JSONObject.NULL)
                    put("revision",revision)
                    put("session",generation)
                }
                put("modes",JSONObject(modes))
                put("supportsSuspension",runCatching{flag("isSupportAsc")}.getOrDefault(false))
                put("supportsOuting",runCatching{flag("isSupportOffload")}.getOrDefault(false))
            }
        }
    }
}
