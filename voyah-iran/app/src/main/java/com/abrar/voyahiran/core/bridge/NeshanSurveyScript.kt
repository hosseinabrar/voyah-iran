package com.abrar.voyahiran.core.bridge

/** Close only the route-quality sheet on our non-interactive map display. */
internal object NeshanSurveyScript {
    val source = """
let restoreSurvey=function(){};
let dismissCardSurveys=function(){};
function installSurveyDismissal() {
    const Dialog=Java.use('android.app.Dialog');
    const show=Dialog.show.overload();
    const tracked=Java.use('java.util.WeakHashMap').${'$'}new();
    function survey(dialog) {
        const window=dialog.getWindow();
        if(window===null)return false;
        const root=window.getDecorView(),resources=root.getResources();
        // Stable resource names verified in 70789 and 70794, not obfuscated classes
        // or generic dialog text. Require the entire three-choice survey signature.
        return ['firstTitleTextView','badCardView','normalCardView','greatCardView','autoCloseTimerProgressBar'].every(function(name){
            const id=resources.getIdentifier(name,'id','org.rajman.neshan.traffic.tehran.navigator');
            return id!==0 && root.findViewById(id)!==null;
        });
    }
    function closeInCard(dialog) {
        if(!enabled || Date.now()-heartbeat>15000 || !dialog.isShowing())return;
        const root=dialog.getWindow().getDecorView(),display=root.getDisplay();
        if(display===null || String(display.getName())!=='VoyahIran.MapPreview')return;
        // Normal dismiss lifecycle, like tapping outside; never click a rating.
        dialog.dismiss();
        console.log('VI_NESHAN_SURVEY_DISMISSED display='+display.getDisplayId());
    }
    show.implementation=function(){
        const result=show.call(this);
        try {
            if(enabled && survey(this)) {
                tracked.put(this,Java.use('java.lang.Boolean').TRUE.value);
                closeInCard(this);
            }
        }catch(e){console.log('VI_NESHAN_SURVEY_ERROR '+e);}
        return result;
    };
    // Called once on card arrival too: a sheet may have opened in fullscreen.
    dismissCardSurveys=function(){
        try {
            const dialogs=tracked.keySet().iterator();
            while(dialogs.hasNext())closeInCard(Java.cast(dialogs.next(),Dialog));
        }catch(e){console.log('VI_NESHAN_SURVEY_ERROR '+e);}
    };
    restoreSurvey=function(){show.implementation=null;tracked.clear();dismissCardSurveys=function(){};};
}
""".trimIndent()
}
