"""Human-authored semantic curriculum. Labels come from specs, not old parser output.

Surface families for dev and final are kept out of training. Synthetic tests are
not a substitute for microphone / real-world evaluation.
"""
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from data import normalize
from full_model import frame, encode_value
from validation import UNITS

ROOT=Path(__file__).resolve().parent
PARTS={'train':{},'dev':{},'final':{}}

def put(split,text,action,params,family):
    text=normalize(text)
    row=dict(text=text,action=action,parameters=params,source='authored-semantic-spec',family=family)
    existing=PARTS[split].get(text)
    if existing and (existing['action']!=action or existing['parameters']!=params):
        raise ValueError(('Conflicting authored label',text,existing,row))
    PARTS[split][text]=row

def command(action,params,phrases):
    for i,text in enumerate(phrases):
        for prefix in ['', 'رها ', 'لطفا ', 'میشه ', 'بی زحمت ', 'لطف می کنی ']:put('train',prefix+text,action,params,'command:'+action)
        for old,new in [(' کن',' بکن'),(' کن',' کنید'),(' بده',' بدین'),(' بذار',' بگذار')]:
            if text.endswith(old):put('train',text[:-len(old)]+new,action,params,'command:'+action)
        put('dev','زحمت بکش '+text,action,params,'command-dev:'+action)
        put('final','از تو می خوام '+text,action,params,'command-final:'+action)

def toggle(action,nouns,params=None,key='enabled',on=True,off=False):
    for noun in nouns.split('|'):
        for value,verbs,state in [(on,['روشن کن','فعال کن','راه بنداز'],'فعال'),(off,['خاموش کن','غیر فعال کن','از کار بنداز'],'غیر فعال')]:
            p={**(params or {}),key:value}
            for verb in verbs:
                for phrase in [f'{noun} رو {verb}',f'{noun} را {verb}',f'رها {verb} {noun} رو',f'لطفا {noun} رو {verb} برام']:
                    put('train',phrase,action,p,'toggle-train')
            put('dev',f'می خوام {noun} {state} بشه',action,p,'toggle-dev')
            put('final',f'{noun} باید {state} باشه',action,p,'toggle-final')

def setting(action,nouns,values,key,params=None,unit=''):
    for noun in nouns.split('|'):
        for value,names in values:
            for name in names.split('|'):
                v=(name+' '+unit).strip();p={**(params or {}),key:value}
                for phrase in [f'{noun} رو روی {v} بذار',f'{noun} را {v} تنظیم کن',f'برای {noun} مقدار {v} رو تنظیم کن',f'{noun} {v} باشه']:
                    put('train',phrase,action,p,'setting-train')
                put('dev',f'{noun} رو بکن {v}',action,p,'setting-dev')
                put('final',f'می خوام {noun} روی {v} قرار بگیره',action,p,'setting-final')

