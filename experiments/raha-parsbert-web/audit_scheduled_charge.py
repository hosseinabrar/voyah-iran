"""Independent text probes; report raw model errors separately from execution gate.

Not a microphone/vehicle test and not used as training data.
"""
import asyncio
import json
import server
from train_scheduled_charge import examples
from data import normalize

async def main():
    trained = {r['text'] for r in examples()}
    cases = []
    def add(text, action, enabled=None):
        cases.append((text, action, {} if enabled is None else {'enabled': enabled}))
    for noun, action in [('شارژ زمان بندی شده', 'SET_SCHEDULED_CHARGING'),
                         ('ایزی اگزیت', 'SET_EASY_EXIT'), ('ورود آسان', 'SET_EASY_ENTRY')]:
        for state, enabled in [('فعال', True), ('خاموش', False)]:
            for template in ['میشه لطفا {n} رو {s} کنی', 'لطفا حالت {n} رو {s} بذار',
                             '{n} رو برام {s} کن']:
                add(template.format(n=noun, s=state), action, enabled)
        for template in ['لطفا {n} رو برای گوشی فعال کن', '{n} رو سه درجه کم کن',
                         '{n} رو هم روشن کن هم خاموش کن', '{n} رو روی هفت تنظیم کن']:
            add(template.format(n=noun), 'UNKNOWN')
    for text in ['موبایلم رو طبق زمانبندی شارژ کن', 'ساعت شروع شارژ رو روی شش تنظیم کن',
                 'ایزی اگزیت مرورگر رو روشن کن', 'شارژ سر ساعت لپ تاپ رو روشن کن',
                 'ورود آسان به حساب بانکی رو فعال کن', 'زمانبندی شارژ رو روی ساعت ده تنظیم کن']:
        add(text, 'UNKNOWN')
    async with server.lifespan(server.app):
        results=[]
        for text, action, params in cases:
            result=server.predict(text)
            predicted=result['prediction']['label']
            score=result['prediction']['score']
            results.append(dict(text=text, held_out=normalize(text) not in trained,
                expected=action, actual=predicted, parameters=result['slots'], score=score,
                exact=predicted==action and result['slots']==params,
                unsafe_full_confidence=action=='UNKNOWN' and predicted!='UNKNOWN' and round(score*100)==100))
        report=dict(total=len(results), held_out=sum(r['held_out'] for r in results), exact=sum(r['exact'] for r in results),
                    unsafe_full_confidence=sum(r['unsafe_full_confidence'] for r in results), results=results)
        (server.ART/'scheduled-charge-audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(json.dumps(report, ensure_ascii=False), flush=True)

if __name__=='__main__':asyncio.run(main())
