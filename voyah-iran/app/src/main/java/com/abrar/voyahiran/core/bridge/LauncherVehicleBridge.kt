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
    @Volatile private var cached: Session? = null
    private val audioRevision=java.util.concurrent.atomic.AtomicLong()
    internal fun audioChanged() { audioRevision.incrementAndGet();cached?.externalChange() }
    private val leaseTimer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r,"LauncherCanLease").apply {isDaemon=true} }
    private val pressureTimer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r,"LauncherPressureRecovery").apply {isDaemon=true} }
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
        if(op=="audio") CommandResult(0,LauncherAudioBridge.command(value).toString()) else {
        val s=session()
        if(op=="telemetry") {
            check(BridgeSubscriptionSession.vipActive) { "VIP session required" }
            CommandResult(0,s.telemetry().toString())
        } else if(op in listOf("read","watch","watch-central","watch-passenger","refresh")) {
            if(op=="refresh")s.refresh()
            CommandResult(0,s.readAll(if(op.startsWith("watch"))value.toLongOrNull() else null,op.removePrefix("watch-").takeIf{op.startsWith("watch-")} ?: "all").toString())
        } else {
            check(BridgeSubscriptionSession.active) { "Subscription inactive" }
            when(op) {
                "reset-trip" -> {
                    check(BridgeSubscriptionSession.vipActive) { "VIP session required" }
                    s.write("TRIPA_RESET_REQ",1)
                }
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
        }
    } catch(e:Exception) {
        if(op!="audio") synchronized(this) {cached?.close();cached=null}
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
                var snapshotChanged=false
                for((name,value) in snapshot)if((!initial || !values.containsKey(name)) && values[name]!=value){values[name]=value;snapshotChanged=true}
                if(snapshotChanged)changed("passenger")
            }
        }
        private val values=HashMap<String,Int?>()
        private val lock=java.lang.Object()
        private var revision=0L
        private var centralRevision=0L
        private var passengerRevision=0L
        private fun scopeOf(name:String)=LauncherWatchPolicy.scopeOf(name)
        private fun changed(scope:String){
            revision++
            if(scope=="central")centralRevision=revision else passengerRevision=revision
            lock.notifyAll()
        }
        private fun scopeRevision(scope:String)=when(scope){"central"->centralRevision;"passenger"->passengerRevision;else->revision}
        private val centralNames by lazy {names.filter{scopeOf(it)=="central"}+"speed"}
        private val passengerNames by lazy {names.filter{scopeOf(it)=="passenger"}+airGetters}
        private val supportsSuspension by lazy {runCatching{flag("isSupportAsc")}.getOrDefault(false)}
        private val supportsOuting by lazy {runCatching{flag("isSupportOffload")}.getOrDefault(false)}
        private val pressureRecovery=PressureRecovery(pressureTimer,
            read={if(listening && binder.isBinderAlive)read("AMBIENT_AIR_PRESSURE") else null},
            recovered={value->storeValue("AMBIENT_AIR_PRESSURE",value)})
        private fun storeValue(name:String,value:Int) {synchronized(lock) {if(values[name]!=value || !values.containsKey(name)){values[name]=value;changed(scopeOf(name))}}}
        private fun update(name:String,value:Int) {
            if(name=="AMBIENT_AIR_PRESSURE") synchronized(pressureRecovery) {
                pressureRecovery.observed(value)
                storeValue(name,value)
            } else storeValue(name,value)
        }
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
                    else -> acceptTelemetryCallback(code,data)
                }
                // Other OEM callbacks are intentionally ignored; never query or write from Binder threads.
                reply?.writeNoException()
                return true
            }
        }
        private val callback=callbackStub.getMethod("asInterface",IBinder::class.java).invoke(null,callbackBinder)
        fun start() {
            watched // Resolve symbols before accepting callbacks.
            telemetryCodes;telemetryCreators
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
                synchronized(pressureRecovery) {
                    pressureRecovery.observed(synchronized(lock){values["AMBIENT_AIR_PRESSURE"]})
                }
                AppLog.info("LauncherVehicle","CAN listener attached; initial snapshot loaded")
            } catch(e:Exception) {close();throw e}
        }
        fun close() {
            pressureRecovery.close()
            val wasListening=listening
            listening=false
            synchronized(lock){lock.notifyAll()}
            runCatching {binder.unlinkToDeath(death,0)}
            if(wasListening)runCatching {api.getMethod("removeCallback",callbackType).invoke(remote,callback)}
        }
        private data class TelemetrySource(val getter:String,val event:String,val parcelType:String?=null,val floating:Boolean=false)
        private val telemetrySources=listOf(
            TelemetrySource("getBatteryRemainingCapacity","onBatteryRemainingCapacityChanged",floating=true),
            TelemetrySource("getFuelLevel","onFuelLevelChanged","FuelLevel"),
            TelemetrySource("getOdometer","onOdometerChanged","Odometer"),
            TelemetrySource("getEnergyConsumptionInfo","onEnergyConsumptionInfoChanged","EnergyConsumptionInfo"))
        private val telemetryCodes by lazy {telemetrySources.associateWith {runCatching{transaction(it.event)}.getOrDefault(-1)}}
        private val telemetryCreators by lazy {telemetrySources.associateWith {source->
            source.parcelType?.let {runCatching{loader.loadClass("com.qinggan.canbus.$it").getField("CREATOR").get(null) as Parcelable.Creator<*>}.getOrNull()}
        }}
        private val telemetryValues=HashMap<String,Any?>()
        private val telemetryVersions=HashMap<String,Long>()
        private val telemetryReads=HashMap<String,Long>()
        private val telemetryReadLock=Any()
        private fun telemetryCall(target:Any?,name:String):Any?=runCatching{
            if(target==null)null else (if(target===remote)api else target.javaClass).getMethod(name).invoke(target)
        }.onFailure {
            val cause=(it as? java.lang.reflect.InvocationTargetException)?.targetException ?: it
            AppLog.changed("Telemetry/$name","Read failed: ${cause.javaClass.simpleName}: ${cause.message}")
        }.getOrNull()
        private fun telemetryMember(target:Any?,name:String):Any?=runCatching{target?.javaClass?.getField(name)?.get(target)}.getOrNull()
        private fun acceptTelemetry(source:TelemetrySource,value:Any?,expected:Long?=null) = synchronized(lock) {
            val key=source.getter
            if(expected!=null && (telemetryVersions[key] ?: 0L)!=expected)return@synchronized
            val previous=telemetryValues[key]
            telemetryValues[key]=value
            telemetryVersions[key]=(telemetryVersions[key] ?: 0L)+1
            // Parcelable snapshots may contain changed fields even when the object is reused.
            if(previous!=value || source.parcelType!=null)changed("central")
        }
        private fun acceptTelemetryCallback(code:Int,data:Parcel) {
            val source=telemetrySources.firstOrNull{telemetryCodes[it]==code} ?: return
            val value=if(source.parcelType!=null) {
                if(data.readInt()!=0)telemetryCreators[source]?.createFromParcel(data) else null
            } else if(source.floating)data.readFloat() else data.readInt()
            acceptTelemetry(source,value)
        }
        /** One initial snapshot, then OEM events. Only absent callbacks/missing snapshots are polled. */
        fun telemetry():JSONObject {
            refreshTelemetry()
            return telemetrySnapshot()
        }
        private fun refreshTelemetry(){
            check(binder.isBinderAlive && listening) {"CAN listener disconnected"}
            lastRead=SystemClock.elapsedRealtime()
            synchronized(telemetryReadLock) {
                for(source in telemetrySources) {
                    val now=SystemClock.elapsedRealtime()
                    val version=synchronized(lock){telemetryVersions[source.getter] ?: 0L}
                    val missing=!LauncherTelemetryReadiness.ready(source.getter,telemetrySnapshot())
                    val unsupported=telemetryCodes[source]==-1 || source.parcelType!=null && telemetryCreators[source]==null
                    if((missing || unsupported) && now-(telemetryReads[source.getter] ?: -5000L)>=1000L){
                        telemetryReads[source.getter]=now
                        acceptTelemetry(source,telemetryCall(remote,source.getter),version)
                        val snapshot=telemetrySnapshot()
                        val ready=LauncherTelemetryReadiness.ready(source.getter,snapshot)
                        AppLog.changed("TelemetryState/${source.getter}",if(ready)"Valid snapshot received; using OEM events" else "Waiting for valid vehicle data; retrying every second")
                    }
                }
            }
        }
        private fun telemetrySnapshot():JSONObject=synchronized(lock) {
                val fuel=telemetryValues["getFuelLevel"]
                val trip=telemetryValues["getEnergyConsumptionInfo"]
                val odometer=telemetryValues["getOdometer"]
                JSONObject().apply {
                    fun number(key:String,value:Any?) {
                        val n=(value as? Number)?.toDouble()
                        put(key,n?.takeIf{it.isFinite() && (it>=0 || key=="averagePower" && it>=-20)} ?: JSONObject.NULL)
                    }
                    number("battery",telemetryValues["getBatteryRemainingCapacity"])
                    number("rpm",telemetryValues["getEngineSpeed"])
                    number("totalRange",telemetryMember(odometer,"mCanTravelMileage"))
                    number("batteryRange",telemetryMember(odometer,"mEVCruisingRange"))
                    number("fuel",telemetryCall(fuel,"getPercentage"))
                    number("instantFuel",telemetryMember(fuel,"mInstantaneousFuelConsumption"))
                    val distance=telemetryCall(trip,"getCurrentTripDistance")
                    val minutes=telemetryCall(trip,"getCurrentTripLastingTime")
                    number("distance",distance)
                    number("minutes",minutes)
                    number("averageFuel",LauncherTripFuel.average(telemetryCall(trip,"getCurrentTripAvgOilConsum"),distance,minutes))
                    number("averagePower",telemetryCall(trip,"getCurrentTripAvgPwrConsum"))
                }
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
                    if(revision==before && values[n]!=value){values[n]=value;changed(scopeOf(n))}
                }
            }
            val before=synchronized(lock){revision}
            val air=runCatching{api.getMethod("getAirCondition").invoke(remote)}.getOrNull()
            synchronized(lock){if(revision==before)acceptAir(air)}
            synchronized(pressureRecovery) {
                pressureRecovery.observed(synchronized(lock){values["AMBIENT_AIR_PRESSURE"]})
            }
        }
        fun externalChange() { synchronized(lock) { changed("passenger") } }
        fun readAll(after:Long?=null,scope:String="all"):JSONObject {
            check(binder.isBinderAlive && listening) {"CAN listener disconnected"}
            lastRead=SystemClock.elapsedRealtime()
            val includeTelemetry=scope!="passenger" && BridgeSubscriptionSession.vipActive
            if(includeTelemetry)refreshTelemetry() // Initial read / bounded fallback; never from a Binder callback.
            return JSONObject().apply {
                var unchanged=false
                synchronized(lock) {
                    val fallback=includeTelemetry && telemetrySources.any {
                        telemetryCodes[it]==-1 || it.parcelType!=null && telemetryCreators[it]==null || !LauncherTelemetryReadiness.ready(it.getter,telemetrySnapshot())
                    }
                    val deadline=SystemClock.elapsedRealtime()+if(fallback)1000L else 2000L
                    while(LauncherWatchPolicy.unchanged(after,revision,scopeRevision(scope)) && listening && binder.isBinderAlive){
                        val remaining=deadline-SystemClock.elapsedRealtime()
                        if(remaining<=0)break
                        lock.wait(remaining)
                    }
                    check(listening && binder.isBinderAlive) {"CAN listener disconnected"}
                    unchanged=scope!="all" && LauncherWatchPolicy.unchanged(after,revision,scopeRevision(scope))
                    if(!unchanged){
                        val selected=when(scope){
                            "central"->centralNames
                            "passenger"->passengerNames
                            else->names+airGetters+"speed"
                        }
                        for(n in selected)put(n,values[n] ?: JSONObject.NULL)
                    }
                    put("revision",revision);put("session",generation)
                    put("audioRevision",audioRevision.get());put("unchanged",unchanged)
                }
                if(!unchanged){
                    if(includeTelemetry)put("telemetry",telemetrySnapshot())
                    if(scope!="passenger"){
                        put("modes",JSONObject(modes))
                        put("supportsSuspension",supportsSuspension);put("supportsOuting",supportsOuting)
                    }
                }
            }
        }
    }
}
