import {microphone} from './microphone.js?v=persistent-ack-2';
import {describeParameters} from './parameters.js';
import {initializeEditor,showEditor,readParameters} from './feedback-editor.js';
const $=id=>document.getElementById(id);
const syncNote=document.createElement('p');syncNote.className='note';
document.querySelector('header').after(syncNote);
async function updateProjectSync(){
 try {
  const r=await fetch('/api/project-sync');if(!r.ok)throw Error();
  const s=await r.json();
  syncNote.textContent=({synced:'آموزش‌های ذخیره‌شده به پروژهٔ اصلی منتقل شدند. برای خودرو باید نسخهٔ جدید ساخته و نصب شود.',
   checking:'در حال بررسی انتقال آموزش‌ها…',pending:'آموزش جدید در انتظار انتقال به پروژه…',
   exporting:'در حال آماده‌سازی آموزش‌ها برای پروژهٔ اصلی…',verifying:'در حال بررسی تطابق مدل پیش از انتقال…',
   installing:'در حال به‌روزرسانی مدل پروژهٔ اصلی…',error:'آموزش ذخیره شد، ولی انتقال به پروژه ناموفق بود؛ دوباره تلاش می‌شود.',
   disabled:'انتقال خودکار آموزش‌ها غیرفعال است.'})[s.state]||'';
 }catch{syncNote.textContent='وضعیت انتقال آموزش‌ها فعلاً در دسترس نیست.';}
 setTimeout(updateProjectSync,5000);
}
updateProjectSync();
fetch('/api/status').then(r=>r.json()).then(s=>{
 if(s.precision?.startsWith('int8')){
  document.querySelector('header > .tag').textContent='نسخهٔ کم‌حجم · قابل آموزش';
  const note=document.createElement('p');note.className='note';
  note.textContent=`نسخهٔ کم‌حجم با ${s.feedback_count.toLocaleString('fa-IR')} بازخورد قبلی فعال است. اصلاح‌های جدید جداگانه ذخیره می‌شوند. آمار ارزیابی قبلی مربوط به نسخهٔ اصلی است، نه این نسخه.`;
  document.querySelector('header').after(note);
 }
}).catch(()=>{});
let last=null,records=[],labels={};
const guard=document.createElement('section');guard.className='panel';guard.style.marginTop='20px';
guard.innerHTML='<h2>۳. سازگاری برداشت با قواعد معنایی</h2><div id="guardResult" class="result">منتظر فرمان</div><p id="guardReason"></p><p id="guardSource"></p><div class="row"><button id="guardYes" disabled>تصمیم محافظ درست است</button><button id="guardNo" disabled>تصمیم محافظ اشتباه است</button></div><div id="guardEdit" hidden><label>نتیجهٔ درست برای همین جمله و همین برداشت<select id="guardExpected"><option value="false">ناسازگار — رد شود</option><option value="true">سازگار — از این محافظ عبور کند</option></select></label><textarea id="guardNote" maxlength="1000" placeholder="دلیل یا راهنمایی شما (اختیاری)"></textarea><button id="guardSave">ثبت اصلاح محافظ</button></div><small>این بخش برداشت مدل را آموزش نمی‌دهد. بازخورد برای همین جمله، فرمان و پارامترها ذخیره می‌شود؛ به جمله‌های دیگر تعمیم خودکار ندارد و سایر کنترل‌ها را دور نمی‌زند.</small><p id="finalDecision"></p>';
document.querySelector('.grid').after(guard);
$('correct').textContent='برداشت مدل درست است';$('wrong').textContent='اصلاح برداشت مدل';
function showGuard(r){const e=r.evidence;$('guardEdit').hidden=true;$('guardYes').disabled=$('guardNo').disabled=!e;
 if(!e){$('guardResult').textContent='محافظ برای این نتیجه در دسترس نیست';return;}
 $('guardResult').textContent=e.allowed?'سازگار — عبور از محافظ':'ناسازگار — رد توسط محافظ';$('guardResult').style.color=e.allowed?'#71dbc1':'#ff7785';$('guardReason').textContent=e.error||'ناسازگاری در قواعد تحت پوشش پیدا نشد؛ این تضمین صحت جمله نیست.';$('guardSource').textContent=e.source==='human_review'?'بر اساس بازخورد ذخیره‌شدهٔ شما؛ نتیجهٔ اولیهٔ قواعد: '+(e.rule_error||'ناسازگاری پیدا نشد'):'بر اساس قواعد هدف و عمل';$('finalDecision').textContent='نتیجهٔ نهایی آزمایش: '+(r.accepted?'پذیرفته شد — اجرا نمی‌شود':'رد شد — '+r.reason);
}
async function guardFeedback(allowed){if(!last)return;const snapshot=last;for(const id of ['guardYes','guardNo','guardSave'])$(id).disabled=true;try{const response=await fetch('/api/evidence-feedback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:snapshot.text,action:snapshot.prediction.label,parameters:snapshot.slots,allowed,note:$('guardNote').value})});const data=await response.json();if(!response.ok)throw Error(data.detail||'ثبت نشد');records.push({text:snapshot.text,feedback_type:'evidence',action:snapshot.prediction.label,parameters:snapshot.slots,allowed,note:$('guardNote').value});if(last===snapshot){const modelReason=snapshot.evidence.model_reason;snapshot.evidence={...data.evidence,model_reason:modelReason,model_accepted:snapshot.evidence.model_accepted};snapshot.reason=data.evidence.error||modelReason;snapshot.accepted=!snapshot.reason;display(snapshot);}message('بازخورد محافظ ذخیره شد؛ مدل فرمان و پروژهٔ اصلی تغییر نکردند.');}catch(e){message(e.message);}finally{for(const id of ['guardYes','guardNo','guardSave'])$(id).disabled=false;}}
$('guardYes').onclick=()=>{$('guardNote').value='';guardFeedback(last.evidence.allowed);};$('guardNo').onclick=()=>{$('guardEdit').hidden=false;$('guardExpected').value=String(!last.evidence.allowed);$('guardNote').value='';};$('guardSave').onclick=()=>guardFeedback($('guardExpected').value==='true');
function message(t){$('status').textContent=t;}
function display(r){last=r;showGuard(r);const accepted=r.evidence?r.evidence.model_accepted:r.accepted;$('result').textContent=r.prediction.title;$('result').className='result '+(accepted?'good':'bad');$('reason').textContent=(r.evidence?r.evidence.model_reason:r.reason)||'برداشت مدل؛ تأیید این بخش فقط فرمان و پارامترهای بالا را آموزش می‌دهد.';$('timing').textContent=`زمان فهم متن: ${r.latency_ms.toLocaleString('fa-IR')} میلی‌ثانیه · امتیاز مدل: ${Math.round(r.prediction.score*100).toLocaleString('fa-IR')} از ۱۰۰ (نه دقت واقعی)`;$('slots').style.direction='rtl';$('slots').style.textAlign='right';$('slots').textContent=describeParameters(r.slots);$('raw').textContent=JSON.stringify(r.candidates,null,2);$('correct').disabled=false;$('wrong').disabled=false;$('correction').hidden=true;}
async function parse(){try{$('parse').disabled=true;message('در حال بررسی متن…');const res=await fetch('/api/intent',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:$('text').value})});if(!res.ok)throw Error('پاسخ نامعتبر از مدل');display(await res.json());message('آمادهٔ فرمان بعدی');}catch(e){message(e.message);}finally{$('parse').disabled=false;}}
async function feedback(correct){
 if(!last)return;
 const record={time:new Date().toISOString(),...last,feedback:correct?'correct':'incorrect',expected:correct?last.prediction.label:$('expected').value};
 let parameters;
 try{parameters=correct?last.slots:readParameters();if(correct&&last.prediction.label==='CALL_CONTACT'&&!parameters.contact_query){showEditor(last.prediction.label,parameters);message('نام مخاطب را وارد کن و اصلاح را ثبت کن.');return;}}catch(e){message(e.message);return;}
 record.expected_parameters=parameters;
 for(const id of ['correct','wrong','save','parse','mic'])$(id).disabled=true;
 message('در حال آموزش فرمان و پارامترها و ذخیره روی لپ‌تاپ… بار اول کمی بیشتر طول می‌کشد.');
 try{
  const response=await fetch('/api/feedback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:record.text,action:record.expected,parameters})});
  const data=await response.json();if(!response.ok)throw Error(typeof data.detail==='string'?data.detail:'آموزش انجام نشد؛ دوباره امتحان کن');
  records.push(record);display(data.result);
  const div=document.createElement('div');div.className='entry';div.textContent=`${record.text} ← ${labels[record.expected]} · ${data.learned?'آموزش داده شد':'ذخیره شد؛ مدل هنوز این مثال را درست تشخیص نمی‌دهد'}`;$('entries').prepend(div);
  message(data.learned?(data.scope==='contact_pattern'?'الگوی نام مخاطب و فرمان ذخیره شد؛ همان ساختار را با اسم دیگری هم امتحان کن.':'فرمان و پارامترها آموزش داده و ذخیره شدند؛ دوباره امتحان کن.'):'بازخورد ذخیره و آموزش اجرا شد، ولی نتیجه هنوز کاملاً درست نیست.');
 }catch(e){message(e.message);}
 finally{for(const id of ['correct','wrong','save','parse','mic'])$(id).disabled=false;}
}
$('mic').onclick=microphone({message,display});$('parse').onclick=parse;$('correct').onclick=()=>feedback(true);$('wrong').onclick=()=>showEditor(last.prediction.label,last.slots);$('save').onclick=()=>feedback(false);$('export').onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify({model:$('model').textContent,records},null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='raha-parsbert-feedback.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
await initializeEditor();
for(const text of ['گرمکن صندلی رو خاموش کن','شیشه رو تا نصفه بکش پایین','ماشین رو بذار تو هیبرید']){const b=document.createElement('button');b.className='example';b.textContent=text;b.onclick=()=>{$('text').value=text;};$('examples').append(b);}
try{const r=await fetch('/api/status');if(!r.ok)throw Error('مدل هنوز آماده نیست');const s=await r.json();labels=s.labels;for(const [value,title] of Object.entries(labels)){const o=document.createElement('option');o.value=value;o.textContent=title;$('expected').append(o);}$('scope').textContent=`${s.report.action_count??27} نوع فرمان · ${s.report.train_count.toLocaleString('fa-IR')} نمونهٔ آموزشی. روی نمونهٔ هر فرمان بزن تا در کادر تست قرار بگیرد.`;$('model').textContent=JSON.stringify({model:s.model,version:s.version,asr:s.asr,training_examples:s.report.train_count,action_accuracy:s.report.action_accuracy??s.report.accuracy,exact_with_parameters:s.report.exact_accuracy,note:s.report.evaluation},null,2);const catalog=await (await fetch('/api/catalog')).json();const render=()=>{$('catalog').replaceChildren();for(const item of catalog.filter(x=>(x.title+' '+x.example).includes($('search').value))){const row=document.createElement('div');row.className='entry';const title=document.createElement('div');title.textContent=item.title;const b=document.createElement('button');b.className='example';b.textContent=item.example;b.onclick=()=>{$('text').value=item.example;$('text').focus();};row.append(title,b);$('catalog').append(row);}};$('search').oninput=render;render();$('mic').disabled=false;$('parse').disabled=false;message('مدل و شنود آفلاین آماده‌اند');}catch(e){message(e.message+'؛ سرور محلی را بررسی کن.');}
