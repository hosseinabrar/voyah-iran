import unittest,json
from middle_values import VALUES,is_middle
from numeric_grounding import ground_numbers
from validation import consistency_reason

class MidpointTests(unittest.TestCase):
    def test_all_contract_values(self):
        for action,(key,value) in VALUES.items():
            params={key:value};schema={'allowed':{action+'|'+key:{key:[json.dumps(value)]}}}
            for word in ['نصف','نصفه','وسط','متوسط']:
                text='مقدار رو تا '+word+' زیاد کن'
                out,keys,error=ground_numbers(text,action,params,schema)
                self.assertIsNone(error);self.assertEqual(out[key],value)
                self.assertIsNone(consistency_reason(text,action,out))
    def test_fractional_units_are_not_midpoints(self):
        for text in ['نصف درجه زیاد کن','نصف ساعت','بیست و نصف','روی ۲۵ و نصف','نصف مقدار فعلی']:
            self.assertFalse(is_middle(text))
    def test_invalid_variant_rejected(self):
        self.assertIsNotNone(ground_numbers('صدا روی نصف','SET_AUDIO_VOLUME',{'level':0},{'allowed':{}})[2])

if __name__=='__main__':unittest.main()
