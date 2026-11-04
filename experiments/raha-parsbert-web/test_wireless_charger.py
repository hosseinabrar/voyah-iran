import unittest
from command_evidence import check
class WirelessChargerEvidenceTest(unittest.TestCase):
 def test_phone_port_and_schedule_are_distinct(self):
  for text,action in [('شارژر رو روشن کن','SET_WIRELESS_CHARGER'),('گوشیمو شارژ کن','SET_WIRELESS_CHARGER'),('در شارژر رو باز کن','OPEN_CHARGE_PORT'),('شارژ زمانبندی شده رو فعال کن','SET_SCHEDULED_CHARGING')]:
   for candidate in ['SET_WIRELESS_CHARGER','OPEN_CHARGE_PORT','SET_SCHEDULED_CHARGING']:
    error=check(text,candidate,{} if candidate=='OPEN_CHARGE_PORT' else {'enabled':True})['error']
    self.assertEqual(error is None,candidate==action,(text,candidate,error))
if __name__=='__main__':unittest.main()
