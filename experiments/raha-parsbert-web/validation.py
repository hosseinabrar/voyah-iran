"""Transparent consistency guards, never replacements for the model prediction."""
import re
from command_clauses import validation_text, blocked_form
from middle_values import VALUES, is_middle

UNITS={'صفر':0,'یک':1,'یه':1,'دو':2,'سه':3,'چهار':4,'پنج':5,'شش':6,'هفت':7,'هشت':8,'نه':9,'ده':10,'یازده':11,'دوازده':12,'سیزده':13,'چهارده':14,'پانزده':15,'شانزده':16,'هفده':17,'هجده':18,'نوزده':19,'بیست':20,'سی':30,'چهل':40,'پنجاه':50,'شصت':60,'هفتاد':70,'هشتاد':80,'نود':90,'صد':100}
NUMERIC_KEYS={'degreesC','percent','position','reservePercent','delta','deltaPercent','minutes','durationMinutes','level','slot','channel','intensity'}

def quantity_text(text,action):
    # "نه بیشتر" is a limit, not the numeral nine; "نه درجه" stays numeric.
    text=re.sub(r"\bنه\s+(?:بیشتر|کمتر)\b", " ", text)
    text=re.sub(r'\b(?:یک|یه)\s+کم\b', 'کمی', text)
    # The number in this fragrance name is part of its name, not a quantity.
    if action in ['SET_FRAGRANCE_CHANNEL','CONFIGURE_FRAGRANCE','SET_FRAGRANCE_DURATION','SET_FRAGRANCE_INTENSITY','ADJUST_FRAGRANCE_INTENSITY']:
        return re.sub(r'\bصد\s*توم(?:ن|ان)ی\b','نامرایحه',text)
    return text

