"""Wiper/rain-sensor aliases: relative sensitivity and four absolute levels."""
import asyncio,json
from pathlib import Path
import server
CASES=[]
for noun in ['برف پاک کن','برفپاک کن','حساسیت برف پاک کن','سنسور باران','حساسیت سنسور باران']:
    for phrase,delta in [('{n} رو تندتر کن',1),('{n} رو کندتر کن',-1),('{n} رو زیاد کن',1),('{n} رو کم کن',-1),('{n} رو یک پله زیاد کن',1),('{n} رو یک پله کم کن',-1)]:
        CASES.append(dict(text=phrase.format(n=noun),action='ADJUST_WIPER_SENSITIVITY',parameters={'delta':delta}))
    for word,level in [('یک',1),('دو',2),('سه',3),('چهار',4),('حداکثر','max'),('حداقل','min')]:
        CASES.append(dict(text=f'{noun} رو روی {word} تنظیم کن',action='SET_WIPER_SENSITIVITY',parameters={'level':level}))
    CASES.append(dict(text=f'{noun} رو روی حداکثر بذار',action='SET_WIPER_SENSITIVITY',parameters={'level':'max'}))
for t in ['برف پاک کن رو گرم کن','سنسور باران رو ماساژ بده','برف پاک کن رو تندتر نکن']:
    CASES.append(dict(text=t,action='UNKNOWN',parameters={}))
from teach_suspension_steps import CASES as HEIGHT
from teach_sunshade_aliases import CASES as SHADE
from teach_sunroof_vent import CASES as VENT
CASES=list({r['text']:r for r in HEIGHT+SHADE+VENT+CASES}.values())
async def main():
    async with server.lifespan(server.app):
        if server.engine.get('project_sync'):server.engine['project_sync'].close()
        before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        trainer=server.engine['trainer']
        print(trainer.train(CASES[0]['text'],CASES[0]['action'],CASES[0]['parameters'],extra_records=CASES[1:],steps=650),flush=True)
        after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        Path('artifacts/wiper-sensitivity-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
        for stage,rows in [('before',before),('after',after)]:
            failures=[]
            for x in rows:
                e=x['case'];r=x['result'];p=r['prediction']
                if not(p['label']==e['action'] and p['parameters']==e['parameters'] and (r['accepted'] if e['action']!='UNKNOWN' else not r['accepted'])):failures.append(x)
            print(stage,'passed',len(rows)-len(failures),'/',len(rows),flush=True)
            if stage=='after':
                for f in failures:print(json.dumps(f,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
