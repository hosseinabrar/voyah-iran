"""Reusable contact-span patterns; not a hardcoded contact-name list."""
import re
from data import normalize

def clean_text(text):return normalize(text).strip(' .،؟!')

def valid_name(name):
    return bool(name and len(name)<=80 and not re.search(r'\b(یه|یک|زنگ|تماس|بزن|بزنی|بگیر|بگیری|رو|را|به|با|بعد|اگر|اگه)\b',name))

def learn_pattern(text,name):
    text=clean_text(text);name=clean_text(name)
    if not valid_name(name) or len(re.findall(r'(?<!\w)'+re.escape(name)+r'(?!\w)',text))!=1 or text.count(name)!=1:
        raise ValueError('نام مخاطب باید دقیقاً یک بار در متن فرمان آمده باشد؛ ابتدا متن شنیده‌شده را اصلاح و بررسی کن')
    before,after=text.split(name)
    if not before.strip() and not after.strip():raise ValueError('جملهٔ تماس را کامل وارد کن')
    return {'before':before,'after':after}

def extract_contact(text,learned=()):
    clean=clean_text(text)
    for rule in reversed(learned):
        m=re.fullmatch(re.escape(rule['before'])+r'(.+?)'+re.escape(rule['after']),clean)
        if m and valid_name(m[1].strip()):return m[1].strip()
    clean=re.sub(r'^(?:(?:هی|سلام) )?رها\s+','',clean)
    clean=re.sub(r'^(?:(?:لطفا|لطف می کنی|میشه|می خوام|میخوام|می خواهم|بی زحمت)\s+)+','',clean)
    clean=re.sub(r'\s+(?:لطفا|برام)$','',clean)
    patterns=[
        r'^(?:یه |یک )?زنگ (?:به )?(.+?) (?:بزن|بزنی|بزنید)$',
        r'^(?:یه |یک )?زنگ (?:بزن|بزنی|بزنید) (?:به )?(.+)$',
        r'^(?:به )?(.+?) (?:رو |را )?(?:یه |یک )?زنگ (?:بزن|بزنی|بزنید)$',
        r'^(?:یه |یک )?تماس (?:با |به )(.+?) (?:بگیر|بگیری|بگیرید|برقرار کن|برقرار کنی)$',
        r'^(?:با |به )(.+?) (?:یه |یک )?تماس (?:بگیر|بگیری|بگیرید|برقرار کن|برقرار کنی)$',
        r'^تماس (?:بگیر|بگیری|برقرار کن) (?:با |به )?(.+)$',
        r'^تماس با (.+)$',
        r'^شماره (.+?) (?:رو |را )?(?:بگیر|بگیری)$',
        r'^(.+?) (?:رو|را) بگیر$',
    ]
    for pattern in patterns:
        m=re.fullmatch(pattern,clean)
        if m and valid_name(m[1].strip()):return m[1].strip()
    return None
