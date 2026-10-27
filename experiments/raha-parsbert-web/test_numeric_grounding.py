import unittest,json
from pathlib import Path
from numeric_grounding import ground_numbers
from practice_data import number_name

class NumericTests(unittest.TestCase):
    def test_ranges(self):
        count=0
        for action,key,values,extra,template in [
            ('SET_AUDIO_VOLUME','level',range(31),{'channel':'media'},'صدا رو بذار رو {}'),
            ('ADJUST_CABIN_TEMPERATURE','delta',range(1,17),{'zone':'front'},'دمای اتاق رو {} درجه زیاد کن'),
            ('SET_CABIN_TEMPERATURE','degreesC',range(16,33),{'zone':'front'},'دمای کابین رو روی {} تنظیم کن'),
            ('SET_FAN_SPEED','level',range(8),{'zone':'front'},'فن رو روی {} بذار'),
            ('SET_WINDOW_POSITION','percent',range(101),{'window':'driver'},'شیشه رو {} درصد باز کن'),
            ('SET_SEAT_HEATING','level',range(1,4),{'seat':'driver','enabled':True},'گرمکن رو روی {} بذار'),
        ]:
            params={**extra,key:999};f=action+'|'+','.join(sorted(params));schema={'allowed':{f:{key:[str(v) for v in values]}}}
            for n in values:
                for text in [str(n),str(n).translate(str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹')),number_name(n)]:
                    out,keys,error=ground_numbers(template.format(text),action,params,schema)
                    self.assertIsNone(error,(text,error));self.assertEqual(out[key],n);count+=1
        self.assertEqual(count,528)

    def test_relative_and_ambiguity(self):
        schema={'allowed':{'ADJUST_CABIN_TEMPERATURE|delta,zone':{'delta':['-3','3','1.5']}}}
        p={'delta':6,'zone':'front'}
        self.assertEqual(ground_numbers('دمای اتاق رو سه درجه کم کن','ADJUST_CABIN_TEMPERATURE',p,schema)[0]['delta'],-3)
        self.assertEqual(ground_numbers('دما رو یک و نیم درجه زیاد کن','ADJUST_CABIN_TEMPERATURE',p,schema)[0]['delta'],1.5)
        for t in ['دما رو سه یا شش درجه زیاد کن','دما رو ۳ درجه کم و ۶ درجه زیاد کن','دما رو منفی سه درجه زیاد کن']:
            self.assertIsNotNone(ground_numbers(t,'ADJUST_CABIN_TEMPERATURE',p,schema)[2])

if __name__=='__main__':unittest.main()
