package com.abrar.voyahiran.core.bridge

import com.abrar.raha.RahaAction
import com.abrar.voyahiran.feature.voice.EnergyStrategyPolicy
import org.json.JSONObject

/** Uses the installed Settings implementation; does not substitute a different strategy on rejection. */
internal object OemEnergySceneActions {
    val actions = setOf(RahaAction.SET_ENERGY_STRATEGY, RahaAction.SET_BATTERY_RESERVE_TARGET,
        RahaAction.STOP_CHARGING, RahaAction.SET_BATTERY_PRECONDITIONING, RahaAction.ACTIVATE_SCENE,
        RahaAction.DEACTIVATE_SCENE, RahaAction.SET_POWER_KEEP_DURATION, RahaAction.SET_POWER_KEEP_ENGINE_CHARGING)

    fun execute(action: RahaAction, params: Map<String, Any>) {
        require(action in actions)
        fun bool() { require(params["enabled"] is Boolean) }
        fun integer(key: String): Int {
            val n = (params[key] as? Number)?.toDouble() ?: error("Missing $key")
            require(n.isFinite() && n % 1 == 0.0 && n in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
            return n.toInt()
        }
        val scenes = setOf("camp", "nap", "party", "babyCare", "carWash", "quickWarm", "powerKeep")
        when (action) {
            RahaAction.SET_ENERGY_STRATEGY -> {
                require(params["strategy"] in setOf("electric", "fuel", "hybrid", "smart", "save"))
                if(params.containsKey("reservePercent")) {
                    require(params["strategy"]=="save")
                    EnergyStrategyPolicy.reserve(integer("reservePercent"))
                }
            }
            RahaAction.SET_BATTERY_RESERVE_TARGET -> EnergyStrategyPolicy.reserve(integer("percent"))
            RahaAction.SET_BATTERY_PRECONDITIONING, RahaAction.SET_POWER_KEEP_ENGINE_CHARGING -> bool()
            RahaAction.SET_POWER_KEEP_DURATION -> require(integer("minutes") in setOf(-1, 30, 60, 90, 120, 150, 180))
            RahaAction.ACTIVATE_SCENE -> {
                require(params["scene"] in scenes)
                if(params.containsKey("minutes")) {
                    require(params["scene"]=="powerKeep")
                    require(integer("minutes") in setOf(-1,30,60,90,120,150,180))
                }
            }
            RahaAction.DEACTIVATE_SCENE -> require(params["scene"]=="current" || params["scene"] in scenes)
            else -> Unit
        }
        val profiles = JSONObject().apply {
            for (ca in listOf(false, true)) for (overseas in listOf(false, true))
                put("$ca/$overseas", JSONObject(EnergyStrategyPolicy.modes(true, ca, overseas)))
        }
        val request = JSONObject().put("action", action.name).put("p", JSONObject(params)).put("profiles", profiles)
        OemActionScriptRunner.run("com.qinggan.app.vehiclesetting", "const request=$request;\n" + SCRIPT)
    }

    private val SCRIPT = """
        'use strict';
        Java.perform(function () { Java.scheduleOnMainThread(function () {
            let stage='initialize';
            try {
                const app=Java.use('android.app.ActivityThread').currentApplication();
                if(app===null) throw new Error('OEM application not initialized');
                const ctx=app.getApplicationContext();
                const Bus=Java.use('com.qinggan.canbus.CanBusManager');
                const bus=Bus.getInstance.overload().call(Bus);
                const VS=Java.use('com.qinggan.canbus.VehicleState');
                const DF=Java.use('com.qinggan.canbus.DFVehicleState');
                const Tool=Java.use('com.qinggan.app.basevehiclesetting.canbustools.CanBusTool');
                const tool=Tool.instance(ctx);
                const SP=Java.use('android.os.SystemProperties');
                const Intent=Java.use('android.content.Intent');
                const Bundle=Java.use('android.os.Bundle');
                const p=request.p, action=request.action;
                function ensure(ok,message) { if(!ok) throw new Error(message); }
                function prop(key,def) { return SP.get.overload('java.lang.String','java.lang.String').call(SP,key,def).toString(); }
                function state(key) { try {return DF[key].value;} catch(e) {return VS[key].value;} }
                function read(key) {return bus.getVehicleState(state(key));}
                function write(key,value) {
                    console.log('RAHA_ACTION_WRITE '+key+'='+value);
                    tool.setVehicleStateBack.overload('com.qinggan.canbus.VehicleState','int','boolean').call(tool,state(key),value,true);
                }
                function parked() {ensure(bus.getGearStatus().toString()==='Parking','OEM scene requires P');}
                function screen() {ensure(Java.use('com.qinggan.utils.ScreenLiftUtils').getScreenLiftState()===2,'OEM scene requires raised screen');}
                function feature(name) {ensure(prop('persist.app.feature.'+name,'true')!=='false','OEM feature unavailable: '+name);}
                function hold() {return Java.use('com.qinggan.scene.powerhold.PowerHoldModeManager').getInstance();}
                function active() {
                    const result=[];
                    if(prop('com.qinggan.restmode.state','off')==='on')result.push('nap');
                    if(prop('com.qinggan.campmode.state','off')==='on')result.push('camp');
                    if(prop('com.qinggan.manualwashmode.state','off')==='on')result.push('carWash');
                    if(hold().inPowerHoldMode() || prop('com.qinggan.powerholdmode.show','off')==='on')result.push('powerKeep');
                    return result;
                }
                function event(destination,type) {
                    const e=Java.use('com.qinggan.bus.QGBusEvent').${'$'}new();
                    e.setDestination(destination); e.setEventType(type);
                    const b=Bundle.${'$'}new(); b.putString('key_src','src_voice'); e.setData(b);
                    Java.use('com.qinggan.bus.QGBus').${'$'}new(ctx).publish(e);
                }
                function startScene(scene) {
                    const featureName={nap:'restmode',camp:'camp',carWash:'washcar',powerKeep:'leavecar'}[scene];
                    if(featureName)feature(featureName);
                    const current=active();
                    ensure(current.every(function(s){return s===scene;}),'Another OEM scene is active: '+current);
                    if(current.indexOf(scene)>=0 && !(scene==='powerKeep' && p.minutes!==undefined))return;
                    screen();
                    if(featureName)parked();
                    const minimum={nap:20,camp:30,powerKeep:15}[scene];
                    if(minimum!==undefined)ensure(read('BMS_SOC_DISPLAY')>=minimum,'Battery below OEM scene minimum '+minimum);
                    if(scene==='powerKeep') {
                        const manager=hold();
                        ensure(manager.canStartPowerHoldMode(),'Power hold rejected by OEM');
                        const duration=p.minutes!==undefined?p.minutes:ctx.getSharedPreferences('power_hold_sp',0).getInt('power_hold_key_duration',60);
                        const rid=ctx.getResources().getIdentifier('power_hold_duration','array','com.qinggan.app.vehiclesetting');
                        ensure(rid!==0 && Array.prototype.indexOf.call(ctx.getResources().getIntArray(rid),duration)>=0,'Unsupported OEM duration');
                        manager.enterPowerHoldMode(manager.isEnableEngineExtender(),duration);
                    } else if(scene==='nap' || scene==='camp') {
                        const i=Intent.${'$'}new(scene==='nap'?'com.qinggan.app.REST_MODE':'com.qinggan.app.CAMP_MODE');
                        i.setFlags(268435456);
                        i.putExtra.overload('java.lang.String','java.lang.String').call(i,scene==='nap'?'key_rest_src':'key_camp_src','src_voice');
                        if(scene==='camp')i.putExtra.overload('java.lang.String','java.lang.String').call(i,'key_camp_name','fire');
                        ctx.startActivity(i);
                    } else if(scene==='carWash') {
                        const i=Intent.${'$'}new('com.qinggan.app.vehiclesetting.switch.DRIVE_MODE');
                        i.setPackage('com.qinggan.app.vehiclesetting');
                        i.putExtra.overload('java.lang.String','java.lang.String').call(i,'com.qinggan.app.vehiclesetting.switch.geton','com.qinggan.app.vehiclesetting.scene.wash_car');
                        ensure(ctx.startService(i)!==null,'OEM car wash service unavailable');
                    } else if(scene==='babyCare' || scene==='party') {
                        // OEM SceneModeActivity routes these as one-shot presets, not CAN scene states.
                        const i=Intent.${'$'}new('com.qinggan.app.vehiclesetting.SELECT_MODE'); i.setPackage('com.qinggan.app.vehiclesetting');
                        i.putExtra.overload('java.lang.String','java.lang.String').call(i,'MODE_NAME',scene==='babyCare'?'呵护宝宝':'party');
                        i.putExtra.overload('java.lang.String','boolean').call(i,'isClick',true);
                        ensure(ctx.startService(i)!==null,'OEM preset service unavailable');
                    } else if(scene==='quickWarm') {
                        const i=Intent.${'$'}new('com.pateo.ivy.scene.fastwarm'); i.setPackage('com.pateo.ivy.sceneengine');
                        ctx.getPackageManager().getApplicationInfo('com.pateo.ivy.sceneengine',0);
                        ctx.sendBroadcast(i);
                    } else throw new Error('Unsupported scene');
                }
                stage=action;
                if(action==='SET_ENERGY_STRATEGY' || action==='SET_BATTERY_RESERVE_TARGET') {
                    const C=Java.use('com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceConfigs');
                    const c=C.${'$'}new();
                    ensure(c.isSupportExtends(),'Energy strategy menu unavailable on this vehicle');
                    const ca=!!c.isDoFengH97acd();
                    // Older China Settings has no X-overseas branch; its Save button stays Save.
                    const x=typeof c.is97XOversea==='function' ? !!c.is97XOversea() : false;
                    const supported=request.profiles[String(ca)+'/'+String(x)];
                    console.log('RAHA_ENERGY_CAPABILITIES ca='+ca+' xOverseas='+x+' modes='+JSON.stringify(supported));
                    if(action==='SET_ENERGY_STRATEGY') {
                        ensure(Object.prototype.hasOwnProperty.call(supported,p.strategy),'Unsupported energy strategy '+p.strategy);
                        const requested=supported[p.strategy];
                        if(p.reservePercent!==undefined) {
                            ensure(p.strategy==='save' && p.reservePercent>=25 && p.reservePercent<=80 && p.reservePercent%5===0,'Invalid reserve target');
                            // Resolve before the first write, even when firmware lacks this field.
                            state('SREV_SOC_SET');
                        }
                        if(read('IVI_SOC_MODESET')!==requested) {
                            // DrivePreferenceFragment.setPowerMode uses the bundle API.
                            const b=Bundle.${'$'}new(); b.putInt(state('IVI_SOC_MODESET').toString(),requested);
                            console.log('RAHA_ACTION_WRITE IVI_SOC_MODESET='+requested+' via bundle');
                            bus.setVehicleAndAirConditionBundleState(null,b);
                            tool.backAfterThree(state('IVI_SOC_MODESET'));
                        }
                        if(p.reservePercent!==undefined)write('SREV_SOC_SET',(p.reservePercent-25)/5);
                    } else {
                        // The slider belongs to value 4, including the overseas Fuel-labelled mode.
                        ensure(read('IVI_SOC_MODESET')===4,'Select the OEM reserve mode before changing its charge target');
                        write('SREV_SOC_SET',(p.percent-25)/5);
                    }
                } else if(action==='STOP_CHARGING') {
                    const m=Java.use('java.util.LinkedHashMap').${'$'}new(); const I=Java.use('java.lang.Integer');
                    m.put(state('HUM_STOP_CHARGE'),I.valueOf.overload('int').call(I,1));
                    m.put(state('CHARGE_GUN_UNLOCK_SET'),I.valueOf.overload('int').call(I,1));
                    tool.setMultiSerially.overload('java.util.Map','boolean').call(tool,m,false);
                } else if(action==='SET_BATTERY_PRECONDITIONING') {
                    write('DRIVER_PREHEAT_SET',p.enabled?1:0);
                } else if(action==='ACTIVATE_SCENE') {
                    startScene(p.scene);
                } else if(action==='DEACTIVATE_SCENE') {
                    // All spoken scene names mean the same global exit operation.
                    // Read actual active modes; never act on the name heard by ASR.
                    const current=active();
                    for(const scene of current) {
                      if(scene==='powerKeep')hold().exitPowerHoldMode();
                      else {
                        const routes={nap:['com.qinggan.app.restmode','qg_action_exit_rest_mode'],camp:['com.qinggan.app.campmode','qg_action_exit_camp_mode'],carWash:['com.qinggan.app.vehiclesetting','qg_action_exit_car_wash_mode']};
                        ensure(routes[scene]!==undefined,'Preset has no persistent exit operation'); event(routes[scene][0],routes[scene][1]);
                      }
                    }
                } else if(action==='SET_POWER_KEEP_DURATION' || action==='SET_POWER_KEEP_ENGINE_CHARGING') {
                    feature('leavecar');
                    const manager=hold(), prefs=ctx.getSharedPreferences('power_hold_sp',0);
                    let minutes=prefs.getInt('power_hold_key_duration',60);
                    const enabled=action==='SET_POWER_KEEP_ENGINE_CHARGING'?p.enabled:manager.isEnableEngineExtender();
                    if(action==='SET_POWER_KEEP_DURATION')minutes=p.minutes;
                    const rid=ctx.getResources().getIdentifier('power_hold_duration','array','com.qinggan.app.vehiclesetting');
                    ensure(rid!==0,'OEM duration resource unavailable');
                    const supported=ctx.getResources().getIntArray(rid);
                    ensure(Array.prototype.indexOf.call(supported,minutes)>=0,'Unsupported OEM power hold duration '+minutes);
                    if(enabled)ensure(Java.use('com.qinggan.scene.powerhold.PowerHoldModeManager').SUPPORT_ENGINE.value,'Engine charging unavailable');
                    if(manager.inPowerHoldMode()) {
                        ensure(manager.canStartPowerHoldMode(),'Power hold update rejected by OEM');
                        manager.enterPowerHoldMode(enabled,minutes);
                    } else {
                        // Configure the next entry without unexpectedly activating a parked scene.
                        ensure(prefs.edit().putInt('power_hold_key_duration',minutes).commit(),'Failed to save duration');
                    }
                    manager.setEnableEngineExtender(enabled);
                } else throw new Error('Unknown action');
                console.log('RAHA_ACTION_OK dispatched='+action+' (OEM/ECU handles completion)');
            } catch(e) { console.log('RAHA_ACTION_ERROR stage='+stage+' '+e+' stack='+e.stack); }
        }); });
    """.trimIndent()
}
