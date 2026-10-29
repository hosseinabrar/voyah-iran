import asyncio,json,shutil
from datetime import datetime
import server
from data import normalize
from middle_values import VALUES

def examples():
    targets=[('SET_AUDIO_VOLUME','بلندی صدا',{'channel':'media'}),('SET_AUDIO_VOLUME','صدا',{'channel':'media'}),
      ('SET_AMBIENT_LIGHT_BRIGHTNESS','نورپردازی',{}),('SET_AMBIENT_LIGHT_BRIGHTNESS','شدت نورپردازی داخلی',{}),
      ('SET_DISPLAY_BRIGHTNESS','نور نمایشگر',{'display':'central'}),('SET_WINDOW_POSITION','شیشه',{'window':'driver'}),
      ('SET_WINDOW_POSITION','شیشه شاگرد',{'window':'passenger'}),('SET_SUNROOF_POSITION','سانروف',{}),('SET_SUNSHADE_POSITION','پرده سقف',{}),
      ('SET_FAN_SPEED','سرعت فن',{'zone':'front'}),('SET_WIPER_SENSITIVITY','حساسیت برف پاک کن',{}),
      ('SET_FRAGRANCE_INTENSITY','شدت رایحه',{}),('SET_REGENERATION_LEVEL','بازیابی انرژی',{}),('SET_SUSPENSION_HEIGHT','ارتفاع تعلیق',{}),('SET_BATTERY_RESERVE_TARGET','ذخیره باتری',{})]
    for zone,suffix in [('front','کابین'),('driver','راننده'),('passenger','شاگرد')]:targets.append(('SET_CABIN_TEMPERATURE','دمای '+suffix,{'zone':zone}))
    for action,names in [('SET_SEAT_HEATING',['گرمکن','گرم کن']),('SET_SEAT_VENTILATION',['سردکن','سرد کن','تهویه']),('SET_SEAT_MASSAGE',['ماساژور'])]:
        for name in names:
            for seat,suffix in [('driver',''),('passenger',' شاگرد')]:targets.append((action,'شدت '+name+' صندلی'+suffix,{'seat':seat,'enabled':True}))
    rows={}
    for action,noun,params in targets:
        k,v=VALUES[action]
        templates=['{n} رو روی {w} تنظیم کن','{n} رو بذار روی {w}','{n} رو {w} کن','{n} رو تا {w} زیاد کن','{n} رو تا {w} کم کن']
        if action in ['SET_WINDOW_POSITION','SET_SUNROOF_POSITION','SET_SUNSHADE_POSITION']:templates+=['{n} رو تا {w} ببند','{n} رو تا {w} باز کن']
        for word in ['نصف','نصفه','وسط','متوسط']:
            for template in templates:
                text=normalize(template.format(n=noun,w=word));rows[text]=dict(text=text,action=action,parameters=dict(params,**{k:v}))
    return list(rows.values())

async def main():
    async with server.lifespan(server.app):
        t=server.engine['trainer'];shutil.copy2(t.path,t.path.with_name('before-midpoints-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.safetensors'))
        rows=examples()
        for r in rows:
            frame=r['action']+'|'+','.join(sorted(r['parameters']))
            for k,v in r['parameters'].items():
                choices=[json.loads(x) for x in t.model.schema['allowed'][frame][k]]
                r['parameters'][k]=next(x for x in choices if x==v and isinstance(x,bool)==isinstance(v,bool))
        merged={normalize(r['text']):r for r in t.rows};merged.update({r['text']:r for r in rows});last=rows[-1];merged.pop(last['text'])
        print('TRAIN',len(rows),'previous',len(t.rows),flush=True)
        t.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()))
        checks=[]
        for r in rows+[dict(r,text='لطفا '+r['text']) for r in rows[::10]]:
            a=server.predict(r['text']);exact=a['prediction']['label']==r['action'] and a['slots']==r['parameters']
            checks.append(dict(text=r['text'],exact=exact,ok=exact and a['accepted'] and round(a['prediction']['score']*100)==100,actual=a['slots'],action=a['prediction']['label'],reason=a['reason'],score=a['prediction']['score']))
        report={'total':len(checks),'exact':sum(c['exact'] for c in checks),'passed':sum(c['ok'] for c in checks),'checks':checks}
        (server.ART/'midpoints-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        print(json.dumps({k:v for k,v in report.items() if k!='checks'}),flush=True);print(json.dumps([c for c in checks if not c['ok']],ensure_ascii=False),flush=True)

if __name__=='__main__':asyncio.run(main())
