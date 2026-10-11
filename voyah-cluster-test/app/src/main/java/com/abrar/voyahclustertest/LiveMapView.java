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
 private MapTuning tuning=new MapTuning(0,0,100,50,85);
 private int sourceWidth=871,sourceHeight=660;
 private final Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
 private long lastReport;private boolean hasFrame;private String lastTuning="";
 LiveMapView(Context context,String session){super(context);this.session=session;
  setBackgroundColor(Color.BLACK);setClipChildren(true);
  texture=new TextureView(context);texture.setOpaque(false);texture.setSurfaceTextureListener(this);
  addView(texture,new FrameLayout.LayoutParams(871,660,Gravity.CENTER));
  mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
  refreshTuning();
 }
 void refreshTuning(){
  try(BufferedReader r=new BufferedReader(new FileReader(new File(getContext().getFilesDir(),"map-tuning")))){
   String value=r.readLine();if(value!=null&&!value.equals(lastTuning)){tuning=MapTuning.parse(value);resizeSource();lastTuning=value;transform();invalidate();}
  }catch(FileNotFoundException ignored){}catch(Exception e){Log.e("VoyahClusterTest","TUNING_APPLY_FAILED",e);}
 }
 private void resizeSource(){
  Display central=getContext().getSystemService(DisplayManager.class).getDisplay(0);
  if(central==null)throw new IllegalStateException("Central display unavailable");
  Point maximum=new Point();central.getRealSize(maximum);
  int w=Math.min(tuning.width,maximum.x),h=Math.min(tuning.height,maximum.y);
  if(w==sourceWidth&&h==sourceHeight)return;
  SurfaceTexture st=texture.getSurfaceTexture();if(st!=null)st.setDefaultBufferSize(w,h);
  if(display!=null)display.resize(w,h,160);
  sourceWidth=w;sourceHeight=h;
  texture.setLayoutParams(new FrameLayout.LayoutParams(w,h,Gravity.CENTER));
  Log.i("VoyahClusterTest","SOURCE_RESIZED size="+w+"x"+h+" max="+maximum.x+"x"+maximum.y);
 }
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);transform();}
 private void transform(){
  float scale=Math.min(getWidth()/(float)sourceWidth,getHeight()/(float)sourceHeight)*tuning.zoom/100f;
  texture.setScaleX(scale);texture.setScaleY(scale);
  texture.setTranslationX(getWidth()*.5f*tuning.x/100f);texture.setTranslationY(getHeight()*.5f*tuning.y/100f);
  // Elliptical radius: 0 at output centre, 100 at the middle of each output edge.
  RadialGradient gradient=new RadialGradient(0,0,1,new int[]{Color.WHITE,Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT},
   new float[]{0,tuning.start/100f,tuning.end/100f,1},Shader.TileMode.CLAMP);
  Matrix matrix=new Matrix();matrix.setScale(Math.max(1,getWidth()/2f),Math.max(1,getHeight()/2f));matrix.postTranslate(getWidth()/2f,getHeight()/2f);
  gradient.setLocalMatrix(matrix);mask.setShader(gradient);
 }
 @Override protected void dispatchDraw(Canvas canvas){
  int layer=canvas.saveLayer(0,0,getWidth(),getHeight(),null);
  super.dispatchDraw(canvas);canvas.drawRect(0,0,getWidth(),getHeight(),mask);canvas.restoreToCount(layer);
 }
 @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){
  try {
   st.setDefaultBufferSize(sourceWidth,sourceHeight);surface=new Surface(st);
   display=getContext().getSystemService(DisplayManager.class).createVirtualDisplay("VoyahClusterTest.MapSource."+session,sourceWidth,sourceHeight,160,surface,
    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
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
