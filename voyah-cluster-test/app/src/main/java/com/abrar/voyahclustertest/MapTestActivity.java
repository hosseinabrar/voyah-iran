package com.abrar.voyahclustertest;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;

/** Animated synthetic map: visible boundaries and animation, no location or network required. */
public final class MapTestActivity extends Activity {
 private final Handler handler=new Handler(); private String session,mode;private MapView map;
 private final Runnable check=new Runnable(){public void run(){
  try(BufferedReader r=new BufferedReader(new FileReader(new File(getFilesDir(),"map-lease")))){
   if(!session.equals(r.readLine())||SystemClock.elapsedRealtime()>=Long.parseLong(r.readLine())){finishAndRemoveTask();return;}
  }catch(Exception e){finishAndRemoveTask();return;}
  handler.postDelayed(this,250);
 }};
 @Override public void onCreate(Bundle b){super.onCreate(b);session=getIntent().getStringExtra("session");mode=getIntent().getStringExtra("mode");
  if(session==null){finish();return;}
  // Never cover the central display if a firmware ignores the OEM launch display options.
  int display=getWindowManager().getDefaultDisplay().getDisplayId();
  if(display==0){android.util.Log.e("VoyahClusterTest","REFUSED_RENDER_ON_DEFAULT_DISPLAY");finish();return;}
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  map=new MapView();setContentView(map);handler.post(check);
 }
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 private final class MapView extends View {
  final Paint p=new Paint(3);final long start=SystemClock.elapsedRealtime();long lastReport;int oldW,oldH;
  MapView(){super(MapTestActivity.this);}
  void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
  void text(Canvas c,String t,float x,float y,float size){p.setColor(Color.WHITE);p.setTextSize(size);c.drawText(t,x,y,p);}
  @Override protected void onDraw(Canvas canvas){
   int w=getWidth(),h=getHeight();float sx=w/1000f,sy=h/600f;
   canvas.drawColor(0xff172532);canvas.save();canvas.scale(sx,sy);
   for(int x=0;x<1000;x+=100)line(canvas,x,0,x,600,0xff304652,2);
   for(int y=0;y<600;y+=100)line(canvas,0,y,1000,y,0xff304652,2);
   line(canvas,100,580,470,330,0xff77828c,38);line(canvas,470,330,470,100,0xff77828c,38);line(canvas,470,100,850,100,0xff77828c,38);
   line(canvas,100,580,470,330,0xff259eff,13);line(canvas,470,330,470,100,0xff259eff,13);line(canvas,470,100,850,100,0xff259eff,13);
   float t=((SystemClock.elapsedRealtime()-start)%5000)/5000f;
   p.setColor(0xffffd857);canvas.drawCircle(470,330-230*t,13,p);
   text(canvas,"SIMULATED MAP / "+mode,40,55,30);text(canvas,w+" x "+h+" px",40,550,28);
   text(canvas,"Display "+getWindowManager().getDefaultDisplay().getDisplayId()+" / "+getResources().getDisplayMetrics().densityDpi+" dpi",40,587,22);
   text(canvas,"TL",10,100,24);text(canvas,"TR",940,100,24);text(canvas,"BL",10,510,24);text(canvas,"BR",940,580,24);
   p.setColor(0xff00efaf);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);canvas.drawRect(4,4,996,596,p);p.setStyle(Paint.Style.FILL);
   text(canvas,String.format(java.util.Locale.US,"%.1f s",(SystemClock.elapsedRealtime()-start)/1000f),800,550,28);canvas.restore();
   long now=SystemClock.elapsedRealtime();
   if(w!=oldW||h!=oldH||now-lastReport>1000){oldW=w;oldH=h;lastReport=now;post(()->{
    String info=session+" display="+getWindowManager().getDefaultDisplay().getDisplayId()+" size="+w+"x"+h+" density="+getResources().getDisplayMetrics().densityDpi+" time="+now+" state="+getIntent().getBundleExtra("com.qinggan.cluster.ClusterActivityState");
    try(FileWriter f=new FileWriter(new File(getFilesDir(),"map-frame"))){f.write(info);}catch(IOException e){android.util.Log.e("VoyahClusterTest","FRAME_REPORT_FAILED",e);}
   });}
   postInvalidateDelayed(33);
  }
 }
}
