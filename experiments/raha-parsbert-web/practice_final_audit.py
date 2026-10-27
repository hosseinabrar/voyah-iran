"""Fresh final audit after first colloquial audit was promoted to correction data."""
import json
import torch
from transformers import AutoTokenizer
from full_model import FullModel
from practice_train import ROOT,OUT,evaluate,summary
from data import normalize

CASES=[
 ('صندلی کناری من رو گرم کن','SET_SEAT_HEATING',{'seat':'passenger','enabled':True}),
 ('ماساژ سمت راننده موجی باشه','SET_SEAT_MASSAGE_PATTERN',{'seat':'driver','pattern':'wave'}),
 ('میخوام سردکن صندلی شاگرد درجه یک بشه','SET_SEAT_VENTILATION',{'seat':'passenger','enabled':True,'level':1}),
 ('گرم کن خودم خاموش بشه','SET_SEAT_HEATING',{'seat':'driver','enabled':False,'level':0}),
 ('دمای شاگرد دو درجه بیشتر بشه لطفا','ADJUST_CABIN_TEMPERATURE',{'zone':'passenger','delta':2.0}),
 ('دمای سمت خودم رو بذار بیست و سه و نیم','SET_CABIN_TEMPERATURE',{'zone':'driver','degreesC':23.5}),
 ('برای کابین دمای بیست و پنج درجه می خوام','SET_CABIN_TEMPERATURE',{'zone':'front','degreesC':25.0}),
 ('فن میخوام پنج باشه','SET_FAN_SPEED',{'zone':'front','level':5}),
 ('شیشه پشت شاگرد بسته باشه لطفا','CLOSE_WINDOW',{'window':'rearRight'}),
 ('می خوام پنجره سمت راننده تا نیمه باز بشه','SET_WINDOW_POSITION',{'window':'driver','percent':50}),
 ('سانروف روی هشتاد و پنج درصد باشه','SET_SUNROOF_POSITION',{'percent':85}),
 ('پرده سقف رو یه مقدار بیشتر ببند','ADJUST_SUNSHADE_POSITION',{'deltaPercent':-10}),
 ('ماشین از اسپورت بره روی اکو','SET_DRIVE_MODE',{'mode':'eco'}),
 ('بجای گردش بیرونی هوا داخلی بچرخه','SET_AIR_RECIRCULATION',{'mode':'internal'}),
 ('مصرف از بنزینی بره به برقی','SET_ENERGY_STRATEGY',{'strategy':'electric'}),
 ('قفل بچه سمت راست غیرفعال باشه','SET_CHILD_LOCK',{'side':'right','enabled':False}),
 ('مه شکن عقب رو لطفا روشن کن','SET_REAR_FOG_LIGHT',{'enabled':True}),
 ('هشدار برای عابر باید خاموش باشه','SET_PEDESTRIAN_WARNING_SOUND',{'enabled':False}),
 ('نقطه کور ماشین رو روشن بذار','SET_BLIND_SPOT_MONITORING',{'enabled':True}),
 ('مقدار بازیابی انرژی متوسط باشه','SET_REGENERATION_LEVEL',{'level':'medium'}),
 ('بلندی صدا رو برسون روی یازده','SET_AUDIO_VOLUME',{'channel':'media','level':11}),
 ('صدا دو پله بیشتر بشه','ADJUST_AUDIO_VOLUME',{'channel':'media','delta':2}),
 ('برو یه آهنگ جلوتر','NEXT_MEDIA',{}),
 ('رها گرفتن فیلم رو متوقف کن','STOP_VIDEO_RECORDING',{}),
 ('برای یک ساعت مه رو با شدت زیاد روشن کن','CONFIGURE_FRAGRANCE',{'channel':3,'durationMinutes':60,'intensity':'high','enabled':True}),
 ('مدت پخش رایحه نامحدود باشه','SET_FRAGRANCE_DURATION',{'durationMinutes':0}),
 ('میخوام یه تماس با مریم بگیری','CALL_CONTACT',{}),
 ('صفحه وسط پنجاه درصد روشن باشه','SET_DISPLAY_BRIGHTNESS',{'display':'central','percent':50}),
 ('تم صفحه تیره بمونه','SET_DISPLAY_THEME_MODE',{'mode':'night'}),
 ('کارواش رو فعالش کن','ACTIVATE_SCENE',{'scene':'carWash'}),
 ('چرا دوربین عقب کار نمی کنه','UNKNOWN',{}),
 ('دیشب صندوق رو باز کردم','UNKNOWN',{}),
 ('به نظرت هیبرید بهتر نیست','UNKNOWN',{}),
 ('اگر کسی سوار شد ماساژ رو روشن کن','UNKNOWN',{}),
 ('درها رو قفل کن و بعد چراغ جلو رو خاموش کن','UNKNOWN',{}),
 ('تلویزیون اتاق رو بی صدا کن','UNKNOWN',{}),
]

def main():
    torch.set_num_threads(3)
    data=json.loads((OUT/'dataset.json').read_text())
    corrections=json.loads((OUT/'contrast-corrections.json').read_text())
    seen={normalize(r['text']) for r in data['train']+data['dev']+corrections}
    rows=[dict(text=t,action=a,parameters=p) for t,a,p in CASES]
    assert not seen.intersection(normalize(r['text']) for r in rows)
    results={}
    for name,path in [('baseline',ROOT/'artifacts/full-intent'),('candidate',OUT/'final')]:
        model=FullModel.load(path);tok=AutoTokenizer.from_pretrained(path,local_files_only=True)
        results[name]=evaluate(model,tok,rows,'cpu');print(name,summary(results[name]),flush=True)
    (OUT/'final-colloquial-audit.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    for row in results['candidate']['results']:
        if not row['exact_correct']:print(row['text'],row['action'],row['parameters'],'=>',row['predicted'])

if __name__=='__main__':main()
