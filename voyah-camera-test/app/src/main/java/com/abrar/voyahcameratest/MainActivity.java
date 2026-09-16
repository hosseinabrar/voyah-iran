package com.abrar.voyahcameratest;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
 private CameraManager manager;
 private TextureView preview;
 private TextView status;
 private Spinner cameras, physical;
 private final Handler handler = new Handler(Looper.getMainLooper());
 private final List<Route> routes = new ArrayList<>();
 private final List<Route> physicalRoutes = new ArrayList<>();
 private Route selected;
 private CameraDevice device;
 private CameraCaptureSession session;
 private Surface surface;
 private int generation;
 private boolean populating, active;
 private long frames, started;
 private File logFile;
 private Size size;
 private final String sessionId = java.util.UUID.randomUUID().toString().substring(0,8);
 private long logSequence;
 private final CameraManager.AvailabilityCallback availability = new CameraManager.AvailabilityCallback() {
  public void onCameraAvailable(String id) { log("AVAILABILITY id="+id+" available=true"); }
  public void onCameraUnavailable(String id) { log("AVAILABILITY id="+id+" available=false (may be occupied, not proof of failure)"); }
 };
 private static class Route {
  final String parent, child;
  Route(String p,String c){parent=p;child=c;}
  public String toString(){return child==null ? "Camera ID: "+parent : "Physical ID: "+child+" / Parent: "+parent;}
 }
 public void onCreate(Bundle state) {
  super.onCreate(state); setContentView(R.layout.activity_main);
  View content=findViewById(android.R.id.content);
  content.setOnApplyWindowInsetsListener((v,insets)->{
   if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
   return insets;
  });
  File dir=new File(getFilesDir(),"reports"); dir.mkdirs();
  logFile=new File(dir,"camera-"+System.currentTimeMillis()+".txt");
  manager=(CameraManager)getSystemService(CAMERA_SERVICE);
  preview=findViewById(R.id.preview);status=findViewById(R.id.status);
  cameras=findViewById(R.id.cameras);physical=findViewById(R.id.physical);
  log("SESSION version=0.1.0 SDK="+Build.VERSION.SDK_INT+" manufacturer="+Build.MANUFACTURER+" model="+Build.MODEL+" fingerprint="+Build.FINGERPRINT);
  log("No images/audio recorded. No ECU commands, root operations, service stops or network probing. Camera2 inventory is not a complete physical vehicle inventory.");
  cameras.setOnItemSelectedListener(listener(false)); physical.setOnItemSelectedListener(listener(true));
  findViewById(R.id.open).setOnClickListener(v->openSelected());
  findViewById(R.id.stop).setOnClickListener(v->{closeCamera("user stop");show("نمایش متوقف شد");});
  findViewById(R.id.refresh).setOnClickListener(v->inventory());
  findViewById(R.id.share).setOnClickListener(v->share());
  preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
   public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){}
   public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){transform();}
   public boolean onSurfaceTextureDestroyed(SurfaceTexture t){closeCamera("surface destroyed");return true;}
   public void onSurfaceTextureUpdated(SurfaceTexture t){
    if(device==null)return;
    frames++;
    if(frames==1){log("FIRST_TEXTURE_FRAME elapsedMs="+(SystemClock.elapsedRealtime()-started));show("تصویر دریافت شد — "+selected);}
    if(frames%300==0)log("TEXTURE_FRAMES count="+frames);
   }
  });
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA},10);
  else inventory();
 }
 private AdapterView.OnItemSelectedListener listener(boolean sub){return new AdapterView.OnItemSelectedListener(){
  public void onNothingSelected(AdapterView<?> p){}
  public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
   if(populating||pos==0)return;
   closeCamera("selection changed");
   selected=(sub?physicalRoutes:routes).get(pos-1);
   if(sub)cameras.setSelection(0);else physical.setSelection(0);
   log("SELECT "+selected);show("انتخاب شد: "+selected+" — نمایش تصویر را بزنید");
  }
 };}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);log("CAMERA_PERMISSION granted="+(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED));inventory();}
 private void fill(Spinner spinner,List<Route> list,String title){
  List<String> names=new ArrayList<>();names.add(title);for(Route r:list)names.add(r.toString());
  ArrayAdapter<String> a=new ArrayAdapter<>(this,R.layout.spinner_item,names);a.setDropDownViewResource(R.layout.spinner_item);spinner.setAdapter(a);
 }
 private void inventory(){
  closeCamera("inventory refresh");selected=null;routes.clear();physicalRoutes.clear();populating=true;
  try{
   String[] ids=manager.getCameraIdList();log("INVENTORY publicIds="+Arrays.toString(ids));
   for(String id:ids){
    CameraCharacteristics c=manager.getCameraCharacteristics(id);routes.add(new Route(id,null));describe(id,c);
    for(String child:c.getPhysicalCameraIds()){
     physicalRoutes.add(new Route(id,child));log("PHYSICAL parent="+id+" id="+child);
     try{describe(child,manager.getCameraCharacteristics(child));}catch(Exception e){log("PHYSICAL_METADATA_UNAVAILABLE "+child+" "+e);}
    }
   }
   show(ids.length==0?"اندروید دوربینی معرفی نکرده است":"دوربین را انتخاب کنید و نمایش تصویر را بزنید");
   if(physicalRoutes.isEmpty())log("No logical-camera physical routes exposed; Apollo cameras cannot be inferred or enumerated through an unverified private interface.");
  }catch(Exception e){error("inventory",e);}
  finally{fill(cameras,routes,"انتخاب دوربین اندروید");fill(physical,physicalRoutes,physicalRoutes.isEmpty()?"مسیر فیزیکی در دسترس نیست":"انتخاب مسیر فیزیکی");populating=false;}
 }
 private void describe(String id,CameraCharacteristics c){
  StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
  log("CHARACTERISTICS id="+id+" facing="+c.get(CameraCharacteristics.LENS_FACING)+" orientation="+c.get(CameraCharacteristics.SENSOR_ORIENTATION)+" hardware="+c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)+" capabilities="+Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))+" previewSizes="+(map==null?"null":Arrays.toString(map.getOutputSizes(SurfaceTexture.class))));
 }
 private void openSelected(){
  if(!active||selected==null){show("ابتدا یک دوربین انتخاب کنید");return;}
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},10);return;}
  if(!preview.isAvailable()){show("نمایشگر آماده نیست؛ دوباره تلاش کنید");return;}
  closeCamera("new open");final int token=generation;final Route route=selected;
  try{
   CameraCharacteristics parent=manager.getCameraCharacteristics(route.parent);
   StreamConfigurationMap map=parent.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
   Size[] sizes=map==null?null:map.getOutputSizes(SurfaceTexture.class);
   if(sizes==null||sizes.length==0)throw new IllegalStateException("No SurfaceTexture output sizes");
   List<Size> candidates=new ArrayList<>(Arrays.asList(sizes));
   if(route.child!=null){
    StreamConfigurationMap childMap=manager.getCameraCharacteristics(route.child).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    Size[] childSizes=childMap==null?null:childMap.getOutputSizes(SurfaceTexture.class);
    if(childSizes==null)throw new IllegalStateException("Physical output sizes unavailable");
    candidates.retainAll(Arrays.asList(childSizes));
   }
   if(candidates.isEmpty())throw new IllegalStateException("No common parent/physical preview sizes");
   candidates.sort(Comparator.comparingLong(s->Math.abs((long)s.getWidth()*s.getHeight()-1280L*720)));
   size=candidates.get(0);frames=0;started=SystemClock.elapsedRealtime();
   preview.getSurfaceTexture().setDefaultBufferSize(size.getWidth(),size.getHeight());transform();
   surface=new Surface(preview.getSurfaceTexture());
   log("OPEN_REQUEST "+route+" size="+size);show("در حال اتصال: "+route);
   manager.openCamera(route.parent,new CameraDevice.StateCallback(){
    public void onOpened(CameraDevice d){if(token!=generation||!active){d.close();return;}device=d;log("OPENED "+route);configure(d,route,token);}
    public void onDisconnected(CameraDevice d){d.close();if(token==generation){closeCamera("disconnected");show("ارتباط دوربین قطع شد");}}
    public void onError(CameraDevice d,int code){d.close();log("DEVICE_ERROR "+route+" code="+code+" (1=in use,2=max cameras,3=disabled,4=device,5=service)");if(token==generation){closeCamera("device error");show("خطای دوربین: "+code+"؛ گزارش را ارسال کنید");}}
   },handler);
   handler.postDelayed(()->{if(token==generation&&frames==0){log("NO_FRAME_TIMEOUT 12000ms");closeCamera("no frame timeout");show("تصویری دریافت نشد؛ گزارش را ارسال کنید");}},12000);
  }catch(Exception e){closeCamera("open exception");error("open "+route,e);}
 }
 private void configure(CameraDevice d,Route route,int token){
  try{
   OutputConfiguration output=new OutputConfiguration(surface);if(route.child!=null)output.setPhysicalCameraId(route.child);
   d.createCaptureSessionByOutputConfigurations(Collections.singletonList(output),new CameraCaptureSession.StateCallback(){
    public void onConfigured(CameraCaptureSession s){
     if(token!=generation||!active){s.close();return;}session=s;log("SESSION_CONFIGURED route="+route+" size="+size);
     try{CaptureRequest.Builder b=d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(surface);
      s.setRepeatingRequest(b.build(),new CameraCaptureSession.CaptureCallback(){
       private int failures;
       private long completed;
       public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){++failures;log("CAPTURE_FAILURE route="+route+" reason="+f.getReason()+" frame="+f.getFrameNumber()+" sequence="+f.getSequenceId()+" captured="+f.wasImageCaptured()+" count="+failures);}
      public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){
        if(++completed==1||completed%300==0)log("CAPTURE_RESULT route="+route+" count="+completed+" frame="+result.getFrameNumber()+" sensorTimestamp="+result.get(CaptureResult.SENSOR_TIMESTAMP)+" exposureNs="+result.get(CaptureResult.SENSOR_EXPOSURE_TIME)+" iso="+result.get(CaptureResult.SENSOR_SENSITIVITY));
       }
       public void onCaptureSequenceAborted(CameraCaptureSession s,int sequence){log("SEQUENCE_ABORTED route="+route+" sequence="+sequence);}
       public void onCaptureBufferLost(CameraCaptureSession s,CaptureRequest r,Surface target,long frame){log("BUFFER_LOST route="+route+" frame="+frame);}
      },handler);log("REPEATING_STARTED "+route);
     }catch(Exception e){closeCamera("repeat exception");error("repeating",e);}
    }
    public void onConfigureFailed(CameraCaptureSession s){log("SESSION_CONFIGURE_FAILED route="+route+" token="+token);s.close();if(token==generation){closeCamera("configure failed");show("راه‌اندازی تصویر ناموفق بود؛ گزارش را ارسال کنید");}}
   },handler);
  }catch(Exception e){closeCamera("configure exception");error("configure",e);}
 }
 private void transform(){
  if(size==null||preview.getWidth()==0||preview.getHeight()==0)return;
  // Preserve the raw sensor orientation, with aspect-fit rather than stretching.
  float w=preview.getWidth(),h=preview.getHeight();float scale=Math.min(w/size.getWidth(),h/size.getHeight());
  Matrix m=new Matrix();m.setScale(size.getWidth()*scale/w,size.getHeight()*scale/h,w/2,h/2);preview.setTransform(m);
 }
 private void closeCamera(String reason){generation++;if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}if(surface!=null){surface.release();surface=null;}log("CLOSE reason="+reason+" textureFrames="+frames);}
 private void show(String text){log("UI_STATUS "+text);status.setText(text);}
 private void error(String where,Exception e){log("ERROR "+where+" "+Log.getStackTraceString(e));show("خطا: "+e.getMessage()+" — گزارش را ارسال کنید");}
 private synchronized void log(String text){
  // Bound each entry below Logcat's byte limit, including Persian text and stack traces.
  long seq=++logSequence;
  String prefix="session="+sessionId+" event="+seq+" attempt="+generation+" uptime="+SystemClock.elapsedRealtime()+" ";
  int parts=Math.max(1,(text.length()+699)/700);
  for(int i=0;i<parts;i++) {
   String chunk=text.substring(i*700,Math.min(text.length(),(i+1)*700));
   Log.i("VI/CameraProbe",prefix+"part="+(i+1)+"/"+parts+" "+chunk);
  }
  if(logFile==null)return;
  try(FileWriter w=new FileWriter(logFile,true)){w.write(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date())+" uptime="+SystemClock.elapsedRealtime()+" "+text+"\n");}
  catch(IOException e){Log.e("VI/CameraProbe","Cannot save report",e);}
 }
 private void share(){
  closeCamera("share report");log("EXPORT report includes all retained test sessions; camera frames are not stored");
  try{
   File exported=new File(getCacheDir(),"camera-report.txt");
   try(FileOutputStream out=new FileOutputStream(exported)){
    File[] reports=logFile.getParentFile().listFiles((d,n)->n.endsWith(".txt"));
    if(reports!=null){Arrays.sort(reports);for(File f:reports){out.write(("\n=== "+f.getName()+" ===\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}}
   }
   android.net.Uri uri=android.net.Uri.parse("content://com.abrar.voyahcameratest.reports/camera-report.txt");
   Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   i.setClipData(ClipData.newRawUri("camera report",uri));startActivity(Intent.createChooser(i,"ارسال گزارش دوربین"));
  }catch(Exception e){error("export",e);}
 }
 protected void onResume(){super.onResume();active=true;manager.registerAvailabilityCallback(availability,handler);log("RESUME");}
 protected void onPause(){active=false;closeCamera("activity paused");manager.unregisterAvailabilityCallback(availability);super.onPause();}
}
