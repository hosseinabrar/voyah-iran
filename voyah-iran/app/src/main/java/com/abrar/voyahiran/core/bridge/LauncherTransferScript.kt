package com.abrar.voyahiran.core.bridge

/** A non-focusable passenger mask around OEM launches from an owned map display. */
internal object LauncherTransferScript {
    val source = """
Java.perform(function () {
    try {
        const Global = Java.use('android.provider.Settings' + String.fromCharCode(36) + 'Global');
        const ATM = Java.use('android.app.ActivityTaskManager');
        const Stack = Java.use('android.app.ActivityManager' + String.fromCharCode(36) + 'StackInfo');
        const DM = Java.use('android.hardware.display.DisplayManager');
        const WM = Java.use('android.view.WindowManager');
        const LP = Java.use('android.view.WindowManager' + String.fromCharCode(36) + 'LayoutParams');
        const View = Java.use('android.view.View');
        const JString = Java.use('java.lang.String');
        const Point = Java.use('android.graphics.Point');
        const Anim = Java.use('com.pateo.material.anim.ActivityAnimUtils');
        const launch = Anim.startActivityByAnim.overload('android.content.Context', 'android.content.Intent', 'int');
        let current = null;
        function startCovered(s) {
            if(s.launched || s.removed)return;
            s.launched=true;
            if(s.drawListener) s.view.getViewTreeObserver().removeOnDrawListener(s.drawListener);
            try {launch.call(Anim,s.ctx,s.intent,1);poll(s);} catch(e){finish(s,false,'launch '+e);}
        }
        const DrawListener = Java.registerClass({
            name:'com.qinggan.launcher.VoyahMapMaskDrawListener',
            implements:[Java.use('android.view.ViewTreeObserver' + String.fromCharCode(36) + 'OnDrawListener')],
            methods:{onDraw:function(){
                const s=current;
                if(s===null || s.launched || s.drawQueued)return;
                s.drawQueued=true;
                setTimeout(function(){Java.scheduleOnMainThread(function(){startCovered(s);});},16);
            }}
        });
        function stacks() {
            const list = ATM.getService().getAllStackInfos(), out = [];
            for (let i=0; i<list.size(); i++) out.push(Java.cast(list.get(i), Stack));
            return out;
        }
        function owns(s,pkg) {
            const top=s.topActivity.value;
            if (top !== null && String(top.getPackageName())===pkg) return true;
            const names=s.taskNames.value;
            return names !== null && Array.prototype.some.call(names,function(n) { return String(n).indexOf(pkg+'/')===0; });
        }
        function publish(s,state) {
            Global.putString(s.ctx.getContentResolver(),'voyah_iran_map_transfer',JSON.stringify({pkg:s.pkg,display:1,state:state,time:Date.now()}));
        }
        function remove(s) {
            if(s.removed)return;
            s.removed=true;
            try { s.wm.removeView(s.view); } catch(e) {}
            if(current===s)current=null;
        }
        function finish(s,fade,reason) {
            if(s.ending)return;
            s.ending=true;
            try {publish(s,'end');} catch(e) {}
            console.log('VI_MAP_TRANSFER end reason='+reason+' package='+s.pkg);
            if(fade) {
                s.view.animate().alpha(0).setDuration(350).start();
                setTimeout(function(){Java.scheduleOnMainThread(function(){remove(s);});},390);
            } else remove(s);
        }
        function poll(s) {
            if(s.removed || s.ending)return;
            try {
                const elapsed=Date.now()-s.started;
                const found=stacks().filter(function(x){return Number(x.displayId.value)===1 && owns(x,s.pkg);})[0];
                let ready=false;
                if(found) {
                    const top=found.topActivity.value, bounds=found.bounds.value;
                    ready=top!==null && String(top.getPackageName())===s.pkg && found.visible.value &&
                        (bounds.isEmpty() || (Math.abs(Number(bounds.left.value)-s.left)<=2 && Math.abs(Number(bounds.top.value)-s.top)<=2 &&
                            Math.abs(bounds.width()-s.width)<=2 && Math.abs(bounds.height()-s.height)<=2));
                }
                s.stable=ready ? s.stable+1 : 0;
                if(s.stable>=3 && elapsed>=500) {finish(s,true,'bounds-ready');return;}
                if(elapsed>=10000) {finish(s,false,'timeout');return;}
            } catch(e) {finish(s,false,'error '+e);return;}
            setTimeout(function(){Java.scheduleOnMainThread(function(){poll(s);});},120);
        }
        launch.implementation=function(ctx,intent,display) {
            if(Number(display)!==1) return launch.call(this,ctx,intent,display);
            if(current!==null) {finish(current,false,'superseded');return launch.call(this,ctx,intent,display);}
            let s=null;
            try {
                const remaining=Number(Global.getString(ctx.getContentResolver(),'voyah_iran_launcher_lease'))-Date.now();
                if(remaining<=0 || remaining>90000 || Global.getInt(ctx.getContentResolver(),'voyah_iran_launcher_home',0)!==1)
                    return launch.call(this,ctx,intent,display);
                const component=intent.getComponent();
                const pkg=component===null ? String(intent.getPackage() || '') : String(component.getPackageName());
                const dm=Java.cast(ctx.getSystemService('display'),DM);
                const source=stacks().filter(function(x){
                    const d=dm.getDisplay(Number(x.displayId.value));
                    return d!==null && String(d.getName())==='VoyahIran.MapPreview' && owns(x,pkg);
                })[0];
                if(!source)return launch.call(this,ctx,intent,display);
                const dest=dm.getDisplay(1);
                if(dest===null)return launch.call(this,ctx,intent,display);
                const dc=ctx.createDisplayContext(dest), wm=Java.cast(dc.getSystemService('window'),WM);
                const view=View.${'$'}new(dc); view.setBackgroundColor(-16777216);
                const size=Point.${'$'}new();dest.getRealSize(size);
                const resolver=ctx.getContentResolver();
                const raw=Global.getString(resolver,'voyah_iran_display_policy');
                const ackRaw=Global.getString(resolver,'voyah_iran_display_policy_ack');
                const request=raw===null?null:String(raw), ack=ackRaw===null?'':String(ackRaw);
                const enabled=request===null?ack==='1:default':(request==='1'||/^1:[a-f0-9]{32}$/.test(request))&&ack===request;
                const app=ctx.getPackageManager().getApplicationInfo(pkg,0);
                const eligible=pkg==='com.abrar.voyahiran'||((Number(app.flags.value)&129)===0&&Number(app.uid.value)>=10000);
                const w=Number(size.x.value),h=Number(size.y.value);
                const inset=enabled&&eligible&&w>465&&h>250&&w>h;
                const left=inset?145:0,top=inset?50:0;
                const lp=LP.${'$'}new(w-left,h-top,2038,0x18 | 0x100 | 0x200,-1);
                lp.gravity.value=51;lp.x.value=left;lp.y.value=top;
                lp.setTitle(JString.${'$'}new('VoyahIran map transfer'));
                s={ctx:Java.retain(ctx),intent:Java.retain(intent),wm:Java.retain(wm),view:Java.retain(view),
                    pkg:pkg,left:left,top:top,width:w-left,height:h-top,started:Date.now(),stable:0,removed:false,ending:false};
                s.drawListener=DrawListener.${'$'}new();
                view.getViewTreeObserver().addOnDrawListener(s.drawListener);
                current=s;wm.addView(view,lp);publish(s,'start');
                console.log('VI_MAP_TRANSFER start package='+pkg+' bounds='+left+','+top+','+w+','+h);
                // Launch after the black overlay draws. A missing draw must not swallow the app launch.
                setTimeout(function(){Java.scheduleOnMainThread(function(){
                    if(!s.launched && !s.removed) {
                        s.launched=true;finish(s,false,'mask-draw-timeout');
                        launch.call(Anim,s.ctx,s.intent,1);
                    }
                });},800);
                return;
            } catch(e) {
                if(s!==null)finish(s,false,'setup '+e);
                console.log('VI_MAP_TRANSFER fallback '+e);
            }
            return launch.call(this,ctx,intent,display);
        };
        console.log('VI_MAP_TRANSFER_READY');
    } catch(e) {console.log('VI_MAP_TRANSFER_UNAVAILABLE '+e);}
});
""".trimIndent()
}
