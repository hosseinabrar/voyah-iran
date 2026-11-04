import unittest
from validation import consistency_reason
from command_evidence import check
class SunshadeAliasesTest(unittest.TestCase):
    def test_aliases_keep_shade_separate_from_roof_glass(self):
        for noun in ['سان شید','سانشید','کاور','پرده','پرده سقف','کاور سانروف','پرده سانروف','کاور سقف']:
            for state,value in [('باز',100),('بسته',0)]:
                text=noun+' '+state
                self.assertIsNone(consistency_reason(text,'SET_SUNSHADE_POSITION',{'position':value}),text)
                self.assertIsNotNone(consistency_reason(text,'SET_SUNSHADE_POSITION',{'position':100-value}),text)
            result=check(noun+' رو باز کن','SET_SUNSHADE_POSITION',{'position':100})
            self.assertEqual([t['kind'] for t in result['targets']],['sunshade'],noun)
            self.assertIsNone(result['error'],noun)
    def test_bare_sunroof_stays_glass(self):
        self.assertIsNotNone(check('سانروف رو باز کن','SET_SUNSHADE_POSITION',{'position':100})['error'])
if __name__=='__main__':unittest.main()
