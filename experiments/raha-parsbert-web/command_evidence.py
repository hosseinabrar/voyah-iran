"""Web experiment: source spans + capability constraints, not a confidence head.

Explicit objects cannot be replaced with defaults. This is a bounded vehicle
ontology, NOT a general Persian semantic parser. Unknown explicit objects fail
closed for the physical-control domains covered here; it never changes a model
prediction into a different executable command.
"""
import re
from data import normalize
from middle_values import is_middle

# Synonyms name entities, not lists of accepted/rejected complete sentences.
ENTITIES = {
    'sunroof': ['سانروف', 'سان روف', 'سقف شیشه ای'],
    'sunshade': ['پرده سانروف', 'پرده سقف', 'پرده', 'سایبان'],
    'window': ['شیشه', 'پنجره'],
    'seat': ['صندلی', 'صندلیم', 'صندلی من', 'نشیمن', 'پشتم', 'کمرم', 'بدنم', 'عضلاتم'],
    'seat_heat': ['گرمکن صندلی', 'گرم کن صندلی', 'گرمایش صندلی', 'گرمکن'],
    'seat_cool': ['سردکن صندلی', 'سرد کن صندلی', 'تهویه صندلی', 'سردکن'],
    'seat_massage': ['ماساژور صندلی', 'ماساژ صندلی', 'ماساژور', 'ماساژ'],
    'climate': ['تهویه', 'کولر', 'بخاری', 'دمای کابین', 'دمای اتاق', 'کابین', 'اتاق'],
    'fan': ['فن کولر', 'فن', 'سرعت فن'],
    'audio': ['بلندی صدا', 'صدای موزیک', 'صدا', 'موزیک', 'موسیقی', 'آهنگ', 'مولتی مدیا'],
    'fragrance': ['رایحه', 'خوشبو کننده', 'خوشبوکننده'],
    'doors': ['قفل درها', 'درب', 'درها', 'در ماشین', 'در خودرو'],
    'tailgate': ['در صندوق', 'درب صندوق', 'صندوق عقب', 'صندوق'],
    'mirror': ['آینه', 'آینه ها'],
    'display': ['نمایشگر', 'صفحه نمایش', 'مانیتور'],
    'ambient': ['نورپردازی داخلی', 'نورپردازی', 'نور کابین'],
    'battery': ['باتری', 'شارژ باتری'],
}

CAPABILITIES = {
    'sunroof': ['OPEN_SUNROOF','CLOSE_SUNROOF','SET_SUNROOF_POSITION','ADJUST_SUNROOF_POSITION'],
    'sunshade': ['SET_SUNSHADE_POSITION','ADJUST_SUNSHADE_POSITION'],
    'window': ['OPEN_WINDOW','CLOSE_WINDOW','SET_WINDOW_POSITION','ADJUST_WINDOW_POSITION','VENT_WINDOW','SET_FRONT_DEFROSTER','SET_REAR_DEFROSTER'],
    'seat': ['SET_SEAT_','ADJUST_SEAT_','SAVE_SEAT_POSITION','RECALL_SEAT_POSITION'],
    'seat_heat': ['SET_SEAT_HEATING','ADJUST_SEAT_HEATING'],
    'seat_cool': ['SET_SEAT_VENTILATION','ADJUST_SEAT_VENTILATION'],
    'seat_massage': ['SET_SEAT_MASSAGE','SET_SEAT_MASSAGE_PATTERN','ADJUST_SEAT_MASSAGE'],
    'climate': ['SET_CLIMATE_','SET_AC_POWER','SET_MAX_COOLING','SET_CABIN_TEMPERATURE','ADJUST_CABIN_TEMPERATURE','SET_FAN_SPEED','ADJUST_FAN_SPEED','SET_AIRFLOW_DIRECTION','SET_AIR_RECIRCULATION','SET_FRONT_CLIMATE_SYNC'],
    'fan': ['SET_FAN_SPEED','ADJUST_FAN_SPEED'],
    'audio': ['SET_AUDIO_','ADJUST_AUDIO_','PLAY_MEDIA','PAUSE_MEDIA','NEXT_MEDIA','PREVIOUS_MEDIA','OPEN_DEFAULT_MUSIC','OPEN_OEM_RADIO','SET_AMBIENT_LIGHT_MODE'],
    'fragrance': ['SET_FRAGRANCE_','ADJUST_FRAGRANCE_','CONFIGURE_FRAGRANCE'],
    'doors': ['SET_DOOR_','SET_CHILD_LOCK'],
    'tailgate': ['OPEN_TAILGATE','CLOSE_TAILGATE'],
    'mirror': ['SET_MIRROR_FOLD'],
    'display': ['SET_DISPLAY_','SWAP_DISPLAY_APPS','SET_REAR_DISPLAY_LOCK'],
    'ambient': ['SET_AMBIENT_LIGHT_','ADJUST_AMBIENT_LIGHT_'],
    'battery': ['SET_BATTERY_','SET_ENERGY_STRATEGY','OPEN_CHARGE_PORT','STOP_CHARGING'],
}

