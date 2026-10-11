package com.abrar.voyahclustertest;

import android.content.Intent;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Temporary OEM presentation client. Does not change persistent card settings. */
final class MapProbe {
 static final String PREFIX="qg.car.cluster.";
 private final Object remote,callback; private final Class<?> service,callbackType;
 private final File folder=new File("/data/user/0/com.abrar.voyahclustertest/files");
 private final ConcurrentLinkedQueue<String> events=new ConcurrentLinkedQueue<>();
 private String active,session; private boolean frameReady; private volatile String requested;
 private long expires,started; private String lastFrame="";
 MapProbe(Object remote,Class<?> service,ClassLoader loader)throws Exception {
  this.remote=remote;this.service=service;
  callbackType=loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerCallback");
  Class<?> stub=loader.loadClass(callbackType.getName()+"$Stub");
  final int connect=transaction(stub,"setClusterConnect"),state=transaction(stub,"setClusterActivityState"),presentation=transaction(stub,"setPresentationState");
  Binder binder=new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
   if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(callbackType.getName());return true;}
   data.enforceInterface(callbackType.getName());
   if(code==connect)events.add("CLUSTER_CONNECTED "+(data.readInt()!=0));
   else if(code==state){String project=data.readString(),category=data.readString();Bundle b=data.readInt()!=0?Bundle.CREATOR.createFromParcel(data):null;events.add("ACTIVITY_STATE "+project+" "+category+" "+describe(b));}
   else if(code==presentation){String project=data.readString(),category=data.readString();int value=data.readInt();events.add("PRESENTATION_CALLBACK "+project+" "+category+" state="+value);if(value==1)requested=category;}
   else events.add("CALLBACK transaction="+code);
   return true;
  }};
  callback=Proxy.newProxyInstance(loader,new Class<?>[]{callbackType},(p,m,a)->{
   if(m.getName().equals("asBinder"))return binder;
   if(m.getName().equals("toString"))return "ClusterTestCallback";
   if(m.getName().equals("hashCode"))return System.identityHashCode(p);
   if(m.getName().equals("equals"))return p==a[0];
   return null;
  });
  service.getMethod("registerCallback",callbackType).invoke(remote,callback);
 }
 private static String describe(Bundle b){return b==null?"null":"visible="+b.getBoolean("com.qg:activityState.visible")+" bounds="+b.getParcelable("com.qg:activityState.unobscured");}
 private static int transaction(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField("TRANSACTION_"+name);f.setAccessible(true);return f.getInt(null);}
 private void call(String name,String category,int state)throws Exception{service.getMethod(name,String.class,int.class).invoke(remote,category,state);}
 private void ready(String category)throws Exception{service.getMethod("notifyReadyToShow",String.class).invoke(remote,category);events.add("READY_SENT "+category);}
 void command(String command)throws Exception {
  if(command.equals("MAP STOP")){stop();return;}
  if(command.equals("MAP INFO")){events.add("CLUSTER_CONNECTED "+service.getMethod("getClusterConnect").invoke(remote));events.add("BUILD "+Build.MODEL+" SDK="+Build.VERSION.SDK_INT+"; callbacks report OEM bounds; BINDER success is not visual success");return;}
  if(command.equals("MAP READY")){if(active==null)throw new IllegalStateException("Start a map mode first");ready(active);return;}
  String mode=command.substring(4);
  if(!mode.equals("SMALL")&&!mode.equals("FULL")&&!mode.equals("MEDIA"))throw new IllegalArgumentException("Unknown map mode");
  stop();active=PREFIX+(mode.equals("MEDIA")?"MEDIA_SMALL":"NAVI_"+mode);session=java.util.UUID.randomUUID().toString();
  expires=SystemClock.elapsedRealtime()+60000;started=SystemClock.elapsedRealtime();frameReady=false;requested=null;lastFrame="";
  writeLease();
  String base=PREFIX+(mode.equals("MEDIA")?"MEDIA":"NAVI");
  call("syncReadyState",base,1);
  Intent intent=new Intent().setClassName("com.abrar.voyahclustertest","com.abrar.voyahclustertest.MapTestActivity").addCategory(base).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
  intent.putExtra("session",session).putExtra("mode",mode);
  service.getMethod("startClusterActivity",Intent.class).invoke(remote,intent);
  call("requestPresentation",active,1);events.add("PRESENTATION_REQUEST "+active);
  events.add("START_REQUEST "+active+" session="+session+" expires=60s; waiting for first frame");
 }
 private void writeLease()throws Exception{
  File f=new File(folder,"map-lease");try(FileWriter w=new FileWriter(f)){w.write(session+"\n"+expires+"\n");}
  int uid=android.system.Os.stat(folder.getPath()).st_uid;android.system.Os.chown(f.getPath(),uid,uid);android.system.Os.chmod(f.getPath(),0600);
  java.lang.Process relabel=new ProcessBuilder("/system/bin/restorecon",f.getPath()).redirectErrorStream(true).start();
  if(!relabel.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){relabel.destroy();throw new IOException("Lease restorecon timed out");}
  if(relabel.exitValue()!=0)throw new IOException("Lease restorecon failed");
 }
 void poll(PrintWriter out)throws Exception {
  if(active!=null){
   if(SystemClock.elapsedRealtime()>=expires){stop();events.add("MAP_TIMEOUT");}
   else {
    File f=new File(folder,"map-frame");String line="";
    if(f.isFile())try(BufferedReader r=new BufferedReader(new FileReader(f))){line=r.readLine();}
    if(line!=null&&line.startsWith(session+" ")&&!line.equals(lastFrame)){
     lastFrame=line;events.add("RENDER "+line);
     if(!frameReady){frameReady=true;events.add("FIRST_FRAME");}
    }
    if(frameReady&&requested!=null){String p=requested;requested=null;if(p.equals(active))ready(p);}
    if(!frameReady&&SystemClock.elapsedRealtime()-started>10000 && started!=0){events.add("NO_FRAME: no drawn frame yet; try explicit Ready once to test hidden-surface handshake. Auto-stop remains 60s.");started=0;}
   }
  }
  String event;while((event=events.poll())!=null){out.println(event);android.util.Log.i("VoyahClusterTest",event);}
 }
 void stop()throws Exception{
  String old=active;active=null;requested=null;new File(folder,"map-lease").delete();
  if(old!=null){call("requestPresentation",old,2);events.add("MAP_CLOSE_REQUEST "+old+"; persistent settings unchanged");}
 }
 void close(){try{stop();}catch(Exception e){android.util.Log.e("VoyahClusterTest","MAP_STOP_FAILED",e);}try{service.getMethod("unregisterCallback",callbackType).invoke(remote,callback);}catch(Exception e){android.util.Log.e("VoyahClusterTest","UNREGISTER_FAILED",e);}}
}
