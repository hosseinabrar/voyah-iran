import tempfile,unittest
from pathlib import Path
from fastapi.testclient import TestClient
from server import create_app
from store import Store

class FakeRuntime:
    version='test-model';titles={'A':'فرمان الف','B':'فرمان ب'};editor={'A':[{'level':[1,2]}],'B':[{}]}
    def predict(self,text):return {'text':text,'prediction':{'label':'A','title':self.titles['A'],'score':.85},'slots':{'level':1},'model':self.version,'personal':False}
    def validate(self,action,params):return action=='B' and params=={} or action=='A' and params in [{'level':1},{'level':2}]

class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.store=Store(self.tmp.name,'x'*40)
        self.app=create_app(FakeRuntime(),self.store,'http://testserver')
        self.a=TestClient(self.app);self.b=TestClient(self.app)
        self.headers={'origin':'http://testserver'}
    def tearDown(self):self.tmp.cleanup()
    def register(self,client,name='علی'):
        return client.post('/raha-train/api/session',json={'name':name},headers=self.headers)
    def post(self,client,path,body):return client.post('/raha-train/api/'+path,json=body,headers=self.headers)
    def test_isolation_and_persistence(self):
        self.register(self.a);self.register(self.b)
        body={'text':'سلام','action':'B','parameters':{},'kind':'correct'}
        self.assertEqual(self.post(self.a,'feedback',body).status_code,200)
        self.assertTrue(self.post(self.a,'intent',{'text':'سلام'}).json()['personal'])
        self.assertFalse(self.post(self.b,'intent',{'text':'سلام'}).json()['personal'])
        files=list(Path(self.tmp.name).glob('*/*.jsonl'));self.assertEqual(len(files),2)
        self.assertIn('pending',next(p.read_text() for p in files if p.stat().st_size))
        token=self.a.cookies.get('raha_tester');again=Store(self.tmp.name,'x'*40)
        self.assertIsNotNone(again.override(again.identity(token),'سلام','test-model'))
        self.assertIsNone(again.override(again.identity(token),'سلام','different-model'))
    def test_csrf_auth_and_invalid_feedback(self):
        self.assertEqual(self.a.post('/raha-train/api/session',json={'name':'علی'}).status_code,403)
        self.assertEqual(self.post(self.a,'intent',{'text':'سلام'}).status_code,401)
        self.register(self.a)
        self.assertEqual(self.post(self.a,'feedback',{'text':'سلام','action':'evil','parameters':{},'kind':'correct'}).status_code,422)
        self.assertIsNone(self.store.identity('0'*32+'.'+'0'*64))
    def test_paths_are_safe(self):
        r=self.register(self.a,'../../evil');self.assertEqual(r.status_code,200)
        user=self.store.identity(self.a.cookies.get('raha_tester'))
        self.assertNotIn('/',user['folder']);self.assertNotIn('..',user['folder'])
        self.assertEqual(self.a.get('/raha-train/web/model.onnx').status_code,404)
        self.assertEqual(self.a.get('/raha-train/api/project-sync').status_code,404)
    def test_configured_subdomain_and_foreign_host(self):
        app=create_app(FakeRuntime(),self.store,'https://raha.voyah-iran.ir')
        client=TestClient(app,base_url='https://raha.voyah-iran.ir')
        self.assertEqual(client.get('/raha-train/').status_code,200)
        self.assertEqual(client.get('/raha-train/',headers={'host':'other.example'}).status_code,400)
    def test_large_body_rejected(self):
        self.assertEqual(self.a.post('/raha-train/api/session',content='x'*9000,headers=self.headers).status_code,413)
    def test_websocket_requires_session_and_origin(self):
        from starlette.websockets import WebSocketDisconnect
        with self.assertRaises(WebSocketDisconnect):
            with self.a.websocket_connect('/raha-train/api/listen'):pass
    def test_listen_start_partial_final_and_reuse(self):
        from unittest.mock import patch
        class Recognizer:
            def __init__(self,*args):pass
            def Reset(self):pass
            def AcceptWaveform(self,pcm):return False
            def PartialResult(self):return '{"partial":"سلام"}'
            def FinalResult(self):return '{"text":"سلام"}'
        self.register(self.a);self.app.state.asr=object();self.app.state.grammar='[]'
        with patch('vosk.KaldiRecognizer',Recognizer):
            with self.a.websocket_connect('/raha-train/api/listen',headers=self.headers) as ws:
                for _ in range(2):
                    ws.send_text('start');self.assertTrue(ws.receive_json()['ready'])
                    ws.send_bytes(b'\0'*100);self.assertEqual(ws.receive_json()['partial'],'سلام')
                    ws.send_text('stop');self.assertEqual(ws.receive_json()['final'],'سلام')

if __name__=='__main__':unittest.main()
