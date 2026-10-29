"""Transparent consistency guards, never replacements for the model prediction."""
import re
from middle_values import VALUES, is_middle

UNITS={'صفر':0,'یک':1,'یه':1,'دو':2,'سه':3,'چهار':4,'پنج':5,'شش':6,'هفت':7,'هشت':8,'نه':9,'ده':10,'یازده':11,'دوازده':12,'سیزده':13,'چهارده':14,'پانزده':15,'شانزده':16,'هفده':17,'هجده':18,'نوزده':19,'بیست':20,'سی':30,'چهل':40,'پنجاه':50,'شصت':60,'هفتاد':70,'هشتاد':80,'نود':90,'صد':100}
NUMERIC_KEYS={'degreesC','percent','reservePercent','delta','deltaPercent','minutes','durationMinutes','level','slot','channel','intensity'}

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

def consistency_reason(text,action,params):
    if action in ['CALL_CONTACT','UNKNOWN','CONFIRM_CALL','CANCEL_DIALOG']:return None
    if 'enabled' in params:
        off=bool(re.search(r'خاموش|غیر\s*فعال',text))
        on=bool(re.search(r'روشن',text))
        if off and on:return 'هم روشن و هم خاموش گفته شده؛ نیاز به روشن‌کردن منظور است'
        if (off and params['enabled'] is True) or (on and params['enabled'] is False):
            return 'برداشت مدل با روشن/خاموش گفته‌شده سازگار نیست'
    if is_middle(text) and action in VALUES:
        key,value=VALUES[action]
        return None if params.get(key)==value else 'مقدار برداشت شده با مقدار میانی سازگار نیست'
    requested=explicit_numbers(text)
    if action=='SET_CABIN_TEMPERATURE' and requested and any(n<16 or n>32 for n in requested):
        return 'دمای قابل تنظیم از ۱۶ تا ۳۲ درجه است'
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
    if 'ساعت' in text:allowed.update(v*60 for v in requested)
    if any(w in text for w in ['نصف','نیمه']) and any('percent' in k.lower() for k in numeric):allowed.add(50.)
    # A minimal consistency check, allowing duration units and relative sign.
    # This does not validate every number in multi-parameter or compound requests.
    actual={abs(v) for v in numeric.values()}
    if not actual.intersection(allowed):return 'عدد برداشت‌شده با عدد گفته‌شده سازگار نیست؛ دوباره بررسی کن'
    return None
