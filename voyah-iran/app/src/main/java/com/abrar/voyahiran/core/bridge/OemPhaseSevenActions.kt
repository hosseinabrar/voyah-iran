package com.abrar.voyahiran.core.bridge

import com.abrar.raha.RahaAction
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import org.json.JSONObject

/** Uses the actual enabled OEM widgets: configuration, pre-click checks and confirmation dialogs stay in charge. */
internal object OemPhaseSevenActions {
    private val media = mapOf(RahaAction.PLAY_MEDIA to "play", RahaAction.PAUSE_MEDIA to "pause", RahaAction.NEXT_MEDIA to "next", RahaAction.PREVIOUS_MEDIA to "previous")
    val actions = media.keys + setOf(
        RahaAction.SET_DISPLAY_BRIGHTNESS,RahaAction.SET_DISPLAY_THEME_MODE,RahaAction.SET_REAR_DISPLAY_LOCK,
        RahaAction.SET_AUDIO_VOLUME,RahaAction.ADJUST_AUDIO_VOLUME,RahaAction.SET_AUDIO_MUTE,
        RahaAction.OPEN_CAMERA,RahaAction.TAKE_PHOTO,RahaAction.START_VIDEO_RECORDING,RahaAction.STOP_VIDEO_RECORDING)

    fun execute(action: RahaAction, p: Map<String,Any>) {
        require(action in actions)
        fun bool() { require(p["enabled"] is Boolean) }
        fun number(key: String): Int { val n=p[key] as? Number ?: error("Missing $key");require(n.toDouble().isFinite() && n.toDouble()==n.toInt().toDouble());return n.toInt() }
        val request=JSONObject().put("action",action.name).put("p",JSONObject(p))
        val mediaKey=media[action]
        if(mediaKey!=null) { require(p.isEmpty()); shell(listOf("/system/bin/cmd","media_session","dispatch",mediaKey));return }
        var process="com.qinggan.app.vehiclesetting"
        var component="$process/.CarSettingActivity"
        var intent=""
        var fragment=""
        val buttons=mutableListOf<String>()
        when(action) {
            RahaAction.SET_REAR_DISPLAY_LOCK -> {
                require(p.keys==setOf("enabled"));bool()
                intent="com.qinggan.app.vehiclesetting.dirvepreference"
                fragment="com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceFragment"
                request.put("toggleField","fragmentRearscreenLockSwitch")
            }
            RahaAction.SET_DISPLAY_BRIGHTNESS,RahaAction.SET_DISPLAY_THEME_MODE -> {
                process="com.qinggan.app.setting";component="$process/.activity.MainActivity";intent=""
                fragment="com.qinggan.app.setting.module.display.DisplayFragment"
                if(action==RahaAction.SET_DISPLAY_THEME_MODE) {
                    require(p.keys==setOf("mode"))
                    buttons+=mapOf("day" to "displayBrightnessRadioDay","night" to "displayBrightnessRadioNight","auto" to "displayBrightnessRadioAuto").getValue(p["mode"] as String)
                } else {
                    require(p["display"] in setOf("central","passenger","cluster","all"))
                    require(p.keys==setOf("display","percent") || p.keys==setOf("display","deltaPercent"))
                    if(p.containsKey("percent"))require(number("percent") in 0..100) else require(number("deltaPercent") in setOf(-10,10))
                }
            }
            RahaAction.SET_AUDIO_VOLUME,RahaAction.ADJUST_AUDIO_VOLUME,RahaAction.SET_AUDIO_MUTE -> {
                process="com.qinggan.app.setting";component="$process/.activity.MainActivity";intent=""
                require(p["channel"] in setOf("media","call","navigation","voice","system"))
                when(action) {
                    RahaAction.SET_AUDIO_VOLUME -> {require(p.keys==setOf("channel","level"));if(p["level"] !in setOf("min","max"))require(number("level") in 0..30)}
                    RahaAction.ADJUST_AUDIO_VOLUME -> {require(p.keys==setOf("channel","delta"));require(number("delta") in setOf(-1,1))}
                    else->{require(p.keys==setOf("channel","enabled"));bool()}
                }
                request.put("audio",true)
            }
            RahaAction.OPEN_CAMERA,RahaAction.TAKE_PHOTO,RahaAction.START_VIDEO_RECORDING,RahaAction.STOP_VIDEO_RECORDING -> {
                if(action==RahaAction.STOP_VIDEO_RECORDING)require(p.isEmpty()) else require(p==mapOf("view" to "default"))
                process="com.qinggan.camera";component="$process/.activity.RecordVideoActivity";intent=""
                request.put("camera",true)
            }
            else->error("Unsupported phase seven action")
        }
        request.put("fragment",fragment).put("buttons",org.json.JSONArray(buttons))
        // Bring the OEM menu into an initialized state; never synthesize a fake Fragment context.
        // Stopping video must not relaunch/reset the recording activity.
        if(action!=RahaAction.STOP_VIDEO_RECORDING) {
            val launch=mutableListOf("/system/bin/am","start","--display","0","-W","-n",component)
            if(intent.isNotEmpty())launch+=listOf("-a",intent)
            if(process=="com.qinggan.app.setting")launch+=listOf("--es","secondary",if(request.optBoolean("audio"))"sound" else "display")
            shell(launch)
        }
        OemActionScriptRunner.run(process,"const request=$request;\n"+SCRIPT)
    }
    private fun shell(args:List<String>) {
        val result=ProcessCommandRunner(useBridge=false).run(args,8)
        check(result.exitCode==0&&!result.timedOut&&!Regex("Error:|Exception|Permission Denial",RegexOption.IGNORE_CASE).containsMatchIn(result.output)) { result.output }
    }
    private val SCRIPT="""
        'use strict';
        Java.perform(function() {
            const p=request.p, action=request.action;
            let finished=false, step=0, toggled=false, attempts=0;
            function ensure(ok,msg) {if(!ok)throw new Error(msg);}
            function concrete(x) {
                ensure(x!==null,'Null OEM object');
                const name=Java.cast(x,Java.use('java.lang.Object')).getClass().getName().toString();
                return Java.cast(x,Java.use(name));
            }
            function ok(detail) {finished=true;console.log('RAHA_ACTION_OK dispatched '+action+' '+detail);}
            function fail(e) {finished=true;console.log('RAHA_ACTION_ERROR '+action+' '+e);}
            function available(view) {
                ensure(view!==null && view.isShown() && view.isEnabled(),'OEM control hidden/disabled/unavailable');
                let parent=view.getParent();
                while(parent!==null && Java.use('android.view.View').class.isInstance(parent)) {
                    const pv=Java.cast(parent,Java.use('android.view.View'));ensure(pv.isEnabled(),'OEM parent disabled');parent=pv.getParent();
                }
            }
            function click(view) {available(view);ensure(view.performClick(),'OEM click was not handled');}
            function switchTo(node,desired) {
                available(node);
                const cb=concrete(concrete(node.switchBinding.value).hintSwitchButton.value);
                available(cb);
                if(cb.isChecked()===desired)return;
                click(cb); // QGCheckBox.toggle executes the OEM pre-click callback and original listener.
                ensure(cb.isChecked()===desired,'OEM rejected switch or requires confirmation');
            }
            function locateToggle(root,key) {
                const Hint=Java.use('com.qinggan.app.basevehiclesetting.widgets.HintSwitch');
                const state=Java.use('com.qinggan.canbus.DFVehicleState')[key].value;
                let result=null;
                function visit(v) {
                    if(result!==null)return;
                    if(Hint.class.isInstance(v)) {
                        const hint=Java.cast(v,Hint), control=hint.getControlVehicle();
                        if(control!==null && control.equals(state) && hint.isShown()) {result=hint;return;}
                    }
                    if(Java.use('android.view.ViewGroup').class.isInstance(v)) {
                        const group=Java.cast(v,Java.use('android.view.ViewGroup'));
                        for(let i=0;i<group.getChildCount();i++)visit(group.getChildAt(i));
                    }
                }
                visit(root);ensure(result!==null,'OEM switch not available: '+key);return result;
            }
            function audio(ctx) {
                const manager=Java.use('com.qinggan.setting.netlib.base.SettingManager').getInstance(ctx).getAudioPolicyManager();
                ensure(manager!==null && manager.isConnected(),'OEM audio policy unavailable');
                const channel={media:3,call:6,navigation:1,voice:22,system:5}[p.channel];
                const maximum=manager.getStreamMaxVolume(channel), current=manager.getStreamVolume(channel);
                ensure(maximum>0 && current>=0 && current<=maximum,'OEM audio range unavailable');
                if(action==='SET_AUDIO_MUTE')manager.setStreamMute(channel,p.enabled);
                else {
                    const value=action==='ADJUST_AUDIO_VOLUME'? Math.max(0,Math.min(maximum,current+p.delta)) : p.level==='max'?maximum:p.level==='min'?0:p.level;
                    ensure(Number.isInteger(value)&&value>=0&&value<=maximum,'Volume outside OEM range');
                    manager.setStreamVolume(channel,value,2);
                }
                ok('OEM audio request issued channel='+channel);
            }
            function camera() {
                const settings=Java.use('com.qinggan.camera.common.CameraSetting').getInstance();
                const helper=Java.use('com.qinggan.camera.common.CanBusHelper').getInstance();
                ensure(!settings.isInReverseState.value && !helper.isDisplayOffOrStandby(),'Camera unavailable in current vehicle state');
                ensure(helper.getarAppCameraState()!==1 && !helper.getfaceidRegistState(true),'Camera currently occupied');
                const holder=Java.use('com.qinggan.camera.common.CameraVoiceGestureAction').getHolder();
                if(action==='OPEN_CAMERA'){ok('camera opened');return;}
                if(action==='STOP_VIDEO_RECORDING') {
                    if(settings.isRecording.value) {
                        const Power=Java.use('com.qinggan.camera.bean.PowerState');
                        Java.use('org.greenrobot.eventbus.EventBus').getDefault().post(Power.${'$'}new(Power.STOPRECORD.value));
                    }
                } else {
                    ensure(!settings.isRecording.value,'Already recording');
                    if(action==='TAKE_PHOTO')holder.jumpPhotoCamera(1);else holder.jumpVideoCamera(2);
                }
                ok('OEM camera request issued');
            }
            function operate(fragment) {
                const binding=concrete(fragment.binding.value);
                if(!toggled && (request.toggle || request.toggleField)) {
                    const node=request.toggleField?concrete(binding[request.toggleField].value):locateToggle(fragment.getView(),request.toggle);
                    switchTo(node,request.desired===undefined?p.enabled:request.desired);toggled=true;
                    if(request.buttons.length){setTimeout(tick,300);return;}
                }
                if(action==='SET_DISPLAY_BRIGHTNESS') {
                    const fields={central:'seekbarDriverScreen',passenger:'seekbarCopilotScreen',cluster:'seekbarMeterScreen'};
                    const names=p.display==='all'?Object.keys(fields):[p.display];
                    // Validate all requested targets before changing any of them.
                    const plans=names.map(function(name) {
                        const view=concrete(binding[fields[name]].value);available(view);
                        const seek=view.getSeekBar();available(seek);
                        const min=seek.getMin(),max=seek.getMax(),current=seek.getProgress();
                        ensure(max>min,'Invalid brightness range');
                        const value=p.percent===undefined? Math.round(current+p.deltaPercent*(max-min)/100):Math.round(min+p.percent*(max-min)/100);
                        return {view:view,seek:seek,value:Math.max(min,Math.min(max,value))};
                    });
                    plans.forEach(function(plan){fragment.onProgressChanged(plan.view,plan.seek,plan.value,true);});
                    ok('brightness through OEM display presenter');return;
                }
                for(;step<request.buttons.length;step++)click(concrete(binding[request.buttons[step]].value));
                ok('OEM UI dispatch; any confirmation dialog remains for the driver');
            }
            function tick() {if(finished)return;Java.scheduleOnMainThread(function() {
                try {
                    const ctx=Java.use('android.app.ActivityThread').currentApplication().getApplicationContext();
                    if(request.audio){audio(ctx);return;}
                    if(request.camera){camera();return;}
                    let found=false;
                    Java.choose(request.fragment,{onMatch:function(f) {
                        if(!found && f.isAdded() && f.isResumed() && f.getView()!==null) {found=true;try{operate(f);}catch(e){fail(e);} }
                    },onComplete:function() {
                        if(!found && !finished) {if(++attempts<15)setTimeout(tick,200);else fail('OEM page not initialized');}
                    }});
                } catch(e) {fail(e);}
            });}
            tick();
        });
    """.trimIndent()
}