OPERATIONS = {
    'heat': ['گرم کن','گرمکن کن','گرم کردن','گرمش کن'],
    'cool': ['خنک کن','سرد کن','خنکش کن','سردش کن'],
    'open': ['باز کن','بازش کن','باز کردن','باز'],
    'close': ['بسته کن','ببند','ببندش','بستن','بسته'],
    'on': ['روشن کن','روشنش کن','فعال کن','روشن'],
    'off': ['خاموش کن','خاموشش کن','غیر فعال کن','غیرفعال کن','خاموش'],
    'massage': ['بمال','ماساژ بده'],
}

def matches(action, capabilities):
    return any(action==c or (c.endswith('_') and action.startswith(c)) for c in capabilities)

def spans(text, vocabulary):
    found=[]
    for kind, aliases in vocabulary.items():
        for alias in aliases:
            for m in re.finditer(r'(?<!\w)'+re.escape(alias)+r'(?!\w)',text):
                found.append(dict(kind=kind,text=m.group(),start=m.start(),end=m.end()))
    # Longest entity wins: "تهویه صندلی" is not generic climate + seat.
    selected=[]
    for item in sorted(found,key=lambda s: (-(s['end']-s['start']),s['start'])):
        if not any(item['start']<s['end'] and s['start']<item['end'] for s in selected):selected.append(item)
    return sorted(selected,key=lambda s:s['start'])

