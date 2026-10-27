"""Ground explicit quantities without changing the model's action or guessing ambiguous roles."""
import json,re
from validation import UNITS

KEYS={'degreesC','percent','reservePercent','delta','deltaPercent','minutes','durationMinutes','level','intensity','slot'}
WORDS={**UNITS,'یکی':1,'شیش':6,'نیم':.5,'نصف':.5,'نصفه':.5}

def quantities(text):
    text=text.translate(str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩٫','01234567890123456789.'))
    words=re.findall(r'-?\d+(?:\.\d+)?|[^\W\d_]+',text)
    found=[];i=0
    while i<len(words):
        w=words[i]
        numeric=bool(re.fullmatch(r'-?\d+(?:\.\d+)?',w))
        if not numeric and w not in WORDS:i+=1;continue
        if w=='یه' and (i+1==len(words) or words[i+1] not in ['درجه','پله','تا','دقیقه','ساعت','درصد']):i+=1;continue
        value=float(w) if numeric else WORDS[w];start=i;i+=1
        while i+1<len(words) and words[i]=='و' and words[i+1] in WORDS:
            part=WORDS[words[i+1]]
            if not (part==.5 or (value>=20 and value%10==0 and 0<part<10)) :break
            value+=part;i+=2
        found.append((value,words[i] if i<len(words) else '',words[start]))
    return found

def ground_numbers(text,action,params,schema):
    result=dict(params);keys=set(params)&KEYS
    if not keys:return result,set(),None
    found=quantities(text)
    if not found:return result,set(),None
    if re.search(r'\b(یا|منفی)\b',text) or len(found)!=1 or len(keys)!=1:
        return result,set(),'عدد یا کاربرد آن در جمله مبهم است؛ یک مقدار مشخص بگو'
    value,unit,word=found[0];key=next(iter(keys))
    if value<0:return result,set(),'مقدار را مثبت بگو و جهت کم یا زیاد شدن را مشخص کن'
    down=bool(re.search(r'\b(کم|کمتر|کاهش|پایین|ببند)\b',text))
    up=bool(re.search(r'\b(زیاد|بیشتر|افزایش|بالا|باز)\b',text))
    if key in ['delta','deltaPercent']:
        if up==down:return result,set(),'جهت افزایش یا کاهش مقدار مشخص نیست'
        value*=1 if up else -1
    elif re.search(r'\b(کم کن|کمتر کن|زیاد کن|بیشتر کن|افزایش|کاهش)\b',text):
        return result,set(),'درخواست تغییر نسبی است ولی مدل تنظیم مقدار ثابت را برداشت کرده'
    if unit=='ساعت':
        if key not in ['minutes','durationMinutes']:return result,set(),'واحد زمان با فرمان تشخیص‌داده‌شده سازگار نیست'
        value*=60
    if unit=='درجه' and action not in ['SET_CABIN_TEMPERATURE','ADJUST_CABIN_TEMPERATURE']:return result,set(),'واحد درجه با فرمان سازگار نیست'
    if unit=='دقیقه' and key not in ['minutes','durationMinutes']:return result,set(),'واحد زمان با فرمان سازگار نیست'
    if unit=='درصد' and key not in ['percent','reservePercent','deltaPercent']:return result,set(),'واحد درصد با فرمان سازگار نیست'
    if word in ['نصف','نصفه'] and key in ['percent','deltaPercent']:value*=100
    frame=action+'|'+','.join(sorted(params))
    allowed=schema.get('allowed',{}).get(frame,{}).get(key,[])
    matches=[json.loads(v) for v in allowed if isinstance(json.loads(v),(int,float)) and not isinstance(json.loads(v),bool) and json.loads(v)==value]
    if not matches:
        return result,set(),'عدد گفته‌شده در محدودهٔ مجاز این فرمان نیست؛ مقدار دیگری حدس زده نمی‌شود'
    result[key]=matches[0]
    return result,{key},None
