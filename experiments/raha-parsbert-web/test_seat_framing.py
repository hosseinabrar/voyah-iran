import unittest
from command_evidence import check

class SeatFramingTests(unittest.TestCase):
 def test_position_framing(self):
  for prep in ['تو','در','تو وضعیت','در وضعیت','تو موقعیت','تو حالت','تو موقعیت شماره']:
   for n in ['یک','دو','سه']:
    text='صندلی رو بذار '+prep+' '+n
    self.assertIsNone(check(text,'RECALL_SEAT_POSITION',{'seat':'driver','slot':'driver'})['error'],text)
 def test_seat_state_framing(self):
  self.assertIsNone(check('گرمکن صندلی رو تو وضعیت خاموش بذار','SET_SEAT_HEATING',{'seat':'driver','enabled':False,'level':0})['error'])
 def test_wrong_object_and_contradictions_still_rejected(self):
  for text,action,params in [
   ('صندلی رو بذار تو یخچال','RECALL_SEAT_POSITION',{'seat':'driver','slot':'driver'}),
   ('سانروف رو تو وضعیت گرم کن','SET_SEAT_HEATING',{'seat':'driver','enabled':True}),
   ('گرمکن صندلی رو تو وضعیت روشن و خاموش بذار','SET_SEAT_HEATING',{'seat':'driver','enabled':True}),
   ('صندلی رو تو وضعیت خاموش بذار','SET_SEAT_HEATING',{'seat':'driver','enabled':True})]:
    self.assertIsNotNone(check(text,action,params)['error'],text)
