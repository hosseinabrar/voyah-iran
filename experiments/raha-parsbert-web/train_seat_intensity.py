"""Teach explicit seat intensity, preserving the existing feedback corpus."""
import asyncio
import json
import shutil
from datetime import datetime
import server
from data import normalize
from train_cabin_relative import examples as cabin_examples
from train_directional_temperature import examples as directional_examples

def examples():
    rows={}
    for feature,names in [('HEATING',['گرمکن صندلی','گرم کن صندلی','گرم‌کن صندلی']),('VENTILATION',['سردکن صندلی','سرد کن صندلی','سرد‌کن صندلی','تهویه صندلی'])]:
        for name in names:
            for seat,suffix in [('driver',''),('driver',' راننده'),('driver',' چپ'),('passenger',' شاگرد'),('passenger',' همراه'),('passenger',' بغل'),('passenger',' راست')]:
                for prefix in ['شدت ','سطح ']:
                    noun=prefix+name+suffix
                    for ending in ['رو کم کن','را کم کن','رو یک سطح کم کن','رو یه پله کم کن']:
                        text=normalize(noun+' '+ending)
                        rows[text]=dict(text=text,action='ADJUST_SEAT_'+feature,parameters={'seat':seat,'delta':-1})
                    for ending in ['رو بذار روی یک','رو بذار رو ۱','رو روی یک تنظیم کن','را روی 1 تنظیم کن']:
                        text=normalize(noun+' '+ending)
                        rows[text]=dict(text=text,action='SET_SEAT_'+feature,parameters={'seat':seat,'enabled':True,'level':1})
    return list(rows.values())

async def main():
    async with server.lifespan(server.app):
        trainer=server.engine['trainer']
        shutil.copy2(trainer.path,trainer.path.with_name('before-seat-intensity-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors'))
        rows=examples();merged={normalize(r['text']):r for r in trainer.rows}
        merged.update({r['text']:r for r in rows});last=rows[-1];merged.pop(last['text'])
        print('TRAIN',len(rows),'previous',len(trainer.rows),flush=True)
        trainer.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()))
        probes=[dict(r,group='direct') for r in rows]
        probes += [dict(r,text='لطفا '+r['text'],group='holdout') for r in rows[::8]]
        probes += [dict(r,group='temperature-regression') for r in cabin_examples()+directional_examples()]
        checks=[]
        for r in probes:
            a=server.predict(r['text']);exact=a['prediction']['label']==r['action'] and a['slots']==r['parameters']
            checks.append(dict(text=r['text'],group=r['group'],exact=exact,ok=exact and a['accepted'] and round(a['prediction']['score']*100)==100,expected=r['parameters'],actual=a['slots'],action=a['prediction']['label'],score=a['prediction']['score'],reason=a['reason']))
        groups={g:{'total':sum(r['group']==g for r in checks),'exact':sum(r['group']==g and r['exact'] for r in checks),'passed':sum(r['group']==g and r['ok'] for r in checks)} for g in ['direct','holdout','temperature-regression']}
        (server.ART/'seat-intensity-report.json').write_text(json.dumps({'groups':groups,'checks':checks},ensure_ascii=False,indent=2))
        print(json.dumps(groups),flush=True)
        print(json.dumps([r for r in checks if not r['ok']],ensure_ascii=False),flush=True)
        if server.engine.get('project_sync'):server.engine['project_sync'].request()

if __name__=='__main__':asyncio.run(main())
