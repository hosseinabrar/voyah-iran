package com.abrar.voyahiran.core.bridge

/** Replays only the stock follow-camera state update, never navigation start/stop. */
internal object NeshanRecenterScript {
    val source = """
function recenterCard(display) {
    Java.scheduleOnMainThread(function() {
        try {
            const cfg=NESHAN_PROFILE.cardRecenter, rs=NESHAN_PROFILE.hook.routeState;
            if(!cfg || !rs || !enabled || Date.now()-heartbeat>15000)return;
            const owner=Java.use(rs.ownerClass);
            const raw=owner[rs.instanceField].value;
            if(raw===null)return;
            const route=Java.cast(raw,Java.use(rs.stateClass));
            if(!route[rs.activeMethod]() || route[rs.engineMethod]()===null)return;
            const wm=Java.use('android.view.WindowManagerGlobal').getInstance();
            const field=wm.getClass().getDeclaredField('mViews');field.setAccessible(true);
            const roots=Java.cast(field.get(wm),Java.use('java.util.List'));
            const View=Java.use('android.view.View');
            let button=null;
            for(let i=0;i<roots.size();i++) {
                const root=Java.cast(roots.get(i),View), d=root.getDisplay();
                if(d===null || Number(d.getDisplayId())!==display || String(d.getName())!=='VoyahIran.MapPreview' ||
                    !root.isShown() || root.getWidth()<=0 || root.getHeight()<=0 || root.isLayoutRequested())continue;
                const id=root.getResources().getIdentifier(cfg.buttonId,'id','org.rajman.neshan.traffic.tehran.navigator');
                const found=id ? root.findViewById(id) : null;
                if(found!==null) { if(button!==null)return;button=Java.cast(found,View); }
            }
            if(button===null || !button.isAttachedToWindow())return;
            // The follow button is normally GONE while already following. Its stock
            // click guard would do nothing; use the exact state-copy/update body.
            const li=View.class.getDeclaredField('mListenerInfo');li.setAccessible(true);
            const info=li.get(button);if(info===null)return;
            const click=info.getClass().getDeclaredField('mOnClickListener');click.setAccessible(true);
            const rawListener=click.get(info);if(rawListener===null)return;
            const listenerType=Java.use(cfg.listenerClass);
            if(!listenerType.class.isInstance(rawListener))return;
            const listener=Java.cast(rawListener,listenerType);
            const fragment=listener[cfg.fragmentField].value;
            const model=fragment[cfg.modelField].value;
            const current=model[cfg.stateMethod]().g();if(current===null)return;
            const builder=Java.use(cfg.builderClass).${'$'}new(Java.cast(current,Java.use(cfg.cameraStateClass)));
            builder.e(Java.use(cfg.followClass).${'$'}new());
            model[cfg.applyMethod](builder.b());
            console.log('VI_NESHAN_RECENTER applied display='+display);
        } catch(e) {console.log('VI_NESHAN_RECENTER failed '+e);}
    });
}
""".trimIndent()
}
