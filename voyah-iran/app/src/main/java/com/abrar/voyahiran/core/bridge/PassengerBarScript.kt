package com.abrar.voyahiran.core.bridge

/** H97C has no DragService/NavigationBarController; retain its native window animations. */
internal object PassengerBarScript {
    val source = """
Java.perform(function(){
    try {
        try {Java.use('com.qinggan.launcher.navigation.NavigationBarController');return;}catch(_){}
        const Bar=Java.use('com.qinggan.mainlauncher.navigation.NavigationBar');
        const Model=Java.use('com.qinggan.app.launcher.LauncherModel');
        const show=Bar.show.overload(), handler=Model.handleUpdateSecondNavigationBar.overload('java.lang.String','java.lang.String','boolean');
        let model=null, hidden=false, queued=false;
        let eventRetries=0,retryPending=false;
        function home(){return typeof globalThis.VI_PASSENGER_HOME_VISIBLE==='function' && globalThis.VI_PASSENGER_HOME_VISIBLE();}
        function sync(){
            if(model===null || model.mMainScreenMoving.value || model.mSecondScreenMoving.value)return false;
            const raw=model.mSecondScreenNavigationBar.value;if(raw===null)return false;
            const bar=Java.cast(raw,Bar), animator=bar.mMoveWindowAnimator.value;
            if(animator!==null && animator.isStarted())return false;
            if(home()) {
                hidden=true;
                if(bar.mRootView.value.getParent()!==null)bar.dismiss();
            }else if(hidden) {hidden=false;bar.show();}
            return true;
        }
        function schedule(){if(queued)return;queued=true;Java.scheduleOnMainThread(function(){queued=false;try{
            if(sync()){eventRetries=0;}
            else if(eventRetries>0&&!retryPending){eventRetries--;retryPending=true;
                console.log('VI_PASSENGER_BAR_DEFER moving-or-animating remaining='+eventRetries);
                setTimeout(function(){retryPending=false;schedule();},100);}
        }catch(e){console.log('VI_PASSENGER_BAR_SYNC '+e);}});}
        show.implementation=function(){
            if(Number(this.mScreenId.value)===1 && home()){hidden=true;schedule();return;}
            return show.call(this);
        };
        handler.implementation=function(pkg,activity,visible){
            if(model===null)model=Java.retain(this);
            const hide=home();if(hide)hidden=true;
            const result=handler.call(this,pkg,activity,hide?false:visible);schedule();return result;
        };
        Java.choose('com.qinggan.app.launcher.LauncherModel',{onMatch:function(v){model=Java.retain(v);return 'stop';},onComplete:schedule});
        const receive=Model.onReceive.overload('android.content.Context','android.content.Intent');
        receive.implementation=function(ctx,intent){
            const result=receive.call(this,ctx,intent);
            if(String(intent.getAction())==='android.intent.action.TOP_ACTIVITY_CHANGED' && intent.getIntExtra('displayId',-1)===1){
                if(model===null)model=Java.retain(this);
                eventRetries=20;console.log('VI_PASSENGER_BAR_EVENT foreground');schedule();
            }
            return result;
        };
        // Recovery only; foreground changes trigger sync immediately above.
        setInterval(schedule,1000);
    }catch(e){console.log('VI_PASSENGER_BAR_UNAVAILABLE '+e);}
});
""".trimIndent()
}
