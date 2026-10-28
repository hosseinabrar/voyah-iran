"""Append-only tester feedback. No model training or global overrides."""
import hashlib,hmac,json,re,secrets,threading,time,unicodedata
from pathlib import Path

def normalize(text):
    return re.sub(r'\s+',' ',text.replace('\u200c',' ').replace('ي','ی').replace('ك','ک')).strip()

class Store:
    def __init__(self,root,secret):
        if len(secret)<32:raise ValueError('Session secret must contain at least 32 characters')
        self.root=Path(root);self.root.mkdir(parents=True,exist_ok=True)
        self.secret=secret.encode();self.lock=threading.Lock()
        (self.root/'sessions').mkdir(exist_ok=True)

    def token(self,device):
        return device+'.'+hmac.new(self.secret,device.encode(),hashlib.sha256).hexdigest()

    def identity(self,token):
        device,_,signature=(token or '').partition('.')
        if not re.fullmatch('[a-f0-9]{32}',device) or not hmac.compare_digest(self.token(device),token or ''):return None
        path=self.root/'sessions'/(device+'.json')
        return json.loads(path.read_text()) if path.exists() else None

    def register(self,name):
        name=unicodedata.normalize('NFC',name).strip()
        if not 1<=len(name)<=60 or any(unicodedata.category(c).startswith('C') for c in name):raise ValueError('نام معتبر وارد کن')
        slug=re.sub(r'[^\w\-]+','-',name,flags=re.UNICODE).strip('-_')[:60] or 'tester'
        folder=slug+'-'+hashlib.sha256(name.encode()).hexdigest()[:10]
        device=secrets.token_hex(16);identity={'device':device,'name':name,'folder':folder}
        with self.lock:
            (self.root/folder).mkdir(exist_ok=True)
            (self.root/'sessions'/(device+'.json')).write_text(json.dumps(identity,ensure_ascii=False))
            (self.root/folder/(device+'.jsonl')).touch()
        return self.token(device),identity

    def append(self,identity,row):
        with self.lock:
            path=self.root/identity['folder']/(identity['device']+'.jsonl')
            if path.stat().st_size>10*1024*1024:raise ValueError('ظرفیت اصلاحات این دستگاه پر شده است')
            record={**row,'id':secrets.token_hex(16),'device':identity['device'],'tester':identity['name'],'time':time.time(),'review':'pending'}
            with path.open('a',encoding='utf-8') as out:out.write(json.dumps(record,ensure_ascii=False)+'\n')
        return record

    def override(self,identity,text,version):
        path=self.root/identity['folder']/(identity['device']+'.jsonl')
        with self.lock:
            lines=path.read_text().splitlines()
        for line in reversed(lines):
            row=json.loads(line)
            if row['text']==normalize(text) and row['model']==version:return row
        return None
