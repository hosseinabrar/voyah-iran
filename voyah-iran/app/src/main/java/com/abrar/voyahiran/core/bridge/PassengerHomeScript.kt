package com.abrar.voyahiran.core.bridge

internal object PassengerHomeScript {
    val source="""
Java.perform(function(){
    const Global=Java.use('android.provider.Settings__DOLLAR__Global');
    const Intent=Java.use('android.content.Intent');
    const Options=Java.use('android.app.ActivityOptions');
    const ctx=Java.use('android.app.ActivityThread').currentApplication();
    const Apps=Java.use('com.qinggan.launcher.base.utils.AppUtils');
    function allowed(){
        if(typeof globalThis.VI_LAUNCHER_SCREEN_UP==='function' && !globalThis.VI_LAUNCHER_SCREEN_UP())return false;
        const now=Date.now();
        return Global.getInt(ctx.getContentResolver(),'voyah_iran_launcher_home',0)===1 &&
            ['voyah_iran_launcher_lease','voyah_iran_launcher_vip_lease'].every(function(k){const n=Number(Global.getString(ctx.getContentResolver(),k))-now;return n>0&&n<=90000;});
    }
    // The foreground broadcast arrives before AppUtils' task cache catches up.
    // A newer event (including a non-home app) replaces the hint immediately.
    let foregroundHint='',foregroundUntil=0;
    globalThis.VI_PASSENGER_FOREGROUND=function(intent){
        const value=intent.getStringExtra('android.intent.extra.COMPONENT_NAME');
        foregroundHint=value===null?'':String(value);
        foregroundUntil=Date.now()+2200;
    };
    globalThis.VI_PASSENGER_TOP_HINT=function(){return Date.now()<foregroundUntil?foregroundHint:'';};
    function top(){return globalThis.VI_PASSENGER_TOP_HINT()||String(Apps.getTopAppInfo(ctx,1,4));}
    function open(drawer){
        if(!allowed())return false;
        const i=Intent.__DOLLAR__new();i.setClassName('com.abrar.voyahiran','com.abrar.voyahiran.feature.launcher.'+(drawer?'PassengerAppsActivity':'PassengerLauncherActivity'));
        i.addFlags(0x10000000|0x04000000);const o=Options.makeBasic();o.setLaunchDisplayId(1);ctx.startActivity(i,o.toBundle());return true;
    }
    globalThis.VI_PASSENGER_OPEN=open;
    globalThis.VI_PASSENGER_HOME_VISIBLE=function(){try{return allowed()&&top().indexOf('feature.launcher.PassengerLauncherActivity')>=0;}catch(_){return false;}};
    let queued=false,last=0;
    function check(){if(queued)return;queued=true;Java.scheduleOnMainThread(function(){queued=false;try{
        if(!allowed())return;
        const t=top();if(t.indexOf('com.qinggan.secondlauncher.activity.SecondMainActivity')<0 && t!=='com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.SecondMainActivity')return;
        if(Date.now()-last<1500)return;
        if(open(false))last=Date.now();
    }catch(e){console.log('VI_PASSENGER_HOME_CHECK '+e);}});}
    globalThis.VI_PASSENGER_HOME_CHECK=check;
    try{
        const Home=Java.use('com.qinggan.secondlauncher.activity.SecondMainActivity');const resume=Home.onResume.overload();
        resume.implementation=function(){const r=resume.call(this);check();return r;};
    }catch(e){console.log('VI_PASSENGER_HOME_RESUME '+e);}
    try{
        const Bar=Java.use('com.qinggan.launcher.navigation.NavigationBarSecond');const click=Bar.onClick.overload('android.view.View');
        click.implementation=function(v){try{const d=v.getDisplay();if(d!==null&&d.getDisplayId()===1){const name=String(v.getResources().getResourceEntryName(v.getId()));
            if((name==='screen_up_home_icon'||name==='screen_down_home_icon')&&open(false))return;
            if((name==='screen_up_all_app_icon'||name==='screen_down_all_app_icon')&&open(true))return;
        }}catch(e){console.log('VI_PASSENGER_HOME_CLICK '+e);}return click.call(this,v);};
    }catch(e){console.log('VI_PASSENGER_HOME_BAR '+e);}
    try{
        const Observer=Java.registerClass({name:'com.abrar.voyahiran.bridge.PassengerHomeObserver',superClass:Java.use('android.database.ContentObserver'),methods:{
            __DOLLAR__init:[{returnType:'void',argumentTypes:['android.os.Handler'],implementation:function(h){this.__DOLLAR__super.__DOLLAR__init(h);}}],
            onChange:[{returnType:'void',argumentTypes:['boolean'],implementation:check}]}});
        const o=Java.retain(Observer.__DOLLAR__new(Java.use('android.os.Handler').__DOLLAR__new(Java.use('android.os.Looper').getMainLooper())));
        ['voyah_iran_launcher_home','voyah_iran_launcher_lease','voyah_iran_launcher_vip_lease'].forEach(k=>ctx.getContentResolver().registerContentObserver(Global.getUriFor(k),false,o));
    }catch(e){console.log('VI_PASSENGER_HOME_OBSERVER '+e);}
    check();
});
""".trimIndent().replace("__DOLLAR__","$")
}
