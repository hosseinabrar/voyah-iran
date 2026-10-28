import {parameterName,parameterValue} from './parameters.js';
const $=id=>document.getElementById(id);
let schema={},fields={},actionOptions=[];
const normalizeSearch=text=>text.replace(/ي/g,'ی').replace(/ك/g,'ک').replace(/[\s\u200c]/g,'').toLowerCase();
function filterActions(){
 if(!actionOptions.length)actionOptions=Array.from($('expected').options,o=>({value:o.value,title:o.textContent}));
 const query=normalizeSearch($('action-search').value);
 const selected=$('expected').value;
 $('expected').replaceChildren();
 for(const item of actionOptions){if(query&&!normalizeSearch(item.title).includes(query))continue;const option=document.createElement('option');option.value=item.value;option.textContent=item.title;$('expected').append(option);}
 if(Array.from($('expected').options).some(o=>o.value===selected))$('expected').value=selected;
 else $('expected').selectedIndex=-1;
 $('save').disabled=!$('expected').value;
}
export function setupEditor(titles,variants){
 schema=variants;actionOptions=Object.entries(titles).map(([value,title])=>({value,title}));
 $('action-search').oninput=filterActions;
 $('parameter-variant').onchange=()=>renderFields({});
 $('expected').onchange=()=>showEditor($('expected').value,{});
 filterActions();
}
function renderFields(current){
 const variants=schema[$('expected').value]??[{}];fields=variants[Number($('parameter-variant').value)]??{};
 $('parameter-fields').replaceChildren();
 for(const [key,originalOptions] of Object.entries(fields)){
  const seatToggle=['SET_SEAT_HEATING','SET_SEAT_VENTILATION','SET_SEAT_MASSAGE'].includes($('expected').value);
  const options=seatToggle&&key==='enabled'?[true,false]:originalOptions;
  const label=document.createElement('label');label.textContent=parameterName(key);
  const control=document.createElement(options===null?'input':'select');control.id='parameter-'+key;control.dataset.key=key;
  if(options===null){control.type='text';control.maxLength=80;control.value=current[key]??'';control.placeholder='مثلاً علی';}
  else{for(const value of options){const opt=document.createElement('option');opt.value=JSON.stringify(value);opt.textContent=parameterValue(value);control.append(opt);}if(options.some(v=>v===current[key]))control.value=JSON.stringify(current[key]);}
  label.append(control);$('parameter-fields').append(label);
  if(seatToggle&&key==='enabled')control.onchange=()=>{
   const values=readParameters();
   if(values.enabled===false)values.level=0;
   else if(values.level===0)delete values.level;
   showEditor($('expected').value,values);
  };
  if(seatToggle&&key==='level'&&current.enabled===false){control.value='0';control.disabled=true;}
 }
}
export function showEditor(action,current){
 $('action-search').value='';filterActions();
 $('expected').value=action;
 $('save').disabled=!action;
 const variants=schema[action]??[{}],select=$('parameter-variant');select.replaceChildren();
 variants.forEach((fields,i)=>{const option=document.createElement('option');option.value=String(i);option.textContent=Object.keys(fields).map(parameterName).join(' + ')||'بدون پارامتر';select.append(option);});
 const keys=Object.keys(current).sort().join(',');const index=variants.findIndex(v=>Object.keys(v).sort().join(',')===keys);
 select.value=String(Math.max(0,index));select.hidden=variants.length<2;
 renderFields(current);$('correction').hidden=false;
}
export function readParameters(){
 if(!$('expected').value)throw Error('عملگر را از نتیجه‌های جست‌وجو انتخاب کن');
 const result={};for(const [key,options] of Object.entries(fields)){
  const value=$('parameter-'+key).value;
  if(options===null){if(!value.trim())throw Error('نام مخاطب را وارد کن');result[key]=value.trim();}
  else result[key]=JSON.parse(value);
 }return result;
}
