package com.abrar.voyahclustertest;
public final class MapTuningCheck {
 public static void main(String[] args){
  MapTuning t=MapTuning.parse("-30 20 125 50 85");
  if(t.x!=-30||t.y!=20||t.zoom!=125||t.start!=50||t.end!=85)throw new AssertionError();
  if(!t.toString().equals("-30 20 125 50 85 871 660"))throw new AssertionError();
  MapTuning size=MapTuning.parse("0 0 100 50 85 1920 720");
  if(size.width!=1920||size.height!=720)throw new AssertionError();
  for(String invalid:new String[]{"0 0 100 85 50","0 0 100 50 50","101 0 100 50 85","0 0 0 50 85","0 0 100 0 101","0 0 100","0 0 100 50 85 0 720","0 0 100 50 85 1920 9000"}){
   try{MapTuning.parse(invalid);throw new AssertionError(invalid);}catch(IllegalArgumentException expected){}
  }
  MapTuning.parse("-100 100 25 0 1");MapTuning.parse("100 -100 300 99 100");
  System.out.println("Map tuning bounds and round-trip passed");
 }
}
