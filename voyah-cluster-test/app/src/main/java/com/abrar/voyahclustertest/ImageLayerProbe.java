package com.abrar.voyahclustertest;

import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

/** Isolated test injector. OFF keeps the same injected ImageView alive. */
final class ImageLayerProbe {
 private Process child;
 private volatile boolean ready;
 private final ConcurrentLinkedQueue<String> lines=new ConcurrentLinkedQueue<>();
 private long pingAt;
 private String mode="off";
 private String lastSent="";
 void command(String value)throws Exception {
  if(!value.equals("map")&&!value.equals("music")&&!value.equals("off"))throw new IllegalArgumentException("Invalid image mode");
  if(child==null||!child.isAlive()){
   if(value.equals("off"))return;
   start();
  }
  mode=value;flushMode();
 }
 private static byte[] asset(ZipFile zip,String name)throws IOException {
  java.util.zip.ZipEntry entry=zip.getEntry("assets/"+name);
  if(entry==null)throw new IOException("Missing asset "+name);
  try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
  }
 }
 private void start()throws Exception {
  final String executable="/data/local/tmp/voyahiran-bridge/neshan/frida-inject";
  if(!new File(executable).canExecute())throw new IOException("Frida runtime missing; install Voyah Iran test runtime first");
  String pid=MapSourceTask.shell("/system/bin/pidof","com.qinggan.instrumentcard").trim();
  if(!pid.matches("[1-9][0-9]*"))throw new IOException("OEM instrumentcard process unavailable: "+pid);
  final String source;
  try(ZipFile zip=new ZipFile(System.getenv("CLASSPATH"))){
   source=new String(asset(zip,"cluster_image_layer.js"),StandardCharsets.UTF_8)
    .replace("__MUSIC__",Base64.encodeToString(asset(zip,"cluster_music_test.png"),Base64.NO_WRAP))
    .replace("__MAP__",Base64.encodeToString(asset(zip,"cluster_map_test.png"),Base64.NO_WRAP));
  }
  File script=new File("/data/local/tmp/voyah-cluster-image-test.js");
  try(FileOutputStream out=new FileOutputStream(script)){out.write(source.getBytes(StandardCharsets.UTF_8));}
  android.system.Os.chmod(script.getPath(),0600);
  ready=false;lastSent="";
  Process p=new ProcessBuilder(executable,"-p",pid,"-s",script.getPath(),"-i").redirectErrorStream(true).start();child=p;
  Thread reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
   String s;while((s=in.readLine())!=null){if(child!=p)break;if(s.contains("VI_IMAGE_TEST READY"))ready=true;lines.add(s);android.util.Log.i("VoyahClusterTest",s);}
  }catch(Exception e){lines.add("IMAGE_ERROR "+e);}},"ClusterImageLog");reader.setDaemon(true);reader.start();
 }
 private void send(String text)throws IOException {child.getOutputStream().write((text+"\n").getBytes(StandardCharsets.UTF_8));child.getOutputStream().flush();}
 private void flushMode()throws IOException {if(ready&&!mode.equals(lastSent)){send(mode);lastSent=mode;}}
 void poll(PrintWriter out)throws IOException {
  String s;while((s=lines.poll())!=null)out.println(s);
  if(child!=null&&child.isAlive()&&ready){flushMode();long now=android.os.SystemClock.elapsedRealtime();if(now-pingAt>=500){send("ping");pingAt=now;}}
 }
 void off(){mode="off";try{flushMode();}catch(Exception ignored){} }
}
