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
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void label(LinearLayout box,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setTextColor(Color.WHITE);box.addView(t);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getFilesDir().mkdirs();
  ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(24),dp(16),dp(24),dp(16));box.setBackgroundColor(0xff172238);scroll.addView(box);setContentView(scroll);
  label(box,"تست متن و فلش کلاستر — 0.2.0");
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
   input.write(TextProbe.encode(icon,road));input.newLine();input.flush();report("APPLY "+selected+" -> icon="+icon+" textMode="+mode+" input="+raw+" sent="+road+" codepoints="+TextProbe.codepoints(road)+" meters=100");
  }catch(Exception e){report("ERROR "+Log.getStackTraceString(e));stopProbe();}});
 }
 private void stopProbe(){
  try{if(input!=null){input.write("STOP\n");input.flush();input.close();}}catch(Exception e){report("STOP_INPUT "+e);}
  input=null;
  if(process!=null){try{process.close();}catch(IOException e){report("CLOSE "+e);}process=null;}
 }
 @Override protected void onResume(){super.onResume();visible=true;}
 @Override protected void onPause(){visible=false;worker.execute(this::stopProbe);super.onPause();}
 @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