def explicit_numbers(text):
    text=text.translate(str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩٫','01234567890123456789.'))
    # Only numerical expressions with context; standalone colloquial 'یه' can mean 'a'.
    words=text.split();values=[];i=0
    while i<len(words):
        word=words[i]
        if re.fullmatch(r'\d+(?:\.\d+)?',word):value=float(word)
        elif word=='یکی':value=1.
        elif word=='شیش':value=6.
        elif word in UNITS and word not in ['یه','نه']:value=float(UNITS[word])
        elif word in ['نیم','نصف','نصفه','نیمه']:value=.5
        else:i+=1;continue
        while i+2<len(words) and words[i+1]=='و' and (words[i+2] in UNITS or words[i+2]=='نیم'):
            value+=.5 if words[i+2]=='نیم' else UNITS[words[i+2]];i+=2
        values.append(value);i+=1
    return values

def change_direction(text, temperature=False):
    # A description of low audibility is not an instruction to decrease volume.
    command=re.sub(r'\bکم\s+(?:می\s*)?شنوم\b',' ',text)
    down=bool(re.search(r'\b(?:کم(?:\s*تر)?(?:ش)?|کاهش|پایین(?:\s*تر)?(?:ش)?|ببند)\b',command))
    up=bool(re.search(r'\b(?:زیاد(?:\s*تر)?(?:ش)?|بیشتر(?:ش)?|افزایش|بالا(?:\s*تر)?(?:ش)?|اضافه|بلند\s*تر(?:ش)?|باز)\b',command))
    warmer=temperature and bool(re.search(r'\bگرم[\s\u200c]*تر(?:ش)?\b',command))
    colder=temperature and bool(re.search(r'\b(?:سرد|خنک)[\s\u200c]*تر(?:ش)?\b',command))
    return up or warmer,down or colder,warmer,colder

def requested_power(text):
    text=validation_text(text)
    # Negated state is not a second positive state: "روشن نمونه، خاموش کن".
    no_on=r'\bروشن\s+(?:نمونه|نماند|نباشه|نباشد)\b'
    no_off=r'\bخاموش\s+(?:نمونه|نماند|نباشه|نباشد)\b'
    off=bool(re.search(no_on,text));on=bool(re.search(no_off,text))
    positive=re.sub(no_on+'|'+no_off,' ',text)
    return on or bool(re.search(r'\b(?:روشن(?:ش)?|(?<!غیر )فعال(?:ش)?)\b',positive)),off or bool(re.search(r'خاموش|غیر\s*فعال',positive))

def consistency_reason(text,action,params):
    if action in ['CALL_CONTACT','UNKNOWN','CONFIRM_CALL','CANCEL_DIALOG']:return None
    if blocked_form(text):return 'جملهٔ منفی، شرطی یا پرسشی؛ محافظ آزمایش مانع پذیرش شد'
    if action in ['SET_LANE_SUPPORT_MODE','SET_DRIVER_MONITORING'] and is_middle(text):
        return 'این حساسیت فقط دو سطح کم و زیاد دارد'
    if action=='SET_SUNSHADE_POSITION':
        # Elliptical noun + state is a complete request; do not invert its endpoint.
        short=re.fullmatch(r'(?:رها\s+)?(?:لطفا\s+)?(?:(?:پرده|کاور)(?:\s+(?:سقف|سانروف))?|سایبان|سان\s*شید)(?:\s+(?:رو|را))?\s+(?:(?:کامل|کاملا)\s+)?(باز|بسته)',validation_text(text))
        if short and params.get('position')!=(100 if short.group(1)=='باز' else 0):
            return 'میزان باز بودن پرده با حالت خواسته شده سازگار نیست'
    if action=='SET_MIRROR_FOLD':
        command=validation_text(text)
        opening=bool(re.search(r'\bباز(?:ش)?\s+(?:کن|بشه|باشه|بمونه)\b',command))
        folding=bool(re.search(r'\b(?:جمع(?:ش)?|تا)\s+(?:کن|بشه|باشه|بمونه)\b',command))
        if opening and folding:return 'جهت آینه متناقض است'
        if opening and params.get('folded') is True or folding and params.get('folded') is False:
            return 'وضعیت جمع شدن آینه با درخواست سازگار نیست'
    if 'enabled' in params:
        on,off=requested_power(text)
        if off and on:return 'هم روشن و هم خاموش گفته شده؛ نیاز به روشن‌کردن منظور است'
        if (off and params['enabled'] is True) or (on and params['enabled'] is False):
            return 'برداشت مدل با روشن/خاموش گفته‌شده سازگار نیست'
    if is_middle(text) and action in VALUES:
        key,value=VALUES[action]
        return None if params.get(key)==value else 'مقدار برداشت شده با مقدار میانی سازگار نیست'
    requested=explicit_numbers(quantity_text(text,action))
    if action=='SET_CABIN_TEMPERATURE' and requested and any(n<15.5 or n>30.5 for n in requested):
        return 'دمای قابل تنظیم از ۱۵٫۵ تا ۳۰٫۵ درجه است'
    if action=='ADJUST_CABIN_TEMPERATURE':
        if requested and any(n<=0 or n>16 for n in requested):
            return 'مقدار تغییر دما باید بیشتر از صفر و حداکثر ۱۶ درجه باشد'
        delta=params.get('delta',0)
        if re.search(r'کم(?:تر)? کن|کاهش|بیار پایین',text) and delta>0:
            return 'جهت تغییر دما با درخواست کاهش سازگار نیست'
        if re.search(r'زیاد کن|بیشتر کن|افزایش|ببر بالا',text) and delta<0:
            return 'جهت تغییر دما با درخواست افزایش سازگار نیست'
    if not requested:return None
    numeric={k:float(v) for k,v in params.items() if k in NUMERIC_KEYS and isinstance(v,(int,float)) and not isinstance(v,bool)}
    if not numeric:return None
    allowed=set(requested)
    if 'deltaPercent' in params and re.search(r'\bپله\b',text):allowed={v*10 for v in requested}
    if 'ساعت' in text:allowed.update(v*60 for v in requested)
    if any(w in text for w in ['نصف','نیمه']) and any('percent' in k.lower() for k in numeric):allowed.add(50.)
    # A minimal consistency check, allowing duration units and relative sign.
    # This does not validate every number in multi-parameter or compound requests.
    actual={abs(v) for v in numeric.values()}
    if not actual.intersection(allowed):return 'عدد برداشت‌شده با عدد گفته‌شده سازگار نیست؛ دوباره بررسی کن'
    return None
