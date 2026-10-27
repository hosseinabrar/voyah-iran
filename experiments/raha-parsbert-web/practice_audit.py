"""Compare baseline and selected candidate on untouched colloquial language."""
import json
import sys
import torch
from transformers import AutoTokenizer
from full_model import FullModel
from practice_train import ROOT,OUT,evaluate,summary
from practice_unseen import UNSEEN
from data import normalize
from validation import consistency_reason

def main():
    torch.set_num_threads(3)
    data=json.loads((OUT/'dataset.json').read_text())
    trained=data['train']+data['dev']+json.loads((OUT/'corrections.json').read_text())
    seen={normalize(r['text']) for r in trained}
    rows=[dict(text=t,action=a,parameters=p) for t,a,p in UNSEEN]
    assert not seen.intersection(normalize(r['text']) for r in rows)
    reports={}
    for name,path in [('baseline',ROOT/'artifacts/full-intent'),('candidate',OUT/'refined')]:
        model=FullModel.load(path);tok=AutoTokenizer.from_pretrained(path,local_files_only=True)
        reports[name]=evaluate(model,tok,rows,'cpu')
        print(name,summary(reports[name]),flush=True)
    (OUT/'unseen-colloquial-audit.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2))
    for r in reports['candidate']['results']:
        if not r['exact_correct']:print(r['text'],'expected',r['parameters'],'got',r['predicted'])

if __name__=='__main__':main()
