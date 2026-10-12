package com.abrar.voyahclustertest;
public final class MapTuningCheck {
 public static void main(String[] args){
  MapTuning t=MapTuning.parse("-123 45");
  if(t.x!=-123||t.y!=45||!t.toString().equals("v2 -123 45 100 0 0 0 100 1"))throw new AssertionError("round trip");
  if(MapTuning.parse("-100 20 100 50 85 800 800 1 2").x!=0)throw new AssertionError("legacy reset");
  if(MapTuning.WIDTH!=871||MapTuning.HEIGHT!=660)throw new AssertionError("card size");
  if(MapTuning.parse("12 -34 50").zoom!=50||MapTuning.parse("12 -34").zoom!=100)throw new AssertionError("zoom migration");
  for(int id=0;id<MapTuning.MASK_COUNT;id++){MapTuning m=MapTuning.parse("12 -34 50 "+id);if(m.maskId!=id||m.x!=12||m.y!=-34||m.zoom!=50)throw new AssertionError("mask round trip");}
  MapTuning full=MapTuning.parse(new MapTuning(2,-10,95,7,120,-40,75,false).toString());
  if(full.maskX!=120||full.maskY!=-40||full.maskScale!=75||full.maskEnabled||full.x!=2||full.zoom!=95)throw new AssertionError("independent mask transform");
  for(String bad:new String[]{"0 0 100 -1","0 0 100 12","v2 0 0 100 0 1921 0 100 1","v2 0 0 100 0 0 0 24 1","v2 0 0 100 0 0 0 100 2","0 0 24","0 0 301","872 0","0 -661","2147483647 0","-2147483648 0","0","a b"}){
   try{MapTuning.parse(bad);throw new AssertionError(bad);}catch(IllegalArgumentException expected){}
  }
  System.out.println("MapTuningCheck passed");
 }
}
