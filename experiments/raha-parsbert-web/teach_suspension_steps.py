"""Teach relative height separately from explicit absolute levels."""
import asyncio,json
from pathlib import Path
import server
CASES=[]
for noun in ['ارتفاع ماشین','ارتفاع خودرو','ارتفاع']:
    for phrase,delta in [('{n} رو بیار پایین',-1),('{n} رو پایین تر بیار',-1),('{n} رو کم کن',-1),('{n} رو یک پله کم کن',-1),('{n} رو ببر بالا',1),('{n} رو زیاد کن',1),('{n} رو یک پله ببر بالا',1),('{n} رو بالاتر ببر',1)]:
        CASES.append(dict(text=phrase.format(n=noun),action='ADJUST_SUSPENSION_HEIGHT',parameters={'delta':delta}))
    for phrase,level in [('{n} رو روی کم تنظیم کن','low'),('{n} رو بذار روی کم','low'),('{n} رو کامل بیار پایین','low'),('{n} رو روی متوسط بذار','medium'),('{n} رو روی زیاد تنظیم کن','high'),('{n} رو تا آخر ببر بالا','high')]:
        CASES.append(dict(text=phrase.format(n=noun),action='SET_SUSPENSION_HEIGHT',parameters={'level':level}))
for t in ['ارتفاع پیتزا رو بیار پایین','ارتفاع رو زیاد نکن','ارتفاع رو بالا ببر و پایین بیار']:
    CASES.append(dict(text=t,action='UNKNOWN',parameters={}))
# Owner-reviewed distinction: movement is relative; placing/setting is absolute.
for noun in ['ارتفاع','ارتفاع ماشین','ارتفاع خودرو']:
    for phrase,delta in [('{n} رو زیادتر کن',1),('{n} رو بالاتر ببر',1),('{n} رو کم کن',-1),('{n} رو کمتر کن',-1),('{n} رو پایین تر بیار',-1)]:
        CASES.append(dict(text=phrase.format(n=noun),action='ADJUST_SUSPENSION_HEIGHT',parameters={'delta':delta}))
    for phrase,level in [('{n} رو بذار رو کم','low'),('{n} رو تنظیم کن وسط','medium'),('{n} رو بذار رو متوسط','medium'),('{n} رو بذار رو زیاد','high'),('{n} رو بالا تنظیم کن','high'),('{n} رو پایین تنظیم کن','low'),('{n} رو بذار پایین','low'),('{n} رو بذار بالا','high'),('سطح {n} رو پایین بذار','low'),('سطح {n} رو بالا بذار','high')]:
        CASES.append(dict(text=phrase.format(n=noun),action='SET_SUSPENSION_HEIGHT',parameters={'level':level}))
for noun in ['ماشین','خودرو']:
    for phrase,delta in [('{n} رو ببر پایین تر',-1),('{n} رو بیار پایین تر',-1),('{n} رو ببر بالاتر',1),('{n} رو بیار بالاتر',1)]:
        CASES.append(dict(text=phrase.format(n=noun),action='ADJUST_SUSPENSION_HEIGHT',parameters={'delta':delta}))
CASES=list({r['text']:r for r in CASES}.values())
async def main():
    async with server.lifespan(server.app):
        if server.engine.get('project_sync'):server.engine['project_sync'].close()
        before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        trainer=server.engine['trainer']
        print(trainer.train(CASES[0]['text'],CASES[0]['action'],CASES[0]['parameters'],extra_records=CASES[1:],steps=650),flush=True)
        after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        Path('artifacts/suspension-owner-phrases-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
        for stage,rows in [('before',before),('after',after)]:
            failures=[]
            for x in rows:
                e=x['case'];r=x['result'];p=r['prediction']
                if not(p['label']==e['action'] and p['parameters']==e['parameters'] and (r['accepted'] if e['action']!='UNKNOWN' else not r['accepted'])):failures.append(x)
            print(stage,'passed',len(rows)-len(failures),'/',len(rows),flush=True)
            if stage=='after':
                for f in failures:print(json.dumps(f,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
