"""Install only a parity-checked immutable inference snapshot, never training data."""
import hashlib,json,shutil,os,tempfile
from export_android import OUT,ROOT
from speech_extras import WORDS,PHRASES

def atomic_copy(source,target):
    fd,name=tempfile.mkstemp(prefix='.raha-sync-',dir=target.parent)
    os.close(fd)
    try:
        shutil.copyfile(source,name)
        os.replace(name,target)
    finally:
        if os.path.exists(name):os.unlink(name)

def atomic_text(target,text):
    fd,name=tempfile.mkstemp(prefix='.raha-sync-',dir=target.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as out:out.write(text)
        os.replace(name,target)
    finally:
        if os.path.exists(name):os.unlink(name)

def main():
    report=json.loads((OUT/'parity-report.json').read_text())
    manifest=json.loads((OUT/'provenance.json').read_text())
    assert not report['failures'] and report['feedback_sha256']==manifest['feedback_sha256']
    graph=OUT/manifest.get('graph','model.fp32.onnx')
    assert report['model_sha256']==hashlib.sha256(graph.read_bytes()).hexdigest()
    target=ROOT.parents[1]/'voyah-iran/raha-runtime/src/main/assets/raha-intent'
    target.mkdir(parents=True,exist_ok=True)
    for name in ['schema.json','contact-rules.json']:
        atomic_copy(OUT/name,target/name)
    atomic_copy(OUT/'tokenizer/vocab.txt',target/'vocab.txt')
    atomic_copy(graph,target/'model.onnx')
    manifest['model_sha256']=hashlib.sha256((target/'model.onnx').read_bytes()).hexdigest()
    manifest.setdefault('precision','fp32')
    atomic_text(target/'manifest.json',json.dumps(manifest,indent=2))
    grammar=json.loads((ROOT/'speech-grammar.json').read_text())['phrases']
    grammar=list(dict.fromkeys(grammar+WORDS+PHRASES))
    resource=ROOT.parents[1]/'voyah-iran/raha-core/src/main/resources/raha-speech-phrases.txt'
    resource.parent.mkdir(parents=True,exist_ok=True)
    atomic_text(resource,'\n'.join(grammar)+'\n')
    print('Installed verified snapshot:',manifest['feedback_count'],'feedback records; no training records packaged')

if __name__=='__main__':main()
