const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/AppleMusicHookScript.kt'),'utf8').split('"""')[1];
for(const pkg of ['com.apple.android.music','another.app']) {
 const methods={};for(const n of ['exists','isFile','canRead','canWrite','canExecute'])methods[n]={call:()=>true};
 const File=Object.fromEntries(Object.keys(methods).map(n=>[n,{overload:()=>methods[n]}]));
 const Build={TAGS:{value:'dev-keys,test-keys'}};
 const thread={currentApplication:()=>({getPackageName:()=>pkg}),currentActivityThread:()=>({mActivities:{value:{values:()=>({toArray:()=>[]})}}})};
 const Java={perform:f=>f(),scheduleOnMainThread:f=>f(),use:n=>({'android.app.ActivityThread':thread,'java.io.File':File,'android.os.Build':Build}[n])};
 if(pkg!=='com.apple.android.music'){assert.throws(()=>vm.runInNewContext(source,{Java,console}));assert.equal(Build.TAGS.value,'dev-keys,test-keys');continue;}
 vm.runInNewContext(source,{Java,console});assert.equal(Build.TAGS.value,'dev-keys,release-keys');
 for(const name of Object.keys(methods))for(const [p,want] of [['/system/bin/su',false],['/system/xbin/su',false],['/system/app/Superuser.apk',false],['/data/user/0/com.apple.android.music/files/music',true],['/system/bin/sh',true],['/system/bin/sushi',true]])assert.equal(methods[name].implementation.call({getCanonicalPath:()=>p}),want,name+' '+p);
}
assert(!source.includes('AppleMusicApplication'));console.log('Apple Music process scope and file isolation passed');
