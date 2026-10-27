"""Supervised aliases for the active web model; never changes Android assets."""
import asyncio
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
import server

ALIASES = {
    'comfort': ['کامفورت','آسایش','راحت','راحتی','comfort','Comfort'],
    'eco': ['اقتصادی','اکو','اکونومی','کم مصرف','eco','economy','Eco'],
    'snow': ['برفی','برف','اسنو','زمستان','زمستون','زمستانی','snow','winter'],
    'outing': ['آفرود','آف رود','اوتینگ','خاکی','offroad','off road','outing'],
    'personal': ['شخصی','پرسونال','ایندیویجوال','شخصی سازی','individual','personal'],
    'sport': ['اسپورت','ورزشی','sport','Sport'],
}
TEMPLATES = ['ماشین رو بذار تو حالت {}','حالت رانندگی رو {} کن',
             'مود ماشین رو بذار روی {}','حالت {} رو فعال کن',
             'رها برو روی مود {}','می تونی حالت رانندگی رو {} کنی',
             'مود رانندگی {} باشه','{}',
             'لطفا حالت رانندگی ماشین رو به {} تغییر بده',
             'می خوام با حالت {} رانندگی کنم']
HOLDOUT = ['رها لطفا مود رانندگی رو بذار روی {}',
           'حالت رانندگی رو تغییر بده به {}']

async def main():
    async with server.lifespan(server.app):
        trainer=server.engine['trainer']
        backup=trainer.path.with_name('before-drive-modes-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors')
        shutil.copy2(trainer.path,backup)
        rows=[dict(text=t.format(alias),action='SET_DRIVE_MODE',parameters={'mode':mode},
                   time=datetime.now(timezone.utc).isoformat())
              for mode,aliases in ALIASES.items() for alias in aliases for t in TEMPLATES]
        # Include all prior corrections, not just the trainer's recent window.
        extras={r['text']:r for r in trainer.rows+rows}
        last=rows[-1];extras.pop(last['text'],None)
        print('Training',len(rows),'new examples; preserving',len(trainer.rows),'prior feedback',flush=True)
        result=trainer.train(last['text'],last['action'],last['parameters'],extra_records=list(extras.values()))
        checks=[]
        for mode,aliases in ALIASES.items():
            for alias in aliases:
                for template in HOLDOUT:
                    text=template.format(alias);r=server.predict(text)
                    checks.append(dict(text=text,expected=mode,actual=r['slots'],accepted=r['accepted'],
                        correct=r['prediction']['label']=='SET_DRIVE_MODE' and r['slots']=={'mode':mode} and r['accepted']))
        report={'training':result,'passed':sum(r['correct'] for r in checks),'total':len(checks),'cases':checks}
        (trainer.path.parent/'drive-modes-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        print(json.dumps({k:v for k,v in report.items() if k!='cases'},ensure_ascii=False),flush=True)
        print(json.dumps([r for r in checks if not r['correct']],ensure_ascii=False),flush=True)

if __name__=='__main__':asyncio.run(main())
