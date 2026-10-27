"""Bounded, traceable augmentation of existing commands; no real contact data."""
import json
import random
import re
from collections import defaultdict,Counter
from pathlib import Path
from data import normalize
from full_model import frame,encode_value

ROOT=Path(__file__).resolve().parent

def variations(text):
    yield text
    yield 'رها '+text
    yield 'لطفا '+text.replace(' رو ',' را ')
    if 'روشن کن' in text: yield text.replace('روشن کن','روشنش کن')
    elif 'خاموش کن' in text:yield text.replace('خاموش کن','خاموشش کن')
    elif 'بذار' in text:yield text.replace('بذار','قرار بده')
    else: yield text+' برام'

def main():
    random.seed(73)
    rows=json.loads((ROOT/'artifacts/teacher-rows.json').read_text())
    coverage=json.loads((ROOT/'artifacts/coverage.json').read_text())
    if coverage['missing']:raise RuntimeError('Missing actions: '+str(coverage['missing']))
    groups=defaultdict(list);quarantined=[]
    for r in rows:
        if re.search(r'\b(نکن|نبند|نذار|آیا|چرا|اگر|اگه)\b',r['text']) or '?' in r['text'] or '؟' in r['text']:
            quarantined.append(r);continue
        groups[frame(r)].append(r)
    selected=[];evaluation=[];titles={}
    for f,group in sorted(groups.items()):
        random.shuffle(group)
        # Prefer existing regression examples; preserve every observed slot value.
        group.sort(key=lambda r:('src/test' not in r['source'],r['source'].startswith('derived:')))
        covered=set();chosen=[]
        for r in group:
            pairs={(k,encode_value(v)) for k,v in r['parameters'].items()}
            if len(chosen)<24 or not pairs<=covered:
                chosen.append(r);covered|=pairs
        selected.extend(chosen)
        sample=chosen[0]
        evaluation.append({**sample,'text':normalize('زحمتش رو بکش '+sample['text']),'source':'held-out:polite-prefix-family'})
        titles.setdefault(sample['action'],sample['description'].split(':')[0])
    # Calls are a separate production flow, outside RahaAction. Names are fictional.
    for name in ['علی','مریم','ناهید','حسین','رضا','فاطمه','محمد','مادر','پدر','پلیس','اورژانس','آتش نشانی']:
        for template in ['به {n} زنگ بزن','با {n} تماس بگیر','شماره {n} رو بگیر','{n} رو بگیر','تماس با {n}']:
            selected.append(dict(text=template.format(n=name),action='CALL_CONTACT',parameters={},description='تماس با مخاطب',source='manual:fictional-contacts'))
    titles['CALL_CONTACT']='تماس با مخاطب'
    evaluation.append(dict(text='لطف می کنی با سارا تماس بگیری',action='CALL_CONTACT',parameters={},source='held-out:contact'))
    for a,title,phrases in [('CONFIRM_CALL','تأیید تماس',['بله','آره','درسته','تایید','بله تماس بگیر','بله بگیر']),('CANCEL_DIALOG','لغو گفتگو',['لغو','کنسل','نه','خیر','نمی خوام','اشتباهه'])]:
        titles[a]=title
        for p in phrases:selected.append(dict(text=p,action=a,parameters={},description=title,source='manual:dialogue'))
    titles['UNKNOWN']='نامفهوم یا خارج از دامنه'
    negatives=['سلام خوبی','امروز هوا خوبه','چه خبر','فردا بارون میاد','برای من غذا سفارش بده','چراغ اتاق خواب رو روشن کن','تلویزیون خونه رو خاموش کن','درب پارکینگ ساختمان رو ببند','ماشین لباسشویی رو روشن کن','پول بفرست','یه جوک بگو','من دیروز کولر رو روشن کردم','یادم نیست شیشه باز بود','به نظرت مود اسپورت بهتره','ساعت چنده','کولر خرابه','هیچی','اسم تو چیه','روشن کن','خاموش کن','پنجره','ماشین','صندلی','چرا این قدر سرده','کتاب بخون','پیامک بفرست','عکس مادرم رو نشان بده']
    # Explicit negatives derived from actual positive nouns, never label arbitrary
    # teacher failures UNKNOWN (many are valid paraphrases the old parser missed).
    negatives.extend(r['text'] for r in quarantined)
    for r in selected[::11]:
        if re.search(r'\bکن\b',r['text']):negatives.append(re.sub(r'\bکن\b','نکن',r['text']))
        negatives.append('آیا '+r['text']+'؟')
    for p in negatives:selected.append(dict(text=p,action='UNKNOWN',parameters={},description=titles['UNKNOWN'],source='manual:negative'))
    eval_text={normalize(r['text']) for r in evaluation}
    train={}
    for r in selected:
        for text in variations(r['text']):
            text=normalize(text)
            if text in eval_text:continue
            if text in train and (frame(train[text])!=frame(r) or train[text]['parameters']!=r['parameters']):raise RuntimeError('Conflicting label: '+text)
            train[text]={**r,'text':text,'family':r['text']}
    dataset=dict(version=2,train=list(train.values()),evaluation=evaluation,titles=titles,
                 provenance={'teacher':'Existing single-command Kotlin recognizer, offline only','coverage':coverage,'quarantined_positive_labels':quarantined,'note':'Synthetic augmentation; held-out phrasing family, not independent real-world benchmark'})
    (ROOT/'dataset.json').write_text(json.dumps(dataset,ensure_ascii=False,indent=2))
    print(json.dumps(dict(train=len(train),evaluation=len(evaluation),actions=len(titles)-1,frames=len(groups),per_action=dict(Counter(r['action'] for r in train.values()))),ensure_ascii=False,indent=2))

if __name__=='__main__':main()
