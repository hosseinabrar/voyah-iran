"""Harvest existing Kotlin language material and label it with the offline teacher.

Unknown teacher outputs are NOT automatically negatives: rule rejection is not
proof of user intent. Source + fingerprints are retained for audit.
"""
import hashlib
import json
import os
import random
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path
from data import normalize

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parents[1]
CORE=REPO/'voyah-iran/raha-core'
ART=ROOT/'artifacts'
JAVA=os.environ.get('JAVA_HOME','/Library/Java/JavaVirtualMachines/jdk-18.0.2.1.jdk/Contents/Home')+'/bin/'

def teacher_setup():
    jar=CORE/'build/libs/command-core-0.1.0.jar'
    if not jar.exists(): raise RuntimeError('Build :command-core:jar first')
    libs=list(Path('/Users/hosseinabrar/voyah/work/macos-tools/gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib').rglob('kotlin-stdlib-2.2.10.jar'))
    if not libs: raise RuntimeError('Set up the repository Kotlin runtime')
    dest=ART/'teacher';dest.mkdir(parents=True,exist_ok=True)
    cp=os.pathsep.join(map(str,[jar,libs[0],dest]))
    subprocess.run([JAVA+'javac','-cp',cp,'-d',str(dest),str(ROOT/'Teacher.java')],check=True)
    return [JAVA+'java','-Dfile.encoding=UTF-8','-Xmx512m','-cp',cp,'Teacher']

def main():
    cmd=teacher_setup()
    sources={}
    def add(text,source):
        text=normalize(text)
        if text and '$' not in text and len(text)<230: sources.setdefault(text,source)
    vocab=subprocess.check_output(cmd+['vocabulary'],text=True).splitlines()
    for text in vocab: add(text,'RahaSpeechVocabulary')
    nouns=set(); hashes={}
    for path in sorted((CORE/'src').rglob('*.kt')):
        content=path.read_text(); hashes[str(path.relative_to(REPO))]=hashlib.sha256(content.encode()).hexdigest()
        for match in re.finditer(r'"([^"\n]*)"',content):
            value=match.group(1)
            if not re.search('[آ-ی]',value) or '$' in value or '\\' in value:continue
            for text in value.split('|'):
                add(text,str(path.relative_to(REPO)))
                if 1<=len(text.split())<=4:nouns.add(text)
    verbs=['رو روشن کن','رو خاموش کن','رو فعال کن','رو غیر فعال کن','رو باز کن','رو ببند','رو زیاد کن','رو کم کن','رو روی متوسط بذار','رو روی حداکثر بذار','رو روی حداقل بذار','رو روی 3 بذار','رو روی 20 بذار','رو روی 50 درصد بذار','رو اتوماتیک کن','رو دستی کن']
    for noun in sorted(nouns):
        for verb in verbs:add(noun+' '+verb,'derived:source-alias+operator')
    # Coverage-oriented numeric/target examples; teacher validates exact semantics.
    for noun in ['دمای اتاق','دمای راننده','دمای شاگرد','سرعت فن','گرمکن صندلی راننده','گرمکن صندلی شاگرد','سردکن صندلی','ماساژ صندلی','شدت رایحه','مدت رایحه','ذخیره باتری','بلندی صدا','روشنایی نمایشگر','حساسیت برف پاک کن','حافظه صندلی']:
        for number in [0,1,2,3,4,5,10,15,18,20,21,22,23,24,25,26,28,30,50,75,100]:
            for template in ['{n} رو روی {v} بذار','{n} رو {v} تا زیاد کن','{n} رو {v} تا کم کن']:
                add(template.format(n=noun,v=number),'derived:numeric')
    for noun in ['شیشه','شیشه راننده','شیشه شاگرد','شیشه عقب چپ','شیشه عقب راست','همه شیشه ها','سانروف','پرده']:
        for number in range(0,101,10):add(f'{noun} رو {number} درصد باز کن','derived:position')
    print(f'Annotating {len(sources)} candidate phrases...',flush=True)
    result=subprocess.run(cmd,input='\n'.join(sources)+'\n',text=True,encoding='utf-8',capture_output=True,check=True)
    rows=[]
    for line in result.stdout.splitlines():
        row=json.loads(line)
        if row['action']=='UNKNOWN':continue
        row['source']=sources[row['text']]
        # Do not train partial/multiple commands from accidental literal harvest.
        if len(row['text'].split())<2:continue
        rows.append(row)
    by_action=Counter(r['action'] for r in rows)
    enum=(CORE/'src/main/kotlin/com/abrar/raha/RahaCommandRecognizer.kt').read_text().split('enum class RahaAction {',1)[1].split('}',1)[0]
    expected=set(re.findall(r'\b[A-Z][A-Z_]+\b',enum))
    ART.mkdir(exist_ok=True)
    (ART/'teacher-rows.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
    report={'candidates':len(sources),'matched':len(rows),'actions':dict(sorted(by_action.items())),'missing':sorted(expected-set(by_action)),'source_hashes':hashes}
    (ART/'coverage.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='source_hashes'},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
