import asyncio
import json
import re
import threading
import time
import zipfile
from contextlib import asynccontextmanager
from pathlib import Path
import torch
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from starlette.middleware.trustedhost import TrustedHostMiddleware
from transformers import AutoTokenizer, AutoModelForSequenceClassification
from vosk import Model, KaldiRecognizer, SetLogLevel
from data import LABELS, TITLES, MODEL, normalize
from full_model import FullModel
from presentation import titles as clean_titles
from validation import consistency_reason
from feedback_training import FeedbackTrainer
from speech_extras import extend_grammar
from contact_extraction import extract_contact
from numeric_grounding import ground_numbers
from typing import Optional

ROOT = Path(__file__).resolve().parent
ART = ROOT/'artifacts'
lock = threading.Lock()
engine = {}

@asynccontextmanager
async def lifespan(app):
    torch.set_num_threads(4)
    model_path = ART/('full-intent' if (ART/'full-intent/report.json').exists() else 'intent')
    active_path=ROOT/'active-model.json'
    if active_path.exists():
        model_path=(ROOT/json.loads(active_path.read_text())['path']).resolve()
        if not model_path.is_relative_to(ART.resolve()):
            raise RuntimeError('Active checkpoint must be inside experiment artifacts')
    if not (model_path/'report.json').exists():
        raise RuntimeError('Train the experiment first: .venv/bin/python train.py')
    engine['tokenizer'] = AutoTokenizer.from_pretrained(model_path, local_files_only=True)
    engine['full']=(model_path/'schema.json').exists()
    engine['model'] = FullModel.load(model_path) if engine['full'] else AutoModelForSequenceClassification.from_pretrained(model_path, local_files_only=True).eval()
    engine['titles']=clean_titles(engine['model'].schema['titles']) if engine['full'] else TITLES
    engine['report'] = json.loads((model_path/'report.json').read_text())
    if engine['full']:
        # These values already exist in the shared level head; expose the full
        # experimental audio range to decoding and the feedback editor.
        schema=engine['model'].schema
        volume_levels=[str(i) for i in range(31)]
        if not set(volume_levels).issubset(schema['slots']['level']):
            raise RuntimeError('Audio range requires level-head expansion')
        schema['allowed']['SET_AUDIO_VOLUME|channel,level']['level']=volume_levels
        schema['allowed']['SET_AMBIENT_LIGHT_MODE|enabled,mode']['mode']=['"normal"','"music"','"driveMode"']
        from temperature_schema import expand_temperature
        expand_temperature(engine['model'])
        schema['allowed']['SET_FAN_SPEED|level,zone']['level']=[str(i) for i in range(8)]+['"min"','"max"']
        schema['allowed']['ADJUST_FAN_SPEED|delta,zone']['delta']=[next(v for v in schema['slots']['delta'] if json.loads(v)==i) for i in range(-7,8) if i]
        for feature in ['HEATING','VENTILATION']:
            schema['allowed'][f'ADJUST_SEAT_{feature}|delta,seat']['delta']=[str(i) for i in [-3,-2,-1,1,2,3]]
        practice=engine['report'].get('version')==3
        engine['trainer'] = FeedbackTrainer(ROOT, engine['model'], engine['tokenizer'],
            dataset_path=ART/'practice/dataset.json' if practice else None,
            checkpoint_name='feedback-head-practice-v3' if practice else 'feedback-head')
        config = ROOT/'web-runtime.json'
        engine['precision'] = 'fp32'
        if config.exists() and json.loads(config.read_text()).get('precision') == 'int8':
            from compact_model import activate
            checkpoint = activate(engine['model'], ROOT, engine['trainer'].path)
            engine['trainer'] = FeedbackTrainer(ROOT, engine['model'], engine['tokenizer'],
                dataset_path=ART/'practice/dataset.json' if practice else None,
                checkpoint_name=checkpoint)
            engine['precision'] = 'int8 encoder / trainable fp32 heads'
    asr = ART/'vosk-model-small-fa-0.42'
    if not asr.exists():
        archive = ROOT.parents[1]/'voyah-iran/raha-runtime/src/main/assets/persian-model.zip'
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not (ART/name).resolve().is_relative_to(ART.resolve()):
                    raise ValueError('Unsafe model archive')
            z.extractall(ART)
    SetLogLevel(-1)
    engine['asr'] = Model(str(asr))
    vocabulary = json.loads((ROOT/'speech-grammar.json').read_text(encoding='utf-8'))['phrases']
    if not vocabulary or '[unk]' not in vocabulary:
        raise RuntimeError('Missing vehicle speech grammar; run export_speech_grammar.py')
    vocabulary = extend_grammar(vocabulary, engine['asr'])
    engine['grammar'] = json.dumps(vocabulary, ensure_ascii=False)
    predict('سلام')
    if engine.get('precision','').startswith('int8') and json.loads((ROOT/'web-runtime.json').read_text()).get('auto_sync_project',False):
        from project_sync import ProjectSync
        engine['project_sync']=ProjectSync(ROOT,engine['trainer'].path)
        engine['project_sync'].start()
    yield
    if engine.get('project_sync'):engine['project_sync'].close()
    engine.clear()

