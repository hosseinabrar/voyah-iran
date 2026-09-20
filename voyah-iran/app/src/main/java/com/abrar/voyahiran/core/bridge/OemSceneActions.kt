package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.voice.SceneCommandPolicy
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import dalvik.system.PathClassLoader
import java.io.File

/** Factory scene entry points and CAN requests; no injection into VehicleSetting. */
internal object OemSceneActions {
    val actions = setOf("ACTIVATE_SCENE", "DEACTIVATE_SCENE")
    private val scenes = setOf("nap", "camp", "carWash", "powerKeep", "party", "babyCare", "quickWarm")
    fun execute(action: String, p: Map<String, Any>): String {
        require(action in actions)
        val scene = p["scene"] as? String ?: error("Missing scene")
        require(scene in scenes || (action == "DEACTIVATE_SCENE" && scene == "current"))
        require(p.keys == setOf("scene") || (action == "ACTIVATE_SCENE" && scene == "powerKeep" && p.keys == setOf("scene", "minutes")))
        val minutes = if (p.containsKey("minutes")) {
            val n = p["minutes"] as? Number ?: error("Invalid duration")
            require(n.toDouble() == n.toInt().toDouble() && n.toInt() in setOf(-1,30,60,90,120,150,180))
            n.toInt()
        } else 60
        try { return executeInstalled(action, scene, minutes) }
        catch (e: LinkageError) { throw IllegalStateException("OEM scene SDK unavailable", e) }
    }
    private fun executeInstalled(action: String, scene: String, minutes: Int): String {
        val apk=File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Installed VehicleSetting SDK unavailable" }
        val loader=object : PathClassLoader(apk.path,"/system/lib64:/system/lib",OemSceneActions::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if(!name.startsWith("com.qinggan.vehicle.") && !name.startsWith("com.qinggan.canbus.")) return super.loadClass(name,resolve)
                synchronized(this) {
                    val type=findLoadedClass(name) ?: findClass(name)
                    if(resolve)resolveClass(type)
                    return type
                }
            }
        }
        val stateType=loader.loadClass("com.qinggan.canbus.VehicleState")
        val facade=loader.loadClass("com.qinggan.canbus.DFVehicleState")
        val api=loader.loadClass("com.qinggan.canbus.ICanBusService")
        val binder=Class.forName("android.os.ServiceManager").getMethod("getService",String::class.java).invoke(null,"qg.canbus") as? IBinder ?: error("CAN service unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor==api.name) { "CAN descriptor mismatch" }
        val remote=loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub").getMethod("asInterface",IBinder::class.java).invoke(null,binder)
        fun field(name:String):Any = checkNotNull(try {facade.getField(name).get(null)} catch(_:NoSuchFieldException){stateType.getField(name).get(null)})
        fun read(name:String):Int = (api.getMethod("getVehicleState",stateType).invoke(remote,field(name)) as Int).also {
            AppLog.info("RahaScene","read field=$name value=$it")
        }
        fun write(name:String,value:Int) {
            AppLog.info("RahaScene","write field=$name value=$value")
            val result=api.getMethod("setVehicleState",stateType,Int::class.javaPrimitiveType).invoke(remote,field(name),value)
            check(result !is Number || result.toInt()>=0) { "OEM rejected $name: $result" }
        }
        fun confirm(label:String, matches:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+6000
            while(!matches()) {check(SystemClock.elapsedRealtime()<deadline) { "Scene feedback not confirmed: $label" };Thread.sleep(150)}
            AppLog.info("RahaScene","confirmed $label")
        }
        fun property(key:String, default:String="off"):String = Class.forName("android.os.SystemProperties")
            .getMethod("get",String::class.java,String::class.java).invoke(null,key,default) as String
        fun setProperty(key:String,value:String) {Class.forName("android.os.SystemProperties").getMethod("set",String::class.java,String::class.java).invoke(null,key,value)}
        fun active():Set<String> = buildSet {
            if(property("com.qinggan.restmode.state")=="on")add("nap")
            if(property("com.qinggan.campmode.state")=="on")add("camp")
            // .manualwashmode.state indicates the DIALOG, not the car-wash mode.
            if(read("SELF_CAR_WASH_STS")==1)add("carWash")
            if(property("com.qinggan.powerholdmode.state")=="on" || read("POWER_HOLD_MODE_SWITCH")==1)add("powerKeep")
        }
        fun powerHold(enable:Boolean) {
            val values=linkedMapOf("POWER_HOLD_MODE_SWITCH" to if(enable)1 else 0)
            if(enable)values["POWER_HOLD_MODE_TIME"]=if(minutes == -1)15 else minutes/30
            // Preserve the engine-extender setting on entry; factory exit disables it.
            if(!enable)values["SCENE_MODE_EXTENDER_SET"]=0
            values.keys.forEach(::field)
            val bundle=Bundle().apply {values.forEach { (key,value)->putInt(field(key).toString(),value) }}
            AppLog.info("RahaScene","power hold bundle=$values")
            val result=api.getMethod("setVehicleAndAirConditionBundleState",Bundle::class.java,Bundle::class.java).invoke(remote,null,bundle)
            check(result !is Number || result.toInt()>=0) { "Power hold rejected: $result" }
            confirm("powerKeep=$enable") {read("POWER_HOLD_MODE_SWITCH")==if(enable)1 else 0}
            setProperty("com.qinggan.powerholdmode.state",if(enable)"on" else "off")
            if(!enable)setProperty("com.qinggan.campmode.state.extender","off")
        }
        AppLog.info("RahaScene","route=direct action=$action scene=$scene minutes=$minutes")
        val current=active()
        AppLog.info("RahaScene","active=$current")
        if(action=="DEACTIVATE_SCENE") {
            // A named exit always means exit whichever persistent scenes are actually active.
            current.forEach { actual ->
                when(actual) {
                    "carWash" -> {write("CAR_CLEANING_MODE_SWITCH",0);confirm("carWash off") {read("SELF_CAR_WASH_STS")==0};setProperty("com.qinggan.manualwashmode.wash_car.state","off")}
                    "powerKeep" -> powerHold(false)
                    else -> {
                        val pkg=if(actual=="camp")"com.qinggan.app.campmode" else "com.qinggan.app.restmode"
                        val event=if(actual=="camp")"qg_action_exit_camp_mode" else "qg_action_exit_rest_mode"
                        OemSceneEventBus.publish(loader,pkg,event)
                        val key=if(actual=="camp")"com.qinggan.campmode.state" else "com.qinggan.restmode.state"
                        confirm("$actual off") {property(key)=="off"}
                    }
                }
            }
            return "CONFIRMED exited scenes=$current"
        }
        check(current.all {it==scene}) {"Another scene is active: $current"}
        if(scene in current && scene!="powerKeep")return "CONFIRMED already active scene=$scene"
        val feature=mapOf("nap" to "restmode","camp" to "camp","carWash" to "washcar","powerKeep" to "leavecar")[scene]
        if(feature!=null) {
            check(property("persist.app.feature.$feature","true")!="false") {"Factory scene unavailable: $scene"}
            check(api.getMethod("getGearStatus").invoke(remote)?.toString()=="Parking") {"Scene requires P"}
        }
        check(loader.loadClass("com.qinggan.utils.ScreenLiftUtils").getMethod("getScreenLiftState").invoke(null)==2) {"Scene requires raised screen"}
        mapOf("nap" to 20,"camp" to 30,"powerKeep" to 15)[scene]?.let {minimum ->
            val soc=read("BMS_SOC_DISPLAY");check(soc in minimum..100) {"Scene battery level unavailable or below $minimum"}
        }
        when(scene) {
            "carWash" -> {
                field("CAR_CLEANING_MODE_SWITCH");field("VEHICLE_TUNK_CONTROL_SWITCH")
                val trunk=read("VEHICLE_TUNK_FEEDBACK_STATUS")
                check(trunk in 0..3) {"Trunk feedback unavailable"}
                write("CAR_CLEANING_MODE_SWITCH",1)
                // Same sequence as the factory 'agree' callback.
                if(trunk!=1)write("VEHICLE_TUNK_CONTROL_SWITCH",1)
                confirm("carWash on") {read("SELF_CAR_WASH_STS")==1}
                setProperty("com.qinggan.manualwashmode.wash_car.state","on")
            }
            "powerKeep" -> powerHold(true)
            "camp", "nap" -> {
                val key=if(scene=="camp")"camp" else "rest"
                am(SceneCommandPolicy.launch(scene))
                confirm("$scene on") {property("com.qinggan.${key}mode.state")=="on"}
            }
            "party", "babyCare" -> {
                am(listOf("startservice","--user","0","-a","com.qinggan.app.vehiclesetting.SELECT_MODE","-p","com.qinggan.app.vehiclesetting","--es","MODE_NAME",if(scene=="party")"party" else "呵护宝宝","--ez","isClick","true"))
                return "DISPATCHED factory one-shot preset=$scene; no persistent scene acknowledgement"
            }
            "quickWarm" -> {
                am(listOf("broadcast","--user","0","-a","com.pateo.ivy.scene.fastwarm","-p","com.pateo.ivy.sceneengine"))
                return "DISPATCHED factory quick-warm broadcast; no persistent scene acknowledgement"
            }
        }
        return "CONFIRMED scene=$scene"
    }
    private fun am(args:List<String>) {
        val result=ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/am")+args,8)
        AppLog.info("RahaScene","am=$args exit=${result.exitCode} output=${result.output}")
        check(result.exitCode==0 && !result.timedOut && !Regex("Error:|Exception|Permission Denial",RegexOption.IGNORE_CASE).containsMatchIn(result.output)) {"OEM scene entry failed: ${result.output}"}
    }
}
