"""Owner-approved one-degree cabin requests; preserve existing feedback."""
import asyncio
import json
import shutil
from datetime import datetime
import server
from data import normalize

def examples():
    rows={}
    for noun in ['کابین','اتاق','ماشین']:
        for delta,words in [(1,['گرم','گرم تر','گرمتر','گرم‌تر']),(-1,['سرد','سرد تر','سردتر','سرد‌تر','خنک','خنک تر','خنکتر','خنک‌تر'])]:
            for word in words:
                for template in ['{n} {w} کن','{n} رو {w} کن','{n} را {w} کن','لطفا {n} رو {w} کن','رها {n} رو {w} کن']:
                    text=normalize(template.format(n=noun,w=word))
                    rows[text]=dict(text=text,action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':'front','delta':delta})
    return list(rows.values())

async def main():
    async with server.lifespan(server.app):
        trainer=server.engine['trainer']
        shutil.copy2(trainer.path,trainer.path.with_name('before-cabin-relative-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors'))
        rows=examples()
        for r in rows:
            r['parameters']['delta']=next(json.loads(v) for v in trainer.model.schema['slots']['delta'] if json.loads(v)==r['parameters']['delta'])
        merged={normalize(r['text']):r for r in trainer.rows}
        merged.update({r['text']:r for r in rows})
        last=rows[-1];merged.pop(last['text'])
        print('TRAIN',len(rows),'examples; prior feedback',len(trainer.rows),flush=True)
        trainer.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()))
        checks=[]
        probes=rows+[dict(r,text='میشه '+r['text']+' لطفا') for r in rows if r['text'].split()[0] in ['کابین','اتاق','ماشین']]
        for r in probes:
            actual=server.predict(r['text'])
            ok=actual['prediction']['label']==r['action'] and actual['slots']==r['parameters'] and actual['accepted'] and round(actual['prediction']['score']*100)==100
            checks.append(dict(text=r['text'],ok=ok,expected=r['parameters'],actual=actual['slots'],action=actual['prediction']['label'],score=actual['prediction']['score'],reason=actual['reason']))
        report={'passed':sum(r['ok'] for r in checks),'total':len(checks),'checks':checks}
        (server.ART/'cabin-relative-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        print(json.dumps({k:v for k,v in report.items() if k!='checks'}),flush=True)
        print(json.dumps([r for r in checks if not r['ok']],ensure_ascii=False),flush=True)
        if server.engine.get('project_sync'):server.engine['project_sync'].request()

if __name__=='__main__':asyncio.run(main())
