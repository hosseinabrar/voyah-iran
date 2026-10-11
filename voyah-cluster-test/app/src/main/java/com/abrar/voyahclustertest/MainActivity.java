package com.abrar.voyahclustertest;

import android.app.Activity;
import android.os.*;
import android.graphics.Color;
import android.text.InputType;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private EditText roadText; private Spinner textMode; private EditText number; private TextView status,logs; private Spinner directions,types;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private java.net.Socket process;private BufferedWriter input; private volatile boolean visible;
 private final Handler tuningHandler=new Handler();private final SeekBar[] sliders=new SeekBar[7];
 private final int[] tuning={0,0,100,50,85,871,660};private boolean updatingSliders;
 private final Runnable sendTuning=()->mapCommand("MAP TUNE "+tuningString());
 private String tuningString(){return new MapTuning(tuning[0],tuning[1],tuning[2],tuning[3],tuning[4],tuning[5],tuning[6]).toString();}
 private void tuningControls(LinearLayout box){
  String saved=getPreferences(0).getString("mapTuning","0 0 100 50 85");
  try{MapTuning t=MapTuning.parse(saved);int[] v={t.x,t.y,t.zoom,t.start,t.end,t.width,t.height};System.arraycopy(v,0,tuning,0,7);}catch(Exception ignored){}
  android.graphics.Point screen=new android.graphics.Point();
  android.view.Display central=getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
  if(central==null)central=getWindowManager().getDefaultDisplay();central.getRealSize(screen);
  tuning[5]=Math.max(Math.min(160,screen.x),Math.min(screen.x,tuning[5]));tuning[6]=Math.max(Math.min(160,screen.y),Math.min(screen.y,tuning[6]));
  label(box,"نشان زنده در مدیای کلاستر — حداکثر رندر "+screen.x+"×"+screen.y);
  label(box,"آفست درصد نیم‌عرض/نیم‌ارتفاع خروجی؛ زوم مقیاس تصویر است. فید از مرکز خروجی تا لبه‌ها اندازه‌گیری می‌شود.");
  String[] titles={"آفست افقی X","آفست عمودی Y","بزرگنمایی تصویر","شروع فید شعاعی","پایان فید شعاعی","عرض رندر","ارتفاع رندر"};
  int[] minimum={-100,-100,25,0,1,Math.min(160,screen.x),Math.min(160,screen.y)},maximum={100,100,300,99,100,screen.x,screen.y};
  TextView[] labels=new TextView[7];
  for(int i=0;i<7;i++){
   final int index=i;TextView label=new TextView(this);labels[i]=label;label.setTextColor(Color.WHITE);label.setTextSize(18);box.addView(label);
   SeekBar slider=new SeekBar(this);sliders[i]=slider;slider.setMax(maximum[i]-minimum[i]);slider.setProgress(tuning[i]-minimum[i]);box.addView(slider);
   label.setText(titles[i]+": "+tuning[i]+(i<5?"%":" px"));
   slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
    public void onStartTrackingTouch(SeekBar s){}
    public void onStopTrackingTouch(SeekBar s){tuningHandler.removeCallbacks(sendTuning);tuningHandler.post(sendTuning);}
    public void onProgressChanged(SeekBar s,int progress,boolean user){
     if(updatingSliders)return;tuning[index]=progress+minimum[index];
     if(tuning[3]>=tuning[4]){if(index==3)tuning[4]=tuning[3]+1;else tuning[3]=tuning[4]-1;}
     updatingSliders=true;for(int j=0;j<7;j++){sliders[j].setProgress(tuning[j]-minimum[j]);labels[j].setText(titles[j]+": "+tuning[j]+(j<5?"%":" px"));}updatingSliders=false;
     getPreferences(0).edit().putString("mapTuning",tuningString()).apply();
     tuningHandler.removeCallbacks(sendTuning);tuningHandler.postDelayed(sendTuning,index>=5?300:120);
    }
   });
  }
  LinearLayout row=new LinearLayout(this);box.addView(row);
  mapButton(row,"نمایش نشان در مدیا","MAP MEDIA");mapButton(row,"توقف تصویر","MAP STOP");
  label(box,"تست تصویر پس از ۶۰ ثانیه متوقف می‌شود؛ برای شروع دوباره نمایش نشان را بزنید. هنگام تست، لانچر اختصاصی و ارسال موسیقی به کلاستر را خاموش کنید تا مالکیت نقشه و مدیا تداخل نکند.");
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void label(LinearLayout box,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setTextColor(Color.WHITE);box.addView(t);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getFilesDir().mkdirs();
  ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(24),dp(16),dp(24),dp(16));box.setBackgroundColor(0xff172238);scroll.addView(box);setContentView(scroll);
  label(box,"تست نقشه، متن و فلش کلاستر — 0.4.0");
  tuningControls(box);
  label(box,"ابتدا نمایش ناوبری نشان در کلاستر را در وویا ایران خاموش کنید. تست را در حالت توقف خودرو انجام دهید.");
  label(box,"جهت نشان — همهٔ مقادیر enum");directions=new Spinner(this);directions.setBackgroundColor(Color.WHITE);
  ArrayAdapter<NeshanEnums.Direction> da=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,NeshanEnums.Direction.values());directions.setAdapter(da);box.addView(directions);
  label(box,"نوع مانور نشان — برای ثبت نتیجه؛ تصویر فقط با عدد زیر تعیین می‌شود");types=new Spinner(this);types.setBackgroundColor(Color.WHITE);types.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,NeshanEnums.Maneuver.values()));box.addView(types);
  label(box,"عدد خام کلاستر (۰ تا ۶۵۵۳۵) — مقدار اولیه صرفاً حدس است");number=new EditText(this);number.setInputType(InputType.TYPE_CLASS_NUMBER);number.setTextSize(24);number.setTextColor(Color.BLACK);number.setBackgroundColor(Color.WHITE);box.addView(number);
  directions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){number.setText(Integer.toString(NeshanEnums.guess(NeshanEnums.Direction.values()[pos])));}});
  directions.setSelection(4);number.setText("1");
  label(box,"متن آزمایشی — بدون حذف کلمات یا فینگلیش‌سازی");
  roadText=new EditText(this);roadText.setSingleLine(true);roadText.setTextSize(24);roadText.setTextColor(Color.BLACK);roadText.setBackgroundColor(Color.WHITE);roadText.setText("همت");box.addView(roadText);
  Spinner samples=new Spinner(this);samples.setBackgroundColor(Color.WHITE);
  String[] sampleText={"همت", "مدیریت", "پ چ ژ گ ک ی", "شارع الهمت", "العربية", "همت ۱۲۳", "Hemmat 123"};
  samples.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sampleText));box.addView(samples);
  samples.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){roadText.setText(sampleText[pos]);}});
  label(box,"روش ارسال متن (ابتدا گزینهٔ اول را تست کنید)");textMode=new Spinner(this);textMode.setBackgroundColor(Color.WHITE);
  textMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"۱. متن اصلی Unicode", "۲. حروف متصل آماده — ترتیب منطقی", "۳. حروف متصل آماده — ترتیب دیداری"}));box.addView(textMode);
  label(box,"در زبان فعلی و زبان عربی خودرو تست کنید. فاصله ۱۰۰ متر؛ ارسال هر ثانیه و حداکثر ۶۰ ثانیه.");
  LinearLayout buttons=new LinearLayout(this);box.addView(buttons);
  Button apply=new Button(this);apply.setText("اعمال");buttons.addView(apply);apply.setOnClickListener(v->apply());
  Button end=new Button(this);end.setText("توقف و پاک‌کردن");buttons.addView(end);end.setOnClickListener(v->worker.execute(()->stopProbe()));
  label(box,"آزمایش تصویر نقشه — مستقل از فلش و متن بالا");
  label(box,"کوچک و بزرگ تصویر آزمایشی هستند؛ ناحیه موسیقی نشان زنده را نمایش می‌دهد. هر حالت ۶۰ ثانیه فعال می‌ماند.");
  LinearLayout mapRow=new LinearLayout(this);box.addView(mapRow);
  mapButton(mapRow,"وضعیت و شنود","MAP INFO");mapButton(mapRow,"نقشه کوچک","MAP SMALL");mapButton(mapRow,"نقشه بزرگ","MAP FULL");
  LinearLayout mapRow2=new LinearLayout(this);box.addView(mapRow2);
  mapButton(mapRow2,"نقشه در ناحیه موسیقی","MAP MEDIA");mapButton(mapRow2,"توقف تصویر (حفظ فلش)","MAP STOP");
  LinearLayout mapRow3=new LinearLayout(this);box.addView(mapRow3);mapButton(mapRow3,"اعلام آمادگی تصویر — تست دستی","MAP READY");
  label(box,"دکمهٔ توقف و پاک‌کردن بالا هر دو تست را متوقف می‌کند. تغییر دائمی در تنظیمات خودرو انجام نمی‌شود. مسیر تصویر فعلاً برای سرویس qg.cluster.service است؛ نبود سرویس گزارش می‌شود.");
  status=new TextView(this);status.setTextColor(0xff78dcff);status.setTextSize(19);status.setText("آمادهٔ تست");box.addView(status);
  logs=new TextView(this);logs.setTextColor(Color.WHITE);logs.setTextSize(13);logs.setTextIsSelectable(true);box.addView(logs);
 }
 private void report(String text){Log.i("VoyahClusterTest",text);runOnUiThread(()->{status.setText(text);logs.append(text+"\n");if(logs.length()>20000)logs.setText(logs.getText().subSequence(logs.length()-15000,logs.length()));});}
 private void apply(){
  final int icon;try{icon=Integer.parseInt(number.getText().toString().trim());if(icon<0||icon>65535)throw new Exception();}catch(Exception e){number.setError("عدد بین ۰ و ۶۵۵۳۵ وارد کنید");return;}
  final String raw=roadText.getText().toString();
  final String road;try{road=TextProbe.prepare(raw,textMode.getSelectedItemPosition());}catch(Exception e){roadText.setError(e.getMessage());return;}
  final int mode=textMode.getSelectedItemPosition();
  final String selected=types.getSelectedItem()+" / "+directions.getSelectedItem();
  worker.execute(()->{try{
   if(!visible)return;
   connectProbe();
   input.write(TextProbe.encode(icon,road));input.newLine();input.flush();report("APPLY "+selected+" -> icon="+icon+" textMode="+mode+" input="+raw+" sent="+road+" codepoints="+TextProbe.codepoints(road)+" meters=100");
  }catch(Exception e){report("ERROR "+Log.getStackTraceString(e));stopProbe();}});
 }
 private void connectProbe() throws Exception {
   if(process==null||process.isClosed()){
    stopProbe();File config=new File(getFilesDir(),"probe.config");
    if(!config.isFile())throw new IOException("ابتدا Start-ClusterTest.ps1 را از لپ‌تاپ اجرا کنید.");
    String port,token;try(BufferedReader c=new BufferedReader(new FileReader(config))){port=c.readLine();token=c.readLine();}
    process=new java.net.Socket();process.connect(new java.net.InetSocketAddress("127.0.0.1",Integer.parseInt(port)),3000);
    input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8));
    input.write(token);input.newLine();input.flush();
    final java.net.Socket current=process;
    Thread reader=new Thread(()->{try(BufferedReader stream=new BufferedReader(new InputStreamReader(current.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=stream.readLine())!=null)report(line);}catch(Exception e){report("CONNECTION "+e);}finally{try{current.close();}catch(Exception ignored){}}},"ClusterLog");reader.setDaemon(true);reader.start();
   }
 }
 private void mapCommand(String command){final String values=tuningString();worker.execute(()->{try{if(!visible)return;connectProbe();if(command.equals("MAP MEDIA")){input.write("MAP TUNE "+values);input.newLine();}input.write(command);input.newLine();input.flush();report("SEND "+command);}catch(Exception e){report("ERROR "+Log.getStackTraceString(e));stopProbe();}});}
 private void mapButton(LinearLayout row,String title,String command){Button b=new Button(this);b.setText(title);row.addView(b,new LinearLayout.LayoutParams(0,dp(64),1));b.setOnClickListener(v->mapCommand(command));}
 private void stopProbe(){
  try{if(input!=null){input.write("STOP\n");input.flush();input.close();}}catch(Exception e){report("STOP_INPUT "+e);}
  input=null;
  if(process!=null){try{process.close();}catch(IOException e){report("CLOSE "+e);}process=null;}
 }
 @Override protected void onResume(){super.onResume();visible=true;}
 @Override protected void onPause(){super.onPause();}
 @Override protected void onStop(){visible=false;worker.execute(this::stopProbe);super.onStop();}
 @Override protected void onDestroy(){tuningHandler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
