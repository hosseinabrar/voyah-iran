package com.abrar.voyahclustertest;

/** Percentages use the output half-width/half-height; mask radius 100 reaches ellipse edges. */
final class MapTuning {
 final int x,y,zoom,start,end,width,height;
 MapTuning(int x,int y,int zoom,int start,int end) {
  this(x,y,zoom,start,end,871,660);
 }
 MapTuning(int x,int y,int zoom,int start,int end,int width,int height) {
  if(x < -100 || x > 100 || y < -100 || y > 100 || zoom < 25 || zoom > 300 || start < 0 || end > 100 || start >= end)
   throw new IllegalArgumentException("Invalid map tuning");
  this.x=x;this.y=y;this.zoom=zoom;this.start=start;this.end=end;
  if(width<1||height<1||width>8192||height>8192)throw new IllegalArgumentException("Invalid source dimensions");
  this.width=width;this.height=height;
 }
 static MapTuning parse(String value) {
  String[] p=value.trim().split(" +");if(p.length!=5&&p.length!=7)throw new IllegalArgumentException("Expected five or seven tuning values");
  return new MapTuning(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),p.length==7?Integer.parseInt(p[5]):871,p.length==7?Integer.parseInt(p[6]):660);
 }
 public String toString(){return x+" "+y+" "+zoom+" "+start+" "+end+" "+width+" "+height;}
}
