package com.abrar.voyahiran.core.bridge

/** Own button inside the passenger navigation window; no OEM visibility-method hooks. */
internal object PassengerDrawerScript {
    val source = """
Java.perform(function () {
    try {
        const TAG = 'voyah-iran-passenger-apps';
        const JString = Java.use('java.lang.String');
        const View = Java.use('android.view.View');
        const ViewGroup = Java.use('android.view.ViewGroup');
        const Frame = Java.use('android.widget.FrameLayout');
        const Params = Java.use('android.widget.FrameLayout__DOLLAR__LayoutParams');
        const Global = Java.use('android.provider.Settings__DOLLAR__Global');
        const Window = Java.use('android.view.WindowManagerGlobal');
        const windowsField = Java.retain(Window.class.getDeclaredField('mViews'));
        windowsField.setAccessible(true);
        const JavaList = Java.use('java.util.List');
        const Button = Java.use('android.widget.ImageButton');
        const Bitmap = Java.use('android.graphics.Bitmap');
        const BitmapConfig = Java.use('android.graphics.Bitmap__DOLLAR__Config');
        const Canvas = Java.use('android.graphics.Canvas');
        const Paint = Java.use('android.graphics.Paint');
        const Background = Java.use('android.graphics.drawable.GradientDrawable');
        const Rect = Java.use('android.graphics.Rect');
        const Intent = Java.use('android.content.Intent');
        const Options = Java.use('android.app.ActivityOptions');
        const Listener = Java.registerClass({
            name: 'com.abrar.voyahiran.inject.PassengerAppsClick',
            implements: [Java.use('android.view.View__DOLLAR__OnClickListener')],
            methods: {onClick: function (view) {
                try {
                    const ctx = view.getContext();
                    if (Global.getInt(ctx.getContentResolver(), 'voyah_iran_passenger_apps', 1) !== 1) return;
                    const intent = Intent.__DOLLAR__new();
                    intent.setClassName('com.qinggan.app.launcher', 'com.qinggan.secondlauncher.activity.AllAppActivity');
                    intent.addFlags(268435456);
                    const options = Options.makeBasic();
                    options.setLaunchDisplayId(1);
                    ctx.startActivity(intent, options.toBundle());
                    console.log('VI_PASSENGER_APPS_OPEN display=1 own-button');
                } catch(e) { console.log('VI_PASSENGER_APPS_CLICK_ERROR ' + e); }
            }}
        });
        const listener = Java.retain(Listener.__DOLLAR__new());
        let lastReport = '', queued = false;
        function name(view) {
            try { return String(view.getResources().getResourceEntryName(view.getId())); }
            catch (_) { return ''; }
        }
        function walk(view, visit, depth) {
            if (depth > 12) return;
            if (visit(view) === false) return;
            if (ViewGroup.class.isInstance(view)) {
                const group = Java.cast(view, ViewGroup);
                for (let i=0; i<group.getChildCount(); i++) walk(group.getChildAt(i), visit, depth+1);
            }
        }
        function isButton(view) { return String(view.getTag()) === TAG; }
        // Last free vertical slot wins. Never cover an existing navigation control.
        function freeSlot(height, size, intervals) {
            const sorted = intervals.slice().sort((a,b)=>a[0]-b[0]);
            let edge=8, result=-1;
            for (const item of sorted.concat([[height-8,height]])) {
                if (item[0]-edge >= size) result=item[0]-size;
                edge=Math.max(edge,item[1]);
            }
            return result;
        }
        function makeButton(ctx) {
            const button=Button.__DOLLAR__new(ctx);
            button.setTag(JString.__DOLLAR__new(TAG));
            button.setContentDescription(JString.__DOLLAR__new('فهرست برنامه‌ها'));
            const image=Bitmap.createBitmap(64,64,BitmapConfig.ARGB_8888.value);
            const canvas=Canvas.__DOLLAR__new(image), paint=Paint.__DOLLAR__new();
            paint.setColor(-1); paint.setAntiAlias(true);
            for(let y=0;y<2;y++) for(let x=0;x<2;x++) canvas.drawRect(8+x*28,8+y*28,28+x*28,28+y*28,paint);
            button.setImageBitmap(image);
            const bg=Background.__DOLLAR__new(); bg.setColor(-14606047); bg.setCornerRadius(12);
            button.setBackground(bg); button.setPadding(10,10,10,10);
            button.setOnClickListener(listener);
            return button;
        }
        function update(root) {
            if (!Frame.class.isInstance(root)) return 'unsupported-container';
            const group=Java.cast(root,Frame), ctx=root.getContext();
            const enabled=Global.getInt(ctx.getContentResolver(),'voyah_iran_passenger_apps',1)===1;
            const rawOwn=root.findViewWithTag(JString.__DOLLAR__new(TAG));
            let own=rawOwn===null?null:Java.cast(rawOwn,View);
            // Hide the stock item regardless of whichever firmware condition controls it.
            walk(root,function(view) {
                if (isButton(view)) return false;
                const id=name(view);
                if (/all_?app.*icon|all_?apps.*button/i.test(id)) { if(view.getVisibility()!==8) view.setVisibility(8); return false; }
            },0);
            if (!enabled) {
                if (own!==null) group.removeView(own);
                return 'disabled';
            }
            const width=root.getWidth(), height=root.getHeight();
            if(width<=0 || height<=0 || !root.isShown()) return 'waiting-layout';
            const size=Math.min(width-16,Math.max(48,Math.round(width*0.65)));
            const origin=Rect.__DOLLAR__new(); if(!root.getGlobalVisibleRect(origin)) return 'hidden';
            const occupied=[];
            walk(root,function(view) {
                if(isButton(view) || view.getVisibility()!==0) return false;
                if(view.equals(root)) return;
                if(view.isClickable() || /temperature.*content/.test(name(view))) {
                    const rect=Rect.__DOLLAR__new();
                    if(view.getGlobalVisibleRect(rect)) occupied.push([rect.top.value-origin.top.value-6,rect.bottom.value-origin.top.value+6]);
                    return false;
                }
            },0);
            const top=freeSlot(height,size,occupied);
            if(top<0) { if(own!==null) own.setVisibility(8); return 'no-free-slot'; }
            if(own===null) { own=makeButton(ctx); group.addView(own,Params.__DOLLAR__new(size,size)); }
            const old=own.getLayoutParams(), lp=Java.cast(old,Params);
            const left=Math.round((width-size)/2);
            if(lp.topMargin.value!==top || lp.leftMargin.value!==left || lp.width.value!==size || lp.height.value!==size) {
                lp.gravity.value=51; lp.topMargin.value=top; lp.leftMargin.value=left;
                lp.width.value=size; lp.height.value=size; own.setLayoutParams(lp);
            }
            if(own.getVisibility()!==0) own.setVisibility(0);
            own.bringToFront();
            return 'shown top='+top+' size='+size;
        }
        function tick() {
            const roots=Java.cast(windowsField.get(Window.getInstance()),JavaList);
            let status='waiting-passenger-bar';
            for(let i=0;i<roots.size();i++) {
                const root=Java.cast(roots.get(i),Java.use('android.view.View'));
                const display=root.getDisplay();
                // Exclude center, cluster, activities and wide passenger panels.
                if(display===null || display.getDisplayId()!==1 || root.getWidth()>320) continue;
                let passenger=false;
                walk(root,function(view) {
                    if(name(view)==='screen_up_home_icon' || String(view.getClass().getName()).indexOf('NavigationBarSecond')>=0) passenger=true;
                },0);
                if(passenger) status=update(root);
            }
            if(status!==lastReport) {lastReport=status;console.log('VI_PASSENGER_APPS_UI '+status);}
        }
        function schedule() {
            if(queued)return;queued=true;
            Java.scheduleOnMainThread(function(){try{tick();}catch(e){console.log('VI_PASSENGER_APPS_UI_ERROR '+e);}finally{queued=false;}});
        }
        schedule();setInterval(schedule,1000);
        console.log('VI_PASSENGER_APPS_UI_READY');
    } catch(e) {console.log('VI_PASSENGER_APPS_UI_UNAVAILABLE '+e);}
});
""".trimIndent().replace("__DOLLAR__", "$")
}
