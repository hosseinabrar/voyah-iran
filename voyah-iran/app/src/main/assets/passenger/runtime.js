'use strict';
(() => {
const $=s=>document.querySelector(s),all=s=>Array.from(document.querySelectorAll(s));
const light=location.pathname.includes('light');document.body.classList.toggle('light',light);
const screen=$('.screen');
let topInset=0;
function resize(){const sy=innerHeight/(720-topInset);screen.style.top=(-topInset*sy)+'px';screen.style.transform=`scale(${innerWidth/1920},${sy})`;}
addEventListener('resize',resize);resize();
function send(kind,value=''){if(window.Passenger)Passenger.send(kind,typeof value==='string'?value:JSON.stringify(value));}
let vehicle={},catalog=[],favorites=['','','','',''],editing=false,picker=null,drag=null,media={},mediaAt=0,animationTimer=null,visible=true,progressTimer=null;
const val=k=>typeof vehicle[k]==='number'?vehicle[k]:null;
const valid=(k,a,b)=>val(k)!==null&&val(k)>=a&&val(k)<=b;
function comfort(op,value){if(vehicle.fresh)send('comfort',{op,value:String(value)});}
function image(el,url){if(!el)return;if(el.dataset.next===url)return;el.dataset.next=url;el.style.opacity='0';setTimeout(()=>{if(el.dataset.next!==url)return;if(url){el.src=url;el.hidden=false;el.onload=()=>el.style.opacity='1';if(el.complete)el.style.opacity='1';}else{el.removeAttribute('src');el.hidden=true;}},140);}
function pressed(el,on){el.setAttribute('aria-pressed',String(on));}
function setButton(el,known,on){el.disabled=!vehicle.fresh||!known;pressed(el,on);}
function seatState(owner){const suffix=owner==='driver'?'LEFT':'RIGHT';return {
vent:val(`FRONT_SEAT_VENTILATION_COMMAND_${suffix}`),heat:val(`FRONT_SEAT_HEATING_COMMAND_${suffix}`),massage:val(`FRONT_SEAT_MASS_INTEN_${suffix}`),mode:val(`FRONT_SEAT_MASS_COMMAND_${suffix}`)};}
function paintVehicle(s){vehicle=s;screen.dataset.stale=String(!s.fresh);
all('[data-owner]').forEach(group=>{const owner=group.dataset.owner,v=seatState(owner),side=owner==='driver'?'left':'right';
group.querySelectorAll('[data-feature]').forEach(b=>{const n=v[b.dataset.feature],known=n!==null&&n>=0&&n<=3;b.disabled=!s.fresh||!known;b.querySelectorAll('img').forEach(im=>im.src=`oem-x/seat_${side}_${b.dataset.feature}_${known?n:0}_normal.png`);});
const active=s.fresh&&(v.vent>0||v.heat>0);group.querySelector('.tint').hidden=!active;group.querySelector('.flow').hidden=!active;
if(active)group.querySelector('.tint').src=`oem-x/seatfront_${v.vent>0?'vent':'hot'}${side}_bg_97c.png`;
group.querySelector('.massage').hidden=!(s.fresh&&v.massage>0);const modes=group.querySelector('.massage-modes');modes.hidden=!(s.fresh&&v.massage>0);
modes.querySelectorAll('button').forEach((b,i)=>setButton(b,v.mode===1||v.mode===2,v.mode===i+1));
});
const scent=val('IVI_FRAG_TASTE_FB'),on=val('FCM_SW_REQ_FB')===2,known=valid('IVI_FRAG_TASTE_FB',1,3)&&valid('FCM_SW_REQ_FB',1,2);
const panel=$('.fragrance'),old=panel.dataset.tone;panel.dataset.off=String(!on||!s.fresh);
if(known&&old!==String(scent)){panel.dataset.tone=String(scent);$('.fragrance-title').textContent=['','Watery mist','Fresh bamboo','Peony scarlet'][scent];image($('.fragrance-object'),`fragrance-wide/object-${scent}${light?(scent===2?'-day-v2':'-day-v1'):''}.png`);}
all('[data-scent]').forEach(b=>{const n=Number(b.dataset.scent);setButton(b,known,on&&scent===n);b.querySelector('img').src=`fragrance-wide/fragrance_icon_${{1:'shuigan',2:'zhuzi',3:'mudan'}[n]}_${on&&scent===n?'on':'off'}.png`;});
all('[data-setting]').forEach(g=>g.querySelectorAll('button').forEach((b,i)=>{const k=g.dataset.setting==='intensity'?'IVI_FRAG_CONCERNTION_FB':'FCM_DURATION_CONTROL',v=g.dataset.setting==='intensity'?i+1:[1,2,0][i];setButton(b,on&&valid(k,g.dataset.setting==='intensity'?1:0,g.dataset.setting==='intensity'?3:2),val(k)===v);}));
for(const [zone,k] of [['driver','getAirLeftTemperature'],['passenger','getAirRightTemperature']]){const w=$(`[data-zone="${zone}"] .temp-wheel`);const known=valid(k,155,305);w.style.visibility=known?'visible':'hidden';if(w.pending===val(k))w.pending=null;if(known&&!w.dragging&&!w.scrolling&&w.pending==null){w.program=true;w.scrollTop=(305-val(k))*12;setTimeout(()=>w.program=false,180);}scaleWheel(w);}
const fan=val('getAirWindSpeed');$('.fan-track>img').src=`oem-ac/air_wind_level_${valid('getAirWindSpeed',0,7)?fan:0}.png`;
all('.light-fan-bars i').forEach((e,i)=>e.classList.toggle('active',i<fan));$('.fan-track input').value=fan||0;$('.fan-track input').disabled=!s.fresh||!valid('getAirWindSpeed',0,7);all('[data-fan-step]').forEach(b=>b.disabled=$('.fan-track input').disabled);
setButton($('.ac-sync'),valid('getAirDUALStatus',0,1),val('getAirDUALStatus')===0);
all('[data-ac]').forEach(b=>{const k={ac:'getAirACStatus',switch:'getAirSWStatus',auto:'getAirMode',front_defrost:'getAirFrontWindowDefogger',rear_defrost:'getAirRearWindowDefogger'}[b.dataset.ac];let active=false,known=false;
if(k){known=valid(k,0,1);active=val(k)===1;}else{known=valid('getAirSupplyStatus',0,6);const n=val('getAirSupplyStatus');active=({blow_face:[0,1,6],blow_foot:[1,2,3,6],blow_window:[3,4,6]}[b.dataset.ac]||[]).includes(n);}
setButton(b,known,active);b.querySelector('img').src=`oem-ac/air_ic_${b.dataset.ac}_${active?'on':'off'}_normal.png`;});
setButton($('.ac-cycle'),valid('getAirCirculationMode',0,2),val('getAirCirculationMode')===0||val('getAirCirculationMode')===2);$('.ac-cycle img').src=`oem-ac/air_cycle_${val('getAirCirculationMode')===1?'outer':'inner'}_normal.png`;
animation();}
all('[data-feature]').forEach(b=>b.onclick=()=>{const owner=b.closest('[data-owner]').dataset.owner;comfort(`${owner}-${b.dataset.feature}`,(seatState(owner)[b.dataset.feature]+1)%4);});
all('.massage-modes button').forEach(b=>b.onclick=()=>comfort(`${b.closest('[data-owner]').dataset.owner}-mode`,b.dataset.mode==='rolling'?2:1));
all('[data-scent]').forEach(b=>b.onclick=()=>comfort('scent',b.getAttribute('aria-pressed')==='true'?0:b.dataset.scent));
all('[data-setting]').forEach(g=>g.querySelectorAll('button').forEach((b,i)=>b.onclick=()=>comfort(g.dataset.setting,g.dataset.setting==='intensity'?i+1:[1,2,0][i])));
function scaleWheel(w){w.querySelectorAll('.temp-row span').forEach((e,i)=>e.style.transform=`scale(${Math.max(0,1-Math.abs(i*60-w.scrollTop)/180)})`);}
all('.temp-wheel').forEach(w=>{let timer,rollback;w.addEventListener('scroll',()=>{scaleWheel(w);if(w.program)return;w.scrolling=true;clearTimeout(timer);timer=setTimeout(()=>{w.scrolling=false;if(vehicle.fresh){w.pending=305-Math.round(w.scrollTop/60)*5;comfort(w.closest('[data-zone]').dataset.zone+'-temp',w.pending);clearTimeout(rollback);rollback=setTimeout(()=>{w.pending=null;paintVehicle(vehicle);},2500);}},220);});w.onpointerdown=()=>w.dragging=true;w.onpointerup=w.onpointercancel=()=>w.dragging=false;});
$('.ac-sync').onclick=()=>comfort('sync',val('getAirDUALStatus')===0?0:1);
$('.fan-track input').onchange=e=>comfort('fan',e.target.value);
all('[data-fan-step]').forEach(b=>b.onclick=()=>comfort('fan',Math.max(0,Math.min(7,val('getAirWindSpeed')+Number(b.dataset.fanStep)))));
all('[data-ac]').forEach(b=>b.onclick=()=>{const k=b.dataset.ac;comfort({switch:'power',blow_face:'airflow',blow_foot:'airflow',blow_window:'airflow'}[k]||k,{blow_face:'face',blow_foot:'feet',blow_window:'windshield'}[k]||(b.getAttribute('aria-pressed')==='true'?0:1));});
$('.ac-cycle').onclick=()=>comfort('recirculation',(val('getAirCirculationMode')+1)%3);
function animation(){if(window.passengerNativeSeats||animationTimer!==null)return;function frame(){animationTimer=null;if(window.passengerNativeSeats||document.hidden||!visible)return;let moving=false;const t=performance.now();all('[data-owner]').forEach(g=>{const v=seatState(g.dataset.owner);if(!vehicle.fresh)return;const side=g.dataset.owner==='driver'?'left':'right';if(v.vent>0||v.heat>0){moving=true;g.querySelector('.flow').src=`oem-x/seat_${v.vent>0?'vent':'hot'}_${side}_${Math.floor(t/50)%89}.png`;}if(v.massage>0){moving=true;g.querySelector('.massage').src=`oem-x/seat_${side==='left'?'main':'co'}_massage_${v.mode===2?'rolling':'waving'}_${Math.floor(t/50)%91}.png`;}});if(moving)animationTimer=setTimeout(frame,50);}frame();}
document.addEventListener('visibilitychange',()=>{if(document.hidden)dismiss();else animation();});
function save(){send('favorites',favorites);renderApps();}
function dismiss(){editing=false;drag=null;if(picker)picker.remove();picker=null;renderApps();}
function pick(index){if(picker)picker.remove();picker=document.createElement('div');picker.className='picker-scrim';const box=document.createElement('div');box.className='picker-box';const h=document.createElement('h2');h.textContent='Choose an app';const list=document.createElement('div');list.className='picker-list';catalog.filter(e=>!favorites.includes(e.id)).forEach(e=>{const b=document.createElement('button'),im=new Image(),label=document.createElement('span');im.src=e.icon;label.textContent=e.label;b.append(im,label);b.onclick=()=>{favorites[index]=e.id;picker.remove();picker=null;save();};list.append(b);});box.append(h,list);picker.append(box);picker.onclick=e=>{if(e.target===picker){picker.remove();picker=null;}};screen.append(picker);}
function renderApps(){const grid=$('.app-grid');grid.innerHTML='';grid.classList.toggle('editing',editing);favorites.forEach((id,i)=>{const e=catalog.find(e=>e.id===id),slot=document.createElement('div'),b=document.createElement('button');slot.className='favorite-slot';slot.dataset.index=i;b.className='app-tile';if(e){const im=new Image();im.src=e.icon;im.draggable=false;b.append(im);}else{const plus=document.createElement('div');plus.className='add-symbol';plus.textContent='+';b.append(plus);}const label=document.createElement('span');label.textContent=e?e.label:'Add';b.append(label);slot.append(b);let hold,down;
b.onpointerdown=ev=>{down={x:ev.clientX,y:ev.clientY};hold=setTimeout(()=>{editing=true;renderApps();},550);if(editing){drag=i;b.setPointerCapture(ev.pointerId);}};
b.onpointermove=ev=>{if(down&&Math.hypot(ev.clientX-down.x,ev.clientY-down.y)>12)clearTimeout(hold);};
b.onpointerup=ev=>{clearTimeout(hold);if(drag!==null&&down&&Math.hypot(ev.clientX-down.x,ev.clientY-down.y)>12){const hit=document.elementFromPoint(ev.clientX,ev.clientY),target=hit&&hit.closest('[data-index]');if(target){const n=Number(target.dataset.index);[favorites[i],favorites[n]]=[favorites[n],favorites[i]];save();}drag=null;down=null;ev.preventDefault();return;}drag=null;if(!editing){if(e)send('open',e.id);else pick(i);}else if(!e)pick(i);};b.onpointercancel=()=>{clearTimeout(hold);drag=null;};b.oncontextmenu=e=>e.preventDefault();
if(editing&&e){const x=document.createElement('button');x.className='remove-favorite';x.textContent='×';x.setAttribute('aria-label','Remove '+e.label);x.onclick=()=>{favorites[i]='';save();};slot.append(x);}grid.append(slot);});
const all=document.createElement('button');all.className='app-tile all-slot';all.innerHTML='<div class="all-apps-icon"><i></i><i></i><i></i><i></i></div><span>All apps</span>';all.onclick=()=>{dismiss();send('allApps');};grid.append(all);}
screen.addEventListener('pointerdown',e=>{if(editing&&!e.target.closest('.apps')&&!e.target.closest('.picker-scrim'))dismiss();});
// Native cards must not cover the application's modal picker or capture its outside taps.
new MutationObserver(()=>send('nativeOverlay',String(!!document.querySelector('.picker-scrim')))).observe(screen,{childList:true});
function mediaPaint(s){media=s;mediaAt=performance.now();$('.track-title').textContent=s.title||'Music';$('.track-artist').textContent=s.artist||'';image($('.album-cover'),s.cover||'');image($('.music-source'),s.source||'');pressed($('.play-pause'),s.playing);$('.play-pause i').className='transport '+(s.playing?'pause':'play');$('.song-progress input').disabled=!s.seek;progress();}
function clock(ms){const n=Math.floor((ms||0)/1000);return Math.floor(n/60)+':'+String(n%60).padStart(2,'0');}
function progress(){if(document.hidden)return;const n=Math.min(media.duration||0,(media.position||0)+(media.playing?performance.now()-mediaAt:0)),input=$('.song-progress input');input.max=media.duration||1;input.value=n;input.style.setProperty('--played',(100*n/(media.duration||1))+'%');$('.elapsed').textContent=clock(n);$('.song-times span:last-child').textContent=clock(media.duration);}
function visibility(on){visible=on;clearInterval(progressTimer);progressTimer=null;clearTimeout(animationTimer);animationTimer=null;if(on){if(!window.passengerNativeMusic)progressTimer=setInterval(progress,1000);animation();}else dismiss();}
visibility(true);$('.song-progress input').onchange=e=>send('seek',e.target.value);
all('.music-controls button').forEach((b,i)=>b.onclick=()=>send('media',['previous','play','next'][i]));$('.album-wrap').onclick=()=>send('source');$('.volume-mute').onclick=()=>send('mute');
let volumeTimer=null,volumeDragging=false,requestedVolume=0;
$('.oem-volume input').oninput=e=>{volumeDragging=true;requestedVolume=Number(e.target.value);volumePaint(requestedVolume);if(volumeTimer===null)volumeTimer=setTimeout(()=>{volumeTimer=null;send('volume',String(requestedVolume));},80);};
$('.oem-volume input').onchange=e=>{clearTimeout(volumeTimer);volumeTimer=null;volumeDragging=false;send('volume',e.target.value);};
$('.oem-volume input').onpointercancel=()=>{clearTimeout(volumeTimer);volumeTimer=null;volumeDragging=false;};
function volumePaint(n){$('.oem-volume input').value=n;$('.volume-number output').textContent=n;$('.volume-fill').style.clipPath=`inset(0 ${100-n/30*100}% 0 0)`;pressed($('.volume-mute'),n===0);}
function weatherPaint(s){$('.weather-city').textContent=s.city+(s.temperature==null?'':` · ${Math.round(s.temperature)}°`);$('.weather-city').title=s.city;image($('.weather-current-icon'),s.icon||'');all('.forecast-hour').forEach((e,i)=>{const h=(s.hours||[])[i];e.querySelector('span').textContent=h?new Date(h.time).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'}):'—';e.querySelector('strong').textContent=h?Math.round(h.temperature)+'°':'—';image(e.querySelector('img'),h?h.icon:'');});}
window.passenger={dismiss,visibility,update(kind,s){if(kind==='frame'){topInset=Math.max(0,Math.min(50,s.top||0));resize();}if(kind==='vehicle')paintVehicle(s);if(kind==='apps'){catalog=s.items||[];favorites=s.favorites||favorites;if(picker){picker.remove();picker=null;}renderApps();}if(kind==='media')mediaPaint(s);if(kind==='volume'&&!volumeDragging)volumePaint(s.value);if(kind==='weather')weatherPaint(s);}};
all('.fragrance-particles').forEach(e=>e.hidden=true);paintVehicle({fresh:false});mediaPaint({});weatherPaint({city:'Weather'});renderApps();
})();
