"""Freeze authored samples and measured results; never includes model checkpoints."""
from pathlib import Path
import json,csv,shutil,sqlite3
src=Path('artifacts/batch08');dst=Path('regression-contracts/batch08');dst.mkdir(exist_ok=True)
r=json.loads((src/'report.json').read_text());report=r['batches']['8'];report['fresh_process_audit']=r['fresh_process_audit'];report['notes']='Synthetic text only. Former holdout was subsequently trained. Final results are in-sample, not generalization accuracy. Exact semantic reviews apply only to personal web; Android intent model is synchronized separately.'
for n in ['samples','baseline','before-holdout-training','final']:shutil.copy2(src/'8'/f'{n}.json',dst/f'{n}.json')
a=json.loads((dst/'baseline.json').read_text());b=json.loads((dst/'final.json').read_text())
fields=['text','group','policy','kind','initial_split','expected_action','expected_parameters','before_action','before_parameters','before_score','before_unsafe','before_pass','after_action','after_parameters','after_score','after_executed','after_unsafe','after_pass']
with (dst/'comparison.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(fields)
 for x,y in zip(a,b):w.writerow([x['text'],x['group'],x['policy'],x.get('kind',''),x['split'],x['action'],json.dumps(x['parameters'],ensure_ascii=False),x['predicted'],json.dumps(x['slots'],ensure_ascii=False),x['score'],x['unsafe'],x['passed'],y['predicted'],json.dumps(y['slots'],ensure_ascii=False),y['score'],y['executed'],y['unsafe'],y['passed']])
with sqlite3.connect(src/'before-evidence.sqlite3') as c:last=c.execute('select coalesce(max(id),0) from reviews').fetchone()[0]
reviews=[]
with sqlite3.connect('artifacts/evidence-feedback.sqlite3') as c:
 for target,allowed,note in c.execute('select target,allowed,note from reviews where id>? order by id',(last,)):
  text,action,params=json.loads(target);reviews.append(dict(text=text,action=action,parameters=params,allowed=bool(allowed),note=note))
(dst/'semantic-feedback.json').write_text(json.dumps(reviews,ensure_ascii=False,indent=2))
(dst/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('intent',len(report['intent_examples']),'semantic',len(report['semantic_examples']),'reviews',len(reviews))
