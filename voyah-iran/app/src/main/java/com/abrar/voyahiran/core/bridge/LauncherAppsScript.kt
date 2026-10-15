package com.abrar.voyahiran.core.bridge

/** Read the OEM's final filtered list and themed drawables in its own process. */
internal object LauncherAppsScript {
    val source = """
Java.perform(function () {
    try {
        const Global = Java.use('android.provider.Settings__DOLLAR__Global');
        const Intent = Java.use('android.content.Intent');
        const JString = Java.use('java.lang.String');
        const ctx = Java.use('android.app.ActivityThread').currentApplication();
        let Data;
        try { Data = Java.use('com.qinggan.launcher.allapp.AllAppDataManager'); }
        catch (_) { Data = Java.use('com.qinggan.launcher.base.allapp.AllAppDataManager'); }
        const Skin = Java.use('com.qinggan.theme.SkinResourceManager');
        const Launch = Java.use('com.qinggan.launcher.base.utils.AppLauncher');
        const excluded = new Set(__EXCLUDED__);
        function allowed() {
            const now = Date.now();
            return Global.getInt(ctx.getContentResolver(), 'voyah_iran_launcher_home', 0) === 1 &&
                ['voyah_iran_launcher_lease', 'voyah_iran_launcher_vip_lease'].every(function (key) {
                    const remaining = Number(Global.getString(ctx.getContentResolver(), key)) - now;
                    return remaining > 0 && remaining <= 90000;
                });
        }
        function beans() {
            const list = Data.getAllApps(0), result = [];
            for(let i=0; i<list.size(); i++) {
                const raw=list.get(i), bean=Java.cast(raw,Java.use(String(raw.getClass().getName())));
                const subtype=String(bean.getSubType());
                if(!subtype || excluded.has(subtype)) continue;
                // Installed third-party apps are collected by our PackageManager, not twice.
                try { if(bean.isDynamicApp()) continue; } catch (_) {}
                result.push({bean:bean, subtype:subtype});
            }
            return result;
        }
        function send(nonce, value) {
            const text=JSON.stringify(value);
            if(text.length>350000)throw new Error('OEM icon catalog exceeds size limit');
            // Binder's buffer is shared with other in-flight calls, especially during boot.
            // Pace small chunks instead of broadcasting the entire base64 icon catalog.
            if(value.items) {
                const count=Math.ceil(text.length/8000);
                let index=0;
                function next() {
                    Java.perform(function() {
                        const reply=Intent.__DOLLAR__new('com.abrar.voyahiran.DRAWER_RESULT');
                        reply.setPackage('com.abrar.voyahiran');
                        reply.putExtra.overload('java.lang.String','java.lang.String').call(reply,'nonce',nonce);
                        reply.putExtra.overload('java.lang.String','java.lang.String').call(reply,'payload',text.slice(index*8000,(index+1)*8000));
                        reply.putExtra.overload('java.lang.String','int').call(reply,'part',index);
                        reply.putExtra.overload('java.lang.String','int').call(reply,'parts',count);
                        ctx.sendBroadcast(reply);
                        if(++index<count)setTimeout(next,40);
                    });
                }
                next();return;
            }
            const reply=Intent.__DOLLAR__new('com.abrar.voyahiran.DRAWER_RESULT');
            reply.setPackage('com.abrar.voyahiran');
            reply.putExtra.overload('java.lang.String','java.lang.String').call(reply,'nonce',nonce);
            reply.putExtra.overload('java.lang.String','java.lang.String').call(reply,'payload',text);
            ctx.sendBroadcast(reply);
        }
        function icon(drawable) {
            if(drawable===null)return '';
            const Bitmap=Java.use('android.graphics.Bitmap');
            const bitmap=Bitmap.createBitmap(112,112,Java.use('android.graphics.Bitmap__DOLLAR__Config').ARGB_8888.value);
            const old=Java.use('android.graphics.Rect').__DOLLAR__new(drawable.getBounds());
            const output=Java.use('java.io.ByteArrayOutputStream').__DOLLAR__new();
            try {
                const w=Math.max(1,drawable.getIntrinsicWidth()), h=Math.max(1,drawable.getIntrinsicHeight());
                const scale=112/Math.max(w,h), dx=Math.round((112-w*scale)/2),dy=Math.round((112-h*scale)/2);
                drawable.setBounds(dx,dy,112-dx,112-dy);
                drawable.draw(Java.use('android.graphics.Canvas').__DOLLAR__new(bitmap));
                bitmap.compress(Java.use('android.graphics.Bitmap__DOLLAR__CompressFormat').PNG.value,100,output);
                return String(Java.use('android.util.Base64').encodeToString(output.toByteArray(),2));
            } finally {drawable.setBounds(old);bitmap.recycle();output.close();}
        }
        let previous='';
        function request() {
            Java.scheduleOnMainThread(function () {
                const raw=String(Global.getString(ctx.getContentResolver(),'voyah_iran_drawer_request')||'');
                if(raw===previous)return;previous=raw;
                const parts=raw.split(':'), action=parts[0],nonce=parts[1],subtype=parts[2];
                const screen=parts.length===4 ? Number(parts[3]) : 0;
                if((parts.length!==3 && parts.length!==4) || (screen!==0 && screen!==1) || !/^[a-f0-9]{32}$/.test(nonce))return;
                try {
                    if(!allowed())throw new Error('Drawer disabled or VIP expired');
                    const all=beans();
                    if(action==='open') {
                        if(!all.some(function(item){return item.subtype===subtype;}))throw new Error('OEM tile unavailable');
                        Launch.startAppBySubType(ctx,subtype,screen);
                        send(nonce,{opened:true});
                        console.log('VI_DRAWER_OPEN '+subtype);
                    } else if(action==='catalog' && subtype==='all') {
                        const skin=Skin.getInstance(ctx), items=[];
                        for(const item of all) {
                            const bean=item.bean;
                            const label=Number(bean.getNameRes())>0?String(ctx.getString(bean.getNameRes())):String(bean.getAppName());
                            items.push({subtype:item.subtype,label:label,icon:icon(skin.getDrawable(bean.getIcon()))});
                        }
                        const value={items:items,light:String(Java.use('android.os.SystemProperties').get('persist.sys.qg.currenttheme','default'))==='simple'};
                        if(JSON.stringify(value).length>350000)throw new Error('OEM icon catalog exceeds transaction limit');
                        send(nonce,value);
                        console.log('VI_DRAWER_CATALOG count='+items.length+' light='+value.light);
                    }
                } catch(e) {send(nonce,{error:String(e)});console.log('VI_DRAWER_ERROR '+e);}
            });
        }
        const Observer=Java.registerClass({name:'com.abrar.voyahiran.bridge.DrawerRequestObserver',
            superClass:Java.use('android.database.ContentObserver'),
            methods:{__DOLLAR__init:[{returnType:'void',argumentTypes:['android.os.Handler'],implementation:function(handler){this.__DOLLAR__super.__DOLLAR__init(handler);}}],onChange:[{returnType:'void',argumentTypes:['boolean'],implementation:function(){request();}}]}});
        const observer=Java.retain(Observer.__DOLLAR__new(Java.use('android.os.Handler').__DOLLAR__new(Java.use('android.os.Looper').getMainLooper())));
        ctx.getContentResolver().registerContentObserver(Global.getUriFor('voyah_iran_drawer_request'),false,observer);
        globalThis.VI_OPEN_DRAWER=function (context) {
            if(!allowed())return false;
            const intent=Intent.__DOLLAR__new();
            intent.setClassName('com.abrar.voyahiran','com.abrar.voyahiran.feature.launcher.LauncherAppsActivity');
            intent.addFlags(0x10000000 | 0x04000000);
            const options=Java.use('android.app.ActivityOptions').makeBasic();options.setLaunchDisplayId(0);
            context.startActivity(intent,options.toBundle());return true;
        };
        // H97A/C use NavigationBar instead of H97X's NavigationBarMain.
        try {
            const Legacy = Java.use('com.qinggan.mainlauncher.navigation.NavigationBar');
            const click = Legacy.onClick.overload('android.view.View');
            click.implementation = function(view) {
                try {
                    if(Number(this.mScreenId.value)===1 && typeof globalThis.VI_PASSENGER_OPEN==='function') {
                        const name=String(view.getResources().getResourceEntryName(view.getId()));
                        if((name==='screen_up_home_icon'||name==='screen_down_home_icon')&&globalThis.VI_PASSENGER_OPEN(false))return;
                        if((name==='screen_up_all_app_icon'||name==='screen_down_all_app_icon')&&globalThis.VI_PASSENGER_OPEN(true))return;
                    }
                    if(Number(this.mScreenId.value)===0 && allowed()) {
                        const name=String(view.getResources().getResourceEntryName(view.getId()));
                        if(name==='screen_up_all_app_icon' && globalThis.VI_OPEN_DRAWER(this.mContext.value))return;
                        if(name==='screen_up_home_icon' || name==='screen_down_home_icon') {
                            const intent=Intent.__DOLLAR__new();
                            intent.setClassName('com.abrar.voyahiran','com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity');
                            intent.addFlags(0x10000000 | 0x04000000);
                            const options=Java.use('android.app.ActivityOptions').makeBasic();options.setLaunchDisplayId(0);
                            this.mContext.value.startActivity(intent,options.toBundle());return;
                        }
                    }
                } catch(e) {console.log('VI_DRAWER_LEGACY_CLICK '+e);}
                return click.call(this,view);
            };
            Legacy.updateSelectedApp.overloads.forEach(function(method) {
                method.implementation=function() {
                    try {
                        if(Number(this.mScreenId.value)===0 && allowed() && String(arguments[0])==='com.abrar.voyahiran') {
                            const top=String(Java.use('com.qinggan.launcher.base.utils.AppUtils').getTopAppInfo(this.mContext.value,0,4));
                            if(top.indexOf('feature.launcher.LauncherAppsActivity')>=0) {this.mScreenUpAllAppView.value.setChecked(true);return;}
                            if(top.indexOf('feature.launcher.VoyahLauncherActivity')>=0) {this.mScreenUpHomeView.value.setChecked(true);return;}
                        }
                    }catch(e){console.log('VI_DRAWER_LEGACY_SELECTION '+e);}
                    return method.apply(this,arguments);
                };
            });
            console.log('VI_DRAWER_LEGACY_READY');
        } catch(_) {} // H97X uses LauncherHomeScript's NavigationBarMain listener.
        console.log('VI_DRAWER_READY');
    } catch(e) {console.log('VI_DRAWER_UNAVAILABLE '+e);}
});
""".trimIndent().replace("__DOLLAR__", "${'$'}").replace("__EXCLUDED__",
        com.abrar.voyahiran.feature.launcher.LauncherAppsPolicy.hidden.joinToString(",", "[", "]") { "\"$it\"" })
}
