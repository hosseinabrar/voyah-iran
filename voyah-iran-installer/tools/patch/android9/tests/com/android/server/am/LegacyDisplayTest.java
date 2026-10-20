package com.android.server.am;
import android.graphics.Rect;
import android.content.pm.*;
import android.content.res.Configuration;
import com.android.server.wm.ConfigurationContainer;
public class LegacyDisplayTest {
 static int count;
 static void check(boolean b,String message){count++;if(!b)throw new AssertionError(message);}
 static void state(String name,boolean value)throws Exception {java.lang.reflect.Field f=VoyahIranLegacyDisplay.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 static class WindowConfig extends android.app.WindowConfiguration { Rect full; public Rect getBounds(){return full;} }
 public static void main(String[] args)throws Exception {
  state("scheduled",true);state("enabled",true);
  ActivityRecord a=new ActivityRecord();a.info=new ActivityInfo();a.info.applicationInfo=new ApplicationInfo();
  a.info.applicationInfo.uid=10050;a.info.applicationInfo.packageName="user.player";
  check(VoyahIranLegacyDisplay.applies(a),"user apps included");
  check(VoyahIranLegacyDisplay.orientation(a,1)==0,"portrait forced landscape");
  check(VoyahIranLegacyDisplay.orientation(a,-2)==-2,"no-source sentinel preserved");
  a.parent=new ConfigurationContainer();a.parent.config=new Configuration();WindowConfig wc=new WindowConfig();
  a.parent.config.windowConfiguration=wc;wc.full=new Rect(0,0,1920,720);Rect out=new Rect();
  a.screen=new ActivityDisplay();a.screen.mDisplay=new android.view.Display();a.screen.config=a.parent.config;a.screen.bounds=wc.full; VoyahIranLegacyDisplay.bounds(a,out);check(out.toString().equals("145,50,1920,720"),"central safe area");
  a.display=1;a.screen.mDisplay.width=1600;wc.full=new Rect(0,0,1600,720);a.screen.bounds=wc.full;VoyahIranLegacyDisplay.bounds(a,out);
  check(out.toString().equals("145,50,1600,720"),"passenger uses its own dimensions");
  a.info.packageName="com.abrar.voyahiran";a.info.name="com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity";
  VoyahIranLegacyDisplay.bounds(a,out);check(out.left==0 && out.top==50,"passenger HOME full width");
  a.display=0;check(VoyahIranLegacyDisplay.leftInset(a)==145,"center stays unchanged");a.display=1;
  a.info.name="com.abrar.voyahiran.feature.launcher.PassengerAppsActivity";check(VoyahIranLegacyDisplay.leftInset(a)==145,"drawer retains gutter");a.info.name=null;
  a.display=0;a.screen.mDisplay.width=1920;wc.full=new Rect(0,0,1920,608);a.screen.bounds=new Rect(0,0,1920,608);
  VoyahIranLegacyDisplay.bounds(a,out);
  check(out.toString().equals("145,50,1920,720"),"112px OEM bottom decor must not shorten safe bounds");
  Configuration override=new Configuration();override.windowConfiguration=new android.app.WindowConfiguration();
  VoyahIranLegacyDisplay.configuration(a,override);
  check(override.windowConfiguration.appBounds.toString().equals("145,50,1920,720") && override.screenHeightDp==670,"configuration includes bottom pixels");
  a.display=2;check(!VoyahIranLegacyDisplay.applies(a),"cluster excluded");a.display=0;
  a.info.applicationInfo.flags=1;check(!VoyahIranLegacyDisplay.applies(a),"factory apps excluded");
  a.info.applicationInfo.flags=128;check(!VoyahIranLegacyDisplay.applies(a),"updated factory apps excluded");
  a.info.applicationInfo.packageName="com.abrar.voyahiran";check(VoyahIranLegacyDisplay.applies(a),"own app included");
  a.type=2;check(!VoyahIranLegacyDisplay.applies(a),"home excluded");a.type=1;
  a.multi=true;check(!VoyahIranLegacyDisplay.applies(a),"multiwindow excluded");a.multi=false;
  a.screen.mDisplay.width=300;a.screen.mDisplay.height=200;wc.full=new Rect(0,0,300,200);a.screen.bounds=wc.full;out.set(1,2,3,4);VoyahIranLegacyDisplay.bounds(a,out);
  check(out.toString().equals("1,2,3,4"),"small/invalid displays unchanged");
  state("enabled",false);check(VoyahIranLegacyDisplay.orientation(a,1)==1,"disable preserves portrait request");
  wc.full=new Rect(0,0,1920,720);VoyahIranLegacyDisplay.bounds(a,out);
  check(out.toString().equals("1,2,3,4"),"disabled bounds unchanged");
  check(VoyahIranLegacyDisplay.nightFor("vehicle","default")==0x20,"Classic/Modern dark");
  check(VoyahIranLegacyDisplay.nightFor("vehicle","simple")==0x10,"Speedy Dash light");
  check(VoyahIranLegacyDisplay.nightFor("dark","simple")==0x20,"dark override");
  check(VoyahIranLegacyDisplay.nightFor("light","default")==0x10,"light override");
  java.lang.reflect.Field night=VoyahIranLegacyDisplay.class.getDeclaredField("nightMode");night.setAccessible(true);night.setInt(null,0x20);
  a.info.applicationInfo.flags=0;a.display=0;override.uiMode=0x13;
  VoyahIranLegacyDisplay.configuration(a,override);check(override.uiMode==0x23,"night works with safe area off and preserves type");
  night.setInt(null,0x10);VoyahIranLegacyDisplay.configuration(a,override);check(override.uiMode==0x13,"switch to light");
  a.info.applicationInfo.packageName="factory.app";a.info.applicationInfo.flags=1;night.setInt(null,0x20);
  VoyahIranLegacyDisplay.configuration(a,override);check(override.uiMode==0x13,"factory theme untouched");
  a.info.applicationInfo.flags=0;a.display=1;VoyahIranLegacyDisplay.configuration(a,override);check(override.uiMode==0x23,"passenger theme applied");
  java.lang.reflect.Field density=VoyahIranLegacyDisplay.class.getDeclaredField("appDensity");density.setAccessible(true);
  a.screen.mDisplay.width=1920;a.screen.mDisplay.height=720;state("enabled",true);
  for(int dpi:new int[]{120,140,160,180,200,240}) {
   check(VoyahIranLegacyDisplay.parseDensities("test.huge="+dpi).get("test.huge")==dpi,"all presets accepted per package");
   check(VoyahIranLegacyDisplay.densityFor(Integer.toString(dpi))==dpi,"preset accepted");density.setInt(null,dpi);
   VoyahIranLegacyDisplay.configuration(a,override);
   check(override.densityDpi==dpi && override.screenWidthDp==1775*160/dpi && override.screenHeightDp==670*160/dpi,"density scales logical area");
   check(override.windowConfiguration.appBounds.toString().equals("145,50,1920,720"),"physical safe area unchanged");
   check(a.override.densityDpi==dpi,"live override updated when bounds unchanged");
  }
  state("enabled",false);density.setInt(null,120);VoyahIranLegacyDisplay.configuration(a,override);
  check(override.densityDpi==120,"density works with safe area disabled");
  a.info.applicationInfo.flags=1;override.densityDpi=213;VoyahIranLegacyDisplay.configuration(a,override);
  check(override.densityDpi==213,"OEM density unchanged");
  check(VoyahIranLegacyDisplay.densityFor(null)==160,"normal is default");
  java.lang.reflect.Field packages=VoyahIranLegacyDisplay.class.getDeclaredField("packageDensities");packages.setAccessible(true);
  packages.set(null,VoyahIranLegacyDisplay.parseDensities("user.player=200;test.other=120;bad=140;test.invalid=999"));
  a.info.applicationInfo.flags=0;a.info.applicationInfo.packageName="user.player";state("enabled",true);
  VoyahIranLegacyDisplay.configuration(a,override);
  check(override.densityDpi==200 && override.screenWidthDp==1775*160/200,"per-package safe-area density");
  check(a.override.densityDpi==200,"live package override updated");
  density.setInt(null,140);VoyahIranLegacyDisplay.configuration(a,override);
  check(override.densityDpi==200 && VoyahIranLegacyDisplay.densityForPackage("test.new")==140,"global changes preserve custom choice");
  packages.set(null,VoyahIranLegacyDisplay.parseDensities("-"));VoyahIranLegacyDisplay.configuration(a,override);
  check(override.densityDpi==140 && a.override.densityDpi==140,"inherit restores current default live");
  check(VoyahIranLegacyDisplay.parseDensities("bad=140;test.invalid=999").isEmpty(),"invalid overrides ignored");
  a.info.applicationInfo.packageName="com.abrar.voyahiran";override.densityDpi=160;
  a.override.densityDpi=160;
  VoyahIranLegacyDisplay.configuration(a,override);
  check(override.densityDpi==160 && a.override.densityDpi==160,"own app excluded from density");
  check(override.screenWidthDp==1775 && override.screenHeightDp==670,"own app safe area retained with native density");
  System.out.println(count+" Android 9 display checks passed");
 }
}