def number_name(n):
    if n%1:return number_name(int(n))+' و نیم'
    n=int(n)
    if n<=20:return {v:k for k,v in UNITS.items() if k not in ['یه','نه']}.get(n,'نه')
    if n<100:return number_name(n//10*10)+' و '+number_name(n%10) if n%10 else {v:k for k,v in UNITS.items()}[n]
    if n==100:return 'صد'
    return str(n)

def numbers(values):
    return [(v,'|'.join(dict.fromkeys([str(v).removesuffix('.0'),str(v).removesuffix('.0').translate(str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹')),number_name(v)]))) for v in values]

def main():
    boolean={
      'SET_AC_POWER':'ای سی|کمپرسور کولر',
      'SET_AMBIENT_LIGHT_POWER':'نورپردازی داخلی|نور محیطی|امبینت',
      'SET_AUTOMATIC_EMERGENCY_BRAKING':'ترمز اضطراری|ترمز اضطراری خودکار|ترمز اتومات اضطراری',
      'SET_AUTO_HIGH_BEAM':'نور بالای خودکار|نوربالای اتوماتیک',
      'SET_BATTERY_PRECONDITIONING':'پیش گرمایش باتری|کنترل دمای باتری',
      'SET_BLIND_SPOT_MONITORING':'رادار نقطه کور|نقطه کور|هشدار نقطه کور',
      'SET_DAYTIME_RUNNING_LIGHTS':'چراغ روز|چراغ روشنایی روز|دی لایت',
      'SET_DOOR_OPEN_WARNING':'هشدار باز شدن در|هشدار باز کردن درب|اخطار در',
      'SET_DRIVER_GESTURES':'تشخیص حرکت دست راننده|کنترل با حرکت دست',
      'SET_DRIVER_MONITORING':'پایش راننده|تشخیص خستگی راننده|پایش حواس پرتی',
      'SET_FORWARD_COLLISION_WARNING':'هشدار برخورد جلو|هشدار تصادف از جلو',
      'SET_FRAGRANCE_POWER':'رایحه|خوشبو کننده|عطر پاش',
      'SET_FRONT_CLIMATE_SYNC':'سینک تهویه|همگام سازی تهویه|سینک',
      'SET_FRONT_CROSS_TRAFFIC_WARNING':'هشدار ترافیک متقاطع جلو|هشدار ترافیک جلو',
      'SET_FRONT_DEFROSTER':'بخارگیر جلو|بخارزدای شیشه جلو',
      'SET_HIGHWAY_SUSPENSION_ADAPTATION':'تعلیق تطبیقی بزرگراه|ارتفاع خودکار بزرگراه',
      'SET_HILL_DESCENT_CONTROL':'کنترل سرازیری|کنترل سرپایینی|اچ دی سی',
      'SET_LEAD_VEHICLE_DEPARTURE_ALERT':'هشدار حرکت ماشین جلو|هشدار شروع حرکت ترافیک',
      'SET_MAX_COOLING':'حداکثر سرمایش|مکس کولر|مکس ای سی',
      'SET_PEDESTRIAN_WARNING_SOUND':'هشدار عابر پیاده|صدای هشدار عابر|هشدار صوتی عابر پیاده',
      'SET_POWER_KEEP_ENGINE_CHARGING':'شارژ با موتور در حالت حفظ برق|شارژ با بنزین در حالت حفظ برق',
      'SET_RAIN_SENSING_WIPERS':'برف پاک کن خودکار|برف پاک کن اتوماتیک',
      'SET_REAR_COLLISION_WARNING':'هشدار برخورد عقب|هشدار تصادف از عقب',
      'SET_REAR_CROSS_TRAFFIC_WARNING':'هشدار ترافیک متقاطع عقب|هشدار ترافیک عقب',
      'SET_REAR_DEFROSTER':'گرمکن شیشه عقب|بخارگیر عقب|بخارزدای عقب',
      'SET_REAR_DISPLAY_LOCK':'قفل نمایشگر عقب|قفل مانیتور عقب',
      'SET_REAR_FOG_LIGHT':'مه شکن عقب|چراغ مه شکن عقب',
      'SET_STABILITY_CONTROL':'کنترل پایداری|سیستم پایداری',
      'SET_TRAFFIC_SIGN_RECOGNITION':'تشخیص تابلوها|تشخیص علائم راهنمایی',
      'SET_LANE_SUPPORT_MODE':'حفظ خط',
    }
    for action,nouns in boolean.items():toggle(action,nouns)
    toggle('SET_CLIMATE_AUTO','تهویه خودکار|کولر اتوماتیک',{'zone':'front'})
    for side,n in [('both','قفل کودک'),('left','قفل کودک چپ'),('right','قفل کودک راست')]:toggle('SET_CHILD_LOCK',n,{'side':side})
    for noun in ['سیستم تهویه','تهویه کابین']:
        command('SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':False},[f'{noun} رو روشن کن',f'{noun} رو فعال کن',f'{noun} رو راه بنداز'])
        command('SET_CLIMATE_POWER',{'enabled':False,'zone':'front'},[f'{noun} رو خاموش کن',f'{noun} رو غیر فعال کن',f'{noun} رو متوقف کن'])
    command('SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':True,'temperatureMinC':26.0},['بخاری رو روشن کن','بخاری رو راه بنداز','کابین رو گرم کن','هوای داخل رو گرم کن'])
    command('SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':True,'temperatureMaxC':23.0},['کولر رو روشن کن','کولر رو راه بنداز','هوای کابین رو خنک کن','سرمایش رو فعال کن','هوا گرمه'])
    command('SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':True,'temperatureLevel':'max'},['اینجا خیلی سرده','خیلی سردمه','داخل ماشین یخ زدم','کابین خیلی سرد شده'])

    seats={'driver':['صندلی راننده','صندلی من','صندلی'], 'passenger':['صندلی شاگرد','صندلی بغل','جای بغلی','صندلی کناری']}
    for suffix,nouns in [('HEATING',['گرمکن','گرم کن']),('VENTILATION',['سردکن','سرد کن']),('MASSAGE',['ماساژ','ماساژور'])]:
        for seat,places in seats.items():
            for place in places:
                for noun in nouns:
                    target=f'{noun} {place}'
                    for enabled,verbs in [(True,['روشن کن','فعال کن','راه بنداز']),(False,['خاموش کن','غیر فعال کن','از کار بنداز'])]:
                        p={'seat':seat,'enabled':enabled}
                        if not enabled:p['level']=0
                        command('SET_SEAT_'+suffix,p,[f'{target} رو {v}' for v in verbs])
                    setting('SET_SEAT_'+suffix,target,numbers([1,2,3])+[('max','حداکثر|بیشترین'),('min','حداقل|کمترین')],'level',{'seat':seat,'enabled':True})
                    for delta,verbs in [(1,['زیاد کن','بیشتر کن']),(-1,['کم کن','کمتر کن'])]:
                        command('ADJUST_SEAT_'+suffix,{'seat':seat,'delta':delta},[f'{target} رو {v}' for v in verbs])
                    if suffix in ['HEATING','VENTILATION']:
                        for amount in range(1,4):
                            for sign,verb in [(-1,'کم'),(1,'زیاد')]:
                                command('ADJUST_SEAT_'+suffix,{'seat':seat,'delta':amount*sign},[f'{target} رو {number_name(amount)} پله {verb} کن',f'{target} رو {amount} تا {verb} کن'])
            if suffix=='HEATING':
                command('SET_SEAT_HEATING',{'seat':seat,'enabled':True},[f'{places[0]} رو گرم کن',f'گرمایش {places[0]} رو فعال کن'])
            if suffix=='VENTILATION':command('SET_SEAT_VENTILATION',{'seat':seat,'enabled':True},[f'{places[0]} رو خنک کن',f'سرمایش {places[0]} رو فعال کن'])
    for seat,places in seats.items():setting('SET_SEAT_MASSAGE_PATTERN','|'.join('ماساژ '+p for p in places),[('wave','موجی|ویوینگ'),('rolling','غلتکی|چرخشی')],'pattern',{'seat':seat})

    zones={'front':'دمای کابین|دمای اتاق|درجه حرارت داخل', 'driver':'دمای سمت راننده|دمای راننده','passenger':'دمای سمت شاگرد|دمای شاگرد|دمای سمت بغل'}
    for zone,nouns in zones.items():
        setting('SET_CABIN_TEMPERATURE',nouns,numbers([i/2 for i in range(32,65)]),'degreesC',{'zone':zone},'درجه')
        setting('SET_CABIN_TEMPERATURE',nouns,[('max','حداکثر'),('min','حداقل')],'temperatureLevel',{'zone':zone})
        for noun in nouns.split('|'):
            for d in [i/2 for i in range(1,33)]:
                for sign,verb in [(1,'زیاد'),(-1,'کم')]:
                    command('ADJUST_CABIN_TEMPERATURE',{'zone':zone,'delta':d*sign},[f'{noun} رو {number_name(d)} درجه {verb} کن',f'{noun} رو {d:g} درجه {verb} کن'])

    windows={'driver':'شیشه راننده|پنجره راننده|شیشه', 'passenger':'شیشه شاگرد|شیشه بغلی', 'rearLeft':'شیشه عقب چپ|پنجره پشت راننده', 'rearRight':'شیشه عقب راست|پنجره پشت شاگرد','all':'همه شیشه ها|تمام پنجره ها','front':'شیشه های جلو','rear':'شیشه های عقب'}
    for window,nouns in windows.items():
        for noun in nouns.split('|'):
            command('OPEN_WINDOW',{'window':window},[f'{noun} رو باز کن',f'{noun} رو بده پایین',f'{noun} رو کامل بکش پایین'])
            command('CLOSE_WINDOW',{'window':window},[f'{noun} رو ببند',f'{noun} رو بده بالا',f'{noun} رو کامل بکش بالا'])
            command('VENT_WINDOW',{'window':window},[f'{noun} رو بذار حالت تهویه',f'{noun} رو ونت کن',f'{noun} رو ببر تو حالت ونت'])
            command('ADJUST_WINDOW_POSITION',{'window':window,'deltaPercent':10},[f'{noun} رو یه کم باز کن',f'{noun} رو بیشتر باز کن'])
            command('ADJUST_WINDOW_POSITION',{'window':window,'deltaPercent':-10},[f'{noun} رو یه کم ببند',f'{noun} رو بیشتر ببند'])
            command('SET_WINDOW_POSITION',{'window':window,'percent':50},[f'{noun} رو تا نصفه بده پایین',f'{noun} رو تا نیمه بکش پایین',f'{noun} رو نصفه باز کن'])
        # Every percentage for driver/passenger; a broad grid for other positions.
        setting('SET_WINDOW_POSITION',nouns,numbers(range(0,101) if window in ['driver','passenger'] else range(0,101,10)),'percent',{'window':window},'درصد')
    setting('SET_SUNROOF_POSITION','سانروف',numbers(range(101)),'percent',unit='درصد')
    setting('SET_SUNSHADE_POSITION','پرده سقف|پرده سانروف',numbers(range(101)),'position',unit='درصد')
    for a,noun in [('ADJUST_SUNROOF_POSITION','سانروف'),('ADJUST_SUNSHADE_POSITION','پرده سقف')]:
        command(a,{'deltaPercent':10},[f'{noun} رو بیشتر باز کن',f'{noun} رو یه کم باز کن'])
        command(a,{'deltaPercent':-10},[f'{noun} رو بیشتر ببند',f'{noun} رو یه کم ببند'])

    simple={
      'OPEN_SUNROOF':['سانروف رو باز کن','سقف شیشه ای رو باز کن','سانروف رو کامل باز کن'],
      'CLOSE_SUNROOF':['سانروف رو ببند','سقف شیشه ای رو ببند','سانروف رو کامل ببند'],
      'OPEN_TAILGATE':['صندوق عقب رو باز کن','در صندوق رو باز کن','صندوق رو باز کن'],
      'CLOSE_TAILGATE':['صندوق عقب رو ببند','در صندوق رو ببند','صندوق رو ببند'],
      'OPEN_CHARGE_PORT':['درگاه شارژ رو باز کن','درپوش شارژ رو باز کن','درب پورت شارژ رو باز کن','می خوام ماشین رو شارژ کنم','می خوام باتری رو شارژ کنم','می خوام شارژر رو وصل کنم'],
      'OPEN_FUEL_FLAP':['در باک رو باز کن','درپوش سوخت رو باز کن','درب باک بنزین رو باز کن','می خوام بنزین بزنم','می خوام به ماشین بنزین بزنم'],
      'OPEN_DEFAULT_MAP':['برنامه نقشه رو باز کن','برو تو نقشه','نقشه رو نشون بده','مسیریاب رو اجرا کن'],
      'OPEN_DEFAULT_MUSIC':['برنامه موزیک رو باز کن','برو تو موزیک پلیر','اپ موسیقی رو اجرا کن'],
      'OPEN_OEM_RADIO':['رادیو رو باز کن','برو تو رادیو','اپ رادیو رو اجرا کن'],
      'PLAY_MEDIA':['موزیک رو پخش کن','آهنگ رو پلی کن','پخش آهنگ رو ادامه بده','موسیقی رو ادامه بده'],
      'PAUSE_MEDIA':['موزیک رو پاز کن','پخش آهنگ رو متوقف کن','موسیقی رو قطع کن','آهنگ رو نگه دار'],
      'NEXT_MEDIA':['آهنگ بعدی رو پخش کن','برو آهنگ بعد','موزیک بعدی','این آهنگ رو رد کن'],
      'PREVIOUS_MEDIA':['آهنگ قبلی رو پخش کن','برگرد آهنگ قبل','موزیک قبلی','برگرد به آهنگ قبلی'],
      'STOP_CHARGING':['شارژ رو متوقف کن','شارژ باتری رو قطع کن','شارژ کردن رو تموم کن'],
      'STOP_VIDEO_RECORDING':['فیلمبرداری رو متوقف کن','ضبط فیلم رو قطع کن','فیلم گرفتن رو تموم کن'],
      'SWITCH_CAMERA':['دوربین رو عوض کن','دوربین رو تغییر بده','برو روی دوربین دیگه'],
      'SWAP_DISPLAY_APPS':['برنامه نمایشگرها رو جابجا کن','جای اپ های دو صفحه رو عوض کن','محتوای نمایشگرها رو با هم عوض کن'],
      'CONFIRM_CALL':['بله','آره','تایید می کنم','بله تماس بگیر','درست است'],
      'CANCEL_DIALOG':['لغو','کنسل کن','نه','اشتباهه','نمی خوام','نه تماس نگیر'],
    }
    for a,phrases in simple.items():command(a,{},phrases)
    for a,phrases in [('OPEN_CAMERA',['دوربین رو باز کن','تصویر دوربین رو نشون بده','برو تو دوربین']),('TAKE_PHOTO',['عکس بگیر','یه عکس بنداز','عکاسی کن']),('START_VIDEO_RECORDING',['فیلم بگیر','فیلمبرداری رو شروع کن','ضبط فیلم رو آغاز کن'])]:command(a,{'view':'default'},phrases)
    command('OPEN_DEFAULT_MAP',{'requiredApp':'neshan'},['نشان رو باز کن','برو تو نشان','برنامه نشان رو اجرا کن'])
    for n in ['علی','مریم','رضا','محمد','سارا','حسین','فاطمه','مادر','پدر','پلیس','اورژانس']:
        command('CALL_CONTACT',{},[f'به {n} زنگ بزن',f'با {n} تماس بگیر',f'شماره {n} رو بگیر',f'{n} رو بگیر'])
    for v,nouns in [('shahpary',['تعمیرگاه شاه پری','شاه پری']),('soroush_central',['نمایندگی مرکزی سروش موتور','سروش مرکزی'])]:
        command('NAVIGATE_TO_SAVED_PLACE',{'destination':v},[f'{n} رو مسیریابی کن' for n in nouns]+[f'بریم {n}' for n in nouns])

    command('SET_ENERGY_STRATEGY',{'strategy':'electric'},['اول باتری رو خالی کن','باتری رو اول مصرف کن','اول از باتری استفاده کن'])
    command('SET_ENERGY_STRATEGY',{'strategy':'fuel'},['اول بنزین بسوزون','بنزین رو اول بسوزون','اول بنزین رو مصرف کن'])
    command('SET_ENERGY_STRATEGY',{'strategy':'save'},['ماشین رو بذار تو حالت سیو','ماشین رو سیو مود کن','مصرف انرژی رو بذار رو حالت سیو مود'])
    command('SET_ENERGY_STRATEGY',{'strategy':'smart'},['مصرف سوخت رو بذار تو حالت اسمارت','ماشین رو بذار روی اسمارت','مصرف انرژی رو اسمارت کن'])
    command('SET_MAX_COOLING',{'enabled':True},['هوا شدیدا گرمه'])
    command('SET_SEAT_MASSAGE',{'enabled':True,'seat':'driver'},['پشتم رو بمال'])
    command('CLOSE_WINDOW',{'window':'rearRight'},['شیشه پشت سری همراه رو ببند'])
    enums=[
     ('SET_DRIVE_MODE','مود رانندگی|حالت رانندگی|مود ماشین','mode',[('sport','اسپورت|ورزشی'),('comfort','کامفورت|راحت'),('eco','اکو|اقتصادی'),('snow','برفی|اسنو'),('outing','اوتینگ'),('personal','شخصی|شخصی سازی')],{}),
     ('SET_ENERGY_STRATEGY','مود مصرف سوخت|حالت مصرف انرژی|ماشین','strategy',[('hybrid','هیبرید|ترکیبی'),('electric','برقی|تمام برقی'),('fuel','بنزینی'),('smart','هوشمند'),('save','ذخیره باتری')],{}),
     ('SET_AIRFLOW_DIRECTION','جهت باد|باد کولر','direction',[('face','صورت'),('feet','پا'),('faceAndFeet','صورت و پا'),('windshield','شیشه جلو'),('all','همه جهت ها')],{'zone':'front'}),
     ('SET_AIR_RECIRCULATION','گردش هوا|تهویه هوا','mode',[('internal','داخلی'),('external','بیرونی|هوای بیرون')],{}),
     ('SET_REGENERATION_LEVEL','بازیابی انرژی|ریکاوری|بازیافت انرژی','level',[('low','کم|حداقل'),('medium','متوسط'),('high','زیاد|حداکثر')],{}),
     ('SET_SUSPENSION_HEIGHT','ارتفاع ماشین|ارتفاع تعلیق','level',[('low','پایین|حداقل'),('medium','متوسط'),('high','بالا|حداکثر')],{}),
     ('SET_DISPLAY_THEME_MODE','تم نمایشگر|تم صفحه|حالت نمایش','mode',[('day','روشن|روز'),('night','تیره|شب'),('auto','خودکار|اتوماتیک')],{}),
     ('SET_HEADLIGHT_MODE','چراغ جلو|چراغ های جلو','mode',[('off','خاموش'),('auto','خودکار'),('lowBeam','نور پایین'),('position','چراغ کوچک')],{}),
     ('SET_AUTO_HOLD','اتو هلد|نگهدارنده ترمز','mode',[('automatic','خودکار'),('deepPedal','فشار عمیق پدال')],{}),
     ('SET_AMBIENT_LIGHT_MODE','حالت نورپردازی|مود نور محیطی','mode',[('normal','عادی'),('music','موزیکال'),('driveMode','هماهنگ با مود رانندگی')],{}),
     ('SET_LANE_DEPARTURE_ALERT_TYPE','نوع هشدار خروج از خط|هشدار رانندگی بین خطوط','type',[('voice','صوتی|با صدا'),('vibration','لرزشی|ویبره')],{}),
     ('SET_LANE_SUPPORT_MODE','رادار بین خطوط|کمک رانندگی بین خطوط','mode',[('off','خاموش'),('warning','هشدار'),('keeping','حفظ خط')],{}),
     ('SET_FRAGRANCE_CHANNEL','رایحه|عطر','channel',[(1,'بامبو|اول'),(2,'گل صد تومانی|دوم'),(3,'مه|سوم')],{}),
     ('SET_FRAGRANCE_INTENSITY','شدت رایحه|شدت خوشبو کننده','intensity',[('low','کم|ضعیف'),('medium','متوسط'),('high','زیاد|قوی')],{}),
    ]
    for a,n,k,values,p in enums:setting(a,n,values,k,p)
    for a,nouns in [('SET_DRIVER_MONITORING','حساسیت پایش راننده'),('SET_FORWARD_COLLISION_WARNING','حساسیت هشدار برخورد جلو'),('SET_LANE_SUPPORT_MODE','حساسیت رادار بین خطوط')]:setting(a,nouns,[('min','کم|حداقل'),('medium','متوسط'),('max','زیاد|حداکثر')],'sensitivity')
    toggle('SET_AMBIENT_LIGHT_MODE','نورپردازی موزیکال',{'mode':'music'})
    command('SET_AMBIENT_LIGHT_MODE',{'mode':'driveMode'},['نورپردازی داخلی رو با مود رانندگی هماهنگ کن','نور داخل رو با حالت رانندگی هماهنگ کن','نورپردازی رو تابع مود رانندگی کن'])
    for side,noun in [('all','آینه ها'),('driver','آینه راننده'),('passenger','آینه شاگرد')]:
        command('SET_MIRROR_FOLD',{'side':side,'folded':True},[f'{noun} رو جمع کن',f'{noun} رو ببند'])
        command('SET_MIRROR_FOLD',{'side':side,'folded':False},[f'{noun} رو باز کن',f'{noun} رو از حالت جمع خارج کن'])
    command('SET_DOOR_LOCK',{'door':'all','locked':True},['درها رو قفل کن','قفل درها رو ببند','درب های ماشین رو قفل کن'])
    command('SET_DOOR_LOCK',{'door':'all','locked':False},['قفل درها رو باز کن','درها رو از قفل در بیار','قفل درب های ماشین رو باز کن'])
    command('SET_PARKING_BRAKE',{'engaged':True},['ترمز دستی رو بکش','ترمز پارک رو فعال کن','ترمز دستی رو درگیر کن'])
    command('SET_PARKING_BRAKE',{'engaged':False},['ترمز دستی رو آزاد کن','ترمز پارک رو خاموش کن','ترمز دستی رو بخوابون'])
    for channel,noun in [('media','صدای موزیک'),('navigation','صدای مسیریابی'),('system','صدای سیستم')]:
        setting('SET_AUDIO_VOLUME',noun,numbers(range(31)),'level',{'channel':channel})
        command('SET_AUDIO_MUTE',{'channel':channel,'enabled':True},[f'{noun} رو میوت کن',f'{noun} رو بی صدا کن'])
        command('SET_AUDIO_MUTE',{'channel':channel,'enabled':False},[f'{noun} رو از میوت در بیار',f'{noun} رو از بی صدا خارج کن'])
    setting('SET_AUDIO_VOLUME','بلندی صدا|ولوم',numbers(range(31)),'level',{'channel':'media'})
    command('SET_AUDIO_VOLUME',{'channel':'media','level':30},['صدا رو تا ته زیاد کن','صدا رو تا آخر زیاد کن','ولوم رو بذار روی حداکثر','بلندی صدا رو تا ته ببر بالا'])
    for action,nouns,extra in [('ADJUST_AUDIO_VOLUME','ولوم|بلندی صدا',{'channel':'media'}),('ADJUST_FAN_SPEED','سرعت فن|دور فن',{'zone':'front'}),('ADJUST_AMBIENT_LIGHT_BRIGHTNESS','نورپردازی داخلی|نور محیطی',{}),('ADJUST_FRAGRANCE_INTENSITY','شدت رایحه|شدت عطر',{}),('ADJUST_REGENERATION_LEVEL','بازیابی انرژی|ریکاوری',{}),('ADJUST_WIPER_SENSITIVITY','حساسیت برف پاک کن',{})]:
        for noun in nouns.split('|'):
            for d,verbs in [(1,['زیاد کن','بیشتر کن']),(-1,['کم کن','کمتر کن'])]:command(action,{**extra,'delta':d},[f'{noun} رو {v}' for v in verbs])
    for d in [2,3,4,5,10]:
        for sign,verb in [(1,'زیاد'),(-1,'کم')]:command('ADJUST_AUDIO_VOLUME',{'channel':'media','delta':sign*d},[f'ولوم رو {number_name(d)} تا {verb} کن',f'صدا رو {d} تا {verb} کن'])
    setting('SET_FAN_SPEED','فن|سرعت فن|دور فن',numbers(range(8))+[('max','حداکثر|بیشترین'),('min','حداقل|کمترین')],'level',{'zone':'front'})
    for n in range(1,8):
        for sign,verb in [(-1,'کم'),(1,'زیاد')]:
            command('ADJUST_FAN_SPEED',{'zone':'front','delta':sign*n},[f'فن رو {n} تا {verb} کن',f'دور فن رو {number_name(n)} پله {verb} کن'])
    setting('SET_WIPER_SENSITIVITY','حساسیت برف پاک کن',numbers(range(1,4))+[('max','حداکثر'),('min','حداقل')],'level')
    setting('SET_AMBIENT_LIGHT_BRIGHTNESS','روشنایی نور محیطی|شدت نورپردازی',numbers(range(101)),'percent',unit='درصد')
    setting('SET_AMBIENT_LIGHT_BRIGHTNESS','روشنایی نور محیطی',[('max','حداکثر'),('min','حداقل')],'level')
    for display,nouns in [('central','روشنایی نمایشگر مرکزی'),('passenger','روشنایی نمایشگر شاگرد'),('cluster','روشنایی کلاستر'),('all','روشنایی همه نمایشگرها')]:
        setting('SET_DISPLAY_BRIGHTNESS',nouns,numbers(range(0,101,5)),'percent',{'display':display},'درصد')
        for d,v in [(10,'زیاد'),(-10,'کم')]:command('SET_DISPLAY_BRIGHTNESS',{'display':display,'deltaPercent':d},[f'{nouns} رو {v} کن'])
    setting('SET_BATTERY_RESERVE_TARGET','حد حفظ شارژ|میزان ذخیره باتری',numbers([25,30,50,75]),'percent',unit='درصد')
    setting('SET_ENERGY_STRATEGY','حالت ذخیره باتری',numbers([25,30,50,75]),'reservePercent',{'strategy':'save'},'درصد')
    setting('SET_FRAGRANCE_DURATION','مدت رایحه|زمان پخش عطر',[(30,'نیم ساعت|سی دقیقه|۳۰ دقیقه'),(60,'یک ساعت|شصت دقیقه|۶۰ دقیقه'),(0,'نامحدود|بدون محدودیت')],'durationMinutes')
    setting('SET_POWER_KEEP_DURATION','زمان حفظ برق|مدت روشن ماندن برق',[(30,'نیم ساعت|۳۰ دقیقه'),(60,'یک ساعت|۶۰ دقیقه'),(90,'یک و نیم ساعت|۹۰ دقیقه'),(120,'دو ساعت|۱۲۰ دقیقه'),(150,'دو و نیم ساعت|۱۵۰ دقیقه'),(180,'سه ساعت|۱۸۰ دقیقه'),(-1,'نامحدود')],'minutes')
    for channel,noun in [(1,'رایحه بامبو'),(2,'رایحه گل صد تومانی'),(3,'رایحه مه')]:
        toggle('CONFIGURE_FRAGRANCE',noun,{'channel':channel})
        setting('CONFIGURE_FRAGRANCE','شدت '+noun,[('low','کم'),('medium','متوسط'),('high','زیاد')],'intensity',{'channel':channel})
        for duration,words in [(30,'نیم ساعت'),(60,'یک ساعت'),(0,'مدت نامحدود')]:
            command('CONFIGURE_FRAGRANCE',{'channel':channel,'durationMinutes':duration,'enabled':True},[f'{noun} رو برای {words} روشن کن',f'{noun} رو {words} فعال کن'])
            for intensity,word in [('medium','متوسط'),('high','زیاد')]:command('CONFIGURE_FRAGRANCE',{'channel':channel,'durationMinutes':duration,'intensity':intensity,'enabled':True},[f'{noun} رو با شدت {word} برای {words} روشن کن',f'برای {words} {noun} رو با شدت {word} فعال کن'])
    for intensity,word in [('low','کم'),('medium','متوسط'),('high','زیاد')]:command('CONFIGURE_FRAGRANCE',{'enabled':True,'intensity':intensity},[f'رایحه رو با شدت {word} روشن کن'])
    for scene,words in [('babyCare','مراقبت کودک|بیبی کر'),('camp','کمپ|کمپینگ'),('carWash','کارواش|شستشو'),('nap','استراحت|چرت'),('party','مهمانی|پارتی'),('powerKeep','حفظ برق|نگه داشتن برق'),('quickWarm','گرمایش سریع')]:
        command('ACTIVATE_SCENE',{'scene':scene},[f'حالت {w} رو فعال کن' for w in words.split('|')]+[f'برو به حالت {w}' for w in words.split('|')])
        command('DEACTIVATE_SCENE',{'scene':'current'},[f'از حالت {w} خارج شو' for w in words.split('|')])
    for m,w in [(30,'نیم ساعت'),(60,'یک ساعت'),(90,'یک و نیم ساعت')]:command('ACTIVATE_SCENE',{'scene':'powerKeep','minutes':m},[f'حفظ برق رو برای {w} فعال کن',f'برق رو {w} روشن نگه دار'])
    for slot,word in [('driver','یک'),('2','دو'),('3','سه')]:
        command('SAVE_SEAT_POSITION',{'seat':'driver','slot':slot},[f'موقعیت صندلی رو در حافظه {word} ذخیره کن',f'حافظه صندلی {word} رو ذخیره کن'])
        command('RECALL_SEAT_POSITION',{'seat':'driver','slot':slot},[f'حافظه صندلی {word} رو بازیابی کن',f'موقعیت صندلی {word} رو لود کن'])

    # Noncommands and unsupported/ambiguous requests must not become vehicle actions.
    for noun in ['کولر','گرمکن صندلی','شیشه راننده','برف پاک کن','رادار نقطه کور','تهویه','چراغ جلو','ماساژ صندلی','دوربین','صندوق']:
        for text in [f'{noun} خرابه',f'دیروز {noun} رو روشن کردم',f'بابام {noun} رو خاموش کرد',f'چرا {noun} روشنه',f'{noun} رو روشن نکن',f'{noun} رو خاموش نکن',f'آیا {noun} روشنه',f'اگه هوا سرد شد {noun} رو روشن کن']:
            command('UNKNOWN',{},[text])
    command('UNKNOWN',{},['تلویزیون رو روشن کن','چراغ اتاق خواب رو خاموش کن','در پارکینگ رو باز کن','من می خوام برم خرید','برای من تاکسی بگیر','یه غذا سفارش بده','پیامک بفرست','ساعت چنده','هوا فردا چطوره','گرمکن یا سردکن رو روشن کن','کولر رو روشن و خاموش کن','چراغ خانه رو روشن کن','دیروز سوار ماشین شدم','کامیون','سرطان','سلام خوبی','همه صندلی ها رو گرم کن','گرمکن صندلی عقب رو روشن کن','بغل','روشن کن','خاموش کن'])

    old=json.loads((ROOT/'dataset.json').read_text())
    schema=json.loads((ROOT/'artifacts/full-intent/schema.json').read_text())
    expected=set(old['titles'])
    assert expected=={r['action'] for r in PARTS['train'].values()},expected-{r['action'] for r in PARTS['train'].values()}
    # Missing/novel frame and value contracts are explicit errors, never guessed labels.
    for split in PARTS.values():
        for row in split.values():
            assert frame(row) in schema['frames'],row
            for k,v in row['parameters'].items():assert encode_value(v) in schema['slots'][k],row
    previous={normalize(r['text']) for r in old['train']}
    train=PARTS['train'];rng=random.Random(20261027)
    tests={}
    for split in ['dev','final']:
        groups=defaultdict(list)
        for row in PARTS[split].values():
            if row['text'] not in train and row['text'] not in previous:groups[row['action']].append(row)
        chosen=[]
        for action,group in sorted(groups.items()):
            rng.shuffle(group)
            chosen.extend(group[:min(len(group),16)])
        tests[split]=chosen
    document={'version':3,'train':list(train.values()),**tests,'titles':old['titles'],
              'note':'Authored semantic specifications, synthetic structural holdouts. No ASR or real-world accuracy claim.'}
    out=ROOT/'artifacts/practice';out.mkdir(exist_ok=True)
    (out/'dataset.json').write_text(json.dumps(document,ensure_ascii=False,indent=2))
    print(json.dumps({'train':len(train),'dev':len(tests['dev']),'final':len(tests['final']),'per_action':dict(Counter(r['action'] for r in train.values()))},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
