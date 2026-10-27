"""Offline temperature practice; stages feedback without overwriting live corrections."""
import asyncio,json,hashlib
from server import app,lifespan,engine,ART,predict
from practice_data import number_name

def examples():
    rows=[]
    for zone,noun in [('front','دمای کابین'),('driver','دمای سمت راننده'),('passenger','دمای سمت شاگرد')]:
        for i in range(32,65):
            n=i/2
            for wording in [f'{noun} رو بذار روی {number_name(n)}',f'{noun} رو روی {n:g} درجه تنظیم کن']:
                rows.append(dict(text=wording,action='SET_CABIN_TEMPERATURE',parameters={'zone':zone,'degreesC':n}))
        for i in range(1,33):
            n=i/2
            for sign,verb in [(-1,'کم'),(1,'زیاد')]:
                for wording in [f'{noun} رو {number_name(n)} درجه {verb} کن',f'{noun} رو {n:g} درجه {verb} کن']:
                    rows.append(dict(text=wording,action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':zone,'delta':n*sign}))
    return rows

async def main():
    async with lifespan(app):
        trainer=engine['trainer'];original=trainer.path
        digest=hashlib.sha256(original.read_bytes()).hexdigest()
        trainer.path=ART/'temperature-feedback-candidate.safetensors'
        rows=examples()
        for sign,verb in [(1,'زیاد'),(-1,'کم')]:
            rows.append(dict(text=f'دمای کولر رو یکی {verb} کن',action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':'front','delta':sign}))
        # Preserve recent human corrections in the supervised batch, not merely its log.
        generated={r['text'] for r in rows}
        rows=[r for r in trainer.rows[-128:] if r['text'] not in generated]+rows
        # Canonical JSON spelling must match existing output classes.
        for row in rows:
            for k,v in row.get('parameters',{}).items():
                if k not in trainer.model.schema['slots']:continue
                row['parameters'][k]=next(json.loads(x) for x in trainer.model.schema['slots'][k] if json.loads(x)==v)
        final=rows[-1]
        print('Training',len(rows),'temperature sentences',flush=True)
        trainer.train(final['text'],final['action'],final['parameters'],extra_records=rows[:-1])
        probes=[]
        for n in range(16,33):
            probes.append((f'دمای کابین رو روی {number_name(n)} تنظیم کن','SET_CABIN_TEMPERATURE',{'zone':'front','degreesC':n}))
        for n in range(1,17):
            for sign,verb in [(1,'بیشتر'),(-1,'کمتر')]:
                probes.append((f'دمای کابین رو {n} درجه {verb} کن','ADJUST_CABIN_TEMPERATURE',{'zone':'front','delta':n*sign}))
        results=[]
        for text,action,params in probes:
            r=predict(text);results.append(dict(text=text,expected=params,actual=r['slots'],accepted=r['accepted'],ok=r['prediction']['label']==action and r['slots']==params))
        report={'count':len(results),'correct':sum(r['ok'] for r in results),'results':results,'source_sha256':digest,'source':str(original)}
        (ART/'temperature-practice-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        print('Audit',report['correct'],'/',report['count'],flush=True)

if __name__=='__main__':asyncio.run(main())
