"""Expand interpolated test cases and numeric ranges missing from literal harvest."""
import json
import subprocess
from collections import Counter
from prepare_full import ROOT,teacher_setup

def main():
    texts=set()
    for noun in ['رایحه','عطر','خوشبو کننده','مدت رایحه']:
        for duration in ['نیم ساعت','یک ساعت','نامحدود','30 دقیقه','60 دقیقه']:
            for verb in ['بذار','تنظیم کن']:texts.add(f'{noun} رو روی {duration} {verb}')
    for noun in ['زمان حفظ برق','مدت حفظ برق','زمان پاور هولد']:
        for duration in ['نیم ساعت','یک ساعت','یک و نیم ساعت','دو ساعت','سه ساعت','نامحدود','صد و پنجاه دقیقه','30 دقیقه','60 دقیقه','90 دقیقه','120 دقیقه','150 دقیقه','180 دقیقه']:
            texts.add(f'{noun} رو روی {duration} تنظیم کن')
    for noun in ['روشنایی نمایشگر','نورپردازی داخلی','شیشه راننده','شیشه شاگرد','شیشه عقب چپ','شیشه عقب راست','پرده','سانروف','حد حفظ شارژ']:
        for n in range(0,101):texts.add(f'{noun} رو روی {n} درصد بذار')
    for noun in ['دمای اتاق','دمای راننده','دمای شاگرد']:
        for n in range(32,65):texts.add(f'{noun} رو روی {n/2:g} بذار')
    for noun in ['بلندی صدا','سرعت فن']:
        for n in range(31):texts.add(f'{noun} رو روی {n} بذار')
    cmd=teacher_setup()
    result=subprocess.run(cmd,input='\n'.join(sorted(texts))+'\n',text=True,encoding='utf-8',capture_output=True,check=True)
    rows=json.loads((ROOT/'artifacts/teacher-rows.json').read_text());unique={r['text']:r for r in rows}
    for line in result.stdout.splitlines():
        r=json.loads(line)
        if r['action']!='UNKNOWN':unique[r['text']]={**r,'source':'derived:expanded-interpolated-tests-and-ranges'}
    rows=list(unique.values())
    (ROOT/'artifacts/teacher-rows.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
    report=json.loads((ROOT/'artifacts/coverage.json').read_text());counts=Counter(r['action'] for r in rows)
    report['missing']=[a for a in report['missing'] if a not in counts];report['actions']=dict(sorted(counts.items()));report['matched']=len(rows)
    (ROOT/'artifacts/coverage.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('Rows:',len(rows),'Actions:',len(counts),'Missing:',report['missing'])

if __name__=='__main__':main()
