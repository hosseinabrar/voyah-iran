package com.abrar.voyahiran.core.bridge

/** Short map-only pan followed by the stock return-to-driving button. Never restarts a route. */
internal object NeshanRecenterScript {
    val source = """
function recenterCard(display,token,deadline) {
    let finished=false,root=null,map=null,route=null,down=false,downAt=0,x=0,y=0;
    const cfg=NESHAN_PROFILE.cardRecenter,rs=NESHAN_PROFILE.hook.routeState;
    function result(reason) {
        if(finished)return;
        if(down && map) {try{touch(3,x,y);}catch(e){}down=false;}
        finished=true;
        console.log('VI_NESHAN_RECENTER_RESULT '+token+' '+reason);
        if(map)map.${'$'}dispose();if(root)root.${'$'}dispose();if(route)route.${'$'}dispose();
    }
    function valid() {
        if(Date.now()>deadline)return 'request_expired';
        if(!enabled || Date.now()-heartbeat>15000)return 'hook_inactive';
        if(!route[rs.activeMethod]() || route[rs.engineMethod]()===null)return 'navigation_inactive';
        const d=root.getDisplay();
        if(d===null || Number(d.getDisplayId())!==display || String(d.getName())!=='VoyahIran.MapPreview')return 'left_card';
        if(!root.isAttachedToWindow() || !root.isShown() || root.getWindowVisibility()!==0)return 'map_root_hidden_or_detached';
        if(root.getWidth()<=0 || root.getHeight()<=0 || root.isLayoutRequested())return 'map_root_layout_pending';
        return null;
    }
    function touch(action,px,py) {
        const Event=Java.use('android.view.MotionEvent'),now=Java.use('android.os.SystemClock').uptimeMillis();
        const e=Event.obtain.overload('long','long','int','float','float','int').call(Event,downAt,now,action,px,py,0);
        try {e.setSource(4098);return map.dispatchTouchEvent(e);} finally {e.recycle();}
    }
    function button() {
        const id=root.getResources().getIdentifier(cfg.buttonId,'id','org.rajman.neshan.traffic.tehran.navigator');
        return id?root.findViewById(id):null;
    }
    function clickIfReady() {
        const b=button();
        if(b===null || !b.isAttachedToWindow() || !b.isShown() || !b.isEnabled() || !b.isClickable())return false;
        if(!b.performClick()){result('follow_click_rejected');return true;}
        console.log('VI_NESHAN_RECENTER_PHASE '+token+' stock_follow_clicked');
        result('applied');return true;
    }
    function later(fn,ms) {
        setTimeout(function(){Java.scheduleOnMainThread(function(){
            if(finished)return;
            try {const reason=valid();if(reason)return result(reason);fn();}
            catch(e){result('gesture_exception:'+String(e).replace(/[\r\n]/g,' ').slice(0,300));}
        });},ms);
    }
    Java.perform(function(){Java.scheduleOnMainThread(function(){
        try {
            if(!cfg || !rs)return result('profile_missing');
            const raw=Java.use(rs.ownerClass)[rs.instanceField].value;
            if(raw===null)return result('route_owner_missing');
            route=Java.retain(Java.cast(raw,Java.use(rs.stateClass)));
            const wm=Java.use('android.view.WindowManagerGlobal').getInstance();
            const field=wm.getClass().getDeclaredField('mViews');field.setAccessible(true);
            const roots=Java.cast(field.get(wm),Java.use('java.util.List'));
            const View=Java.use('android.view.View'),Group=Java.use('android.view.ViewGroup'),Map=Java.use('com.carto.ui.MapView');
            const matches=[];
            function visit(v,r,depth) {
                if(depth>40)return;
                if(Map.class.isInstance(v) && v.isShown() && v.isAttachedToWindow() && v.getWidth()>0 && v.getHeight()>0)matches.push({root:r,map:v});
                if(Group.class.isInstance(v)) {const g=Java.cast(v,Group);for(let i=0;i<g.getChildCount();i++)visit(g.getChildAt(i),r,depth+1);}
            }
            for(let i=0;i<roots.size();i++) {
                const r=Java.cast(roots.get(i),View),d=r.getDisplay();
                if(d!==null && Number(d.getDisplayId())===display && String(d.getName())==='VoyahIran.MapPreview' && r.isShown())visit(r,r,0);
            }
            if(matches.length!==1)return result('map_view_count:'+matches.length);
            root=Java.retain(matches[0].root);map=Java.retain(Java.cast(matches[0].map,View));
            const reason=valid();if(reason)return result(reason);
            if(clickIfReady())return;
            if(deadline-Date.now()<500)return result('insufficient_gesture_time');
            const slop=Java.use('android.view.ViewConfiguration').get(map.getContext()).getScaledTouchSlop();
            const distance=Math.max(24,Number(slop)*2+4);
            if(distance>map.getWidth()/4)return result('map_too_small_for_pan');
            x=map.getWidth()/2;y=map.getHeight()/2;
            downAt=Java.use('android.os.SystemClock').uptimeMillis();down=true;
            if(!touch(0,x,y))return result('map_touch_rejected');
            console.log('VI_NESHAN_RECENTER_PHASE '+token+' pan_started distance='+distance);
            later(function(){x+=distance/2;touch(2,x,y);
                later(function(){x+=distance/2;touch(2,x,y);
                    later(function(){touch(1,x,y);down=false;
                        function waitButton(){if(!clickIfReady())later(waitButton,80);}
                        later(waitButton,80);
                    },40);
                },40);
            },40);
        }catch(e){result('gesture_setup_exception:'+String(e).replace(/[\r\n]/g,' ').slice(0,300));}
    });});
}
""".trimIndent()
}
