"""Second supervised round: colloquial failure correction plus balanced replay."""
import json
import os
import random
import time
from collections import defaultdict
import torch
from transformers import AutoTokenizer
from full_model import FullModel,frame,encode_value
from practice_train import ROOT,OUT,evaluate,summary
from practice_probes import PROBES
from data import normalize

def corrections():
    rows={}
    def add(text,action,p):
        for prefix in ['', 'رها ', 'لطفا ', 'میشه ', 'بی زحمت ', 'برای من ']:
            t=normalize(prefix+text)
            rows[t]=dict(text=t,action=action,parameters=p,source='colloquial-correction')
    for text,action,p in PROBES:add(text,action,p)
    for side,locs in [('driver',['خودم','من','راننده']),('passenger',['شاگرد','بغلم','کناریم'])]:
        for loc in locs:
            add(f'صندلی {loc} دیگه گرم نشه','SET_SEAT_HEATING',{'seat':side,'enabled':False,'level':0})
            add(f'صندلی {loc} دیگه خنک نشه','SET_SEAT_VENTILATION',{'seat':side,'enabled':False,'level':0})
            add(f'ماساژ {loc} دیگه کار نکنه','SET_SEAT_MASSAGE',{'seat':side,'enabled':False,'level':0})
            for number in [18.,20.,22.,22.5,24.,26.,28.]:
                add(f'دمای سمت {loc} بشه {number:g}','SET_CABIN_TEMPERATURE',{'zone':side,'degreesC':number})
            for action,noun in [('SET_SEAT_HEATING','گرمکن'),('SET_SEAT_VENTILATION','سردکن'),('SET_SEAT_MASSAGE','ماساژ')]:
                add(f'{noun} {loc} رو غیرفعالش کن',action,{'seat':side,'enabled':False,'level':0})
                add(f'{noun} {loc} رو فعالش کن',action,{'seat':side,'enabled':True})
    for n in [18.,20.,22.,24.,26.,28.]:add(f'داخل ماشین رو روی {n:g} درجه تنظیمش کن','SET_CABIN_TEMPERATURE',{'zone':'front','degreesC':n})
    for action,noun,params in [('ADJUST_FAN_SPEED','سرعت فن',{'zone':'front'}),('ADJUST_AUDIO_VOLUME','بلندی صدا',{'channel':'media'}),('ADJUST_FRAGRANCE_INTENSITY','شدت رایحه',{}),('ADJUST_REGENERATION_LEVEL','بازیابی انرژی',{})]:
        for delta,word in [(1,'بیشتر'),(-1,'کمتر')]:add(f'{noun} یه پله {word} بشه',action,{**params,'delta':delta})
    for noun,a in [('ترمز اضطراری','SET_AUTOMATIC_EMERGENCY_BRAKING'),('رادار نقطه کور','SET_BLIND_SPOT_MONITORING'),('هشدار عابر','SET_PEDESTRIAN_WARNING_SOUND'),('گرمکن شیشه عقب','SET_REAR_DEFROSTER'),('چراغ روز','SET_DAYTIME_RUNNING_LIGHTS')]:
        add(f'{noun} رو غیرفعالش کن',a,{'enabled':False})
        add(f'{noun} رو فعالش کن',a,{'enabled':True})
    for dir,part in [('face','صورتم'),('feet','پام'),('windshield','شیشه جلو'),('faceAndFeet','صورتم و پام')]:
        add(f'می خوام باد بخوره به {part}','SET_AIRFLOW_DIRECTION',{'zone':'front','direction':dir})
        add(f'فقط {part} باد بخوره','SET_AIRFLOW_DIRECTION',{'zone':'front','direction':dir})
    for text,mode in [('هوای بیرون نیاد گردش داخلی باشه','internal'),('هوای بیرون بیاد گردش بیرونی باشه','external'),('گردش داخلی نمی خوام هوای بیرون بیاد','external')]:add(text,'SET_AIR_RECIRCULATION',{'mode':mode})
    for display,noun in [('central','وسط'),('passenger','شاگرد'),('cluster','کلاستر')]:
        for n in [20,30,40,50,60,70,80]:add(f'نور صفحه {noun} {n} درصد باشه','SET_DISPLAY_BRIGHTNESS',{'display':display,'percent':n})
    for word,mode in [('شب','night'),('روز','day'),('خودکار','auto')]:add(f'تم رو {word} کن','SET_DISPLAY_THEME_MODE',{'mode':mode})
    for text in ['دیگه فیلم نگیر','فیلم گرفتن کافیه','ضبط ویدیو رو تموم کن']:add(text,'STOP_VIDEO_RECORDING',{})
    for text in ['فیلم گرفتن رو شروع کن','الان ویدیو بگیر']:add(text,'START_VIDEO_RECORDING',{'view':'default'})
    for text in ['پخش رو از سر بگیر','ادامه آهنگ رو پخش کن','آهنگ رو دوباره پلی کن']:add(text,'PLAY_MEDIA',{})
    for text in ['دو صفحه رو جابجا کن','محتوای صفحه ها جاشون عوض بشه','دو مانیتور رو با هم جابجا کن']:add(text,'SWAP_DISPLAY_APPS',{})
    for noun in ['کولر','درها','شیشه','صندوق','چراغ ها']:
        add(f'نمی خوام {noun} باز بشه','UNKNOWN',{})
        add(f'{noun} رو روشن کن و یه آهنگ پخش کن','UNKNOWN',{})
        add(f'{noun} خونه رو روشن کن','UNKNOWN',{})
    return list(rows.values())

