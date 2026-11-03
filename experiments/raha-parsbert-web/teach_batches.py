"""Offline supervised practice; all final samples are training samples.
No ASR/CAN execution. Original baselines remain immutable in prior artifacts.
"""
import asyncio,json,shutil,sqlite3,random,time
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from data import normalize
from batch02_samples import samples
OUT=Path('artifacts/batches-01-02-completion');OUT.mkdir(exist_ok=True)

def assess(rows):
    representatives={}
    for r in rows:
        if r['valid']:representatives.setdefault(r['group'],r)
    out=[]
    for i,r in enumerate(rows):
        p=server.predict(r['text']);exact=p['prediction']['label']==r['action'] and p['slots']==r['parameters']
        context_ok=(r.get('context')=='pending_call_confirmation' and exact and p['evidence']['model_reason']=='پاسخ مربوط به گفتگوی تماس است؛ این صفحه تماس در انتظاری ندارد' and p['evidence']['allowed'])
        execute=(p['accepted'] or context_ok) and round(p['prediction']['score']*100)==100
        q=r if r['valid'] else representatives[r['group']]
        semantic=ef.evaluate(r['text'],q['action'],q['parameters'])['allowed']==r['valid']
        out.append(dict(**r,exact=exact,semantic=semantic,passed=(exact and execute) if r['valid'] else not execute,predicted=p['prediction']['label'],slots=p['slots'],score=p['prediction']['score'],reason=p['reason'],contextual=context_ok))
    return out

def summary(rows):
    return {name:dict(count=len(rs),intent_exact=sum(r['exact'] for r in rs),semantic_correct=sum(r['semantic'] for r in rs),end_to_end=sum(r['passed'] for r in rs)) for name,rs in [('all',rows),('valid',[r for r in rows if r['valid']]),('invalid',[r for r in rows if not r['valid']]),('original_holdout',[r for r in rows if r['split']=='holdout'])]}

def teach_semantics(rows,all_rows,ledger):
    reps={}
    for r in all_rows:
        if r['valid']:reps.setdefault(r['group'],r)
    for r in rows:
        q=r if r['valid'] else reps[r['group']]
        pairs=[(q['action'],q['parameters'])]
        if not r['valid'] and r.get('predicted','UNKNOWN')!='UNKNOWN':pairs.append((r['predicted'],r['slots']))
        for action,params in pairs:
            if ef.evaluate(r['text'],action,params)['allowed']!=r['valid']:
                ef.save(r['text'],action,params,r['valid'],'تمرین دسته ۱ و ۲: '+('برداشت معتبر' if r['valid'] else 'جمله غیردستوری، متناقض یا بی معنی'))
                ledger.add(r['text'])

def train(tr,rows,worst,steps=1000):
    merged={normalize(r['text']):r for r in tr.rows}
    for r in rows:
        merged[r['text']]={k:r[k] for k in ['text','action','parameters']}
    # Contact correction includes learning reusable name-span patterns.
    from contact_extraction import learn_pattern
    for r in rows:
        if r['action']=='CALL_CONTACT':
            rule=learn_pattern(r['text'],r['parameters']['contact_query'])
            if rule not in tr.contact_rules:tr.contact_rules.append(rule)
    last=merged.pop(worst['text'])
    print('TRAIN',len(rows),'replay',len(merged),'focus',last['text'],flush=True)
    tr.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()),steps=steps)

async def main():
    project_sync.ProjectSync.start=lambda self:None
    async with server.lifespan(server.app):
        tr=server.engine['trainer']
        if not (OUT/'before.safetensors').exists():shutil.copy2(tr.path,OUT/'before.safetensors')
        if not (OUT/'before-evidence.sqlite3').exists():
            with ef.connect() as src:
                with sqlite3.connect(OUT/'before-evidence.sqlite3') as dest:src.backup(dest)
        b1=json.loads(Path('regression-contracts/batch01/samples.json').read_text());b2=samples()
        (OUT/'batch02-samples.json').write_text(json.dumps(b2,ensure_ascii=False,indent=2))
        prior=[r for r in tr.rows if 'parameters' in r and r['action']!='CALL_CONTACT'];prior=random.Random(310).sample(prior,min(200,len(prior)))
        def regression():
            vals=[]
            for r in prior:
                p=server.predict(r['text']);vals.append(p['prediction']['label']==r['action'] and p['slots']==r['parameters'])
            return vals
        rb=regression();report={'rounds':[],'notes':'Final sample scores are in-sample. CANCEL_DIALOG is judged with a pending call confirmation, not by bypassing the live web guard.'}
        ledgers={1:{'intent':set(),'semantic':set()},2:{'intent':set(),'semantic':set()}}
        async def stage(label,rows,training,batch,max_rounds=5):
            for turn in range(max_rounds+1):
                res=assess(rows);(OUT/(label+'-latest.json')).write_text(json.dumps(res,ensure_ascii=False,indent=2))
                if turn==0:
                    bp=OUT/(label+'-baseline.json')
                    if not bp.exists():bp.write_text(json.dumps(res,ensure_ascii=False,indent=2))
                    report[label+'_baseline']=summary(json.loads(bp.read_text()))
                print('ASSESS',label,turn,summary(res),flush=True)
                report['rounds'].append(dict(stage=label,round=turn,summary=summary(res)))
                selected=[r for r in res if training=='all' or r['split']=='train']
                teach_semantics(selected,rows,ledgers[batch]['semantic'])
                failures=[r for r in selected if not r['exact'] or (r['valid'] and not r['passed'])]
                if not failures or turn==max_rounds:break
                ledgers[batch]['intent'].update(r['text'] for r in failures)
                worst=min(failures,key=lambda r:(r['exact'],r['score']))
                train(tr,selected,worst,steps=1200)
            final=assess(rows);report[label+'_after']=summary(final)
            (OUT/(label+'-final.json')).write_text(json.dumps(final,ensure_ascii=False,indent=2))
            return final
        if not (OUT/'batch01-complete-final.json').exists():await stage('batch01-complete',b1,'all',1)
        await stage('batch02-heldout',b2,'train',2,3)
        await stage('batch02-complete',b2,'all',2)
        combined=assess(b1+b2)
        if not all(r['passed'] and r['semantic'] and r['exact'] for r in combined):
            await stage('combined-maintenance',b1+b2,'all',2,4)
        final=assess(b1+b2);report['final_batch01']=summary(final[:600]);report['final_batch02']=summary(final[600:]);report['regression']={'count':len(prior),'before':sum(rb),'after':sum(regression())}
        report['guidance']={str(k):{name:sorted(values) for name,values in v.items()} for k,v in ledgers.items()}
        (OUT/'final.json').write_text(json.dumps(final,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('DONE',report['final_batch01'],report['final_batch02'],report['regression'],flush=True)
if __name__=='__main__':asyncio.run(main())
