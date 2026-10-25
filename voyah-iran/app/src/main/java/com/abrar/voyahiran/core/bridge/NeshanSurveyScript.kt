package com.abrar.voyahiran.core.bridge

/** Close the route-quality sheet; temporarily all displays for hardware testing. */
internal object NeshanSurveyScript {
    val source = """
let restoreSurvey=function(){};
let dismissCardSurveys=function(){};
function installSurveyDismissal() {
    const config=profile.surveySuppression;
    if(!config || !config.enabled)return;
    const Dialog=Java.use('android.app.Dialog');
    const show=Dialog.show.overload();
    const tracked=Java.use('java.util.WeakHashMap').${'$'}new();
    function survey(dialog) {
        const window=dialog.getWindow();
        if(window===null)return false;
        const root=window.getDecorView(),resources=root.getResources();
        return config.requiredViewIds.every(function(name){
            const id=resources.getIdentifier(name,'id',config.resourcePackage);
            return id!==0 && root.findViewById(id)!==null;
        });
    }
    const pending=Java.use('java.util.WeakHashMap').${'$'}new();
    const Listener=Java.registerClass({
        name:'com.abrar.voyahiran.NeshanSurveyAttachListener',
        implements:[Java.use('android.view.View${'$'}OnAttachStateChangeListener')],
        methods:{
            onViewAttachedToWindow:function(view){
                // Defer dismissal out of the framework's attachment traversal.
                view.removeOnAttachStateChangeListener(this);
                pending.remove(view);
                Java.scheduleOnMainThread(function(){dismissCardSurveys();});
            },
            onViewDetachedFromWindow:function(view){}
        }
    });
    function closeSurvey(dialog) {
        if(!enabled || Date.now()-heartbeat>15000 || !dialog.isShowing())return;
        const root=dialog.getWindow().getDecorView(),display=root.getDisplay();
        // Temporary test scope: all displays. Restore card-only filtering after approval.
        if(!root.isAttachedToWindow())return;
        // Normal dismiss lifecycle, like tapping outside; never click a rating.
        dialog.dismiss();
        console.log('VI_NESHAN_SURVEY_DISMISSED display='+(display===null?'unknown':display.getDisplayId()));
    }
    show.implementation=function(){
        const result=show.call(this);
        try {
            if(enabled && survey(this)) {
                tracked.put(this,Java.use('java.lang.Boolean').TRUE.value);
                const root=this.getWindow().getDecorView();
                if(!root.isAttachedToWindow() && !pending.containsKey(root)) {
                    const listener=Listener.${'$'}new();
                    pending.put(root,listener);
                    root.addOnAttachStateChangeListener(listener);
                }
                closeSurvey(this);
            }
        }catch(e){console.log('VI_NESHAN_SURVEY_ERROR '+e);}
        return result;
    };
    // Called once on card arrival too: a sheet may have opened in fullscreen.
    dismissCardSurveys=function(){
        try {
            const dialogs=tracked.keySet().iterator();
            while(dialogs.hasNext())closeSurvey(Java.cast(dialogs.next(),Dialog));
        }catch(e){console.log('VI_NESHAN_SURVEY_ERROR '+e);}
    };
    restoreSurvey=function(){
        show.implementation=null;
        const entries=pending.entrySet().iterator();
        while(entries.hasNext()) {
            const entry=entries.next();
            Java.cast(entry.getKey(),Java.use('android.view.View')).removeOnAttachStateChangeListener(
                Java.cast(entry.getValue(),Java.use('android.view.View${'$'}OnAttachStateChangeListener')));
        }
        pending.clear();tracked.clear();dismissCardSurveys=function(){};
    };
}
""".trimIndent()
}
