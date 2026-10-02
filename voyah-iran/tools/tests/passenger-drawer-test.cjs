const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const base=path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge');
const read=n=>fs.readFileSync(path.join(base,n),'utf8').split('val source = """')[1].split('""".trimIndent()')[0].replaceAll('__DOLLAR__','$');
const source=read('PassengerDrawerScript.kt');new vm.Script(source);new vm.Script(read('OemNavigationScript.kt'));
let enabled=true;
const rect=()=>({top:{value:0},bottom:{value:0}});
const params=(w,h)=>({width:{value:w},height:{value:h},topMargin:{value:0},leftMargin:{value:0},gravity:{value:0}});
const node=(id,top,bottom,click=true)=>({id,top,bottom,visible:0,getVisibility(){return this.visible},setVisibility(v){this.visible=v},equals(v){return this===v},isClickable(){return click},getGlobalVisibleRect(r){r.top.value=top;r.bottom.value=bottom;return true}});
const stock=node('screen_up_all_app_icon',620,690);stock.visible=8;
const controls=[node('screen_up_home_icon',10,90),node('music',160,230),node('seat',480,560),stock];
const root={children:controls,findViewWithTag:()=>root.children.find(v=>v.own)||null,getContext:()=>({getContentResolver:()=>({})}),getWidth:()=>145,getHeight:()=>720,isShown:()=>true,getGlobalVisibleRect:r=>{r.top.value=0;return true},removeView:v=>root.children=root.children.filter(x=>x!==v),addView:(v,p)=>{v.lp=p;root.children.push(v)}};
const button=()=>({...node('custom',0,0),own:true,getLayoutParams(){return this.lp},setLayoutParams(p){this.lp=p},bringToFront(){}});
const ctx={JString:{$new:s=>({javaString:s})},View:{},Frame:{class:{isInstance:()=>true}},Java:{cast:x=>x},Global:{getInt:()=>enabled?1:0},TAG:'tag',Params:{$new:params},Rect:{$new:rect},name:v=>v.id,isButton:v=>!!v.own,walk:(r,f)=>r.children.slice().forEach(f),makeButton:button};
vm.createContext(ctx);
vm.runInContext(source.slice(source.indexOf('        function freeSlot'),source.indexOf('        function makeButton'))+source.slice(source.indexOf('        function update(root)'),source.indexOf('        function tick()')),ctx);
assert(ctx.update(root).startsWith('shown'));assert.equal(root.children.filter(v=>v.own).length,1);
for(let i=0;i<20;i++){stock.visible=0;ctx.update(root)}
assert.equal(stock.visible,8);assert.equal(root.children.filter(v=>v.own).length,1);
enabled=false;ctx.update(root);assert(!root.children.some(v=>v.own));assert.equal(stock.visible,8);
root.children=root.children.filter(v=>v!==stock);enabled=true;ctx.update(root);assert.equal(root.children.filter(v=>v.own).length,1);
root.children.push(node('occupied',0,720));assert.equal(ctx.update(root),'no-free-slot');assert.equal(root.children.find(v=>v.own).visible,8);
assert.equal(ctx.freeSlot(720,90,[[0,300],[390,720]]),300);
assert(source.includes('display.getDisplayId()!==1'));assert(source.includes('options.setLaunchDisplayId(1)'));
console.log('PASS: actual update function; missing/hidden/reappearing OEM button, repeated repair without duplicates, disable removal, free-slot collision guard, display-1 scope');

// Exercise real construction, not only a fake makeButton used by layout tests.
let description=null,tag=null,image=null,click=null,tiles=0;
const javaText=value=>{assert(value && typeof value.javaString==='string','Expected java.lang.String, not JS string');return value.javaString};
const actualButton={setTag:v=>tag=javaText(v),setContentDescription:v=>description=javaText(v),setImageBitmap:v=>image=v,setBackground:()=>{},setPadding:()=>{},setOnClickListener:v=>click=v};
const construction={Button:{$new:()=>actualButton},JString:{$new:s=>({javaString:s})},TAG:'voyah-iran-passenger-apps',Bitmap:{createBitmap:()=>({bitmap:true})},BitmapConfig:{ARGB_8888:{value:1}},Canvas:{$new:()=>({drawRect:()=>tiles++})},Paint:{$new:()=>({setColor:()=>{},setAntiAlias:()=>{}})},Background:{$new:()=>({setColor:()=>{},setCornerRadius:()=>{}})},listener:{click:true}};
vm.createContext(construction);
vm.runInContext(source.slice(source.indexOf('        function makeButton'),source.indexOf('        function update(root)')),construction);
assert.strictEqual(construction.makeButton({}),actualButton);
assert.equal(description,'فهرست برنامه‌ها');assert.equal(tag,'voyah-iran-passenger-apps');assert.equal(tiles,4);assert(image.bitmap);assert(click.click);
console.log('PASS: actual button construction, Java CharSequence/Object arguments, icon and listener');

// Execute restored passenger-only list hook with the real script body.
const navigation=read('OemNavigationScript.kt');
const stockApps=[{getPackageName:()=> 'stock'}];
const user={getPackageName:()=> 'user.music'}, system={getPackageName:()=> 'system.app'};
const list=a=>({items:[...a],size(){return this.items.length},get(i){return this.items[i]},add(x){this.items.push(x)}});
let allow=true,queries=0;
const original={call:(_,screen)=>list(screen===1?stockApps:[...stockApps,user,system,user])};
const manager={getAllApps:{overload:()=>original}};
const java={use:n=>n.endsWith('AllAppDataManager')?manager:n==='java.util.ArrayList'?{$new:x=>list(x.items)}:{},cast:x=>x};
const baseApp={getContentResolver:()=>({}),getPackageManager:()=>({getApplicationInfo:p=>{queries++;return {flags:{value:p==='system.app'?1:0}}},getLaunchIntentForPackage:()=>({})})};
const sandbox={VI_STOCK_PASSENGER_LIST:true,Java:java,Global:{getInt:()=>allow?1:0},Base:{getInstance:()=>baseApp},console};
vm.runInNewContext(navigation.slice(navigation.indexOf('        // Use the factory passenger drawer'),navigation.indexOf('        function remember(value)')),sandbox);
assert.equal(original.implementation(0).size(),4);assert.equal(queries,0);
allow=false;assert.equal(original.implementation(1).size(),1);assert.equal(queries,0);
allow=true;const enriched=original.implementation(1);
assert.deepEqual(enriched.items.map(x=>x.getPackageName()),['stock','user.music']);
assert.equal(stockApps.length,1);
console.log('PASS: passenger-only user apps, setting off, no central scan, no duplicates, original list unchanged');

for (const flag of [false, undefined]) {
    let touched=false;
    vm.runInNewContext(navigation.slice(navigation.indexOf('        // Use the factory passenger drawer'),navigation.indexOf('        function remember(value)')),
        {VI_STOCK_PASSENGER_LIST:flag,Java:{use:()=>{touched=true;throw Error('Must not access OEM list')}},console});
    assert.equal(touched,false);
}
console.log('PASS: custom-drawer variants and missing policy never access or hook OEM app lists');
