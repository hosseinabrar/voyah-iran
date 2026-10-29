import unittest
from command_evidence import check, ENTITIES, CAPABILITIES

class EvidenceTests(unittest.TestCase):
    def test_seat_intensity_word_is_a_modifier_not_an_unknown_target(self):
        for feature in ['گرم کن صندلی','سردکن صندلی','تهویه صندلی']:
            action='SET_SEAT_HEATING' if feature.startswith('گرم') else 'SET_SEAT_VENTILATION'
            self.assertIsNone(check('شدت '+feature+' رو بذار روی یک',action,{'enabled':True,'level':1,'seat':'driver'})['error'])
        self.assertIsNotNone(check('شدت سانروف رو گرم کن','SET_SEAT_HEATING',{'enabled':True,'seat':'driver'})['error'])

    def test_counterexamples(self):
        cases=[('سانروف رو گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('صندلی رو باز کن','SET_SEAT_HEATING',{'enabled':True}),
            ('صندلی رو گرم کن','SET_SEAT_HEATING',{'enabled':False}),
            ('سانروف رو گرم کن','OPEN_SUNROOF',{}),
            ('سانروف رو باز بسته کن','OPEN_SUNROOF',{}),
            ('سانروف رو ببند','OPEN_SUNROOF',{}),
            ('سانروف رو بخور','OPEN_SUNROOF',{}),
            ('صندلی موزی رو گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('پیتزا رو گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('بزن بریم گرم','SET_SEAT_HEATING',{'enabled':True}),
            ('صدا رو گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('سانروف و صندلی رو گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('تهویه صندلی رو روشن کن','SET_CLIMATE_POWER',{'enabled':True}),
            ('سانروف رو روشن کن','OPEN_SUNROOF',{})]
        for t,a,p in cases:
            with self.subTest(text=t,action=a):self.assertIsNotNone(check(t,a,p)['error'])

    def test_valid_defaults_and_explicit_commands(self):
        for t,a,p in [('گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('لطفاً گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('صندلی رو گرم کن','SET_SEAT_HEATING',{'enabled':True}),
            ('تهویه صندلی رو روشن کن','SET_SEAT_VENTILATION',{'enabled':True}),
            ('سانروف رو باز کن','OPEN_SUNROOF',{}),
            ('سانروف رو تا نصفه باز کن','SET_SUNROOF_POSITION',{'percent':50}),
            ('سانروف رو ببند','CLOSE_SUNROOF',{}),
            ('پرده سانروف رو باز کن','SET_SUNSHADE_POSITION',{'percent':100}),
            ('گرمکن صندلی رو خاموش کن','SET_SEAT_HEATING',{'enabled':False}),
            ('هوا گرمه','SET_CLIMATE_POWER',{'enabled':True}),
            ('بریم خونه','NAVIGATE_TO_SAVED_PLACE',{'destination':'home'})]:
            with self.subTest(text=t):self.assertIsNone(check(t,a,p)['error'])

    def test_every_explicit_incompatible_entity_is_rejected(self):
        for target,aliases in ENTITIES.items():
            if target in {'seat','seat_heat'}:continue
            for alias in aliases:
                with self.subTest(alias=alias):self.assertIsNotNone(check(alias+' رو گرم کن','SET_SEAT_HEATING',{'enabled':True})['error'])

    def test_evidence_is_traceable_to_original_normalized_text(self):
        text='لطفا سانروف رو گرم کن'
        evidence=check(text,'SET_SEAT_HEATING',{})
        for s in evidence['targets']+evidence['operations']:
            self.assertEqual(text[s['start']:s['end']],s['text'])

if __name__=='__main__':unittest.main()
