"""Focused sunshade aliases, opening/closing and percentage regression."""
import asyncio,json
from pathlib import Path
import server
ALIASES=['سان شید','سانشید','کاور','پرده','پرده سقف','کاور سانروف','پرده سانروف','کاور سقف']
CASES=[]
for noun in ALIASES:
    for phrase,pos in [('{n} رو باز کن',100),('{n} باز',100),('باز کردن {n}',100),('{n} رو کامل باز کن',100),('{n} رو ببند',0),('{n} بسته',0),('بستن {n}',0),('{n} رو تا آخر ببند',0),('{n} رو تا نصفه باز کن',50),('{n} رو روی پنجاه درصد تنظیم کن',50),('{n} رو بیست درصد باز کن',20),('میزان باز بودن {n} رو بذار روی ۷۵ درصد',75),('{n} رو سی درصد باز کن',30)]:
        CASES.append(dict(text=phrase.format(n=noun),action='SET_SUNSHADE_POSITION',parameters={'position':pos}))
for text,action in [('سانروف رو باز کن','OPEN_SUNROOF'),('سانروف رو ببند','CLOSE_SUNROOF'),('کاور رو گرم کن','UNKNOWN'),('پرده سقف رو ماساژ بده','UNKNOWN'),('کاور گوشی رو باز کن','UNKNOWN')]:
    CASES.append(dict(text=text,action=action,parameters={}))
async def main():
    async with server.lifespan(server.app):
        if server.engine.get('project_sync'):server.engine['project_sync'].close()
        before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        trainer=server.engine['trainer']
        print(trainer.train(CASES[0]['text'],CASES[0]['action'],CASES[0]['parameters'],extra_records=CASES[1:],steps=650),flush=True)
        after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        Path('artifacts/sunshade-alias-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
        for stage,rows in [('before',before),('after',after)]:
            failures=[]
            for x in rows:
                e=x['case'];r=x['result'];p=r['prediction']
                if not(p['label']==e['action'] and p['parameters']==e['parameters'] and (r['accepted'] if e['action']!='UNKNOWN' else not r['accepted'])):failures.append(x)
            print(stage,'passed',len(rows)-len(failures),'/',len(rows),flush=True)
            if stage=='after':
                for f in failures:print(json.dumps(f,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
