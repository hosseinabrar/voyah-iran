import unittest,json
from pathlib import Path
from numeric_grounding import ground_numbers
from practice_data import number_name

class NumericTests(unittest.TestCase):
    def test_seat_memory_symbolic_slots(self):
        for action in ['SAVE_SEAT_POSITION','RECALL_SEAT_POSITION']:
            schema={'allowed':{action+'|seat,slot':{'slot':['"driver"','"2"','"3"']}}}
            for word,expected in [('یک','driver'),('دو','2'),('سه','3'),('۲','2'),('3','3')]:
                out,keys,error=ground_numbers(f'مموری صندلی {word} رو آپدیت کن',action,{'seat':'driver','slot':'driver'},schema)
                self.assertIsNone(error)
                self.assertEqual(out,{'seat':'driver','slot':expected})
                self.assertEqual(keys,{'slot'})
            self.assertIsNotNone(ground_numbers('حافظه صندلی چهار رو ذخیره کن',action,{'seat':'driver','slot':'2'},schema)[2])

    def test_colloquial_degrees(self):
        for action,extra in [('ADJUST_AUDIO_VOLUME',{'channel':'media'}),('ADJUST_FAN_SPEED',{'zone':'front'})]:
            params={**extra,'delta':1}
            schema={'allowed':{action+'|'+','.join(sorted(params)):{'delta':['-4','4']}}}
            for verb,expected in [('زیاد',4),('کم',-4)]:
                out,keys,error=ground_numbers(f'صدا رو چهار درجه {verb} کن',action,params,schema)
                self.assertIsNone(error)
                self.assertEqual(out['delta'],expected)
                self.assertEqual(keys,{'delta'})
            self.assertIsNotNone(ground_numbers('صدا رو صد درجه زیاد کن',action,params,schema)[2])

    def test_temperature_comparatives(self):
        action='ADJUST_CABIN_TEMPERATURE'
        schema={'allowed':{action+'|delta,zone':{'delta':['-3','3']}}}
        for separator in ['', ' ', '\u200c']:
            for word,expected in [('گرم',3),('سرد',-3),('خنک',-3)]:
                text=f'اتاق رو سه درجه {word}{separator}تر کن'
                out,keys,error=ground_numbers(text,action,{'delta':6,'zone':'front'},schema)
                self.assertIsNone(error,text)
                self.assertEqual(out,{'delta':expected,'zone':'front'})
                self.assertEqual(keys,{'delta'})
        for text in ['اتاق رو سه درجه گرم تر و سرد تر کن','اتاق رو سه درجه گرم تر کم کن']:
            self.assertIsNotNone(ground_numbers(text,action,{'delta':3,'zone':'front'},schema)[2])
        self.assertIsNotNone(ground_numbers('اتاق رو سه درجه گرم تر کن','SET_CABIN_TEMPERATURE',{'degreesC':23,'zone':'front'},schema)[2])

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
