"""Risk-aware offline text practice; rejection is acceptable for ASR-corrupted input."""
import asyncio,json,shutil,sqlite3,random
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from data import normalize
from batch09_samples import samples as ninth
from teach_batches import assess as previous_assess,train
OUT=Path('artifacts/batch09');OUT.mkdir(exist_ok=True)

def evaluate(rows):
    import importlib, validation
    importlib.reload(validation)
    server.consistency_reason=validation.consistency_reason
    import numeric_grounding
    importlib.reload(numeric_grounding)
    server.ground_numbers=numeric_grounding.ground_numbers
    reps={}
    for r in rows:
        if r['policy']=='correct':reps.setdefault(r['group'],r)
    out=[]
    for r in rows:
        p=server.predict(r['text']);expected=p['prediction']['label']==r['action'] and p['slots'] in [r['parameters']]+r.get('alternatives',[])
        pending=(r['context']=='pending_call_confirmation' and expected and p['evidence']['model_reason']=='پاسخ مربوط به گفتگوی تماس است؛ این صفحه تماس در انتظاری ندارد' and p['evidence']['allowed'])
        eligible=p['accepted'] or pending
        execute=eligible and round(p['prediction']['score']*100)==100
        unsafe=execute and (r['policy']=='reject' or not expected)
        passed=(expected and execute) if r['policy']=='correct' else not unsafe
        if r['policy']=='correct':semantic=ef.evaluate(r['text'],r['action'],r['parameters'])['allowed']
        elif r['policy']=='reject':
            q=reps[r['group']];semantic=not ef.evaluate(r['text'],q['action'],q['parameters'])['allowed']
        else:semantic=expected or p['prediction']['label']=='UNKNOWN' or not p['evidence']['allowed']
        out.append(dict(**r,exact=expected,passed=passed,unsafe=unsafe,executed=execute,semantic=semantic,predicted=p['prediction']['label'],slots=p['slots'],score=p['prediction']['score'],reason=p['reason']))
    return out

def summary(rows):
    return {name:dict(count=len(rs),correct=sum(r['passed'] for r in rs),unsafe=sum(r['unsafe'] for r in rs),executed_correct=sum(r['executed'] and r['exact'] and r['policy']!='reject' for r in rs),rejected=sum(not r['executed'] for r in rs),semantic_correct=sum(r['semantic'] for r in rs)) for name,rs in [('all',rows),('clear',[r for r in rows if r['policy']=='correct']),('noisy',[r for r in rows if r['policy']=='correct_or_reject']),('invalid',[r for r in rows if r['policy']=='reject']),('holdout',[r for r in rows if r['split']=='holdout'])]}

def semantic_teach(selected,rows,ledger):
    reps={}
    for r in rows:
        if r['policy']=='correct':reps.setdefault(r['group'],r)
    for r in selected:
        pairs=[]
        if r['policy']=='correct':pairs=[(r['action'],r['parameters'],True)]
        if r['policy']=='reject':
            q=reps[r['group']];pairs=[(q['action'],q['parameters'],False)]
        if r['predicted']!='UNKNOWN' and (r['policy']=='reject' or (r['policy']=='correct_or_reject' and not r['exact'])):pairs.append((r['predicted'],r['slots'],False))
        for a,p,allowed in pairs:
            if ef.evaluate(r['text'],a,p)['allowed']!=allowed:
                ef.save(r['text'],a,p,allowed,'تمرین خطاهای شنیداری: '+('برداشت معتبر' if allowed else 'رد برداشت نامرتبط یا جمله نامعتبر'))
                ledger.add(r['text'])

async def main():
    if (OUT/"report.json").exists():raise RuntimeError("Choose a new output directory; preserve the recorded baseline.")
    project_sync.ProjectSync.start=lambda self:None
    async with server.lifespan(server.app):
        tr=server.engine['trainer'];shutil.copy2(tr.path,OUT/'before.safetensors')
        with ef.connect() as src:
            with sqlite3.connect(OUT/'before-evidence.sqlite3') as dst:src.backup(dst)
        old=json.loads(Path('regression-contracts/batch01/samples.json').read_text())+json.loads(Path('regression-contracts/batch02/samples.json').read_text())
        report={'batches':{},'notes':'Text ASR perturbations, not actual audio. No execution threshold changes. Rejection is valid only for noisy/invalid inputs. Confirmation requires a pending call.'}
        all_new=[]
        for number,rows in [(9,ninth())]:
            folder=OUT/str(number);folder.mkdir(exist_ok=True);(folder/'samples.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
            baseline=evaluate(rows);(folder/'baseline.json').write_text(json.dumps(baseline,ensure_ascii=False,indent=2));print('BASELINE',number,summary(baseline),flush=True)
            intent=set();sem=set();history=[]
            # Keep one evaluation before broadening to the former holdout.
            for phase,maxround in [('train',2),('all',5)]:
                for step in range(maxround+1):
                    res=evaluate(rows);(folder/'latest.json').write_text(json.dumps(res,ensure_ascii=False,indent=2));print('ASSESS',number,phase,step,summary(res),flush=True);history.append(dict(phase=phase,step=step,metrics=summary(res)))
                    selected=[r for r in res if phase=='all' or r['split']=='train']
                    semantic_teach(selected,rows,sem)
                    failures=[r for r in selected if not r['passed'] or (r['policy']=='reject' and r['predicted']!='UNKNOWN') or (r['policy']=='correct_or_reject' and not r['exact'] and r['predicted']!='UNKNOWN' and round(r['score']*100)==100)]
                    if not failures or step==maxround:break
                    intent.update(r['text'] for r in failures)
                    learning=[]
                    for r in selected:
                        reject=r['policy']=='reject' or (r['policy']=='correct_or_reject' and not r['exact'])
                        learning.append(dict(text=r['text'],action='UNKNOWN' if reject else r['action'],parameters={} if reject else r['parameters']))
                    worst=min(failures,key=lambda r:(not r['unsafe'],r['score']))
                    focus=next(r for r in learning if r['text']==worst['text'])
                    train(tr,learning,focus,steps=1000)
                if phase=='train':(folder/'before-holdout-training.json').write_text(json.dumps(evaluate(rows),ensure_ascii=False,indent=2))
            final=evaluate(rows);(folder/'final.json').write_text(json.dumps(final,ensure_ascii=False,indent=2));report['batches'][str(number)]={'baseline':summary(baseline),'after':summary(final),'intent_examples':sorted(intent),'semantic_examples':sorted(sem),'history':history};all_new+=rows
        # Recheck every earlier example, not just a small random slice.
        earlier=previous_assess(old);new=evaluate(all_new)
        report['prior_1200']={'count':1200,'correct':sum(r['passed'] for r in earlier),'exact':sum(r['exact'] for r in earlier)}
        report['combined_new']=summary(new)
        previous_noisy=[]
        for name in ['batch03','batch04','batch05','batch06','batch07','batch08']:
            previous_noisy+=json.loads((Path('regression-contracts')/name/'samples.json').read_text())
        previous_results=evaluate(previous_noisy)
        report['prior_noisy_4820']=summary(previous_results)
        (OUT/'previous-noisy-final.json').write_text(json.dumps(previous_results,ensure_ascii=False,indent=2))
        (OUT/'prior-final.json').write_text(json.dumps(earlier,ensure_ascii=False,indent=2));(OUT/'combined-final.json').write_text(json.dumps(new,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('DONE',report['prior_1200'],report['combined_new'],flush=True)
if __name__=='__main__':asyncio.run(main())
