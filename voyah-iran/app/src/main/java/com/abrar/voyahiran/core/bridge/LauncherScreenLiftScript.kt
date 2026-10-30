package com.abrar.voyahiran.core.bridge

/** OEM layout state, shared by both Home/drawer entry points. Never moves other apps. */
internal object LauncherScreenLiftScript {
    val source="""
Java.perform(function(){
    const ctx=Java.use('android.app.ActivityThread').currentApplication();
    const Global=Java.use('android.provider.Settings__DOLLAR__Global');
    // Same live query as ScreenLiftUtils.checkScreenFrontApp, not AppUtils' cached top.
    const Displays=Java.use('com.qinggan.os.ServiceManager');
    const Intent=Java.use('android.content.Intent');
    let state=2;
    let generation=0;
    const pending=[null,null];
    try{state=Number(Java.use('com.qinggan.utils.ScreenLiftUtils').getScreenLiftState());}catch(e){console.log('VI_LIFT_STATE '+e);}
    globalThis.VI_LAUNCHER_SCREEN_UP=function(){return state!==1;};
    function enabled(){
        return Global.getInt(ctx.getContentResolver(),'voyah_iran_launcher_home',0)===1 &&
            ['voyah_iran_launcher_lease','voyah_iran_launcher_vip_lease'].every(function(k){const n=Number(Global.getString(ctx.getContentResolver(),k))-Date.now();return n>0&&n<=90000;});
    }
    function activity(top){const parts=top.split('/');let a=parts[parts.length-1];return a.charAt(0)==='.'?parts[0]+a:a;}
    function open(screen,drawer,stock){
        if(screen===1 && Java.use('android.os.SystemProperties').getBoolean('persist.second.screen.disable',false))return;
        const key=stock+':'+drawer, now=Date.now();
        if(pending[screen] && pending[screen].key===key && now-pending[screen].at<1500)return;
        const i=Intent.__DOLLAR__new('android.intent.action.MAIN');
        if(stock){
            i.setClassName('com.qinggan.app.launcher',screen===0?'com.qinggan.app.launcher.activity.MainActivity':
                (drawer?'com.qinggan.secondlauncher.activity.AllAppActivity':'com.qinggan.secondlauncher.activity.SecondMainActivity'));
            if(screen===0){
                Java.use('com.qinggan.launcher.base.BaseApplication').setMainPagePosition(drawer?1:0);
                i.putExtra.overload('java.lang.String','boolean').call(i,'showAllApp',drawer);
                i.putExtra.overload('java.lang.String','java.lang.String').call(i,'extra','home');
            }
        }else{
            i.setClassName('com.abrar.voyahiran','com.abrar.voyahiran.feature.launcher.'+
                (screen===0?(drawer?'LauncherAppsActivity':'VoyahLauncherActivity'):(drawer?'PassengerAppsActivity':'PassengerLauncherActivity')));
        }
        i.addFlags(0x10000000|0x04000000);
        const options=Java.use('android.app.ActivityOptions').makeBasic();options.setLaunchDisplayId(screen);
        pending[screen]={key:key,at:now};
        try{ctx.startActivity(i,options.toBundle());}catch(e){pending[screen]=null;throw e;}
        console.log('VI_LIFT_SWITCH display='+screen+' stock='+stock+' drawer='+drawer);
    }
    function reconcile(onlyScreen){const expected=generation;Java.scheduleOnMainThread(function(){
        if(expected!==generation || !enabled())return;
        for(let screen=0;screen<2;screen++)try{
            if(onlyScreen!==undefined && screen!==onlyScreen)continue;
            const a=activity(String(Displays.getDpyTopAppInfo(ctx,screen,4)));
            if(state===1){
                const prefix='com.abrar.voyahiran.feature.launcher.';
                if(a===prefix+(screen===0?'VoyahLauncherActivity':'PassengerLauncherActivity'))open(screen,false,true);
                else if(a===prefix+(screen===0?'LauncherAppsActivity':'PassengerAppsActivity'))open(screen,true,true);
            }else if(state===2){
                if(screen===0 && a==='com.qinggan.app.launcher.activity.MainActivity'){
                    const page=Number(Java.use('com.qinggan.launcher.base.BaseApplication').getMainPagePosition());
                    if(page===0||page===1)open(screen,page===1,false);
                }else if(screen===1 && a==='com.qinggan.secondlauncher.activity.SecondMainActivity')open(screen,false,false);
                else if(screen===1 && a==='com.qinggan.secondlauncher.activity.AllAppActivity')open(screen,true,false);
            }
        }catch(e){console.log('VI_LIFT_SWITCH_ERROR '+e);}
    });}
    globalThis.VI_LIFT_RECONCILE=reconcile;
    try{
        const Receiver=Java.registerClass({name:'com.abrar.voyahiran.bridge.LauncherLiftReceiver',superClass:Java.use('android.content.BroadcastReceiver'),methods:{onReceive:{returnType:'void',argumentTypes:['android.content.Context','android.content.Intent'],implementation:function(c,i){
            if(String(i.getAction())==='action.qg.layout.changed'){
                const next=i.getIntExtra('type',0);if(next!==1&&next!==2)return;
                // Boot can repeat the current layout; only a real lift transition owns navigation.
                if(next===state)return;
                state=next;generation++;pending[0]=pending[1]=null;
                if(state===1 && typeof globalThis.VI_HOME_UNCOVER==='function')globalThis.VI_HOME_UNCOVER();
                reconcile();
            }else if(state===1){reconcile();}
        }}}});
        const filter=Java.use('android.content.IntentFilter').__DOLLAR__new('action.qg.layout.changed');
        filter.addAction('android.intent.action.TOP_ACTIVITY_CHANGED');
        const receiver=Java.retain(Receiver.__DOLLAR__new());ctx.registerReceiver(receiver,filter);
    }catch(e){console.log('VI_LIFT_LISTENER_ERROR '+e);}
    // Already lowered when the bridge attaches: leave the factory interface visible.
    if(state===1)reconcile();
});
""".trimIndent().replace("__DOLLAR__","$")
}
