import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import evidence_feedback as f

class ReviewTests(unittest.TestCase):
    def test_independent_exact_review(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(f,'PATH',Path(tmp)/'reviews.db'):
            text='سانروف رو گرم کن';action='SET_SEAT_HEATING';params={'enabled':True,'seat':'driver'}
            self.assertFalse(f.evaluate(text,action,params)['allowed'])
            f.save(text,action,params,True,'تست اصلاح')
            result=f.evaluate(text,action,params)
            self.assertTrue(result['allowed']);self.assertIsNotNone(result['rule_error'])
            self.assertEqual(result['source'],'human_review')
            self.assertFalse(f.evaluate(text,action,dict(params,seat='passenger'))['allowed'])
            self.assertFalse(f.evaluate('پیتزا رو گرم کن',action,params)['allowed'])
            f.save(text,action,params,False,'نباید پذیرفته شود')
            self.assertFalse(f.evaluate(text,action,params)['allowed'])

if __name__=='__main__':unittest.main()
