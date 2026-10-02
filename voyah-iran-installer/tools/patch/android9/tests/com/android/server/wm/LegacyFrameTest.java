package com.android.server.wm;
import android.graphics.Rect;
public class LegacyFrameTest {
 public static void main(String[] args) {
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
