import {microphone} from './microphone.js';
import {describeParameters} from './parameters.js';
import {showEditor,readParameters,setupEditor} from './feedback-editor.js';
const $=id=>document.getElementById(id);let current=null;
const message=text=>{$('message').textContent=text;};
async function api(path,body){const response=await fetch('./api/'+path,{method:body?'POST':'GET',headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined});if(!response.ok){const e=await response.json();throw Error(typeof e.detail==='string'?e.detail:'درخواست انجام نشد');}return response.json();}
function display(result){current=result;$('text').value=result.text;const score=Math.floor(result.prediction.score*100+.5);const unknown=result.prediction.label.toUpperCase()==='UNKNOWN';$('result').hidden=false;$('feedback').hidden=false;$('action').textContent=result.prediction.title;$('parameters').textContent=describeParameters(result.slots);$('score').textContent=score.toLocaleString('fa-IR')+' / ۱۰۰';$('result').classList.toggle('good',score===100&&!unknown);$('result').classList.toggle('unknown',unknown);$('personal').hidden=!result.personal;$('correction').hidden=true;}
function showCapabilities(titles){
 const items=Object.entries(titles).filter(([action])=>action.toUpperCase()!=='UNKNOWN').map(([,title])=>title.trim());
 $('capabilities-list').replaceChildren(...[...new Set(items)].map(title=>{const item=document.createElement('li');item.textContent=title;return item;}));
 $('capabilities').hidden=false;
}
async function start(){const session=await api('session');$('welcome').hidden=!!session.name;$('trainer').hidden=!session.name;if(session.name){const catalog=await api('catalog');setupEditor(catalog.titles,catalog.schema);showCapabilities(catalog.titles);}}
$('welcome').onsubmit=async e=>{e.preventDefault();try{await api('session',{name:$('name').value});await start();}catch(e){message(e.message);}};
$('mic').onclick=microphone({message,display,listening:()=>{current=null;$('feedback').hidden=true;$('result').hidden=true;$('correction').hidden=true;}});
$('correct').onclick=()=>{if(current)showEditor(current.prediction.label,current.slots);};
$('cancel').onclick=()=>{$('correction').hidden=true;};
async function save(kind){if(!current||current.text!==$('text').value.trim()){message('اول متن را بررسی کن');return;}const controls=[$('save'),$('confirm')];controls.forEach(b=>b.disabled=true);try{const action=kind==='confirm'?current.prediction.label:$('expected').value;const parameters=kind==='confirm'?current.slots:readParameters();display(await api('feedback',{text:current.text,action,parameters,kind}));message('ثبت شد');}catch(e){message(e.message);}finally{controls.forEach(b=>b.disabled=false);}}
$('confirm').onclick=()=>save('confirm');$('save').onclick=()=>save('correct');
start().catch(e=>message(e.message));
