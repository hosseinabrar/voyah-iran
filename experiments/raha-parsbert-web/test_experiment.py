import unittest
import json
import wave
from unittest.mock import patch
import numpy as np
from fastapi.testclient import TestClient
from data import training_data, EVAL, normalize, LABELS
from server import app, ART,engine

class DataTests(unittest.TestCase):
    def test_contact_parameters(self):
        from server import contact_target
        for text,name in [('به علی زنگ بزن','علی'),('می خوام زنگ بزنی به علی','علی'),('میخوام یه تماس با مریم بگیری','مریم'),('میخوام با رضا تماس بگیری','رضا'),('رها لطف می کنی با سارا تماس بگیری','سارا')]:
            self.assertEqual(contact_target(text),name)

    def test_normalization(self):
        self.assertEqual(normalize('  گرم‌کن  صندلي '), 'گرم کن صندلی')

    def test_no_eval_leakage(self):
        train={r['text'] for r in training_data()}
        self.assertFalse(train.intersection(normalize(t) for t,_ in EVAL))
        self.assertEqual(set(LABELS), {r['label'] for r in training_data()})

class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context=TestClient(app)
        cls.client=cls.context.__enter__()

    @classmethod
    def tearDownClass(cls):
        cls.context.__exit__(None,None,None)

    def test_intent_is_real_and_no_execution(self):
        result=self.client.post('/api/intent',json={'text':'رها گرمکن صندلی رو خاموش کن'}).json()
        self.assertTrue(result['accepted'])
        self.assertEqual(result['prediction']['label'],'SET_SEAT_HEATING' if engine['full'] else 'seat_heat_off')
        if engine['full']:
            self.assertEqual(result['slots']['enabled'],False)
            self.assertEqual(result['slots']['seat'],'driver')
        self.assertFalse(result['executes_vehicle'])

    def test_empty_and_long_input(self):
        self.assertFalse(self.client.post('/api/intent',json={'text':''}).json()['accepted'])
        self.assertEqual(self.client.post('/api/intent',json={'text':'x'*501}).status_code,422)

    def test_feedback_endpoint(self):
        with patch.object(engine['trainer'],'train',return_value={'count':1,'learned':True,'scope':'action_only'}) as train:
            response=self.client.post('/api/feedback',json={'text':'گرمکن صندلی رو خاموش کن','action':'SET_SEAT_HEATING'})
            self.assertEqual(response.status_code,200)
            self.assertTrue(response.json()['learned'])
            self.assertIn('result',response.json())
            train.assert_called_once_with('گرمکن صندلی رو خاموش کن','SET_SEAT_HEATING')
        self.assertEqual(self.client.post('/api/feedback',json={'text':'سلام','action':'BAD_LABEL'}).status_code,422)

    def test_websocket_silence(self):
        with self.client.websocket_connect('/api/listen',headers={'origin':'http://localhost:18766'}) as ws:
            ws.send_text('start')
            self.assertTrue(ws.receive_json()['ready'])
            ws.send_bytes(b'\0'*3200)
            self.assertIn('partial',ws.receive_json())
            ws.send_text('stop')
            result=ws.receive_json()
            self.assertFalse(result['result']['accepted'])

    def test_websocket_uses_vehicle_grammar(self):
        from vosk import KaldiRecognizer
        vocabulary=json.loads(engine['grammar'])
        self.assertIn('[unk]',vocabulary)
        self.assertIn('علی',vocabulary)
        for word in ['جا', 'جای', 'بغل', 'بغلی', 'کناری', 'کناریم', 'بغلم', 'خودم', 'غیرفعالش', 'دیروز', 'اگه', 'وقتی', 'تلویزیون']:
            self.assertIn(word,vocabulary)
        from speech_extras import WORDS, PHRASES
        for word in set(' '.join(WORDS+PHRASES).split()):
            self.assertGreaterEqual(engine['asr'].vosk_model_find_word(word),0,word)
        self.assertIn('به علی زنگ بزن',vocabulary)
        self.assertIn('گرمکن صندلی رو خاموش کن',vocabulary)
        self.assertIn('Raha vehicle vocabulary',self.client.get('/api/status').json()['asr'])
        with patch('server.KaldiRecognizer', wraps=KaldiRecognizer) as factory:
            with self.client.websocket_connect('/api/listen',headers={'origin':'http://localhost:18766'}) as ws:
                ws.send_text('start')
                self.assertTrue(ws.receive_json()['ready'])
                ws.send_text('stop')
                ws.receive_json()
                # Reuse the same recognizer and connection for subsequent turns.
                for _ in range(3):
                    ws.send_bytes(b'\\0'*3200)  # idle audio must be ignored
                    ws.send_text('start')
                    self.assertTrue(ws.receive_json()['ready'])
                    ws.send_bytes(b'\\0'*3200)
                    self.assertIn('partial',ws.receive_json())
                    ws.send_text('stop')
                    self.assertIn('final',ws.receive_json())
            factory.assert_called_once_with(engine['asr'],16000,engine['grammar'])

    def test_real_asr_fixture(self):
        # Included upstream Vosk speech fixture, NOT a recording of the user's microphone.
        with wave.open(str(ART/'vosk-model-small-fa-0.42/test/test.wav')) as audio:
            self.assertEqual(audio.getnchannels(),1)
            self.assertEqual(audio.getsampwidth(),2)
            ratio=audio.getframerate()//16000
            self.assertEqual(audio.getframerate(),ratio*16000)
            with self.client.websocket_connect('/api/listen',headers={'origin':'http://localhost:18766'}) as ws:
                ws.send_text('start')
                self.assertTrue(ws.receive_json()['ready'])
                while True:
                    pcm=audio.readframes(4000*ratio)
                    if not pcm: break
                    samples=np.frombuffer(pcm,dtype='<i2')
                    pcm=samples[:len(samples)//ratio*ratio].reshape(-1,ratio).mean(axis=1).astype('<i2').tobytes()
                    ws.send_bytes(pcm)
                    self.assertIn('partial', ws.receive_json())
                ws.send_text('stop')
                result=ws.receive_json()
                self.assertTrue(result['final'])
                print('ASR fixture transcript:',result['final'])

    def test_cross_origin_denied(self):
        from starlette.websockets import WebSocketDisconnect
        with self.assertRaises(WebSocketDisconnect):
            with self.client.websocket_connect('/api/listen',headers={'origin':'https://example.com'}):
                pass

if __name__=='__main__': unittest.main()
