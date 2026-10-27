"""Teach supported scene names and activation/deactivation, preserving feedback."""
import asyncio,json,shutil
from datetime import datetime,timezone
import server

ALIASES={
 'camp':['کمپ','کمپینگ'],
 'carWash':['کارواش','شستشو','شستشوی ماشین'],
 'babyCare':['مراقبت از کودک','مراقبت کودک','کودک','مراقبت از بچه','بیبی کر'],
 'nap':['استراحت','چرت'],
 'party':['مهمانی','مهمونی','پارتی'],
 'powerKeep':['حفظ برق','نگهداری برق'],
 'quickWarm':['گرمایش سریع'],
}
ON=['سناریو {}','حالت {}','سناریو {} رو فعال کن','ماشین رو بذار تو سناریو {}',
    'ماشین رو ببر تو حالت {}','رها سناریوی {} رو روشن کن','{} رو فعال کن',
    'می تونی سناریو {} رو فعال کنی','حالت {} رو روشن کن']
OFF=['سناریو {} رو خاموش کن','از حالت {} خارج شو','سناریوی {} رو غیر فعال کن']

async def main():
 async with server.lifespan(server.app):
    trainer=server.engine['trainer']
    shutil.copy2(trainer.path,trainer.path.with_name('before-scenes-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors'))
    rows=[]
    def add(text,action,scene):
        rows.append(dict(text=text,action=action,parameters={'scene':scene},time=datetime.now(timezone.utc).isoformat()))
    for scene,aliases in ALIASES.items():
        for alias in aliases:
            for template in ON:add(template.format(alias),'ACTIVATE_SCENE',scene)
            for template in OFF:add(template.format(alias),'DEACTIVATE_SCENE','current')
    # Bare unambiguous names requested by the user. Avoid generic "کودک"
    # or "برق" alone triggering a scene outside a scene command.
    for alias,scene in [('کارواش','carWash'),('کمپ','camp'),('کمپینگ','camp'),('مراقبت از کودک','babyCare')]:
        add(alias,'ACTIVATE_SCENE',scene)
    extras={r['text']:r for r in trainer.rows+rows};last=rows[-1];extras.pop(last['text'],None)
    print('Training',len(rows),'scene examples, retaining',len(trainer.rows),'feedback',flush=True)
    outcome=trainer.train(last['text'],last['action'],last['parameters'],extra_records=list(extras.values()))
    cases=[]
    for scene,aliases in ALIASES.items():
        for alias in aliases:
            for template,action,target in [
                ('رها لطفا ماشین رو بذار تو سناریو {}','ACTIVATE_SCENE',scene),
                ('سناریوی {} رو برام فعال کن','ACTIVATE_SCENE',scene),
                ('لطفا از سناریو {} خارج شو','DEACTIVATE_SCENE','current')]:
                text=template.format(alias);r=server.predict(text)
                cases.append(dict(text=text,correct=r['accepted'] and r['prediction']['label']==action and r['slots']=={'scene':target},actual=r['slots'],reason=r['reason']))
    report=dict(training=outcome,total=len(cases),passed=sum(c['correct'] for c in cases),cases=cases)
    (trainer.path.parent/'scenes-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='cases'},ensure_ascii=False),flush=True)
    print(json.dumps([c for c in cases if not c['correct']],ensure_ascii=False),flush=True)

if __name__=='__main__':asyncio.run(main())
