package com.abrar.voyahiran.core.bridge

/** Separate from maneuver epochs: only a real navigation start creates a trip. */
internal object NeshanTripScript {
    val source="""
const VI_TRIP = (function() {
    const cfg = NESHAN_PROFILE.routeRestart, stateCfg = NESHAN_PROFILE.hook.routeState;
    let startHook=null, stopHook=null, trip=null, serial=0, disabled=false;
    const epoch=Date.now().toString();
    function state() {
        const owner=Java.use(stateCfg.ownerClass);
        const field=owner.class.getDeclaredField(stateCfg.instanceField);
        field.setAccessible(true);
        const value=field.get(null);
        return value===null ? null : Java.cast(value,Java.use(stateCfg.stateClass));
    }
    function active() {
        const s=state();
        return s!==null && s[stateCfg.activeMethod]()===true && s[stateCfg.engineMethod]()!==null;
    }
    function sameDestination() {
        const engine=state()[stateCfg.engineMethod]();
        const point=engine[cfg.navigatorMethod]()[cfg.currentDestinationMethod]();
        return point!==null && Math.abs(Number(point[cfg.latitudeMethod]())-trip.lat)<0.000001 &&
            Math.abs(Number(point[cfg.longitudeMethod]())-trip.lon)<0.000001;
    }
    function activity() {
        const thread=Java.use('android.app.ActivityThread').currentActivityThread();
        const f=thread.getClass().getDeclaredField('mActivities');f.setAccessible(true);
        const records=Java.cast(f.get(thread),Java.use('java.util.Map')).values().iterator();
        let found=null;
        while(records.hasNext()) {
            const record=records.next(), af=record.getClass().getDeclaredField('activity');af.setAccessible(true);
            const a=Java.cast(af.get(record),Java.use('android.app.Activity'));
            if(String(a.getClass().getName())!==cfg.activityClass || a.isFinishing() || a.isDestroyed())continue;
            if(!a.getWindow().getDecorView().isShown())continue;
            if(found!==null)return null; // Ambiguous windows are never guessed.
            found=a;
        }
        return found;
    }
    function location(a) {
        if(a===null)return {origin:'unknown',display:-1};
        const d=a.getWindowManager().getDefaultDisplay(), id=Number(d.getDisplayId());
        const name=String(d.getName());
        return {origin:id>1 && name==='VoyahIran.MapPreview' ? 'card' : (id===0 || id===1 ? 'external' : 'unknown'),display:id};
    }
    function publish() {
        if(!cfg || disabled)return;
        try {
            const running=active(), valid=trip!==null && running;
            console.log('VI_NESHAN_TRIP '+JSON.stringify(trip===null ? {valid:false,active:running} : Object.assign({},trip,{valid:valid,active:running})));
        } catch(e) {console.log('VI_NESHAN_TRIP '+JSON.stringify({valid:false}));}
    }
    function install() {
        if(!cfg || !stateCfg)return;
        try {
            const Type=Java.use(stateCfg.stateClass);
            const start=Type[cfg.startMethod].overload(cfg.routeClass,'int','java.lang.String','java.lang.String');
            const stop=Type[cfg.stopMethod].overload();
            startHook=start;stopHook=stop;
            start.implementation=function(route,index,a,b) {
                let next=null;
                try {
                    const meta=route[cfg.metadataMethod](), point=meta[cfg.destinationMethod]();
                    const lat=Number(point[cfg.latitudeMethod]()),lon=Number(point[cfg.longitudeMethod]());
                    const stops=meta[cfg.stopsMethod]();
                    if(!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180)throw Error('coordinates');
                    next=Object.assign({id:epoch+'-'+(++serial),lat:lat,lon:lon,stops:stops===null ? 0 : Number(stops.size()),
                        mode:String(meta[cfg.routingTypeMethod]().name()),attempted:false,stage:'active'},location(activity()));
                } catch(e) {next=null;}
                const result=start.call(this,route,index,a,b);
                trip=next;publish();return result;
            };
            stop.implementation=function() {
                const result=stop.call(this);
                if(trip!==null)trip.stage=trip.stage==='stopping' ? 'stopped' : 'ended';
                publish();return result;
            };
        } catch(e) {disable();console.log('VI_NESHAN_TRIP_UNAVAILABLE');}
    }
    function cancel(id,display) {
        Java.scheduleOnMainThread(function() {
            try {
                if(disabled || !cfg || trip===null || trip.id!==id || trip.attempted || trip.origin!=='external' ||
                    trip.stops!==0 || trip.mode!=='CAR' || !active() || !sameDestination())return;
                const a=activity(), where=location(a);
                if(where.origin!=='card' || where.display!==display)return;
                const resource=a.getResources().getIdentifier(cfg.stopButtonId,'id',String(a.getPackageName()));
                const button=a.findViewById(resource);
                if(button===null || !button.isAttachedToWindow() || !button.isEnabled() || !button.hasOnClickListeners())return;
                // The stock end-trip action, never an engine reset or force-stop.
                // Set the one-shot marker before the click, even if cancellation fails.
                trip.attempted=true;trip.stage='stopping';
                if(!button.performClick())trip.stage='failed';
                publish();
            } catch(e) {if(trip!==null && trip.id===id)trip.stage='failed';publish();}
        });
    }
    function disable() {
        disabled=true;trip=null;
        if(startHook)startHook.implementation=null;
        if(stopHook)stopHook.implementation=null;
    }
    return {install:install,publish:publish,cancel:cancel,disable:disable};
})();
""".trimIndent()
}
