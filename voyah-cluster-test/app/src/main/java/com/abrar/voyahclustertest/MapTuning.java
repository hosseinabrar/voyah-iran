package com.abrar.voyahclustertest;
/** Fixed launcher render; translation is in pixels. */
final class MapTuning {
 static final int WIDTH=871, HEIGHT=660;
 static final int MASK_COUNT=12;
 final int x,y,zoom,maskId,maskX,maskY,maskScale; final boolean maskEnabled;
 MapTuning(int x,int y) {this(x,y,100);}
 MapTuning(int x,int y,int zoom) {this(x,y,zoom,0);}
 MapTuning(int x,int y,int zoom,int maskId) {this(x,y,zoom,maskId,0,0,100,true);}
 MapTuning(int x,int y,int zoom,int maskId,int maskX,int maskY,int maskScale,boolean maskEnabled) {
  if(Math.abs((long)x)>WIDTH||Math.abs((long)y)>HEIGHT)throw new IllegalArgumentException("Invalid offset");
  if(zoom<25||zoom>300)throw new IllegalArgumentException("Invalid zoom");
  if(maskId<0||maskId>=MASK_COUNT)throw new IllegalArgumentException("Invalid mask");
  if(Math.abs((long)maskX)>1920||Math.abs((long)maskY)>720||maskScale<25||maskScale>300)throw new IllegalArgumentException("Invalid mask transform");
  this.maskX=maskX;this.maskY=maskY;this.maskScale=maskScale;this.maskEnabled=maskEnabled;
  this.x=x;this.y=y;this.zoom=zoom;this.maskId=maskId;
 }
 static MapTuning parse(String value) {
  String[] p=value.trim().split("\\s+");
  if(p.length==9&&p[0].equals("v2")){if(!p[8].equals("0")&&!p[8].equals("1"))throw new IllegalArgumentException("Invalid enabled flag");return new MapTuning(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7]),p[8].equals("1"));}
  if(p.length>=2&&p.length<=4)return new MapTuning(Integer.parseInt(p[0]),Integer.parseInt(p[1]),p.length>=3?Integer.parseInt(p[2]):100,p.length==4?Integer.parseInt(p[3]):0);
  if(p.length==5||p.length==7||p.length==8||p.length==9)return new MapTuning(0,0);
  throw new IllegalArgumentException("Expected X Y pixel offsets and zoom percent");
 }
 public String toString(){return "v2 "+x+" "+y+" "+zoom+" "+maskId+" "+maskX+" "+maskY+" "+maskScale+" "+(maskEnabled?1:0);}
}
