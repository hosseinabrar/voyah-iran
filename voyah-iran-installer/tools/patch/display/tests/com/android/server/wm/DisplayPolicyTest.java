package com.android.server.wm;
import android.graphics.Rect; import android.content.res.Configuration; import android.content.pm.*;
public class DisplayPolicyTest {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void state(boolean b)throws Exception {java.lang.reflect.Field f=VoyahIranDisplayPolicy.class.getDeclaredField("enabled");f.setAccessible(true);f.set(null,b);}
 static class Config extends android.app.WindowConfiguration {
  Rect bounds=new Rect(),app;
  public Rect getBounds(){return bounds;} public Rect getAppBounds(){return app;}
  public void setBounds(Rect r){bounds=new Rect(r);} public void setAppBounds(Rect r){app=new Rect(r);}
 }
 static class Activity extends ActivityRecord {
  int display,type=1; boolean multi;
  Configuration config=new Configuration();
  Activity(){ info=new ActivityInfo();info.applicationInfo=new ApplicationInfo();info.applicationInfo.packageName="test.user";info.applicationInfo.uid=10068;
   info.packageName="test.user"; config.windowConfiguration=new Config(); }
  int getDisplayId(){return display;} public int getActivityType(){return type;}
  boolean inMultiWindowMode(){return multi;}
  Configuration getResolvedOverrideConfiguration(){return config;}
 }
 public static void main(String[] args)throws Exception {
  java.lang.reflect.Field init=VoyahIranDisplayPolicy.class.getDeclaredField("scheduled");init.setAccessible(true);init.set(null,true);
  Activity a=new Activity();state(true);
  check(VoyahIranDisplayPolicy.applies(a),"user app eligible");
  check(VoyahIranDisplayPolicy.orientation(a,7)==0,"portrait forced landscape");
  check(VoyahIranDisplayPolicy.orientation(a,-2)==-2,"unset sentinel preserved");
  a.info.applicationInfo.flags=1;check(!VoyahIranDisplayPolicy.applies(a),"OEM excluded");
  a.info.applicationInfo.flags=128;check(!VoyahIranDisplayPolicy.applies(a),"updated OEM excluded");
  a.info.applicationInfo.packageName="com.abrar.voyahiran";check(VoyahIranDisplayPolicy.applies(a),"Voyah Iran included even system install");
  a.display=1;check(!VoyahIranDisplayPolicy.applies(a),"passenger display excluded");a.display=0;
  a.type=2;check(!VoyahIranDisplayPolicy.applies(a),"home excluded");a.type=1;
  a.multi=true;check(!VoyahIranDisplayPolicy.applies(a),"PiP/multiwindow excluded");a.multi=false;
  Configuration parent=new Configuration();Config parentWin=new Config();parentWin.bounds=new Rect(0,0,1920,720);parentWin.app=new Rect(0,0,1920,640);
  parent.windowConfiguration=parentWin;parent.densityDpi=160;
  VoyahIranDisplayPolicy.configuration(a,parent);
  Config out=(Config)a.config.windowConfiguration;
  check(out.bounds.toString().equals("145,50,1920,720"),"safe area bounds");
  check(out.app.toString().equals("145,50,1920,640"),"existing content restriction preserved");
  check(a.config.orientation==2 && a.config.screenWidthDp==1775 && a.config.screenHeightDp==590,"landscape resource configuration");
  WindowState w=new WindowState();w.mActivityRecord=a;w.mAttrs=new android.view.WindowManager.LayoutParams();w.mAttrs.type=1;
  WindowFrames frames=new WindowFrames();frames.mFrame=new Rect(-100,-100,2000,900);
  frames.mParentFrame=new Rect(0,0,1920,720);frames.mContentFrame=new Rect(0,0,1920,400);
  Rect containing=new Rect(0,0,1920,720),display=new Rect(0,0,1920,720);
  VoyahIranDisplayPolicy.beforeFrame(w,frames,containing,display);VoyahIranDisplayPolicy.afterFrame(w,frames);
  check(frames.mFrame.toString().equals("145,50,1920,720"),"oversized app frame contained");
  check(frames.mContentFrame.bottom==400,"existing IME content height preserved");
  w.mAttrs.type=2011;frames.mFrame=new Rect(0,400,1920,720);VoyahIranDisplayPolicy.afterFrame(w,frames);
  check(frames.mFrame.left==0 && frames.mFrame.top==400,"IME window unchanged");
  state(false);check(VoyahIranDisplayPolicy.orientation(a,7)==7,"disable restores original request");
  w.mAttrs.type=1;frames.mFrame=new Rect(0,0,1920,720);VoyahIranDisplayPolicy.afterFrame(w,frames);
  check(frames.mFrame.left==0 && frames.mFrame.top==0,"disabled frame unmodified");
  System.out.println(checks+" display policy checks passed");
 }
}