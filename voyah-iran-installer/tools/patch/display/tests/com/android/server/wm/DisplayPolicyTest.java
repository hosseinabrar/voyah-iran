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
  if (!VoyahIranDisplayPolicy.supportsLoginWelcomeVariant("f0506h f0506h f0506h for arm64 qti/f0506h/f0506h:9/PQ3B/9:user/jenkins-PRJ_H97a_8155_OTA_1010-9,test-keys")) throw new AssertionError("fingerprint-only h97a");
  if (!VoyahIranDisplayPolicy.supportsLoginWelcomeVariant("msmnile_gvmq msmnile_gvmq msmnile_gvmq for arm64 sa8155 qti/msmnile_gvmq/msmnile_gvmq:11/RQ3A.211001.001/35:user/jenkins-PRJ_H97c_Platform_8155_OTA7.2_0430-35,test-keys")) throw new AssertionError("fingerprint-only h97c");
  if (VoyahIranDisplayPolicy.supportsLoginWelcomeVariant("h97x") || VoyahIranDisplayPolicy.supportsLoginWelcomeVariant("h97abc")) throw new AssertionError("unrelated model");

  java.lang.reflect.Field init=VoyahIranDisplayPolicy.class.getDeclaredField("scheduled");init.setAccessible(true);init.set(null,true);
  Activity a=new Activity();state(true);
  check(VoyahIranDisplayPolicy.nightFor("vehicle","default")==0x20,"Classic and Modern dark");
  check(VoyahIranDisplayPolicy.nightFor("vehicle","simple")==0x10,"Speedy Dash light");
  check(VoyahIranDisplayPolicy.nightFor("dark","simple")==0x20,"explicit dark ignores vehicle");
  check(VoyahIranDisplayPolicy.nightFor("light","default")==0x10,"explicit light ignores vehicle");
  java.lang.reflect.Field night=VoyahIranDisplayPolicy.class.getDeclaredField("nightMode");night.setAccessible(true);night.setInt(null,0x20);
  state(false); Configuration themeParent=new Configuration();a.config.uiMode=0x13;
  VoyahIranDisplayPolicy.configuration(a,themeParent);
  check(a.config.uiMode==0x23,"night changes independently of safe area, preserves car type");
  night.setInt(null,0x10);VoyahIranDisplayPolicy.configuration(a,themeParent);
  check(a.config.uiMode==0x13,"light transition");
  a.info.applicationInfo.flags=1;night.setInt(null,0x20);VoyahIranDisplayPolicy.configuration(a,themeParent);
  check(a.config.uiMode==0x13,"OEM theme untouched");a.info.applicationInfo.flags=0;
  a.display=1;VoyahIranDisplayPolicy.configuration(a,themeParent);check(a.config.uiMode==0x23,"passenger theme applied");a.display=0;
  night.setInt(null,0);state(true);
  check(VoyahIranDisplayPolicy.applies(a),"user app eligible");
  check(VoyahIranDisplayPolicy.orientation(a,7)==0,"portrait forced landscape");
  check(VoyahIranDisplayPolicy.orientation(a,-2)==-2,"unset sentinel preserved");
  a.info.applicationInfo.flags=1;check(!VoyahIranDisplayPolicy.applies(a),"OEM excluded");
  a.info.applicationInfo.flags=128;check(!VoyahIranDisplayPolicy.applies(a),"updated OEM excluded");
  a.info.applicationInfo.packageName="com.abrar.voyahiran";check(VoyahIranDisplayPolicy.applies(a),"Voyah Iran included even system install");
  a.display=1;check(VoyahIranDisplayPolicy.applies(a),"passenger display included");
  check(VoyahIranDisplayPolicy.orientation(a,7)==0,"passenger portrait forced landscape");
  a.display=2;check(!VoyahIranDisplayPolicy.applies(a),"cluster/other display excluded");a.display=0;
  a.type=2;check(!VoyahIranDisplayPolicy.applies(a),"home excluded");a.type=1;
  a.multi=true;check(!VoyahIranDisplayPolicy.applies(a),"PiP/multiwindow excluded");a.multi=false;
  Configuration parent=new Configuration();Config parentWin=new Config();parentWin.bounds=new Rect(0,0,1920,720);parentWin.app=new Rect(0,0,1920,640);
  parent.windowConfiguration=parentWin;parent.densityDpi=160;
  VoyahIranDisplayPolicy.configuration(a,parent);
  Config out=(Config)a.config.windowConfiguration;
  check(out.bounds.toString().equals("145,50,1920,720"),"safe area bounds");
  check(out.app.toString().equals("145,50,1920,640"),"existing content restriction preserved");
  check(a.config.orientation==2 && a.config.screenWidthDp==1775 && a.config.screenHeightDp==590,"landscape resource configuration");
  a.display=1; parentWin.bounds=new Rect(0,0,1600,720);parentWin.app=new Rect(0,0,1600,640);
  VoyahIranDisplayPolicy.configuration(a,parent);
  check(out.bounds.toString().equals("145,50,1600,720"),"passenger uses own display width");
  check(out.app.toString().equals("145,50,1600,640"),"passenger content preserved");
  a.info.packageName="com.abrar.voyahiran";a.info.name="com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity";
  check(!VoyahIranDisplayPolicy.applies(a),"passenger HOME bypasses geometry policy");
  check(VoyahIranDisplayPolicy.orientation(a,7)==7,"passenger HOME keeps native orientation request");
  out.bounds=new Rect(0,0,1600,720);out.app=new Rect(0,0,1600,640);
  VoyahIranDisplayPolicy.configuration(a,parent);
  check(out.bounds.toString().equals("0,0,1600,720") && out.app.toString().equals("0,0,1600,640"),"passenger HOME retains native bounds and content");
  night.setInt(null,0x20);a.config.uiMode=0x13;VoyahIranDisplayPolicy.configuration(a,parent);
  check(a.config.uiMode==0x23,"passenger HOME still follows vehicle theme");night.setInt(null,0);
  WindowState homeWindow=new WindowState();homeWindow.mActivityRecord=a;homeWindow.mAttrs=new android.view.WindowManager.LayoutParams();homeWindow.mAttrs.type=1;
  WindowFrames homeFrames=new WindowFrames();homeFrames.mFrame=new Rect(0,0,1600,720);
  Rect homeContaining=new Rect(0,0,1600,720),homeDisplay=new Rect(homeContaining);
  VoyahIranDisplayPolicy.beforeFrame(homeWindow,homeFrames,homeContaining,homeDisplay);VoyahIranDisplayPolicy.afterFrame(homeWindow,homeFrames);
  check(homeFrames.mFrame.toString().equals("0,0,1600,720") && homeContaining.toString().equals("0,0,1600,720"),"passenger HOME windows not clipped");
  a.display=0;check(VoyahIranDisplayPolicy.applies(a),"exemption limited to passenger display");a.display=1;
  a.display=0;check(VoyahIranDisplayPolicy.leftInset(a)==145,"passenger component on center cannot remove center gutter");a.display=1;
  a.info.name="com.abrar.voyahiran.feature.launcher.PassengerAppsActivity";check(VoyahIranDisplayPolicy.leftInset(a)==145 && VoyahIranDisplayPolicy.applies(a),"drawer keeps navigation bar gutter");a.info.name=null;
  a.info.applicationInfo.packageName="system.camera";check(!VoyahIranDisplayPolicy.applies(a),"passenger OEM excluded");
  a.info.applicationInfo.packageName="com.abrar.voyahiran";
  state(false);check(VoyahIranDisplayPolicy.orientation(a,7)==7,"passenger disable restores orientation");state(true);
  a.display=0;parentWin.bounds=new Rect(0,0,1920,720);parentWin.app=new Rect(0,0,1920,640);VoyahIranDisplayPolicy.configuration(a,parent);
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
  java.lang.reflect.Field density=VoyahIranDisplayPolicy.class.getDeclaredField("appDensity");density.setAccessible(true);
  a.info.applicationInfo.packageName="test.user";a.info.applicationInfo.flags=0;
  for(int dpi:new int[]{120,140,160,180,200}) {
   check(VoyahIranDisplayPolicy.densityFor(Integer.toString(dpi))==dpi,"preset accepted");density.setInt(null,dpi);
   state(true);VoyahIranDisplayPolicy.configuration(a,parent);
   check(a.config.densityDpi==dpi && a.config.screenWidthDp==1775*160/dpi && a.config.screenHeightDp==590*160/dpi,"density scales logical safe area, not physical bounds");
   check(out.bounds.toString().equals("145,50,1920,720"),"physical safe area stays fixed");
   state(false);VoyahIranDisplayPolicy.configuration(a,parent);
   check(a.config.screenWidthDp==1920*160/dpi,"density independent of safe area toggle");
  }
  check(VoyahIranDisplayPolicy.densityFor("999")==160,"invalid preset falls back to normal");
  a.info.applicationInfo.packageName="factory.camera";a.info.applicationInfo.flags=1;a.config.densityDpi=213;
  VoyahIranDisplayPolicy.configuration(a,parent);check(a.config.densityDpi==213,"OEM density untouched");
  java.lang.reflect.Field packages=VoyahIranDisplayPolicy.class.getDeclaredField("packageDensities");packages.setAccessible(true);
  packages.set(null,VoyahIranDisplayPolicy.parseDensities("test.user=120;test.other=200;bad=140;test.invalid=999"));
  check(VoyahIranDisplayPolicy.densityForPackage("test.user")==120,"package override wins");
  density.setInt(null,180);
  check(VoyahIranDisplayPolicy.densityForPackage("test.user")==120 && VoyahIranDisplayPolicy.densityForPackage("test.new")==180,"global changes preserve overrides");
  a.info.applicationInfo.flags=0;a.info.applicationInfo.packageName="test.user";a.display=1;state(true);
  VoyahIranDisplayPolicy.configuration(a,parent);check(a.config.densityDpi==120 && a.config.screenWidthDp==1775*160/120,"package density applied on passenger display");
  packages.set(null,VoyahIranDisplayPolicy.parseDensities("-"));VoyahIranDisplayPolicy.configuration(a,parent);
  check(a.config.densityDpi==180,"removing override follows current default");
  check(VoyahIranDisplayPolicy.parseDensities("bad=140;test.invalid=999").isEmpty(),"invalid overrides ignored");
  a.info.applicationInfo.packageName="com.abrar.voyahiran";a.config.densityDpi=160;
  VoyahIranDisplayPolicy.configuration(a,parent);
  check(a.config.densityDpi==160,"own app excluded from density while safe area remains");
  check(out.bounds.toString().equals("145,50,1920,720"),"own app safe area retained");
  System.out.println(checks+" display policy checks passed");
 }
}
