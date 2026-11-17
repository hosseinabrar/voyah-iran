import {setText,setVisible,change} from './motion.js';
const $=id=>document.getElementById(id);
const n=(v,d=0)=>typeof v==='number'&&Number.isFinite(v)?v.toFixed(d):'—';
export function batteryPolicy(snapshot,now=Date.now(),busy=false){
 const v=snapshot?.values||{}, times=snapshot?.fieldTimes||{};
 const fresh=keys=>keys.every(k=>times[k]>0&&now-times[k]<=30000&&times[k]<=now+5000&&v[k]!==undefined);
 const common=busy?'در انتظار پاسخ خودرو…':!fresh(['charging','chargeConnected'])?'برای دریافت وضعیت تازه، تازه‌سازی کنید.':'';
 const limit=common||(v.charging?'هنگام شارژ امکان تغییر حداکثر شارژ نیست.':v.chargeComplete===true?'پس از تکمیل شارژ امکان تغییر حداکثر شارژ نیست.':!fresh(['chargeComplete'])?'وضعیت تکمیل شارژ هنوز دریافت نشده.':!fresh(['chargeLimit'])?'حداکثر شارژ خودرو هنوز دریافت نشده.':(!fresh(['chargeLimitControl'])||v.chargeLimitControl!==true)?'تنظیم از راه دور برای این خودرو در دسترس نیست.':'');
 const schedule=common||(v.chargeConnected?'برای تغییر زمان‌بندی، ابتدا گان شارژ را جدا کنید.':!fresh(['chargeScheduled'])?'وضعیت زمان‌بندی هنوز دریافت نشده.':(!fresh(['chargeScheduleControl'])||v.chargeScheduleControl!==true)?'تنظیم از راه دور برای این خودرو در دسترس نیست.':'');
 return {limit,schedule};
}
export function batteryArtworkState(percent,charging){
 const known=typeof percent==='number'&&Number.isFinite(percent)&&percent>=0&&percent<=100;
 const cells=known?Math.ceil(percent/25):0;
 return {cells,color:charging===true?'green':cells<=1&&known?'red':'blue',known};
}
export function chargingPresentation(v,now=Date.now()){
 const disconnected=v.chargeConnected===false;
 const charging=!disconnected&&v.charging===true;
 const complete=!disconnected&&!charging&&v.chargeComplete===true;
 const waiting=v.chargeConnected===true&&!charging&&!complete&&(v.chargeWaiting===true||v.chargeScheduled===true);
 const duration=(minutes,suffix)=>{const m=Math.max(0,Math.ceil(minutes));return `${Math.floor(m/60)} ساعت و ${m%60} دقیقه ${suffix}`};
 let eta='';
 if(charging&&Number.isFinite(v.chargeRemainingMinutes))eta=duration(v.chargeRemainingMinutes,'تا تکمیل');
 if(waiting){
  if(Number.isFinite(v.chargeNextStartAt)&&v.chargeNextStartAt>now)eta=duration((v.chargeNextStartAt-now)/60000,'تا شروع شارژ');
  else if(validTime(v.chargeStart))eta=`شروع شارژ ساعت ${v.chargeStart}`;
 }
 return {charging,complete,waiting,color:charging||complete?'green':waiting?'yellow':'',
 label:disconnected?'شارژر وصل نیست':charging?'در حال شارژ':complete?'شارژ کامل شد':waiting?'در انتظار شروع شارژ':v.chargeConnected===true?'شارژر متصل است':'وضعیت شارژ نامشخص',eta};
}
export function setupBattery({getSnapshot,send,refresh,onExpired}){
 let pending=false,dirty=false;
 const render=()=>{
  const snapshot=getSnapshot(),v=snapshot?.values||{};
  const val=k=>v[k];
  const text=(id,x)=>setText($(id),x);
  $('batteryPanel').classList.toggle('stale',!snapshot||Date.now()-snapshot.observedAt>60000||Date.now()-(snapshot.fieldTimes?.battery||0)>60000);
  text('batteryPercent',n(val('battery'))+'%');
  const state=chargingPresentation(Object.fromEntries(['charging','chargeComplete','chargeWaiting','chargeConnected','chargeScheduled','chargeStart','chargeRemainingMinutes','chargeNextStartAt'].map(k=>[k,val(k)])));
  const {charging,complete,waiting}=state;
  $('batteryPanel').classList.toggle('is-charging',charging);
  $('batteryPanel').classList.toggle('is-waiting',waiting);
  $('batteryPanel').classList.toggle('is-charge-idle',val('chargeConnected')===false||complete);
  text('batteryChargeState',state.label);
  const art=batteryArtworkState(val('battery'),charging||complete);
  if($('batteryArtwork').dataset.color!==art.color)change($('batteryArtwork'),()=>$('batteryArtwork').dataset.color=art.color);
  $('batteryArtwork').setAttribute('aria-label',art.known?`${n(val('battery'))}%`:'وضعیت باتری نامشخص');
  for(const [i,cell] of [...$('batteryArtwork').querySelectorAll('.battery-cells img')].entries()){if(cell.classList.contains('filled')!==(i<art.cells))change(cell,()=>cell.classList.toggle('filled',i<art.cells));}
  setVisible($('batteryArtwork').querySelector('.battery-art-bolt'),charging||complete);
  setVisible($('chargingBolt'),charging||complete);
  $('batteryChargeState').className=state.color;
  setVisible($('batteryPower'),charging||waiting);
  text('batteryPower',charging?n(val('chargePowerKW'),1)+' kW':waiting?'– kW':'');
  $('batteryEta').className=waiting?'yellow':'';
  text('batteryEta',state.eta);
  for(const [id,key,unit,d] of [['packVoltage','batteryVoltage',' V',1],['packCurrent','batteryCurrent',' A',1],['packTemp','batteryTemperature','°C',0],['batteryHealth','batteryHealth','%',0],['auxVoltage','auxVoltage',' V',1],['auxSOC','auxSOC','%',0]])text(id,n(val(key),d)+unit);
  if(!dirty&&!pending){const next=String(Number.isFinite(val('chargeLimit'))?val('chargeLimit'):80);if($('chargeLimitInput').value!==next)change($('chargeLimitInput'),()=>$('chargeLimitInput').value=next);text('chargeLimitValue',n(val('chargeLimit'))+'%')}
  $('chargeLimitInput').style.setProperty('--fill',`${Math.max(0,Math.min(100,(Number($('chargeLimitInput').value)-60)/40*100))}%`);
  const policy=batteryPolicy(snapshot,Date.now(),pending);
  $('chargeLimitInput').disabled=!!policy.limit;$('applyChargeLimit').disabled=!!policy.limit||!dirty;
  $('scheduleToggle').disabled=!!policy.schedule;$('editSchedule').disabled=!!policy.schedule;
  $('scheduleToggle').checked=val('chargeScheduled')===true;
  text('limitReason',policy.limit);text('scheduleReason',policy.schedule);
  text('scheduleTimes',validTime(val('chargeStart'))&&validTime(val('chargeEnd'))?`${val('chargeStart')} — ${val('chargeEnd')}`:'— : —');
  if($('scheduleDialog').open){$('scheduleSave').disabled=!!policy.schedule;$('scheduleStart').disabled=!!policy.schedule;$('scheduleEnd').disabled=!!policy.schedule;}
 };
 async function command(action,parameters){
  const policy=batteryPolicy(getSnapshot(),Date.now(),pending);
  if(action==='limit'?policy.limit:policy.schedule)return;
  pending=true;render();$('batteryNotice').textContent='در حال ارسال…';
  try{
   const r=await send({kind:'battery-command',action,...parameters});
   if(r.status==='subscription_expired'){onExpired();return}
   $('batteryNotice').textContent=({accepted:'درخواست به خودرو ارسال شد.',failed:'ارسال درخواست انجام نشد؛ دوباره تلاش کنید.',confirmed:'تنظیم با پاسخ خودرو تأیید شد.',state_blocked:'وضعیت خودرو تغییر کرده؛ دوباره تازه‌سازی کنید.',data_unavailable:'اطلاعات تازهٔ خودرو در دسترس نیست.',unsupported:'تنظیم از راه دور برای این خودرو در دسترس نیست.',invalid:'مقدار واردشده معتبر نیست.'})[r.status]||'نتیجه تأیید نشد؛ وضعیت خودرو را تازه‌سازی کنید.';
   if(r.status==='confirmed'||r.status==='accepted'){dirty=false;$('scheduleDialog').close()}
  }catch{$('batteryNotice').textContent='پاسخ خودرو نرسید؛ پیش از ارسال دوباره، وضعیت را تازه‌سازی کنید.'}
  finally{pending=false;await refresh();render()}
 }
 $('chargeLimitInput').oninput=()=>{dirty=true;$('chargeLimitInput').style.setProperty('--fill',`${(Number($('chargeLimitInput').value)-60)/40*100}%`);$('chargeLimitValue').textContent=$('chargeLimitInput').value+'%';$('applyChargeLimit').disabled=!!batteryPolicy(getSnapshot(),Date.now(),pending).limit};
 $('applyChargeLimit').onclick=()=>command('limit',{percent:Number($('chargeLimitInput').value)});
 $('scheduleToggle').onchange=()=>{
  const v=getSnapshot()?.values||{};
  if($('scheduleToggle').checked&&(!validTime(v.chargeStart)||!validTime(v.chargeEnd))){openSchedule();render();return}
  command('schedule',{enabled:$('scheduleToggle').checked,start:v.chargeStart,end:v.chargeEnd});
 };
 function openSchedule(){const v=getSnapshot()?.values||{};$('scheduleStart').value=validTime(v.chargeStart)?v.chargeStart:'';$('scheduleEnd').value=validTime(v.chargeEnd)?v.chargeEnd:'';$('scheduleError').textContent='';$('scheduleDialog').showModal()}
 $('editSchedule').onclick=openSchedule;$('scheduleCancel').onclick=()=>$('scheduleDialog').close();
 $('scheduleSave').onclick=()=>{const start=$('scheduleStart').value,end=$('scheduleEnd').value;if(!validTime(start)||!validTime(end)||start===end){$('scheduleError').textContent='ساعت شروع و پایان متفاوت و معتبر وارد کنید.';return}command('schedule',{enabled:true,start,end})};
 return {render};
}
function validTime(v){return typeof v==='string'&&/^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(v)}
