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
    for action in ['ADJUST_WINDOW_POSITION','ADJUST_SUNROOF_POSITION','ADJUST_SUNSHADE_POSITION']:
        for text in ['سانروف رو ده درصد بیشتر بسته کن','سانروف رو ده درصد بیشتر باز کن','سانروف رو یک پله بسته کن','سانروف رو یک پله باز کن','سانروف رو ده درصد باز و بسته کن']:
            add(text,action,{'deltaPercent':999},{'deltaPercent':['-10','10']})
    add('رایحه بامبو رو نیم ساعت با شدت زیاد روشن کن','CONFIGURE_FRAGRANCE',
        {'channel':1,'durationMinutes':60,'enabled':True,'intensity':'high'}, {'durationMinutes':['30','60']})
    for text in ['چراغ روشنایی روز رو خاموش کن','چراغ روشنایی روز رو روشن کن','چراغ روز رو روشن و خاموش کن','روشن، کن']:
        for enabled in [False,True]:
            add(text,'SET_DAYTIME_RUNNING_LIGHTS',{'enabled':enabled},{})
    for text in ['عطر گل صد تومنی رو انتخاب کن','رایحه صدتومنی رو انتخاب کن','عطر گل صد تومانی رو انتخاب کن','رایحه گل صد تومنی شماره سه رو انتخاب کن']:
        add(text,'SET_FRAGRANCE_CHANNEL',{'channel':2},{})
    add('گل صد تومنی رو برای سی دقیقه فعال کن','CONFIGURE_FRAGRANCE',{'channel':2,'durationMinutes':60,'enabled':True,'intensity':'high'},{'durationMinutes':['30','60']})
    for text in ['زمان حفظ برق رو روی صد و بیست دقیقه بذار','زمان حفظ برق رو صد و پنجاه دقیقه کن','زمان حفظ برق رو صد و هشتاد دقیقه کن','زمان حفظ برق رو صد و بیست و پنج دقیقه کن','زمان حفظ برق رو سی و شصت دقیقه کن']:
        add(text,'SET_POWER_KEEP_DURATION',{'minutes':30},{'minutes':['30','60','90','120','150','180','-1']})
    for text in ['پرده سانروف رو سی درصد باز کن','پرده سانروف رو تا نصف باز کن','پرده سانروف رو صد و بیست درصد باز کن','پرده سانروف رو سی دقیقه باز کن']:
        add(text,'SET_SUNSHADE_POSITION',{'position':90},{'position':[str(n) for n in range(101)]})
    for action in ['SET_LANE_SUPPORT_MODE','SET_DRIVER_MONITORING']:
        add('حساسیت رو روی متوسط بذار',action,{'sensitivity':'max'},{'sensitivity':['"min"','"max"']})
    for number in [15,15.5,30.5,31,32]:
        add(f'دما رو روی {number} بذار','SET_CABIN_TEMPERATURE',{'degreesC':20,'zone':'front'},{'degreesC':[str(i/2) for i in range(31,62)]})
    target=ROOT.parents[1]/'voyah-iran/raha-core/src/test/resources/intent-grounding-parity.json'
    target.write_text(json.dumps(rows,ensure_ascii=False))
    print('Guard parity cases:',len(rows))

if __name__=='__main__':main()
