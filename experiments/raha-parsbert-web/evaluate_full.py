"""Evaluate saved checkpoint, including independent OOD/negation probes."""
import json
import time
from pathlib import Path
import torch
from transformers import AutoTokenizer
from full_model import FullModel,allowed_values
from data import normalize

ROOT=Path(__file__).resolve().parent
EXTRA=[
    ('چراغ راهروی خونه رو خاموش کن','UNKNOWN',{}),
    ('فردا صبح هوا سرد میشه','UNKNOWN',{}),
    ('دیروز بابام شیشه رو باز کرد','UNKNOWN',{}),
    ('هشدار نقطه کور چرا خاموش شده','UNKNOWN',{}),
    ('درهای ماشین رو قفل نکن','UNKNOWN',{}),
    ('گرمکن صندلی را روشن نکن','UNKNOWN',{}),
    ('آیا شیشه شاگرد بسته است','UNKNOWN',{}),
    ('برای من تاکسی بگیر','UNKNOWN',{}),
    ('یه پیتزا می خوام','UNKNOWN',{}),
    ('من می خوام برم خرید','UNKNOWN',{}),
    ('آره تماس بگیر','CONFIRM_CALL',{}),
    ('نه اشتباهه','CANCEL_DIALOG',{}),
    ('رها لطف می کنی با سارا تماس بگیری','CALL_CONTACT',{}),
    ('میشه درجه حرارت کابین رو روی ۲۴ بذاری','SET_CABIN_TEMPERATURE',{'zone':'front','degreesC':24.0}),
]

def main():
    torch.set_num_threads(4)
    path=ROOT/'artifacts/full-intent'
    data=json.loads((ROOT/'dataset.json').read_text())
    schema=json.loads((path/'schema.json').read_text())
    schema['allowed']=allowed_values(data['train'])
    (path/'schema.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2))
    model=FullModel.load(path);tok=AutoTokenizer.from_pretrained(path,local_files_only=True)
    seen={normalize(r['text']) for r in data['train']}
    tests=data['evaluation']+[dict(text=t,action=a,parameters=p,source='independent-manual-probe') for t,a,p in EXTRA]
    assert not seen.intersection(normalize(r['text']) for r in tests)
    results=[]
    with torch.inference_mode():
        for r in tests:
            start=time.perf_counter()
            inputs=tok(normalize(r['text']),return_tensors='pt',max_length=48,truncation=True)
            logits,slots=model(**inputs)
            predicted=model.decode(logits,slots)[0][0]
            results.append({**r,'predicted':predicted,'action_correct':r['action']==predicted['action'],'exact_correct':r['action']==predicted['action'] and r['parameters']==predicted['parameters'],'latency_ms':round((time.perf_counter()-start)*1000)})
    report=json.loads((path/'report.json').read_text())
    report.update(results=results,action_accuracy=sum(r['action_correct'] for r in results)/len(results),exact_accuracy=sum(r['exact_correct'] for r in results)/len(results),evaluation_count=len(results),negative_count=sum(r['action']=='UNKNOWN' for r in results),evaluation='Synthetic unseen phrasing + small manual negative probe. Not a representative real-world benchmark; raw model accuracy before guards.')
    (path/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    (ROOT/'full-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False,indent=2))
    print('FAILURES:')
    for r in results:
        if not r['exact_correct']:print(r['text'],r['action'],r['parameters'],'=>',r['predicted'])

if __name__=='__main__':main()
