import asyncio,json
from pathlib import Path
import server,project_sync
from teach_batch07 import OUT,evaluate,summary
from teach_batches import assess,train

def confident_wrong(r):
    return r['predicted']!='UNKNOWN' and round(r['score']*100)==100 and (r['policy']=='reject' or not r['exact'])
async def main():
    project_sync.ProjectSync.start=lambda self:None
    async with server.lifespan(server.app):
        tr=server.engine['trainer'];old=json.loads(Path('regression-contracts/batch01/samples.json').read_text())+json.loads(Path('regression-contracts/batch02/samples.json').read_text())
        rows=json.loads((OUT/'7/samples.json').read_text())
        for name in ['batch03','batch04','batch05','batch06']:
            rows+=json.loads((Path('regression-contracts')/name/'samples.json').read_text())
        for turn in range(4):
            prior=assess(old);fresh=evaluate(rows)
            failures=[r for r in prior if not r['passed']]
            newbad=[r for r in fresh if not r['passed'] or confident_wrong(r)]
            print('AUDIT',turn,'prior',sum(r['passed'] for r in prior),'new',summary(fresh),'confident_wrong',sum(confident_wrong(r) for r in fresh),flush=True)
            if not failures and not newbad:break
            if turn==3:break
            learning=[{k:r[k] for k in ['text','action','parameters']} for r in prior]
            for r in fresh:
                reject=r['policy']=='reject' or (r['policy']=='correct_or_reject' and not r['exact'])
                learning.append(dict(text=r['text'],action='UNKNOWN' if reject else r['action'],parameters={} if reject else r['parameters']))
            worst=min(failures+newbad,key=lambda r:r['score']);focus=next(r for r in learning if r['text']==worst['text'])
            train(tr,learning,focus,steps=500)
        report=json.loads((OUT/'report.json').read_text())
        report['fresh_process_audit']={'prior_count':1200,'prior_correct':sum(r['passed'] for r in prior),'new':summary(fresh),'wrong_candidates_at_100':sum(confident_wrong(r) for r in fresh)}
        for n,start,end in [('7',0,800)]:
            report['batches'][n]['after']=summary(fresh[start:end]);(OUT/n/'final.json').write_text(json.dumps(fresh[start:end],ensure_ascii=False,indent=2))
        (OUT/'combined-final.json').write_text(json.dumps(fresh,ensure_ascii=False,indent=2));(OUT/'prior-final.json').write_text(json.dumps(prior,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':asyncio.run(main())
