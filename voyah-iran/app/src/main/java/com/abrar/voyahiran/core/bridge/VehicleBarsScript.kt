package com.abrar.voyahiran.core.bridge

/** Background only: OEM icons, touch targets and theme handling remain owned by the car. */
internal object VehicleBarsScript {
    val source = """
Java.perform(function () {
    try {
        const Controller=Java.use('com.qinggan.launcher.navigation.NavigationBarController');
        const Navigation=Java.use('com.qinggan.launcher.navigation.INavigationBar');
        const screenUp=Navigation.getScreenUpView.overload();
        const Color=Java.use('android.graphics.drawable.ColorDrawable');
        const View=Java.use('android.view.View');
        const JString=Java.use('java.lang.String');
        const LP=Java.use('android.view.WindowManager'+String.fromCharCode(36)+'LayoutParams');
        const WM=Java.use('android.view.WindowManager');
        // addView is declared by ViewManager, not WindowManager. Frida does not
        // reliably expose methods inherited from another interface on this ROM.
        const addView=Java.use('android.view.ViewManager').addView.overload('android.view.View','android.view.ViewGroup'+String.fromCharCode(36)+'LayoutParams');
        const removeView=WM.removeViewImmediate.overload('android.view.View');
        const DM=Java.use('android.hardware.display.DisplayManager');
        const Point=Java.use('android.graphics.Point');
        const Props=Java.use('android.os.SystemProperties');
        const Lift=Java.use('com.qinggan.utils.ScreenLiftUtils');
        const bars=new Map(), states=new Map();
        function remember(bar) {
            const display=Number(bar.mScreenId.value);
            if(display!==0 && display!==1)return;
            const old=bars.get(display);
            if(old && old.bar.equals(bar))return;
            if(old)restore(old);
            bars.set(display,{bar:Java.retain(bar),view:null,original:null,paint:null});
        }
        function restore(b) {
            try {
                if(b.view!==null && b.paint!==null && b.view.getBackground()!==null && b.view.getBackground().equals(b.paint))
                    b.view.setBackground(b.original);
            } catch(e) {console.log('VI_BAR_RESTORE_ERROR '+e);}
            b.view=null;b.original=null;b.paint=null;
        }
        function removeTop(s) {
            if(s.view!==null) {try{removeView.call(s.wm,s.view);}catch(e){console.log('VI_BAR_REMOVE_ERROR '+e);} s.view=null;}
            s.key='';
        }
        function sync(ctx,display,enabled) {
            if(display!==0 && display!==1)return;
            let s=states.get(display);
            if(!s) {s={view:null,key:'',wm:null};states.set(display,s);}
            const b=bars.get(display);
            let stage="lift-state";
            try {
                // The lowered display has different factory controls/geometry.
                if(!enabled || Number(Lift.getScreenLiftState())!==2) {
                    if(b)restore(b);removeTop(s);return;
                }
                stage='theme';
                const light=String(Props.get('persist.sys.qg.currenttheme','default'))==='simple';
                const color=light ? -1709069 : -16777216; // #e5ebf3 / black
                if(b) {
                    stage='navigation-view';
                    const raw=b.bar.mNavigationBar.value;
                    // Interface-typed Frida handles need not expose Object.getClass().
                    // Invoke the declared OEM method explicitly, without runtime introspection.
                    stage='navigation-screen-up';
                    const rawView=raw===null?null:screenUp.call(raw);
                    const v=rawView===null?null:Java.cast(rawView,View);
                    if(v!==null) {
                        stage='navigation-background';
                        const current=v.getBackground();
                        if(b.view===null || !b.view.equals(v) || b.paint===null || current===null || !current.equals(b.paint)) {
                            restore(b);b.view=Java.retain(v);b.original=current===null?null:Java.retain(current);b.paint=null;
                        }
                        if(b.paint===null || Number(b.paint.getColor())!==color) {
                            b.paint=Color.__DOLLAR__new(color);v.setBackground(b.paint);
                        }
                    }
                }
                stage='display-service';
                const dm=Java.cast(ctx.getSystemService('display'),DM), d=dm.getDisplay(display);
                if(d===null) {if(b)restore(b);removeTop(s);return;}
                const size=Point.__DOLLAR__new();d.getRealSize(size);
                // Same physical safe-area pixels as the framework policy, independent of app DPI.
                const left=145, height=50, width=Number(size.x.value)-left;
                const key=display+':'+width+':'+color;
                if(s.key===key)return;
                removeTop(s);
                if(width<=0)return;
                stage='display-context';
                const dc=ctx.createDisplayContext(d);
                s.wm=Java.cast(dc.getSystemService('window'),WM);
                const v=View.__DOLLAR__new(dc);v.setBackgroundColor(color);
                // OEM QG_STATUSBAR (2118) is layer 17; this is layer 12. Keep x>=145
                // because QG_NAVIGATION_BAR (2110) is layer 3 and must never be covered.
                stage='window-params';
                const lp=LP.__DOLLAR__new(width,height,2038,0x8|0x10|0x100|0x200,-3);
                lp.gravity.value=51;lp.x.value=left;lp.y.value=0;
                lp.setTitle(JString.__DOLLAR__new('VoyahIran.BarBackground.'+display));
                stage='window-add';
                addView.call(s.wm,v,lp);s.view=v;s.key=key;
                console.log('VI_BAR_THEME display='+display+' light='+light);
            } catch(e) {if(b)restore(b);removeTop(s);console.log('VI_BAR_THEME_ERROR stage='+stage+' '+e+' stack='+e.stack);}
        }
        globalThis.VI_SYNC_BAR_BACKGROUND=sync;
        const show=Controller.show.overload();
        show.implementation=function(){remember(this);return show.call(this);};
        globalThis.VI_RELEASE_BAR_BACKGROUND=function(controller){
            const display=Number(controller.mScreenId.value), b=bars.get(display), s=states.get(display);
            if(b && b.bar.equals(controller)){restore(b);bars.delete(display);if(s)removeTop(s);}
        };
        Java.choose('com.qinggan.launcher.navigation.NavigationBarController',{
            onMatch:function(instance){remember(instance);},onComplete:function(){}
        });
        console.log('VI_BAR_THEME_READY');
    } catch(e) {console.log('VI_BAR_THEME_UNAVAILABLE '+e);}
});
""".trimIndent().replace("__DOLLAR__", "$")
}
