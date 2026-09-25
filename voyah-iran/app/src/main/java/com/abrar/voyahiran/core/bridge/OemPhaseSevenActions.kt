package com.abrar.voyahiran.core.bridge

import com.abrar.raha.RahaAction
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import org.json.JSONObject

/** Direct OEM services; only explicit camera commands can launch the camera activity. */
internal object OemPhaseSevenActions {
    private val media = mapOf(RahaAction.PLAY_MEDIA to "play", RahaAction.PAUSE_MEDIA to "pause", RahaAction.NEXT_MEDIA to "next", RahaAction.PREVIOUS_MEDIA to "previous")
    val actions = media.keys + setOf(
        RahaAction.SET_DISPLAY_BRIGHTNESS,RahaAction.SET_DISPLAY_THEME_MODE,RahaAction.SET_REAR_DISPLAY_LOCK,
        RahaAction.SET_AUDIO_VOLUME,RahaAction.ADJUST_AUDIO_VOLUME,RahaAction.SET_AUDIO_MUTE,
        RahaAction.OPEN_CAMERA,RahaAction.TAKE_PHOTO,RahaAction.START_VIDEO_RECORDING,RahaAction.STOP_VIDEO_RECORDING,RahaAction.SWITCH_CAMERA)

    fun execute(action: RahaAction, p: Map<String,Any>) {
        require(action in actions)
        fun bool() { require(p["enabled"] is Boolean) }
        fun number(key: String): Int { val n=p[key] as? Number ?: error("Missing $key");require(n.toDouble().isFinite() && n.toDouble()==n.toInt().toDouble());return n.toInt() }
        val request=JSONObject().put("action",action.name).put("p",JSONObject(p))
        val mediaKey=media[action]
        if(mediaKey!=null) { require(p.isEmpty()); shell(listOf("/system/bin/cmd","media_session","dispatch",mediaKey));return }
        var process="com.qinggan.app.vehiclesetting"
        when(action) {
            RahaAction.SET_REAR_DISPLAY_LOCK -> {
                require(p.keys==setOf("enabled"));bool()
                OemRearDisplayLock.execute(p.getValue("enabled") as Boolean)
                return
            }
            RahaAction.SET_DISPLAY_BRIGHTNESS,RahaAction.SET_DISPLAY_THEME_MODE -> {
                process="com.qinggan.app.setting"
                if(action==RahaAction.SET_DISPLAY_THEME_MODE) {
                    require(p.keys==setOf("mode"))
                    require(p["mode"] in setOf("day", "night", "auto"))
                } else {
                    require(p["display"] in setOf("central","passenger","cluster","all"))
                    require(p.keys==setOf("display","percent") || p.keys==setOf("display","deltaPercent"))
                    if(p.containsKey("percent"))require(number("percent") in 0..100) else require(number("deltaPercent") in setOf(-10,10))
                }
            }
            RahaAction.SET_AUDIO_VOLUME,RahaAction.ADJUST_AUDIO_VOLUME,RahaAction.SET_AUDIO_MUTE -> {
                process="com.qinggan.app.setting"
                require(p["channel"] in setOf("media","call","navigation","voice","system"))
                when(action) {
                    RahaAction.SET_AUDIO_VOLUME -> {require(p.keys==setOf("channel","level"));if(p["level"] !in setOf("min","max"))require(number("level") in 0..30)}
                    RahaAction.ADJUST_AUDIO_VOLUME -> {require(p.keys==setOf("channel","delta"));require(number("delta") in setOf(-1,1))}
                    else->{require(p.keys==setOf("channel","enabled"));bool()}
                }
                request.put("audio",true)
            }
            RahaAction.OPEN_CAMERA,RahaAction.TAKE_PHOTO,RahaAction.START_VIDEO_RECORDING,RahaAction.STOP_VIDEO_RECORDING,RahaAction.SWITCH_CAMERA -> {
                if(action==RahaAction.STOP_VIDEO_RECORDING || action==RahaAction.SWITCH_CAMERA)require(p.isEmpty()) else require(p==mapOf("view" to "default"))
                process="com.qinggan.camera"
                request.put("camera",true)
            }
            else->error("Unsupported phase seven action")
        }
        if (request.optBoolean("audio")) { OemAudioBinder.execute(action, p); return }
        if (action == RahaAction.SET_DISPLAY_BRIGHTNESS || action == RahaAction.SET_DISPLAY_THEME_MODE) {
            OemDisplayBinder.execute(action, p)
            return
        }
        if(request.optBoolean("camera")) OemCameraCapability.requireDvr()
        // Only explicit camera entry may bring its own UI forward; never open Settings.
        if(request.optBoolean("camera") && action!=RahaAction.STOP_VIDEO_RECORDING && action!=RahaAction.SWITCH_CAMERA) {
            shell(listOf("/system/bin/am","start","--display","0","-W","-n","com.qinggan.camera/.activity.RecordVideoActivity"))
        }
        if(action==RahaAction.OPEN_CAMERA)return
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
            let finished=false;
            let stage='initializing';
            const readyDeadline=Date.now()+5000;
            function ensure(ok,msg) {if(!ok)throw new Error(msg);}
            function ok(detail) {finished=true;console.log('RAHA_ACTION_OK dispatched '+action+' '+detail);}
            function fail(e) {finished=true;console.log('RAHA_ACTION_ERROR '+action+' stage='+stage+' '+e+' stack='+(e.stack||''));}
            function camera() {
                const settings=Java.use('com.qinggan.camera.common.CameraSetting').getInstance();
                const helper=Java.use('com.qinggan.camera.common.CanBusHelper').getInstance();
                ensure(!settings.isInReverseState.value && !helper.isDisplayOffOrStandby(),'Camera unavailable in current vehicle state');
                ensure(helper.getarAppCameraState()!==1 && !helper.getfaceidRegistState(true),'Camera currently occupied');
                ensure(Java.use('com.qinggan.camera.common.SupportUtils').isSupportDvr(),'DVR option no longer enabled');
                stage='activity';
                const rawActivity=settings.getTopActivity();
                const activity=rawActivity===null?null:Java.cast(rawActivity,Java.use('android.app.Activity'));
                if(action!=='STOP_VIDEO_RECORDING' && (activity===null || !settings.active.value || !activity.getWindow().getDecorView().isShown())) {
                    ensure(Date.now()<readyDeadline,'Camera activity did not become ready');
                    setTimeout(tick,100);return;
                }
                if(action==='OPEN_CAMERA'){ok('camera opened');return;}
                if(action==='SWITCH_CAMERA') {
                    ensure(!settings.isRecording.value,'Stop recording before switching camera');
                    const id=activity.getResources().getIdentifier('camera_change_iv','id','com.qinggan.camera');
                    ensure(id!==0,'Camera switch resource unavailable');
                    stage='switch-view';
                    const rawButton=activity.findViewById(id);
                    const button=rawButton===null?null:Java.cast(rawButton,Java.use('android.view.View'));
                    ensure(button!==null && button.isShown() && button.isEnabled(),'Camera switch unavailable');
                    stage='switch-click';
                    ensure(button.performClick(),'Camera switch rejected');
                    ok('OEM camera switch clicked');return;
                }
                if(action==='STOP_VIDEO_RECORDING') {
                    if(settings.isRecording.value) {
                        const Power=Java.use('com.qinggan.camera.bean.PowerState');
                        Java.use('org.greenrobot.eventbus.EventBus').getDefault().post(Power.${'$'}new(Power.STOPRECORD.value));
                    }
                } else {
                    ensure(!settings.isRecording.value,'Already recording');
                    const holder=Java.use('com.qinggan.camera.common.CameraVoiceGestureAction').getHolder();
                    if(action==='TAKE_PHOTO')holder.jumpPhotoCamera(1);else holder.jumpVideoCamera(2);
                }
                ok('OEM camera request issued');
            }
            function tick() {if(finished)return;Java.scheduleOnMainThread(function() {
                try {
                    const ctx=Java.use('android.app.ActivityThread').currentApplication().getApplicationContext();
                    if(request.camera){camera();return;}
                    throw new Error('Unsupported injected action');
                } catch(e) {fail(e);}
            });}
            tick();
        });
    """.trimIndent()
}
