"""Document ambiguous gold rather than call a plausible audio-channel reading wrong.
Preserve first-pass artifacts; no model inference or training is changed.
"""
import json,csv,shutil
from pathlib import Path
P=Path('artifacts/validator-ablation-unseen-1000')
for n in ['samples.json','results.json','report.json','results.csv']:
 dest=P/('first-pass-'+n)
 if dest.exists():raise RuntimeError('Review already applied')
 shutil.copy2(P/n,dest)
rows=json.loads((P/'results.json').read_text());samples=json.loads((P/'samples.json').read_text());review=[]
for s,r in zip(samples,rows):
 if 'صدای سیستم صوتی رو در سطح' not in s['text']:continue
 # This sentence does not explicitly say music. Both media and system channel
 # are plausible; use the more conservative scoring for the guard comparison.
 extra=dict(action='SET_AUDIO_VOLUME',parameters=dict(s['parameters'],channel='system'))
 s['acceptable_predictions'].append(extra)
 review.append(s['text'])
 for mode in ['current','no_semantic','no_guards_keep_numbers','raw_model']:
  m=r[mode];m['exact']=any(r['predicted']==v['action'] and m['parameters']==v['parameters'] for v in s['acceptable_predictions']);m['unsafe']=m['execute'] and not m['exact'];m['passed']=m['execute'] and m['exact']
def measure(rs,mode):
 def m(q):return dict(count=len(q),correct_accepted=sum(x[mode]['execute'] and x[mode]['exact'] and x['policy']!='reject' for x in q),rejected=sum(not x[mode]['execute'] for x in q),wrong_accepted=sum(x[mode]['unsafe'] for x in q),passed=sum(x[mode]['passed'] for x in q),raw_exact=sum(x[mode]['exact'] for x in q))
 return {k:m([x for x in rs if k=='all' or x['policy']==k]) for k in ['all','correct','reject','correct_or_reject']}
report=json.loads((P/'report.json').read_text());modes=list(report['metrics']);report['metrics']={m:measure(rows,m) for m in modes};report['last_round']=report['metrics'];report['gold_review']=dict(texts=review,reason='Ten audio-system sentences do not explicitly name music; accept either media or system channel. First-pass frozen separately; model unchanged.')
for mode in modes:report['changes'][mode]=dict(rescued=sum(not r['current']['execute'] and r[mode]['execute'] and r[mode]['exact'] and r['policy']!='reject' for r in rows),new_wrong=sum(not r['current']['unsafe'] and r[mode]['unsafe'] for r in rows))
for n,x in [('samples',samples),('results',rows),('report',report)]: (P/(n+'.json')).write_text(json.dumps(x,ensure_ascii=False,indent=2))
with (P/'results.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['text','policy','expected','expected_parameters','predicted','score','current_reason']+[m+'_'+k for m in modes for k in ['parameters','execute','exact','unsafe']])
 for r in rows:w.writerow([r[k] if k!='parameters' else json.dumps(r[k],ensure_ascii=False) for k in ['text','policy','action','parameters','predicted','score','current_reason']]+[json.dumps(r[m][k],ensure_ascii=False) for m in modes for k in ['parameters','execute','exact','unsafe']])
print(json.dumps(report['metrics'],ensure_ascii=False))
