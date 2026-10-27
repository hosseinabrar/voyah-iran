"""Exact numeric grounding for unambiguous absolute volume commands only."""
import re
from validation import UNITS

def volume_number(raw):
    text=raw.translate(str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩','01234567890123456789')).strip()
    if re.fullmatch(r'\d+',text):return int(text)
    words={**UNITS,'یکی':1,'شیش':6}
    if text in words and text!='یه':return words[text]
    parts=text.split(' و ')
    if len(parts)==2 and parts[0] in ['بیست','سی'] and parts[1] in words and 1<=words[parts[1]]<=9:
        return words[parts[0]]+words[parts[1]]
    return None

def explicit_volume(text,action):
    if action!='SET_AUDIO_VOLUME':return None
    prefix=r'(?:رها\s+)?(?:لطفا\s+)?(?:می خوام\s+)?'
    noun=r'(?:صدا|بلندی صدا|صدای موسیقی|صدای موزیک|صدای سیستم|صدای مسیریابی|ولوم|مولتی مدیا)'
    patterns=[
        prefix+noun+r'\s+(?:رو|را)\s+(?:بذار|بگذار|تنظیم کن)\s+رو(?:ی)?\s+(.+)',
        prefix+noun+r'\s+(?:رو|را)\s+رو(?:ی)?\s+(.+?)\s+(?:بذار|بگذار|تنظیم کن|قرار بده)',
        prefix+noun+r'\s+(?:رو|را)\s+(.+?)\s+کن',
    ]
    for pattern in patterns:
        match=re.fullmatch(pattern,text.strip().rstrip(' .،!'))
        if match:
            value=volume_number(match[1])
            if value is not None:return value
    return None
