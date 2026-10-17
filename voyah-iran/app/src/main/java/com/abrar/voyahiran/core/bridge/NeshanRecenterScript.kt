package com.abrar.voyahiran.core.bridge

/** Bounded stock UI gesture loop. Does not modify the camera model or restart navigation. */
internal object NeshanRecenterScript {
    val source = """
let recenterSession=null;
function cancelRecenterSession(){if(recenterSession)recenterSession.stop('cancelled');}
function recenterCard(display,token,deadline) {
    cancelRecenterSession();
    const received=Date.now();
    let finished=false,root=null,down=false,downAt=0,x=0,y=0,attempt=0,method='setup',buttonSeen=false;
    function call(o,n,...args){method=n;return o[n](...args);}
    function touch(action){
        method='MotionEvent.obtain/dispatchTouchEvent';
        const E=Java.use('android.view.MotionEvent'),now=Java.use('android.os.SystemClock').uptimeMillis();
        const e=E.obtain.overload('long','long','int','float','float','int').call(E,downAt,now,action,x,y,0);
        try{e.setSource(4098);return root.dispatchTouchEvent(e);}finally{e.recycle();}
    }
    function stop(reason){
        if(finished)return;
        finished=true;
        if(down&&root){try{touch(3);}catch(e){}down=false;}
        if(root){root.${'$'}dispose();root=null;}
        if(recenterSession===session)recenterSession=null;
        console.log('VI_NESHAN_RECENTER_RESULT '+token+' '+reason);
    }
    const session={stop:stop};recenterSession=session;
    function fail(e){
        console.log('VI_NESHAN_RECENTER_ERROR '+token+' method='+method+' '+String(e));
        String(e.stack||e).split(/\r?\n/).slice(0,16).forEach(function(s){console.log('VI_NESHAN_RECENTER_STACK '+token+' '+s);});
        stop('gesture_exception');
    }
    function valid(){
        if(Date.now()>=deadline)return 'request_expired';
        if(!enabled||Date.now()-heartbeat>15000)return 'hook_inactive';
        const rs=NESHAN_PROFILE.hook.routeState;
        const raw=Java.use(rs.ownerClass)[rs.instanceField].value;
        if(raw===null)return 'navigation_inactive';
        const state=Java.cast(raw,Java.use(rs.stateClass));
        if(!call(state,rs.activeMethod)||call(state,rs.engineMethod)===null)return 'navigation_inactive';
        if(root){
            const d=call(root,'getDisplay');
            if(d===null||Number(d.getDisplayId())!==display||String(d.getName())!=='VoyahIran.MapPreview'||!root.isAttachedToWindow()||!root.isShown())return 'left_card';
        }
        return null;
    }
    function schedule(fn,ms){
        setTimeout(function(){
            if(finished)return;
            const queued=Date.now();
            Java.scheduleOnMainThread(function(){
                if(finished)return;
                try{
                    console.log('VI_NESHAN_RECENTER_TIMING '+token+' queueMs='+(Date.now()-queued)+' elapsedMs='+(Date.now()-received));
                    const reason=valid();if(reason)return stop(reason);fn();
                }catch(e){fail(e);}
            });
        },ms);
    }
    function tick(){
        const started=Date.now();attempt++;
        if(!root){
            // Only inspect top-level windows. No recursive view traversal or map cache.
            const wm=Java.use('android.view.WindowManagerGlobal').getInstance();
            const f=wm.getClass().getDeclaredField('mViews');f.setAccessible(true);
            const roots=Java.cast(f.get(wm),Java.use('java.util.List')),V=Java.use('android.view.View');
            for(let i=0;i<roots.size();i++){
                const r=Java.cast(roots.get(i),V),d=r.getDisplay();
                if(d!==null&&Number(d.getDisplayId())===display&&String(d.getName())==='VoyahIran.MapPreview'&&r.isShown()){
                    if(root)return stop('multiple_card_windows');
                    root=Java.retain(r);
                }
            }
        }
        if(!root){schedule(tick,200);return;}
        const reason=valid();if(reason)return stop(reason);
        // followButton belongs to free driving, not active turn-by-turn navigation.
        const name='back_to_navigation_button';
        const resources=call(root,'getResources');
        const id=call(resources,'getIdentifier',name,'id','org.rajman.neshan.traffic.tehran.navigator');
        const raw=id?root.findViewById(id):null;
        const b=raw===null?null:Java.cast(raw,Java.use('android.view.View'));
        console.log('VI_NESHAN_RECENTER_TIMING '+token+' attempt='+attempt+' lookupMs='+(Date.now()-started));
        let blocked=id?'view_missing':'resource_id_missing';
        if(b){
            const shown=call(b,'isShown'),attached=call(b,'isAttachedToWindow'),enabledButton=call(b,'isEnabled');
            const clickable=call(b,'isClickable'),listener=call(b,'hasOnClickListeners');
            // The view exists even while GONE. Latch only once it is actually shown.
            if(shown&&attached)buttonSeen=true;
            blocked=!attached?'detached':!shown?'hidden':!enabledButton?'disabled':!listener?'listener_missing':'none';
            console.log('VI_NESHAN_RECENTER_BUTTON '+token+' attempt='+attempt+' name='+name+' id='+id+' found=true class='+String(b.${'$'}className)+' shown='+shown+' visibility='+call(b,'getVisibility')+' attached='+attached+' enabled='+enabledButton+' clickable='+clickable+' listener='+listener+' blocked='+blocked+' dragStopped='+buttonSeen);
            // performClick invokes the registered listener; isClickable is not its prerequisite.
            if(blocked==='none'){
                const clicked=call(b,'performClick');
                console.log('VI_NESHAN_RECENTER_BUTTON '+token+' performClick='+clicked);
                if(clicked)return stop('applied');
            }
        }else{
            console.log('VI_NESHAN_RECENTER_BUTTON '+token+' attempt='+attempt+' name='+name+' id='+id+' found=false blocked='+blocked+' dragStopped='+buttonSeen);
        }
        if(buttonSeen){schedule(tick,200);return;}
        if(root.isLayoutRequested()||root.getWidth()<100||root.getHeight()<100){schedule(tick,200);return;}
        x=root.getWidth()/2;y=root.getHeight()/2;
        const distance=Math.max(24,Number(Java.use('android.view.ViewConfiguration').get(root.getContext()).getScaledTouchSlop())*2+4);
        const direction=attempt%2?1:-1;
        downAt=Java.use('android.os.SystemClock').uptimeMillis();down=true;
        if(!touch(0))return stop('touch_rejected');
        console.log('VI_NESHAN_RECENTER_PHASE '+token+' drag attempt='+attempt);
        schedule(function(){x+=direction*distance;touch(2);
            schedule(function(){touch(1);down=false;schedule(tick,Math.max(0,200-(Date.now()-started)));},40);
        },40);
    }
    // Acceptance is separate from completion: do not block the bridge/heartbeat for ten seconds.
    console.log('VI_NESHAN_RECENTER_RESULT '+token+' accepted');
    Java.perform(function(){schedule(tick,0);});
}
""".trimIndent()
}
