"""Third-round contrasts from observed errors. The first audit is now practice."""
from practice_unseen import UNSEEN
from data import normalize

def contrasts():
    result={}
    def add(t,a,p):
        for pre in ['', 'رها ', 'لطفا ']:
            text=normalize(pre+t);result[text]=dict(text=text,action=a,parameters=p,source='contrastive-correction-round-3')
    for t,a,p in UNSEEN:add(t,a,p)
    modes={'comfort':'کامفورت','sport':'اسپورت','eco':'اکو','snow':'برفی','outing':'اوتینگ','personal':'شخصی'}
    for source,s in modes.items():
        for target,t in modes.items():
            if source==target:continue
            for phrase in [f'مود ماشین از {s} به {t} تغییر کنه',f'حالت رانندگی رو از {s} ببر روی {t}',f'دیگه {s} نمی خوام {t} باشه']:
                add(phrase,'SET_DRIVE_MODE',{'mode':target})
    for source,s in [('internal','داخلی'),('external','بیرونی')]:
        target,t=('external','بیرونی') if source=='internal' else ('internal','داخلی')
        for phrase in [f'گردش هوای کابین رو از {s} ببر روی {t}',f'گردش هوا از {s} به {t} تغییر کنه',f'گردش {s} نمی خوام {t} باشه']:
            add(phrase,'SET_AIR_RECIRCULATION',{'mode':target})
    for source,s in [('electric','برقی'),('fuel','بنزینی'),('hybrid','هیبرید')]:
        for target,t in [('electric','برقی'),('fuel','بنزینی'),('hybrid','هیبرید')]:
            if source!=target:add(f'مصرف انرژی از {s} به {t} تغییر کنه','SET_ENERGY_STRATEGY',{'strategy':target})
    for window,n in [('driver','راننده'),('passenger','شاگرد'),('rearLeft','عقب چپ'),('rearRight','عقب راست'),('all','ها'),('front','های جلو'),('rear','های عقب')]:
        for action,verb in [('OPEN_WINDOW','باز'),('CLOSE_WINDOW','بسته')]:
            add(f'شیشه {n} {verb} بشه',action,{'window':window})
            add(f'پنجره {n} {verb} باشه',action,{'window':window})
            add(f'می خوام شیشه {n} {verb} بشه',action,{'window':window})
    for zone,z in [('driver','راننده'),('passenger','شاگرد'),('front','کابین')]:
        for v,word in [(1.,'یک'),(2.,'دو'),(3.,'سه')]:
            for d,verb in [(v,'بیشتر'),(-v,'کمتر')]:
                add(f'دمای {z} {word} درجه {verb} بشه','ADJUST_CABIN_TEMPERATURE',{'zone':zone,'delta':d})
                add(f'{z} {word} درجه '+('گرم تر' if d>0 else 'خنک تر')+' بشه','ADJUST_CABIN_TEMPERATURE',{'zone':zone,'delta':d})
    for a,n in [('SET_AUTOMATIC_EMERGENCY_BRAKING','ترمز اضطراری'),('SET_BLIND_SPOT_MONITORING','رادار نقطه کور'),('SET_PEDESTRIAN_WARNING_SOUND','هشدار عابر پیاده'),('SET_FRONT_DEFROSTER','بخارگیر جلو'),('SET_REAR_DEFROSTER','گرمکن شیشه عقب'),('SET_FRAGRANCE_POWER','رایحه'),('SET_STABILITY_CONTROL','کنترل پایداری'),('SET_AC_POWER','ای سی'),('SET_RAIN_SENSING_WIPERS','برف پاک کن خودکار'),('SET_REAR_FOG_LIGHT','مه شکن عقب')]:
        for enabled,words in [(True,['فعال','روشن']),(False,['غیرفعال','غیر فعال','خاموش'])]:
            for word in words:
                add(f'{n} {word} باشه',a,{'enabled':enabled})
                add(f'{n} رو {word} کن',a,{'enabled':enabled})
    for side,n in [('left','چپ'),('right','راست'),('both','هر دو طرف')]:
        for enabled,word in [(True,'فعال'),(False,'غیرفعال')]:
            add(f'قفل کودک {n} رو {word} کن','SET_CHILD_LOCK',{'side':side,'enabled':enabled})
            add(f'قفل بچه {n} {word} باشه','SET_CHILD_LOCK',{'side':side,'enabled':enabled})
    for first in ['کولر رو روشن کن','شیشه رو باز کن','موزیک رو پخش کن','رادار نقطه کور رو خاموش کن']:
        for second in ['صندوق رو ببند','سانروف رو باز کن','تم رو تیره کن','ترمز دستی رو آزاد کن']:
            for join in [' بعد هم ',' و بعد ',' و ']:add(first+join+second,'UNKNOWN',{})
        for condition in ['اگه بارون اومد ','وقتی رسیدم خونه ','اگه هوا گرم شد ','وقتی حرکت کردم ']:add(condition+first,'UNKNOWN',{})
    return list(result.values())