def check(text, action, params):
    text=normalize(text)
    targets=spans(text,ENTITIES)
    # Operations cannot be extracted from inside an entity (e.g. گرم کن صندلی).
    operations=[s for s in spans(text,OPERATIONS) if not any(s['start']<t['end'] and t['start']<s['end'] for t in targets)]
    result=dict(targets=targets,operations=operations,error=None)
    def reject(message):result['error']=message;return result
    if action=='UNKNOWN':return result
    for target in targets:
        if not matches(action,CAPABILITIES[target['kind']]):
            return reject('برداشت فرمان با هدف صریح «'+target['text']+'» سازگار نیست')
    kinds={s['kind'] for s in operations}
    if {'open','close'}<=kinds or {'on','off'}<=kinds or {'heat','cool'}<=kinds:
        return reject('بیش از یک عمل متضاد گفته شده؛ فرمان اجرا نمی‌شود')
    if 'heat' in kinds and not matches(action,['SET_SEAT_HEATING','ADJUST_SEAT_HEATING','SET_CABIN_TEMPERATURE','ADJUST_CABIN_TEMPERATURE','SET_CLIMATE_POWER','SET_BATTERY_PRECONDITIONING']):
        return reject('عمل گرم کردن با فرمان پیشنهادی سازگار نیست')
    if 'cool' in kinds and not matches(action,['SET_SEAT_VENTILATION','ADJUST_SEAT_VENTILATION','SET_CABIN_TEMPERATURE','ADJUST_CABIN_TEMPERATURE','SET_CLIMATE_POWER','SET_AC_POWER','SET_MAX_COOLING']):
        return reject('عمل خنک کردن با فرمان پیشنهادی سازگار نیست')
    if 'massage' in kinds and not matches(action,['SET_SEAT_MASSAGE','ADJUST_SEAT_MASSAGE']):
        return reject('عمل ماساژ با فرمان پیشنهادی سازگار نیست')
    for operation,opposite in [('open','CLOSE_'),('close','OPEN_')]:
        if operation in kinds and action.startswith(opposite):return reject('جهت باز یا بسته کردن با جمله سازگار نیست')
    position_actions=['SET_SUNROOF_POSITION','ADJUST_SUNROOF_POSITION','SET_SUNSHADE_POSITION','ADJUST_SUNSHADE_POSITION','SET_WINDOW_POSITION','ADJUST_WINDOW_POSITION','SET_DOOR_LOCK','SET_MIRROR_FOLD']
    if kinds.intersection({'open','close'}) and not matches(action,['OPEN_','CLOSE_']+position_actions):
        return reject('عمل باز یا بسته کردن با فرمان پیشنهادی سازگار نیست')
    if 'enabled' in params:
        if ('off' in kinds and params['enabled'] is True) or ('on' in kinds and params['enabled'] is False):
            return reject('وضعیت روشن یا خاموش با جمله سازگار نیست')
        if kinds.intersection({'heat','cool','massage'}) and params['enabled'] is False:
            return reject('عمل خواسته شده با خاموش کردن سازگار نیست')
    if action=='ADJUST_CABIN_TEMPERATURE' and 'delta' in params:
        if ('heat' in kinds and params['delta']<=0) or ('cool' in kinds and params['delta']>=0):
            return reject('جهت تغییر دما با عمل گفته شده سازگار نیست')
    if 'percent' in params:
        if not is_middle(text) and (('close' in kinds and params['percent']!=0) or ('open' in kinds and params['percent']==0)):
            return reject('مقدار بازشدن با عمل گفته شده سازگار نیست')
    if any(t['kind'] in {'sunroof','window','sunshade'} for t in targets) and kinds.intersection({'on','off'}):
        if not action.endswith('DEFROSTER'):return reject('روشن یا خاموش کردن با کنترل بازشدن سازگار نیست')
    # Unknown explicit object: never fill its place with an implicit seat/window.
    covered_action=any(matches(action,CAPABILITIES[k]) for k in ['sunroof','sunshade','window','seat','seat_heat','seat_cool','seat_massage'])
    if covered_action:
        marker=re.search(r'(?<!\w)(?:رو|را)(?!\w)',text)
        if marker:
            before=text[:marker.start()]
            if before.strip() and not any(t['end']<=marker.start() for t in targets):
                return reject('هدف صریح جمله شناسایی نشد؛ هدف پیش فرض جایگزین نمی‌شود')
        # Strict coverage for the initial physical-control scope. Unknown words
        # remain visible evidence; unlike intent classification they cannot vanish.
        if any(t['kind'] in {'sunroof','sunshade','window','seat','seat_heat','seat_cool','seat_massage'} for t in targets):
            remaining=list(text)
            for s in targets+operations:remaining[s['start']:s['end']]=' '*(s['end']-s['start'])
            fillers=set('رها لطفا لطفاً رو را کن کنید بده بکن بکنید بذار بگذار تنظیم تا روی به برام برای من کمی کم بیشتر کمتر زیاد بالا پایین ببر بیار بکش راننده شاگرد همراه چپ راست جلو عقب پشت عقبی جلویی سمت طرف همه هر دو نصف نصفه نیمه کامل کاملا کاملاً ته حداکثر حداقل درصد درجه سطح پله یکی یه یک دو سه چهار پنج شش شیش هفت هشت نه ده بیست سی چهل پنجاه شصت هفتاد هشتاد نود صد و صفر میشه میتونی می تونی بازش بسته اش'.split())
            # Request framing and modifiers are grammatical coverage, not targets.
            framing=['از تو می خوام','می خوام','قرار بگیره','باید','باشه','جای بغلی','تو حالت','از کار بنداز']
            residual=''.join(remaining)
            for phrase in framing:residual=re.sub(r'(?<!\w)'+re.escape(phrase)+r'(?!\w)',' ',residual)
            fillers.update('کناری بغل بغلی ها های تمام بیشترین کمترین ونت حافظه مموری موقعیت ذخیره بازیابی لود شدت وسط متوسط میانه'.split())
            uncovered=[w for w in residual.split() if w not in fillers and not re.fullmatch(r'[\d۰-۹٠-٩]+[.,٫]?[\d۰-۹٠-٩]*',w)]
            result['uncovered']=uncovered
            if uncovered:return reject('بخش «'+' '.join(uncovered)+'» در برداشت فرمان توضیح داده نشده است')
        # Defaults only with supported, complete imperative evidence. No dropping
        # "بزن بریم" to turn a fragment ending in گرم into a seat command.
        if not targets and action in ['SET_SEAT_HEATING','SET_SEAT_VENTILATION']:
            allowed={'لطفا','لطفاً','رها','یه','کم','یکم','یک','مقدار','کن'}
            remainder=list(text)
            for s in operations:remainder[s['start']:s['end']]=' '*(s['end']-s['start'])
            leftover=set(''.join(remainder).split())-allowed
            if not operations or leftover:return reject('برای استفاده از هدف پیش فرض، درخواست کامل و مشخص نیست')
    return result
