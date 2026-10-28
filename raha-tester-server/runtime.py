import hashlib,json,sys,threading
from pathlib import Path
import numpy as np
import onnxruntime as ort
from tokenizers import BertWordPieceTokenizer

class Runtime:
    def __init__(self,bundle):
        self.bundle=Path(bundle)
        for name,digest in json.loads((self.bundle/'checksums.json').read_text()).items():
            if hashlib.sha256((self.bundle/name).read_bytes()).hexdigest()!=digest:
                raise RuntimeError('Snapshot integrity failed: '+name)
        sys.path.insert(0,str(self.bundle))
        from presentation import titles
        self.schema=json.loads((self.bundle/'schema.json').read_text())
        self.titles=titles(self.schema['titles'])
        self.rules=json.loads((self.bundle/'contact-rules.json').read_text())
        self.version=json.loads((self.bundle/'manifest.json').read_text())['model_sha256']
        self.tokenizer=BertWordPieceTokenizer(str(self.bundle/'vocab.txt'),lowercase=False,strip_accents=False)
        self.tokenizer.enable_truncation(max_length=48)
        options=ort.SessionOptions();options.intra_op_num_threads=2
        self.session=ort.InferenceSession(str(self.bundle/'model.onnx'),options,providers=['CPUExecutionProvider'])
        self.lock=threading.Lock()
        self.editor={}
        for frame in self.schema['frames']:
            action,keys=frame.split('|')
            fields={k:[json.loads(v) for v in self.schema.get('allowed',{}).get(frame,{}).get(k,self.schema['slots'][k])] for k in keys.split(',') if k}
            self.editor.setdefault(action,[]).append(fields)
        self.editor['CALL_CONTACT']=[{'contact_query':None}]

    def validate(self,action,params):
        if action=='CALL_CONTACT':
            return set(params)=={'contact_query'} and isinstance(params['contact_query'],str) and 0<len(params['contact_query'].strip())<=80
        for variant in self.editor.get(action,[]):
            if set(variant)!=set(params):continue
            if all(any(type(v)==type(params[k]) and v==params[k] or
                       isinstance(v,(int,float)) and not isinstance(v,bool) and isinstance(params[k],(int,float)) and not isinstance(params[k],bool) and v==params[k]
                       for v in values) for k,values in variant.items()):return True
        return False

    def predict(self,text):
        from data import normalize
        from numeric_grounding import ground_numbers
        from contact_extraction import extract_contact
        text=normalize(text)
        encoded=self.tokenizer.encode(text)
        ids=np.array([encoded.ids],dtype=np.int64)
        with self.lock:
            outputs=dict(zip([o.name for o in self.session.get_outputs()],self.session.run(None,{'input_ids':ids,'attention_mask':np.ones_like(ids),'token_type_ids':np.zeros_like(ids)})))
        def probs(name):
            logits=outputs[name][0].astype(np.float64);v=np.exp(logits-logits.max());return v/v.sum()
        scores=probs('frames');index=int(scores.argmax());frame=self.schema['frames'][index];action,keys=frame.split('|');params={}
        for key in filter(None,keys.split(',')):
            values=self.schema['slots'][key]
            allowed=self.schema.get('allowed',{}).get(frame,{}).get(key,values)
            indices=[values.index(v) for v in allowed];p=probs('slot_'+key)
            params[key]=json.loads(values[max(indices,key=lambda i:p[i])])
        params,_,_=ground_numbers(text,action,params,self.schema)
        if action=='CALL_CONTACT':params={'contact_query':extract_contact(text,self.rules) or ''}
        return {'text':text,'prediction':{'label':action,'title':self.titles[action],'score':round(float(scores[index]),4)},'slots':params,'model':self.version,'personal':False}
