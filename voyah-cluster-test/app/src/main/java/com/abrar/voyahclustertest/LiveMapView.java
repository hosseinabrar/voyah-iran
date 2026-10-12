package com.abrar.voyahclustertest;

import android.content.Context;
import android.graphics.*;
import android.hardware.display.*;
import android.os.SystemClock;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;

/** GPU composition: a resizable live map texture; no screenshot polling or bitmap copies. */
final class LiveMapView extends FrameLayout implements TextureView.SurfaceTextureListener {
 private final String session;
 private final TextureView texture;
 private VirtualDisplay display;private Surface surface;
 private MapTuning tuning=new MapTuning(0,0);
 private int sourceWidth=871,sourceHeight=660;
 private final Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
 private static final int[] MASKS={R.drawable.cluster_mask_01,R.drawable.cluster_mask_02,R.drawable.cluster_mask_03,R.drawable.cluster_mask_04,R.drawable.cluster_mask_05,R.drawable.cluster_mask_06,R.drawable.cluster_mask_07,R.drawable.cluster_mask_08,R.drawable.cluster_mask_09,R.drawable.cluster_mask_10,R.drawable.cluster_mask_11,R.drawable.cluster_mask_12};
 private final Bitmap[] maskCache=new Bitmap[MapTuning.MASK_COUNT];
 private Bitmap boxMask; private final RectF panel=new RectF(90,154,580,660);
 private final RectF maskBounds=new RectF();
 private long lastReport;private boolean hasFrame;private String lastTuning="";
 LiveMapView(Context context,String session){super(context);this.session=session;
  setBackgroundColor(Color.TRANSPARENT);setClipChildren(true);
  texture=new TextureView(context);texture.setOpaque(false);texture.setSurfaceTextureListener(this);
  addView(texture,new FrameLayout.LayoutParams(871,660,Gravity.CENTER));
  mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
  loadMask();refreshTuning();
 }
 void refreshTuning(){
  try(BufferedReader r=new BufferedReader(new FileReader(new File(getContext().getFilesDir(),"map-tuning")))){
   String value=r.readLine();if(value!=null&&!value.equals(lastTuning)){tuning=MapTuning.parse(value);loadMask();lastTuning=value;transform();invalidate();}
  }catch(FileNotFoundException ignored){}catch(Exception e){Log.e("VoyahClusterTest","TUNING_APPLY_FAILED",e);}
 }
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);transform();}
 private void transform(){
  // Zoom around source center; output offsets and OEM mask remain independent.
  float halfW=panel.width()*tuning.maskScale/200f,halfH=panel.height()*tuning.maskScale/200f;
  float cx=panel.centerX()+tuning.maskX,cy=panel.centerY()+tuning.maskY;
  maskBounds.set(cx-halfW,cy-halfH,cx+halfW,cy+halfH);
  Log.i("VoyahClusterTest","MAP_GEOMETRY mapOffset="+tuning.x+","+tuning.y+" zoom="+tuning.zoom+" maskOffset="+tuning.maskX+","+tuning.maskY+" maskScale="+tuning.maskScale+" enabled="+tuning.maskEnabled+" maskBounds="+maskBounds);
  texture.setScaleX(tuning.zoom/100f);texture.setScaleY(tuning.zoom/100f);
  texture.setTranslationX(panel.centerX()-getWidth()/2f+tuning.x);
  texture.setTranslationY(panel.centerY()-getHeight()/2f+tuning.y);
 }
 private void loadMask(){
  if(maskCache[tuning.maskId]!=null){boxMask=maskCache[tuning.maskId];return;}
  BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
  Bitmap original=BitmapFactory.decodeResource(getResources(),MASKS[tuning.maskId],options);
  int w=original.getWidth(),h=original.getHeight();int[] pixels=new int[w*h];
  original.getPixels(pixels,0,w,0,0,w,h);
  for(int i=0;i<pixels.length;i++){
   int c=pixels[i];int a=Math.round((Color.red(c)+Color.green(c)+Color.blue(c))/3f);
   pixels[i]=(a<<24)|0xffffff;
  }
  boxMask=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);original.recycle();maskCache[tuning.maskId]=boxMask;
  mask.setFilterBitmap(true);
  Log.i("VoyahClusterTest","FIXED_MASK id="+tuning.maskId+" panel="+panel+" source="+sourceWidth+"x"+sourceHeight);
 }
 @Override protected void dispatchDraw(Canvas canvas){
  if(!tuning.maskEnabled){super.dispatchDraw(canvas);return;}
  int clip=canvas.save();canvas.clipRect(maskBounds);
  int layer=canvas.saveLayer(maskBounds,null);
  super.dispatchDraw(canvas);
  canvas.drawBitmap(boxMask,null,maskBounds,mask);
  canvas.restoreToCount(layer);canvas.restoreToCount(clip);
 }
 @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){
  try {
   st.setDefaultBufferSize(sourceWidth,sourceHeight);surface=new Surface(st);
   int destroyContent=DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL").getInt(null);
   display=getContext().getSystemService(DisplayManager.class).createVirtualDisplay("VoyahClusterTest.MapSource."+session,sourceWidth,sourceHeight,160,surface,
    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|destroyContent);
   if(display==null)throw new IOException("No virtual display");
   File target=new File(getContext().getFilesDir(),"map-source"),temp=new File(target+".new");
   try(FileWriter f=new FileWriter(temp)){f.write(session+" "+display.getDisplay().getDisplayId());}
   if(!temp.renameTo(target))throw new IOException("Cannot publish source display");
   Log.i("VoyahClusterTest","SOURCE_CREATED display="+display.getDisplay().getDisplayId()+" size="+sourceWidth+"x"+sourceHeight+" density=160");
  }catch(Exception e){Log.e("VoyahClusterTest","SOURCE_CREATE_FAILED",e);close();}
 }
 @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){st.setDefaultBufferSize(sourceWidth,sourceHeight);}
 @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){close();return true;}
 @Override public void onSurfaceTextureUpdated(SurfaceTexture st){
  hasFrame=true;long now=SystemClock.elapsedRealtime();if(now-lastReport<1000)return;lastReport=now;
  try(FileWriter f=new FileWriter(new File(getContext().getFilesDir(),"map-frame"))){
   f.write(session+" live=true display="+getDisplay().getDisplayId()+" output="+getWidth()+"x"+getHeight()+" source="+sourceWidth+"x"+sourceHeight+" time="+now+" tuning="+tuning);
  }catch(IOException e){Log.e("VoyahClusterTest","FRAME_REPORT_FAILED",e);}
 }
 void close(){if(display!=null){display.release();display=null;}if(surface!=null){surface.release();surface=null;}hasFrame=false;}
}
