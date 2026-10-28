import asyncio,json,os,time
from collections import OrderedDict
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI,Request,WebSocket,WebSocketDisconnect,HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel,Field
from starlette.middleware.trustedhost import TrustedHostMiddleware
from store import Store,normalize

HERE=Path(__file__).resolve().parent
PREFIX='/raha-train'
COOKIE='raha_tester'

class Name(BaseModel):name:str=Field(min_length=1,max_length=60)
class Text(BaseModel):text:str=Field(min_length=1,max_length=500)
class Correction(Text):
    action:str=Field(max_length=100)
    parameters:dict
    kind:str=Field(pattern='^(confirm|correct)$')

def create_app(runtime=None,store=None,origin=None):
    origin=origin or os.environ.get('PUBLIC_ORIGIN','https://voyah-iran.ir')
    bundle=HERE/'bundle'
    @asynccontextmanager
    async def lifespan(app):
        if runtime is None:
            from runtime import Runtime
            app.state.runtime=Runtime(bundle)
            from vosk import Model,SetLogLevel
            SetLogLevel(-1);app.state.asr=Model(str(bundle/'vosk'))
            app.state.grammar=(bundle/'grammar.json').read_text()
        yield
    app=FastAPI(lifespan=lifespan,docs_url=None,redoc_url=None,openapi_url=None)
    app.state.runtime=runtime
    app.state.store=store or Store(os.environ.get('TESTER_DATA',str(HERE/'data')),os.environ.get('SESSION_SECRET',''))
    app.add_middleware(TrustedHostMiddleware,allowed_hosts=['voyah-iran.ir','127.0.0.1','localhost','testserver'])
    buckets=OrderedDict();connections=set();max_connections=4
    def identity(token):
        user=app.state.store.identity(token)
        if user is None:raise HTTPException(401,'نامت را وارد کن')
        return user
    def rate(key,limit=60):
        now=time.monotonic();timestamps=[t for t in buckets.pop(key,[]) if now-t<60]
        if len(timestamps)>=limit:raise HTTPException(429,'کمی صبر کن و دوباره امتحان کن')
        timestamps.append(now);buckets[key]=timestamps
        while len(buckets)>4096:buckets.popitem(last=False)
    @app.middleware('http')
    async def security(request,call_next):
        from fastapi.responses import JSONResponse
        if request.method=='POST':
            if request.headers.get('origin')!=origin:return JSONResponse({'detail':'درخواست نامعتبر'},403)
            try:
                rate(request.client.host if request.client else 'unknown')
                if len(await request.body())>8192:return JSONResponse({'detail':'درخواست بزرگ است'},413)
            except HTTPException as e:return JSONResponse({'detail':e.detail},e.status_code)
        response=await call_next(request)
        response.headers['Cache-Control']='no-store'
        response.headers['X-Content-Type-Options']='nosniff'
        response.headers['Referrer-Policy']='no-referrer'
        response.headers['Content-Security-Policy']="default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
        response.headers['Permissions-Policy']='microphone=(self), camera=()'
        return response
    def result(user,text):
        r=app.state.runtime.predict(text)
        correction=app.state.store.override(user,text,r['model'])
        if correction:
            r['prediction']={'label':correction['action'],'title':app.state.runtime.titles[correction['action']],'score':1.0}
            r['slots']=correction['parameters'];r['personal']=True
        return r
    @app.get(PREFIX+'/')
    def home():return FileResponse(HERE/'web/index.html')
    @app.get(PREFIX+'/web/{name}')
    def assets(name:str):
        if name in ['app.js','style.css']:return FileResponse(HERE/'web'/name)
        if name in ['microphone.js','pcm.js','feedback-editor.js','parameters.js']:return FileResponse(bundle/name)
        raise HTTPException(404)
    @app.get(PREFIX+'/api/session')
    def session(request:Request):
        user=app.state.store.identity(request.cookies.get(COOKIE))
        return {'name':user['name'] if user else None}
    @app.post(PREFIX+'/api/session')
    def register(body:Name,request:Request):
        from fastapi.responses import JSONResponse
        existing=app.state.store.identity(request.cookies.get(COOKIE))
        if existing:return {'name':existing['name']}
        try:token,user=app.state.store.register(body.name)
        except ValueError as e:raise HTTPException(422,str(e))
        response=JSONResponse({'name':user['name']})
        response.set_cookie(COOKIE,token,max_age=365*86400,httponly=True,secure=origin.startswith('https:'),samesite='strict',path=PREFIX)
        return response
    @app.get(PREFIX+'/api/catalog')
    def catalog(request:Request):
        identity(request.cookies.get(COOKIE))
        return {'titles':app.state.runtime.titles,'schema':app.state.runtime.editor}
    @app.post(PREFIX+'/api/intent')
    async def intent(body:Text,request:Request):
        user=identity(request.cookies.get(COOKIE));return await asyncio.to_thread(result,user,body.text)
    @app.post(PREFIX+'/api/feedback')
    async def feedback(body:Correction,request:Request):
        user=identity(request.cookies.get(COOKIE));r=app.state.runtime
        if not r.validate(body.action,body.parameters):raise HTTPException(422,'فرمان یا پارامتر نامعتبر است')
        original=await asyncio.to_thread(r.predict,body.text)
        try:
            app.state.store.append(user,{'text':normalize(body.text),'action':body.action,'parameters':body.parameters,'kind':body.kind,'model':r.version,'prediction':original})
        except ValueError as e:raise HTTPException(409,str(e))
        return await asyncio.to_thread(result,user,body.text)
    @app.websocket(PREFIX+'/api/listen')
    async def listen(ws:WebSocket):
        if ws.headers.get('origin')!=origin:await ws.close(code=1008);return
        try:user=identity(ws.cookies.get(COOKIE))
        except HTTPException:await ws.close(code=1008);return
        device=user['device']
        if device in connections or len(connections)>=max_connections:await ws.close(code=1013);return
        connections.add(device)
        try:
            await ws.accept()
            from vosk import KaldiRecognizer
            recognizer=KaldiRecognizer(app.state.asr,16000,app.state.grammar)
            fragments=[];total=0;listening=False
            while True:
                msg=await asyncio.wait_for(ws.receive(),timeout=90)
                if msg['type']=='websocket.disconnect':break
                if msg.get('text')=='start':
                    rate('ws:'+device,30);recognizer.Reset();fragments=[];total=0;listening=True
                    await ws.send_json({'ready':True})
                elif msg.get('bytes') is not None and listening:
                    pcm=msg['bytes'];total+=len(pcm)
                    if len(pcm)>65536 or len(pcm)%2 or total>960000:await ws.close(code=1009);break
                    complete=await asyncio.to_thread(recognizer.AcceptWaveform,pcm)
                    if complete:fragments.append(json.loads(recognizer.Result()).get('text',''));partial=''
                    else:partial=json.loads(recognizer.PartialResult()).get('partial','')
                    await ws.send_json({'partial':normalize(' '.join(fragments+[partial]))})
                elif msg.get('text')=='stop' and listening:
                    listening=False
                    final=json.loads(await asyncio.to_thread(recognizer.FinalResult)).get('text','')
                    transcript=normalize(' '.join(fragments+[final]))
                    await ws.send_json({'final':transcript,'result':await asyncio.to_thread(result,user,transcript)})
        except (WebSocketDisconnect,asyncio.TimeoutError,HTTPException):pass
        finally:
            connections.discard(device)
            try:await ws.close()
            except RuntimeError:pass
    return app
