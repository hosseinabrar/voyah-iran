"""Targeted OEM sunroof ventilation teaching; no full-corpus verification."""
import asyncio
import json
from pathlib import Path
import server
CASES = [dict(text=t, action='VENT_WINDOW', parameters={'window':'sunroof'}) for t in [
'حالت تهویه سانروف', 'تهویه سانروف رو فعال کن', 'سانروف رو بذار روی حالت تهویه',
'سانروف رو بذار تو حالت تهویه', 'سانروف رو ونت کن', 'ونت سانروف رو فعال کن',
'سانروف حالت تهویه', 'سانروف تهویه', 'سانروف رو برای تهویه باز کن',
'حالت تهویه سانروف رو روشن کن', 'سانروف را در حالت تهویه قرار بده',
'سانروف رو به حالت تهویه ببر', 'لطفا تهویه سانروف رو فعال کن',
'سانروف ونت', 'تهویه سانروف روشن', 'سانروف رو روی تهویه بذار',
]] + [dict(text=t, action=a, parameters={}) for t,a in [
('سانروف رو باز کن','OPEN_SUNROOF'),('سانروف رو ببند','CLOSE_SUNROOF'),
('تهویه سانروف رو خاموش کن','CLOSE_SUNROOF'),
('از حالت تهویه سانروف خارج شو و سانروف رو ببند','CLOSE_SUNROOF'),
('سانروف رو گرم کن','UNKNOWN'),('تهویه پیتزا رو فعال کن','UNKNOWN'),
('سانروف رو تهویه نکن','UNKNOWN'),('تهویه سانروف روشنه؟','UNKNOWN')]]
for target, phrases in {
'all': ['تهویه شیشه ها', 'شیشه ها رو ونت کن', 'شیشه ها رو بذار تو حالت تهویه', 'حالت تهویه شیشه ها رو فعال کن', 'پنجره ها رو برای تهویه باز کن', 'شیشه ها حالت تهویه'],
'allWithSunroof': ['تهویه شیشه ها و سانروف', 'سانروف و شیشه ها رو ونت کن', 'شیشه ها و سانروف رو بذار تو حالت تهویه', 'حالت تهویه شیشه ها و سانروف رو فعال کن', 'پنجره ها و سانروف رو برای تهویه باز کن', 'سانروف و پنجره ها حالت تهویه', 'تهویه سانروف و شیشه ها', 'هم شیشه ها هم سانروف رو ونت کن', 'تهویه شیشه ها و سانروف رو روشن کن'],
}.items():
    CASES.extend(dict(text=t,action='VENT_WINDOW',parameters={'window':target}) for t in phrases)
async def main():
    async with server.lifespan(server.app):
        sync=server.engine.get('project_sync')
        if sync: sync.close()
        before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        trainer=server.engine['trainer']
        print(trainer.train(CASES[0]['text'],CASES[0]['action'],CASES[0]['parameters'],extra_records=CASES[1:],steps=650),flush=True)
        after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
        Path('artifacts/sunroof-vent-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
        for row in after: print(json.dumps(row,ensure_ascii=False),flush=True)
if __name__=='__main__': asyncio.run(main())
