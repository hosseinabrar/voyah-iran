package com.abrar.voyahclustertest;

import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import dalvik.system.PathClassLoader;
import java.io.*;
import java.lang.reflect.*;

/** Runs in an isolated root app_process. EOF or STOP clears guidance. */
public final class ClusterProbe {
 private static final ImageLayerProbe imageLayer=new ImageLayerProbe();
 private static void log(String s) { System.out.println(s); System.out.flush(); Log.i("VoyahClusterTest",s); }
 public static void main(String[] args) {
  try {
   File folder=new File("/data/user/0/com.abrar.voyahclustertest/files");
   int uid=android.system.Os.stat(folder.getPath()).st_uid;
   java.nio.channels.FileChannel channel=new RandomAccessFile(new File(folder,"probe.lock"),"rw").getChannel();
   java.nio.channels.FileLock lock=channel.tryLock();if(lock==null){log("Already running");return;}
   try(java.net.ServerSocket server=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))) {
    String token=java.util.UUID.randomUUID().toString();File config=new File(folder,"probe.config");
    try(FileWriter w=new FileWriter(config)){w.write(server.getLocalPort()+"\n"+token+"\n");}
    android.system.Os.chown(config.getPath(),uid,uid);android.system.Os.chmod(config.getPath(),0600);
    server.setSoTimeout(3600000);log("SERVER_READY protocol=text-v2 uid="+android.os.Process.myUid());
    try {while(true){
     java.net.Socket socket;
     try{socket=server.accept();}catch(java.net.SocketTimeoutException e){break;}
     try(java.net.Socket connected=socket) {
      socket.setSoTimeout(3000);
      BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));
      if(!token.equals(reader.readLine()))continue;
      socket.setSoTimeout(100);
      PrintWriter output=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8),true);
      runSession(reader,output);
     }catch(Exception e){log("SESSION_ERROR "+Log.getStackTraceString(e));}
    }}finally{config.delete();}
   }finally{lock.release();channel.close();}
  }catch(Throwable e){log("SERVER_ERROR "+Log.getStackTraceString(e));}
 }
 private static void runSession(BufferedReader reader,PrintWriter output) {
  Api api=null; MapProbe map=null;
  try {
   api=new Api();output.println("READY protocol=text-v2 root cluster connection");
   String road="Hemmat";Integer icon=null;long next=0;
   while(true) {
    String line=null;
    try{line=reader.readLine();if(line==null||line.equals("STOP"))break;}catch(java.net.SocketTimeoutException timeout){}
    if(line!=null && line.startsWith("IMAGE ")){try{if(map!=null){map.close();map=null;}imageLayer.command(line.substring(6));}catch(Exception e){output.println("IMAGE_ERROR "+Log.getStackTraceString(e));}line=null;}
    try{imageLayer.poll(output);}catch(Exception e){output.println("IMAGE_ERROR "+e);}
    if(line!=null && line.startsWith("MAP ")){imageLayer.off();try{if(map==null)map=new MapProbe(api.remote,api.service,api.loader);map.command(line);}catch(Exception e){output.println("MAP_ERROR "+Log.getStackTraceString(e));}line=null;}
    if(map!=null)try{map.poll(output);}catch(Exception e){output.println("MAP_POLL_ERROR "+Log.getStackTraceString(e));map.close();map=null;}
    if(line!=null){try{String[] parts=line.split("\\t",-1);if(parts.length!=2)throw new IllegalArgumentException("Expected icon and base64 text; update helper");int value=Integer.parseInt(parts[0]);String nextRoad=TextProbe.decode(parts[1]);if(value<0||value>65535)throw new IllegalArgumentException("0..65535");icon=value;road=nextRoad;next=0;}catch(Exception e){output.println("INVALID "+line);}}
    long now=SystemClock.elapsedRealtime();
    if(icon!=null&&now>=next){api.send(icon,road);String message="BINDER_RETURNED icon="+icon+" meters=100 road="+road+" codepoints="+TextProbe.codepoints(road)+" state=GUIDING (verify visually)";log(message);output.println(message);if(output.checkError())break;next=now+1000;}
   }
  }catch(Throwable e){String message="ERROR "+Log.getStackTraceString(e);log(message);output.println(message);}
  finally {imageLayer.off();if(map!=null)map.close();if(api!=null)try{api.send(null,"");output.println("STOPPED MAN_STOP");log("STOPPED MAN_STOP");}catch(Throwable e){log("CLEAR_FAILED "+Log.getStackTraceString(e));output.println("CLEAR_FAILED "+e);}}
 }
 private static final class Api {
  final Object remote;final Class<?> info,guide,service;final ClassLoader loader;final Method update;
  Api() throws Exception {
   String path="/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk";
   if(!new File(path).isFile())throw new FileNotFoundException(path);
   loader=new PathClassLoader(path,"/system/lib64:/system/lib",ClusterProbe.class.getClassLoader()) {
    @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
     if(!name.startsWith("com.qinggan.cluster."))return super.loadClass(name,resolve);
     synchronized(this){Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;}
    }
   };
   service=loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerService");
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"qg.cluster.service");
   if(binder==null||!service.getName().equals(binder.getInterfaceDescriptor()))throw new IllegalStateException("Cluster service missing/mismatch");
   remote=loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerService$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   info=loader.loadClass("com.qinggan.cluster.info.NaviInfo");guide=loader.loadClass("com.qinggan.cluster.info.GuideState");
   update=service.getMethod("updateNaviInfo",info);
  }
  void send(Integer icon,String road)throws Exception {
   Object n=info.getConstructor().newInstance();Object state=null;
   for(Object value:guide.getEnumConstants())if(((Enum<?>)value).name().equals(icon==null?"MAN_STOP":"GUIDING"))state=value;
   if(state==null)throw new IllegalStateException("GuideState missing");
   info.getMethod("setGuideState",guide).invoke(n,state);
   info.getMethod("setCurrentRoadName",String.class).invoke(n,"");
   info.getMethod("setTurnRoadName",String.class).invoke(n,icon==null?"":road);
   if(icon!=null){info.getMethod("setTbtIconId",int.class).invoke(n,icon);info.getMethod("setNextDistance",int.class).invoke(n,100);}
   update.invoke(remote,n);
  }
 }
}
