"""Compare this bounded guard with the unchanged intent model; no training."""
import asyncio
import json

async def main():
    import server
    async with server.lifespan(server.app):
        guard=server.check_command_evidence
        dataset=json.loads(server.engine['trainer'].dataset.read_text())
        report=[]
        for row in dataset['final']:
            text=row['text']
            server.check_command_evidence=lambda *_:dict(error=None)
            old=server.predict(text)
            server.check_command_evidence=guard
            new=server.predict(text)
            report.append(dict(text=text,expected=row['action'],prediction=old['prediction']['label'],
                before=old['accepted'],after=new['accepted'],reason=new['reason']))
        correct=[r for r in report if r['before'] and r['prediction']==r['expected'] and r['expected']!='UNKNOWN']
        wrong=[r for r in report if r['before'] and r['prediction']!=r['expected']]
        summary=dict(count=len(report),baseline_correct=len(correct),correct_retained=sum(r['after'] for r in correct),baseline_wrong=len(wrong),wrong_rejected=sum(not r['after'] for r in wrong))
        probes=[]
        for text in ['سانروف رو گرم کن','سانروف رو باز بسته کن','صندلی رو گرم کن','گرم کن','سانروف رو باز کن','پیتزا رو گرم کن','تهویه صندلی رو روشن کن','سانروف رو بخور','بزن بریم گرم']:
            r=server.predict(text);probes.append(r)
            print(text,r['prediction']['label'],r['accepted'],r['reason'],flush=True)
        print(summary,flush=True)
        (server.ART/'command-evidence-report.json').write_text(json.dumps(dict(summary=summary,probes=probes,results=report),ensure_ascii=False,indent=2))

if __name__=='__main__':asyncio.run(main())
