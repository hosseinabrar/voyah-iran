package com.android.server.wm;
import android.graphics.Rect;
public class LegacyFrameTest {
 public static void main(String[] args) {
  if (!VoyahIranLegacyFrames.supportsLoginWelcomeVariant("f0506h f0506h f0506h for arm64 qti/f0506h/f0506h:9/PQ3B/9:user/jenkins-PRJ_H97a_8155_OTA_1010-9,test-keys")) throw new AssertionError("fingerprint-only h97a");
  if (!VoyahIranLegacyFrames.supportsLoginWelcomeVariant("msmnile_gvmq msmnile_gvmq msmnile_gvmq for arm64 sa8155 qti/msmnile_gvmq/msmnile_gvmq:11/RQ3A.211001.001/35:user/jenkins-PRJ_H97c_Platform_8155_OTA7.2_0430-35,test-keys")) throw new AssertionError("fingerprint-only h97c");
  if (VoyahIranLegacyFrames.supportsLoginWelcomeVariant("h97x") || VoyahIranLegacyFrames.supportsLoginWelcomeVariant("h97abc")) throw new AssertionError("unrelated model");

  Rect safe=new Rect(145,50,1920,720);
  Rect frame=new Rect(145,50,1920,608);
  VoyahIranLegacyFrames.restoreBottom(frame,608,safe);
  if(frame.bottom!=720)throw new AssertionError("bottom decor retained");
  frame.bottom=400;VoyahIranLegacyFrames.restoreBottom(frame,608,safe);
  if(frame.bottom!=400)throw new AssertionError("shorter content frame expanded");
  frame.bottom=720;VoyahIranLegacyFrames.restoreBottom(frame,608,safe);
  if(frame.bottom!=720)throw new AssertionError("full frame changed");
  frame.bottom=608;VoyahIranLegacyFrames.restoreBottom(frame,720,safe);
  if(frame.bottom!=608)throw new AssertionError("non-decor frame changed");
  System.out.println("4 Android 9 bottom frame checks passed");
 }
}
