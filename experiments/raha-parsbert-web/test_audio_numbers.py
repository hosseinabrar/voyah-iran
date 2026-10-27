import unittest
from audio_numbers import explicit_volume
from practice_data import number_name

class AudioNumberTests(unittest.TestCase):
    def test_full_range_all_spellings(self):
        for n in range(31):
            for word in [str(n),str(n).translate(str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹')),number_name(n)]:
                for template in ['صدا رو بذار رو {}','بلندی صدا رو روی {} تنظیم کن','ولوم رو {} کن']:
                    text=template.format(word)
                    self.assertEqual(explicit_volume(text,'SET_AUDIO_VOLUME'),n,text)

    def test_no_guess_for_ambiguous_or_relative_requests(self):
        for text in ['صدا رو ۵ تا کم کن','صدا رو روی ۱۵ یا ۵ تنظیم کن','صدا رو روی ۱۵ بذار و در رو باز کن','صدا رو روی منفی پنج بذار','صدا رو روی نصف بذار']:
            self.assertIsNone(explicit_volume(text,'SET_AUDIO_VOLUME'),text)
        self.assertIsNone(explicit_volume('صدا رو بذار رو ۱۵','ADJUST_AUDIO_VOLUME'))
        self.assertEqual(explicit_volume('صدا رو بذار رو ۳۱','SET_AUDIO_VOLUME'),31)

if __name__=='__main__':unittest.main()