app=FastAPI(lifespan=lifespan)

@app.middleware('http')
async def fresh_client_assets(request, call_next):
    response = await call_next(request)
    if request.url.path == '/' or request.url.path.startswith('/web/'):
        response.headers['Cache-Control'] = 'no-store'
    return response
app.add_middleware(TrustedHostMiddleware, allowed_hosts=['127.0.0.1', 'localhost', 'testserver'])

def predict(raw):
    if engine.get('full'):return predict_full(raw)
    started = time.perf_counter()
    text=normalize(raw)
    with lock, torch.inference_mode():
        batch=engine['tokenizer'](text, return_tensors='pt', max_length=48, truncation=True)
        probs=engine['model'](**batch).logits.softmax(-1)[0]
        scores, ids=probs.topk(3)
    candidates=[{'label':LABELS[int(i)], 'title':TITLES[LABELS[int(i)]], 'score':round(float(s),4)} for s,i in zip(scores,ids)]
    top=candidates[0]
    reason=None
    if not text:
        reason='متنی دریافت نشد'
    elif len(engine['tokenizer'].encode(text))>48:
        reason='برای این تست یک فرمان کوتاه بگو'
    elif top['label']=='unknown':
        reason='فرمان در دامنهٔ این آزمایش تشخیص داده نشد'
    elif top['score']<.65 or top['score']-candidates[1]['score']<.20:
        reason='مدل مطمئن نیست؛ نیاز به توضیح بیشتر'
    elif re.search(r'\b(نکن|نبند|نذار|اگر|اگه|آیا|چرا)\b',text) or '?' in text or '؟' in text:
        reason='جملهٔ منفی، شرطی یا پرسشی؛ محافظ آزمایش مانع پذیرش شد'
    # Slots are explicitly rules, not claimed as a trained slot model.
    slots={}
    if top['label'].startswith(('seat_', 'window_')):
        if 'شاگرد' in text and 'راننده' in text:
            reason='این نمونه فقط یک صندلی یا شیشه را پشتیبانی می‌کند'
        elif any(x in text for x in ['عقب', 'همه', 'چپ', 'راست']):
            reason='محل گفته‌شده خارج از دامنهٔ این نمونه است'
        slots['target']='passenger' if 'شاگرد' in text else 'driver'
        slots['target_source']='explicit' if any(x in text for x in ['شاگرد','راننده']) else 'default'
    if top['label'].startswith('window_'):
        slots['open_percent']={'window_half':50,'window_open':100,'window_close':0}[top['label']]
    return dict(text=text, accepted=reason is None, reason=reason, prediction=top, candidates=candidates, slots=slots, slot_method='قواعد محدود، نه مدل آموزش‌دیده', latency_ms=round((time.perf_counter()-started)*1000), executes_vehicle=False)

def contact_target(text):
    trainer=engine.get('trainer')
    return extract_contact(text,trainer.contact_rules if trainer else ())

