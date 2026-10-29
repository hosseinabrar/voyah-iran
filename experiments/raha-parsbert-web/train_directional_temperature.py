"""Teach relative cabin temperature with explicit driver/passenger scope."""
import asyncio
import json
import shutil
from datetime import datetime
import server
from data import normalize
from train_cabin_relative import examples as cabin_examples

ALIASES={'driver':['راننده','چپ'],'passenger':['شاگرد','همراه','بغل','بغلی','راست']}

def examples():
    rows={}
    for zone,aliases in ALIASES.items():
        for alias in aliases:
            for noun in [alias,'سمت '+alias,'دمای '+alias,'دمای سمت '+alias]:
                for delta,words in [(1,['گرم','گرم تر','گرمتر','گرم‌تر']),(-1,['سرد','سرد تر','سردتر','سرد‌تر','خنک','خنک تر','خنکتر','خنک‌تر'])]:
                    for word in words:
                        for marker in ['',' رو',' را']:
                            text=normalize(f'{noun}{marker} {word} کن')
                            rows[text]=dict(text=text,action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':zone,'delta':delta})
    return list(rows.values())

async def main():
    async with server.lifespan(server.app):
        trainer=server.engine['trainer']
        shutil.copy2(trainer.path,trainer.path.with_name('before-directional-temperature-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors'))
        rows=examples()
        for r in rows:
            r['parameters']['delta']=next(json.loads(v) for v in trainer.model.schema['slots']['delta'] if json.loads(v)==r['parameters']['delta'])
        merged={normalize(r['text']):r for r in trainer.rows}
        merged.update({r['text']:r for r in rows})
        last=rows[-1];merged.pop(last['text'])
        print('TRAIN',len(rows),'new examples; previous',len(trainer.rows),flush=True)
        trainer.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()))
        probes=[dict(r,group='direct') for r in rows]+[dict(r,group='cabin-regression') for r in cabin_examples()]
        for zone,aliases in ALIASES.items():
            for alias in aliases:
                for word,delta in [('گرم',1),('گرم تر',1),('سرد',-1),('سردتر',-1),('خنک',-1),('خنک تر',-1)]:
                    probes.append(dict(text=f'رها لطفا دمای سمت {alias} رو {word} کن',action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':zone,'delta':delta},group='holdout'))
        checks=[]
        for r in probes:
            a=server.predict(r['text']);exact=a['prediction']['label']==r['action'] and a['slots']==r['parameters']
            checks.append(dict(text=r['text'],group=r['group'],exact=exact,ok=exact and a['accepted'] and round(a['prediction']['score']*100)==100,expected=r['parameters'],actual=a['slots'],action=a['prediction']['label'],score=a['prediction']['score'],reason=a['reason']))
        groups={g:{'total':sum(r['group']==g for r in checks),'exact':sum(r['group']==g and r['exact'] for r in checks),'passed':sum(r['group']==g and r['ok'] for r in checks)} for g in ['direct','cabin-regression','holdout']}
        (server.ART/'directional-temperature-report.json').write_text(json.dumps({'groups':groups,'checks':checks},ensure_ascii=False,indent=2))
        print(json.dumps(groups),flush=True)
        print(json.dumps([r for r in checks if not r['ok']],ensure_ascii=False),flush=True)
        if server.engine.get('project_sync'):server.engine['project_sync'].request()

if __name__=='__main__':asyncio.run(main())
