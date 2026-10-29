"""Teach direct OEM Easy Entry/Exit toggles, retaining all reviewed feedback."""
import asyncio
import json
import shutil
from datetime import datetime
import server
from data import normalize

ALIASES = {
    'SET_EASY_ENTRY': ['ورود آسان', 'ورود راحت', 'ایزی انتری', 'ایزی اینتری', 'easy entry'],
    'SET_EASY_EXIT': ['خروج آسان', 'خروج راحت', 'ایزی اگزیت', 'easy exit'],
}

def examples():
    rows = {}
    for action, nouns in ALIASES.items():
        for noun in nouns:
            for enabled, states in [(True, ['روشن', 'فعال']), (False, ['خاموش', 'غیر فعال', 'غیرفعال'])]:
                for state in states:
                    for template in ['{n} رو {s} کن', '{n} را {s} کن', '{n} رو بذار تو حالت {s}',
                                     '{n} رو روی {s} بذار', '{n} رو {s} بذار', 'حالت {n} رو {s} کن',
                                     'لطفا {n} رو {s} کن', 'رها {n} رو {s} کن',
                                     'میشه {n} رو {s} کن برام', 'میشه {n} رو بذار تو حالت {s} برام',
                                     'میشه {n} رو {s} بذار برام']:
                        text = normalize(template.format(n=noun, s=state))
                        rows[text] = dict(text=text, action=action, parameters={'enabled': enabled})
            if noun.startswith('easy'):
                for enabled, verb in [(True,'enable'),(False,'disable'),(True,'turn on'),(False,'turn off')]:
                    text=verb+' '+noun
                    rows[text]=dict(text=text,action=action,parameters={'enabled':enabled})
    return list(rows.values())

async def main(steps=1800, report_name='easy-access-report.json'):
    async with server.lifespan(server.app):
        t=server.engine['trainer']
        shutil.copy2(t.path,t.path.with_name('before-easy-access-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors'))
        previous=list(t.rows)
        rows=examples()
        merged={normalize(r['text']):r for r in previous}
        merged.update({r['text']:r for r in rows})
        last=rows[-1];merged.pop(last['text'])
        print('TRAIN',len(rows),'previous',len(previous),flush=True)
        t.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()),steps=steps)
        holdout=[dict(r,text='لطفا '+r['text']+' برام') for r in rows if not r['text'].startswith(('رها','لطفا','میشه','enable','disable','turn'))][::4]
        seen={normalize(r['text']) for r in rows+previous}
        holdout=[r for r in holdout if normalize(r['text']) not in seen]
        checks=[]
        for group, cases in [('train', rows),('holdout',holdout),('previous',previous)]:
            for r in cases:
                a=server.predict(r['text'])
                exact=a['prediction']['label']==r['action'] and ('parameters' not in r or a['slots']==r['parameters'])
                checks.append(dict(group=group,text=r['text'],exact=exact,accepted=a['accepted'],score=a['prediction']['score'],action=a['prediction']['label'],actual=a['slots'],reason=a['reason']))
        report={g:dict(total=sum(c['group']==g for c in checks),exact=sum(c['group']==g and c['exact'] for c in checks),hundred=sum(c['group']==g and c['exact'] and round(c['score']*100)==100 for c in checks)) for g in ['train','holdout','previous']}
        (server.ART/report_name).write_text(json.dumps(dict(summary=report,checks=checks),ensure_ascii=False,indent=2))
        print(json.dumps(report,ensure_ascii=False),flush=True)
        print(json.dumps([c for c in checks if c['group']!='previous' and (not c['exact'] or round(c['score']*100)!=100)],ensure_ascii=False),flush=True)

if __name__=='__main__':asyncio.run(main())