def predict_full(raw):
    start=time.perf_counter();text=normalize(raw)
    with lock,torch.inference_mode():
        token_count=len(engine['tokenizer'].encode(text))
        batch=engine['tokenizer'](text,return_tensors='pt',max_length=48,truncation=True)
        logits,slots=engine['model'](**batch)
        ranked=engine['model'].decode(logits,slots)[0]
    candidates=[dict(label=r['action'],title=engine['titles'][r['action']],score=r['score'],parameters=r['parameters'],slot_scores=r['slot_scores']) for r in ranked]
    top=candidates[0];reason=None;params=dict(top['parameters'])
    params,grounded_keys,numeric_error=ground_numbers(text,top['label'],params,engine['model'].schema)
    if not text:reason='متنی دریافت نشد'
    elif token_count>48:reason='برای این تست یک فرمان کوتاه بگو'
    elif top['label']=='UNKNOWN':reason='فرمان مشخصی تشخیص داده نشد'
    elif top['score']<.65 or top['score']-candidates[1]['score']<.15:reason='نوع فرمان هنوز مطمئن نیست؛ برداشت پیشنهادی را بررسی کن'
    elif any(v<.65 for k,v in top['slot_scores'].items() if k not in grounded_keys):reason='مدل دربارهٔ یکی از پارامترها مطمئن نیست'
    if numeric_error:reason=numeric_error
    if re.search(r'\b(نکن|نبند|نذار|اگر|اگه|آیا|چرا)\b',text) or '?' in text or '؟' in text:
        reason='جملهٔ منفی، شرطی یا پرسشی؛ محافظ آزمایش مانع پذیرش شد'
    if top['label']=='CALL_CONTACT':
        target=contact_target(text)
        if target:params['contact_query']=target
        else:reason='نیت تماس تشخیص داده شد ولی نام مخاطب استخراج نشد'
    if top['label'] in ['CONFIRM_CALL','CANCEL_DIALOG']:
        reason='پاسخ مربوط به گفتگوی تماس است؛ این صفحه تماس در انتظاری ندارد'
    consistency=consistency_reason(text,top['label'],params)
    if consistency:reason=consistency
    return dict(text=text,accepted=reason is None,reason=reason,prediction=top,candidates=candidates,slots=params,slot_scores={k:v for k,v in top['slot_scores'].items() if k not in grounded_keys},slot_method='عدد صریح: استخراج دقیق از متن؛ سمت و سایر پارامترها: مدل آموزش‌دیده' if grounded_keys else 'پارامترهای خودرو: سرهای آموزش‌دیدهٔ ParsBERT؛ نام مخاطب: استخراج متنی',latency_ms=round((time.perf_counter()-start)*1000),executes_vehicle=False)

class Request(BaseModel):
    text: str = Field(max_length=500)

class FeedbackRequest(BaseModel):
    text: str = Field(min_length=1,max_length=500)
    action: str = Field(max_length=100)
    parameters: Optional[dict] = None

def learn_feedback(req):
    try:
        with lock:
            outcome=engine['trainer'].train(req.text,req.action) if req.parameters is None else engine['trainer'].train(req.text,req.action,req.parameters)
            if engine.get('project_sync'):engine['project_sync'].request()
    except ValueError as e:
        raise HTTPException(status_code=422,detail=str(e))
    return {**outcome,'result':predict(req.text)}

@app.post('/api/feedback')
async def feedback(req:FeedbackRequest):
    if not engine.get('full'):
        raise HTTPException(status_code=409,detail='مدل کامل آماده نیست')
    return await asyncio.to_thread(learn_feedback,req)

@app.post('/api/intent')
async def intent(req:Request):
    return await asyncio.to_thread(predict, req.text)

@app.get('/api/project-sync')
def project_sync_status():
    sync=engine.get('project_sync')
    return sync.status() if sync else {'state':'disabled'}

@app.get('/api/status')
def status():
    return dict(model=MODEL, precision=engine.get('precision','fp32'), feedback_count=len(engine['trainer'].rows) if engine.get('trainer') else 0, version=engine['report'].get('version',2 if engine['full'] else 1), labels=engine['titles'], report={k:v for k,v in engine['report'].items() if k!='results'}, asr='Vosk small-fa-0.42 · local · Raha vehicle vocabulary', audio_stored=False)

@app.get('/api/catalog')
def catalog():
    path=ART/'practice/dataset.json' if engine['report'].get('version')==3 else ROOT/'dataset.json'
    if not path.exists():return []
    dataset=json.loads(path.read_text());examples={}
    for row in dataset['train']:
        if row['action'] not in examples:examples[row['action']]=row['text']
    return [dict(action=k,title=v,example=examples.get(k,'')) for k,v in clean_titles(dataset['titles']).items()]

@app.get('/api/feedback-schema')
def feedback_schema():
    schema=engine['model'].schema
    result={}
    for target in schema['frames']:
        action,keys=target.split('|')
        fields={k:[json.loads(v) for v in schema.get('allowed',{}).get(target,{}).get(k,schema['slots'][k])] for k in filter(None,keys.split(','))}
        result.setdefault(action,[]).append(fields)
    result['CALL_CONTACT']=[{'contact_query':None}]
    return result

@app.websocket('/api/listen')
async def listen(ws:WebSocket):
    if ws.headers.get('origin') not in ['http://127.0.0.1:18766', 'http://localhost:18766']:
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
                await ws.send_json({'partial':normalize(' '.join(fragments+[partial]))})
            elif msg.get('text')=='start':
                recognizer.Reset()
                fragments=[]
                total=0
                listening=True
                await ws.send_json({'ready':True})
            elif msg.get('text')=='stop' and listening:
                listening=False
                final=json.loads(await asyncio.to_thread(recognizer.FinalResult)).get('text','')
                transcript=normalize(' '.join(fragments+[final]))
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