def main():
    torch.manual_seed(817);rng=random.Random(817);torch.set_num_threads(4)
    device='mps' if torch.backends.mps.is_available() else 'cpu'
    data=json.loads((OUT/'dataset.json').read_text())
    new=corrections()
    third=os.environ.get('RAHA_CONTRAST_ROUND')=='1'
    if third:
        from practice_contrasts import contrasts
        new=list({r['text']:r for r in new+contrasts()}.values())
    final_text={r['text'] for r in data['final']}
    assert not final_text.intersection(r['text'] for r in new)
    (OUT/('contrast-corrections.json' if third else 'corrections.json')).write_text(json.dumps(new,ensure_ascii=False,indent=2))
    groups=defaultdict(list)
    for r in data['train']:groups[r['action']].append(r)
    replay=[r for g in groups.values() for r in rng.sample(g,min(len(g),100))]
    rows=replay+new
    source=OUT/('refined' if third else 'best')
    destination=OUT/('final' if third else 'refined')
    model=FullModel.load(source).to(device);tok=AutoTokenizer.from_pretrained(source,local_files_only=True)
    for name,p in model.bert.named_parameters():p.requires_grad=any(f'encoder.layer.{n}.' in name for n in range(8,12))
    opt=torch.optim.AdamW([{'params':[p for p in model.bert.parameters() if p.requires_grad],'lr':8e-6}, {'params':list(model.frame_head.parameters())+list(model.slot_heads.parameters()),'lr':8e-5}],weight_decay=.01)
    enc=tok([r['text'] for r in rows],padding='max_length',truncation=True,max_length=48,return_tensors='pt')
    model.eval();cache=[]
    with torch.no_grad():
        for i in range(0,len(rows),64):cache.append(model.frozen_prefix({k:v[i:i+64].to(device) for k,v in enc.items()}).cpu().half())
    cache=torch.cat(cache)
    targets=torch.tensor([model.schema['frames'].index(frame(r)) for r in rows])
    slots={k:torch.tensor([v.index(encode_value(r['parameters'][k])) if k in r['parameters'] else -100 for r in rows]) for k,v in model.schema['slots'].items()}
    weights=torch.tensor([.65/len(replay)]*len(replay)+[.35/len(new)]*len(new))
    probes=[dict(text=t,action=a,parameters=p) for t,a,p in PROBES]
    start=time.monotonic();history=[]
    for epoch in range(3 if third else 4):
        model.train();total=0;steps=0
        for ids in torch.multinomial(weights,len(rows),replacement=True).split(32):
            opt.zero_grad(set_to_none=True)
            pred,sp=model.from_prefix(cache[ids].float().to(device),enc['attention_mask'][ids].to(device))
            loss=torch.nn.functional.cross_entropy(pred,targets[ids].to(device));terms=[]
            for k,logits in sp.items():
                truth=slots[k][ids].to(device)
                if (truth!=-100).any():terms.append(torch.nn.functional.cross_entropy(logits,truth,ignore_index=-100))
            if terms:loss=loss+torch.stack(terms).mean()
            loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1);opt.step();steps+=1;total+=loss.item()
        practiced=evaluate(model,tok,probes,device)
        entry=dict(epoch=epoch+1,loss=total/steps,seconds=round(time.monotonic()-start),practiced=summary(practiced));history.append(entry)
        print('REFINE',json.dumps(entry),flush=True)
    model.save(destination);tok.save_pretrained(destination)
    final=evaluate(model,tok,data['final'],device)
    report=json.loads((source/'report.json').read_text())
    report.update(**summary(final),results=final['results'],refinement_history=history,correction_examples=len(new),train_count=len(data['train'])+len(new))
    (destination/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    (destination/'practiced-probes.json').write_text(json.dumps(practiced,ensure_ascii=False,indent=2))
    print('REFINED FINAL',json.dumps(summary(final)),flush=True)

if __name__=='__main__':main()
