// Keep capture and recognizer warm; only send audio during an explicit listening turn.
export function microphone({message,display}) {
 const $=id=>document.getElementById(id);
 let stream,ctx,node,source,analyser,ws,timer,raf,flushed,watchdog,bytes=0,active=false,busy=false;
 const release=document.createElement('button');
 release.textContent='بستن میکروفون';release.hidden=true;$('mic').after(release);
 function idle(){active=false;clearTimeout(timer);cancelAnimationFrame(raf);$('level').style.width='0';$('mic').textContent='شروع شنود';$('mic').classList.remove('recording');$('mic').disabled=false;$('parse').disabled=false;busy=false;}
 function close(){const old=ws;ws=null;if(old){old.onclose=null;old.close();}node?.disconnect();source?.disconnect();stream?.getTracks().forEach(t=>t.stop());ctx?.close();stream=ctx=node=source=analyser=null;flushed?.();flushed=null;release.hidden=true;idle();}
 release.onclick=()=>{close();message('میکروفون بسته شد');};
 window.addEventListener('pagehide',close);
 function meter(){if(!active)return;const data=new Float32Array(analyser.fftSize);analyser.getFloatTimeDomainData(data);const rms=Math.sqrt(data.reduce((s,x)=>s+x*x,0)/data.length);$('level').style.width=Math.min(100,rms*450)+'%';raf=requestAnimationFrame(meter);}
 async function prepare(){
  if(ws?.readyState===1&&stream?.active)return;
  close();busy=true;$('mic').disabled=true;
  stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true},video:false});
  ctx=new AudioContext();await ctx.resume();await ctx.audioWorklet.addModule('/web/pcm.js?v=persistent-ack-2');
  source=ctx.createMediaStreamSource(stream);node=new AudioWorkletNode(ctx,'pcm');analyser=ctx.createAnalyser();analyser.fftSize=256;
  source.connect(analyser);source.connect(node);node.connect(ctx.destination);
  node.onprocessorerror=()=>{close();message('پردازش صدای میکروفون متوقف شد؛ صفحه را تازه‌سازی کن');};
  node.port.onmessage=e=>{if(e.data==='started')return;if(e.data==='flushed'){flushed?.();flushed=null;}else if(active&&ws?.readyState===1&&e.data instanceof ArrayBuffer){bytes+=e.data.byteLength;ws.send(e.data);}};
  ws=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/api/listen`);
  await new Promise((resolve,reject)=>{const deadline=setTimeout(()=>reject(Error('اتصال به سرور طول کشید')),10000);ws.onopen=()=>{clearTimeout(deadline);resolve();};ws.onerror=()=>{clearTimeout(deadline);reject(Error('اتصال شنود برقرار نشد'));};});
  ws.onmessage=e=>{const m=JSON.parse(e.data);
   if(m.ready){active=true;busy=false;bytes=0;node.port.postMessage('start');$('text').value='';$('mic').textContent='پایان و بررسی';$('mic').classList.add('recording');$('mic').disabled=false;$('parse').disabled=true;message('دارم می‌شنوم…');meter();timer=setTimeout(stop,29000);clearTimeout(watchdog);watchdog=setTimeout(()=>{if(active&&!bytes){close();message('صدا به تشخیص گفتار نرسید؛ صفحه را تازه‌سازی و دوباره امتحان کن');}},2500);}
   if(m.partial!==undefined)$('text').value=m.partial;
   if(m.final!==undefined){idle();$('text').value=m.final;display(m.result);message(m.final?'آمادهٔ جملهٔ بعدی؛ میکروفون باز است':'صدایی تشخیص داده نشد؛ دوباره شروع شنود را بزن');}
   if(m.error){close();message(m.error);}
  };
  ws.onclose=()=>{close();message('اتصال شنود قطع شد؛ برای اتصال دوباره شروع شنود را بزن');};
  release.hidden=false;
 }
 async function stop(){if(!active||busy)return;busy=true;$('mic').disabled=true;clearTimeout(timer);
  await new Promise(resolve=>{const deadline=setTimeout(resolve,500);flushed=()=>{clearTimeout(deadline);resolve();};node.port.postMessage('flush');});
  active=false;cancelAnimationFrame(raf);$('level').style.width='0';
  if(ws?.readyState===1){ws.send('stop');message('در حال بررسی فرمان…');}else close();
 }
 return async()=>{if(busy)return;if(active)return stop();busy=true;$('mic').disabled=true;
  try{await prepare();await ctx.resume();ws.send('start');}
  catch(e){close();message('میکروفون آماده نشد: '+e.message);}
 };
}
