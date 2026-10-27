"""Cross-language parity for numeric postprocessing; direction is model-only."""
import json
from export_android import OUT,ROOT
from numeric_grounding import ground_numbers
from validation import consistency_reason
from practice_data import number_name

def main():
    schema=json.loads((OUT/'schema.json').read_text());rows=[]
    def add(text,action,params,allowed):
        frame=action+'|'+','.join(sorted(params))
        out,keys,error=ground_numbers(text,action,params,{'allowed':{frame:allowed}})
        error=error or consistency_reason(text,action,out)
        rows.append(dict(text=text,action=action,parameters=params,
            allowed={k:[json.loads(v) for v in values] for k,values in allowed.items()},
            expected=out,grounded=sorted(keys),rejected=bool(error)))
    for row in json.loads((OUT/'parity-fixtures.json').read_text()):
        candidate=row['expected'];action=candidate['action'];params=candidate['parameters']
        frame=action+'|'+','.join(sorted(params))
        add(row['text'],action,params,schema['allowed'].get(frame,{}))
    for action,key,values,extra,template in [
        ('SET_AUDIO_VOLUME','level',range(31),{'channel':'media'},'صدا رو بذار رو {}'),
        ('ADJUST_CABIN_TEMPERATURE','delta',range(1,17),{'zone':'front'},'دمای اتاق رو {} درجه زیاد کن'),
        ('SET_CABIN_TEMPERATURE','degreesC',range(16,33),{'zone':'front'},'دمای کابین رو روی {} تنظیم کن'),
        ('SET_FAN_SPEED','level',range(8),{'zone':'front'},'فن رو روی {} بذار'),
        ('SET_WINDOW_POSITION','percent',range(101),{'window':'driver'},'شیشه رو {} درصد باز کن'),
        ('SET_SEAT_HEATING','level',range(1,4),{'seat':'driver','enabled':True},'گرمکن رو روی {} بذار')]:
        for n in values:
            for text in [str(n),str(n).translate(str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹')),number_name(n)]:
                add(template.format(text),action,{**extra,key:999},{key:[str(v) for v in values]})
    for word in ['گرمتر','گرم تر','گرم‌ تر','سردتر','سرد تر','خنک‌تر']:
        add(f'اتاق رو سه درجه {word} کن','ADJUST_CABIN_TEMPERATURE',{'delta':6,'zone':'front'},{'delta':['-3','3']})
    add('صدا رو چهار درجه زیاد کن','ADJUST_AUDIO_VOLUME',{'channel':'media','delta':1},{'delta':['-4','4']})
    for action in ['SAVE_SEAT_POSITION','RECALL_SEAT_POSITION']:
        for word in ['یک','دو','سه','چهار','۲','3']:
            add(f'مموری صندلی {word} رو آپدیت کن',action,{'seat':'driver','slot':'driver'},{'slot':['"driver"','"2"','"3"']})
    target=ROOT.parents[1]/'voyah-iran/raha-core/src/test/resources/intent-grounding-parity.json'
    target.write_text(json.dumps(rows,ensure_ascii=False))
    print('Guard parity cases:',len(rows))

if __name__=='__main__':main()
