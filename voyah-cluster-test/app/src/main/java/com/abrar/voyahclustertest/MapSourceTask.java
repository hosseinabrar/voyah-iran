package com.abrar.voyahclustertest;

import android.content.ComponentName;
import android.graphics.Rect;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Root-side transfer of only Neshan's normal task, using the validated stack transfer API. */
final class MapSourceTask {
 private static final String PKG="org.rajman.neshan.traffic.tehran.navigator";
 private final Class<?> type;private final Object api;private int original=0,target=-1;
 MapSourceTask()throws Exception {
  boolean modern=android.os.Build.VERSION.SDK_INT>=29;
  type=Class.forName(modern?"android.app.IActivityTaskManager":"android.app.IActivityManager");
  api=Class.forName(modern?"android.app.ActivityTaskManager":"android.app.ActivityManager").getMethod("getService").invoke(null);
 }
 private Object stack()throws Exception {
  Object found=null;
  for(Object s:(List<?>)type.getMethod("getAllStackInfos").invoke(api)){
   ComponentName top=(ComponentName)s.getClass().getField("topActivity").get(s);
   String[] names=(String[])s.getClass().getField("taskNames").get(s);
   boolean match=top!=null&&PKG.equals(top.getPackageName());
   if(names!=null)for(String n:names)if(n!=null&&(n.equals(PKG)||n.startsWith(PKG+"/")))match=true;
   if(match){if(found!=null)throw new IOException("Multiple Neshan stacks; close duplicate tasks first");found=s;}
  }return found;
 }
 static String shell(String...args)throws Exception {
  Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  if(!p.waitFor(8,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Command timeout: "+args[0]);}
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
  try(InputStream stream=p.getInputStream()){while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);}
  String output=bytes.toString("UTF-8");
  if(p.exitValue()!=0||output.contains("Error:"))throw new IOException(output);return output;
 }
 void start(int display,String session)throws Exception {
  Class<?> global=Class.forName("android.hardware.display.DisplayManagerGlobal");
  Object info=global.getMethod("getDisplayInfo",int.class).invoke(global.getMethod("getInstance").invoke(null),display);
  if(info==null||!("VoyahClusterTest.MapSource."+session).equals(info.getClass().getField("name").get(info))||
    !"com.abrar.voyahclustertest".equals(info.getClass().getField("ownerPackageName").get(info)))throw new IOException("Invalid source display");
  Object before=stack();original=before==null?0:before.getClass().getField("displayId").getInt(before);target=display;
  shell("/system/bin/am","start","--user","0","--display",String.valueOf(display),"-n","com.abrar.voyahclustertest/.MapSourceAnchorActivity");
  if(before!=null)move(before,display);
  else {
   String component=shell("/system/bin/cmd","package","resolve-activity","--brief","--user","0","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER",PKG).trim();
   component=component.substring(component.lastIndexOf('\n')+1).trim();
   if(!component.startsWith(PKG+"/")||!component.matches("[A-Za-z0-9_.$/]+"))throw new IOException("Neshan launcher unavailable");
   shell("/system/bin/am","start","--user","0","--display",String.valueOf(display),"-f","0x10200000","-n",component);
  }
  Object after=stack();long deadline=android.os.SystemClock.elapsedRealtime()+5000;
  while((after==null||after.getClass().getField("displayId").getInt(after)!=display)&&android.os.SystemClock.elapsedRealtime()<deadline){Thread.sleep(100);after=stack();}
  if(after==null||after.getClass().getField("displayId").getInt(after)!=display)throw new IOException("Neshan did not reach source display");
  fit(after);
  android.util.Log.i("VoyahClusterTest","LIVE_MAP_STARTED source="+display+" restore="+original);
 }
 private void fit(Object s)throws Exception {
  int[] tasks=(int[])s.getClass().getField("taskIds").get(s);if(tasks.length!=1)throw new IOException("Shared stack refused");
  type.getMethod("setTaskWindowingMode",int.class,int.class,boolean.class).invoke(api,tasks[0],1,false);
  type.getMethod("resizeTask",int.class,Rect.class,int.class).invoke(api,tasks[0],null,0);
 }
 private void move(Object s,int display)throws Exception {
  int[] tasks=(int[])s.getClass().getField("taskIds").get(s);if(tasks.length!=1)throw new IOException("Shared stack refused");
  Object config=s.getClass().getField("configuration").get(s),wc=config.getClass().getField("windowConfiguration").get(config);
  if(!Integer.valueOf(1).equals(wc.getClass().getMethod("getActivityType").invoke(wc)))throw new IOException("Not a normal app stack");
  int id=s.getClass().getField("stackId").getInt(s);
  try {type.getMethod("moveStackToDisplay",int.class,int.class).invoke(api,id,display);}
  catch(InvocationTargetException e){Object landed=stack();if(!(e.getCause() instanceof NullPointerException)||landed==null||landed.getClass().getField("displayId").getInt(landed)!=display)throw e;}
  Object landed=stack();if(landed==null||landed.getClass().getField("displayId").getInt(landed)!=display)throw new IOException("Move not confirmed");fit(landed);
 }
 void close()throws Exception {
  if(target<0)return;Object s=stack();
  if(s!=null&&s.getClass().getField("displayId").getInt(s)==target){
   Class<?> global=Class.forName("android.hardware.display.DisplayManagerGlobal");
   Object exists=global.getMethod("getDisplayInfo",int.class).invoke(global.getMethod("getInstance").invoke(null),original);
   move(s,exists==null?0:original);
  }target=-1;
 }
}
