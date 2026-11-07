"""Independent 2.1.1 rule-recognizer test host. No torch/transformers/ONNX imports."""
import asyncio, base64, json, os, subprocess, threading, time
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.trustedhost import TrustedHostMiddleware
from pydantic import BaseModel, Field
from vosk import Model, KaldiRecognizer, SetLogLevel
from export_lessons import export, TARGET, ROOT, REPO
from speech_extras import extend_grammar

PORT=int(os.environ.get('RAHA_OLD_PORT','18770'))
LAN_HOSTS=[h for h in os.environ.get('RAHA_LAN_HOSTS','').split(',') if h]
ALLOWED_ORIGINS=[f'{s}://{h}:{PORT}' for h in ['localhost','127.0.0.1']+LAN_HOSTS for s in ['http','https']]
engine={}; lock=threading.RLock()
class Worker:
 def __init__(self):
  java=os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home')+'/bin/java'
  cp=(REPO/'voyah-iran/raha-core/build/old-raha-classpath.txt').read_text()
  self.proc=subprocess.Popen([java,'-Xmx256m','-cp',str(ROOT/'build')+os.pathsep+cp,'OldRahaWorker',str(TARGET)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,bufsize=1)
 def send(self,line):
  with lock:
   self.proc.stdin.write(line+'\n');self.proc.stdin.flush();reply=self.proc.stdout.readline()
   if not reply:raise RuntimeError('Old Raha JVM stopped')
   return json.loads(reply)
 def recognize(self,text):return self.send(base64.b64encode(text.encode()).decode())
 def close(self):
  self.proc.stdin.close()
  try:self.proc.wait(timeout=5)
  except subprocess.TimeoutExpired:self.proc.terminate();self.proc.wait(timeout=5)

@asynccontextmanager
async def lifespan(app):
 engine['worker']=Worker()
 engine['rows']=json.loads((ROOT/'lessons.json').read_text())
 engine['titles']=json.loads((ROOT/'original-status.json').read_text())['labels']
 engine['schema']=json.loads((ROOT/'feedback-schema.json').read_text())
 SetLogLevel(-1)
 engine['asr']=Model(str(REPO/'experiments/raha-parsbert-web/artifacts/vosk-model-small-fa-0.42'))
 vocabulary=json.loads((ROOT/'speech-grammar.json').read_text())['phrases']
 engine['grammar']=json.dumps(extend_grammar(vocabulary,engine['asr']),ensure_ascii=False)
 yield
 engine['worker'].close();engine.clear()
app=FastAPI(lifespan=lifespan)
app.add_middleware(TrustedHostMiddleware,allowed_hosts=['localhost','127.0.0.1','testserver']+LAN_HOSTS)

@app.middleware('http')
async def no_cache(request,call_next):
 response=await call_next(request);response.headers['Cache-Control']='no-store';return response

def predict(raw):
 start=time.perf_counter();r=engine['worker'].recognize(raw);a=r['action'];accepted=a!='UNKNOWN';reason=None if accepted else r.get('reason','فرمان مشخصی تشخیص داده نشد')
 top=dict(label=a,title=engine['titles'].get(a,a),score=1.0 if accepted else 0.0,parameters=r['parameters'],slot_scores={})
 return dict(text=raw,accepted=accepted,reason=reason,prediction=top,candidates=[top],slots=r['parameters'],slot_scores={},evidence=None,source=r['source'],slot_method=r['source'],latency_ms=round((time.perf_counter()-start)*1000),executes_vehicle=False)
class Request(BaseModel):text:str=Field(max_length=500)
class FeedbackRequest(Request):
 action:str
 parameters:dict
@app.post('/api/intent')
async def intent(req:Request):return await asyncio.to_thread(predict,req.text)

def save(req):
 with lock:
  if req.action not in engine['titles']:raise HTTPException(422,'فرمان نامعتبر است')
  variants=engine['schema'].get(req.action,[])
  if req.action!='UNKNOWN' and not any(set(req.parameters)==set(v) and all(v[k] is None or req.parameters[k] in v[k] for k in v) for v in variants):
   raise HTTPException(422,'پارامترهای انتخاب‌شده با فرمان سازگار نیستند')
  row=dict(text=req.text,action=req.action,parameters=req.parameters)
  new=engine['rows']+[row];export(new);engine['verification']=dict(state='idle',outdated=True);
  with (ROOT/'corrections.jsonl').open('a') as log:log.write(json.dumps(row,ensure_ascii=False)+'\n')
  engine['rows']=json.loads((ROOT/'lessons.json').read_text())
  engine['worker'].send('RELOAD')
  result=predict(req.text)
  return dict(learned=result['prediction']['label']==req.action and result['slots']==req.parameters,scope='exact_example',result=result)
@app.post('/api/feedback')
async def feedback(req:FeedbackRequest):return await asyncio.to_thread(save,req)
@app.get('/api/status')
def status():return dict(semantic_guard_enabled=False,model='Raha 2.1.1 rules + saved examples',version='old-raha',labels=engine['titles'],report=dict(train_count=len(engine['rows']),action_count=len(engine['titles']),evaluation='تطبیق قواعد یا مثال ذخیره‌شده؛ امتیاز احتمال مدل نیست'),asr='Vosk small-fa-0.42 · local',audio_stored=False)
@app.get('/api/catalog')
def catalog():return json.loads((ROOT/'catalog.json').read_text())
@app.get('/api/feedback-schema')
def schema():return engine['schema']
@app.get('/api/project-sync')
def sync():return dict(state='synced',verification=engine.get('verification',dict(state='idle')))
@app.post('/api/project-sync/verify')
def verify():
 if engine.get('verification',{}).get('state')=='running':return dict(started=False)
 engine['verification']=dict(state='running')
 def run():
  failures=[];rows=list(engine['rows'])
  try:
   for row in rows:
    r=engine['worker'].recognize(row['text'])
    if r['action']!=row['action'] or r['parameters']!=row['parameters']:failures.append(dict(text=row['text'],expected=row,actual=r))
   engine['verification']=dict(state='passed' if not failures else 'mismatch',count=len(rows),failure_count=len(failures),failures=failures[:30])
  except Exception as e:engine['verification']=dict(state='error',error=str(e))
  (ROOT/'verification-report.json').write_text(json.dumps(engine['verification'],ensure_ascii=False,indent=2))
 threading.Thread(target=run,daemon=True).start();return dict(started=True)

@app.websocket('/api/listen')
async def listen(ws:WebSocket):
    if ws.headers.get('origin') not in ALLOWED_ORIGINS:
        await ws.close(code=1008)
        return
    await ws.accept()
    recognizer=KaldiRecognizer(engine['asr'], 16000, engine['grammar'])
    fragments=[]
    total=0
    listening=False
    try:
        while True:
            msg=await ws.receive()
            if msg['type']=='websocket.disconnect':
                break
            if msg.get('bytes') is not None:
                if not listening:
                    continue
                pcm=msg['bytes']
                total+=len(pcm)
                if total>16000*2*30 or len(pcm)>65536 or len(pcm)%2:
                    await ws.send_json({'error':'حداکثر زمان ضبط ۳۰ ثانیه است'})
                    break
                complete=await asyncio.to_thread(recognizer.AcceptWaveform, pcm)
                if complete:
                    fragment=json.loads(recognizer.Result()).get('text','')
                    if fragment: fragments.append(fragment)
                    partial=''
                else:
                    partial=json.loads(recognizer.PartialResult()).get('partial','')
                await ws.send_json({'partial':' '.join(filter(None, fragments+[partial]))})
            elif msg.get('text')=='start':
                recognizer.Reset()
                fragments=[]
                total=0
                listening=True
                await ws.send_json({'ready':True})
            elif msg.get('text')=='stop' and listening:
                listening=False
                final=json.loads(await asyncio.to_thread(recognizer.FinalResult)).get('text','')
                transcript=' '.join(filter(None, fragments+[final]))
                result=await asyncio.to_thread(predict, transcript)
                await ws.send_json({'final':transcript, 'result':result})
    except WebSocketDisconnect:
        pass
    finally:
        try: await ws.close()
        except RuntimeError: pass

@app.get('/')
def home():
    return FileResponse(ROOT/'web/index.html')

app.mount('/web', StaticFiles(directory=ROOT/'web'), name='web')
