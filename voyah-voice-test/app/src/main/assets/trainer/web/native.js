let serial=0;
const pending=new Map();
export function request(path,body={}){
 return new Promise((resolve,reject)=>{const id=++serial;pending.set(id,{resolve,reject});AndroidTrainer.request(id,path,JSON.stringify(body));});
}
window.nativeReply=(id,value,error)=>{const job=pending.get(id);if(!job)return;pending.delete(id);if(error)job.reject(Error(error));else job.resolve(value);};
export function microphone({message,display,listening}){
 let active=false;
 const button=document.getElementById('mic'),level=document.getElementById('level');
 window.nativeSpeech=event=>{
  if(event.type==='start'){active=true;listening();document.getElementById('text').value='';button.textContent='لغو شنود';button.classList.add('recording');message('دارم می‌شنوم…');}
  if(event.type==='partial')document.getElementById('text').value=event.text;
  if(event.type==='level')level.style.width=event.level+'%';
  if(event.type==='decoding'){button.disabled=true;message('در حال بررسی فرمان…');}
  if(['result','error','cancel'].includes(event.type)){active=false;button.disabled=false;button.textContent='شروع شنود';button.classList.remove('recording');level.style.width='0';
   if(event.type==='result'){display(event.result);message('');}else message(event.message||'');}
 };
 return async()=>{button.disabled=true;try{await request(active?'cancel':'listen');}catch(e){message(e.message);}finally{button.disabled=false;}};
}
