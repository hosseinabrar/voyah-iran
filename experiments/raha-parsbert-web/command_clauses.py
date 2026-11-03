"""Normalize only explicit request clauses for validation, never model input."""
import re

def validation_text(text):
    # Courtesy at the start is not a deferred environmental condition.
    text=re.sub(r'^(?:رها\s+)?(?:اگر|اگه)\s+(?:ممکنه|ممکن است|می\s*تونی|می\s*توانی)\s+','',text)
    on=r'(?:روشن|(?<!غیر )فعال)'
    off=r'(?:خاموش|غیر\s*فعال)'
    for source,target in [(on,off),(off,on)]:
        # Keep the affirmative command, not a free-standing prohibition.
        text=re.sub(r'\b'+source+r'\s+نکن\s+(?:فقط|در\s+عوض)\s+('+target+r'(?:ش)?\s+کن)\b',r'\1',text)
    for source,target in [('خاموش','روشن'),('روشن','خاموش')]:
        text=re.sub(r'\bاز\s+(?:حالت\s+)?'+source+r'\s+به\s+(?:حالت\s+)?'+target+r'\s+(?:تغییر(?:ش)?\s+بده|تبدیل\s+کن|ببر)\b',target+' کن',text)
    for state,destination in [(on,'خاموش'),(off,'روشن')]:
        text=re.sub(r'\bاز\s+حالت\s+'+state+r'\s+(?:در\s*بیار|خارج\s+کن)\b',destination+' کن',text)
    return text

def blocked_form(text):
    text=validation_text(text)
    return bool(re.search(r'\b(نکن|نبند|نذار|اگر|اگه|آیا|چرا)\b',text) or '?' in text or '؟' in text)
