import unittest
from contact_extraction import extract_contact,learn_pattern

TEMPLATES=['یه زنگ به {n} بزن','زنگ بزن به {n}','به {n} زنگ بزن','به {n} یه زنگ بزن','{n} رو یه زنگ بزن','یه تماس با {n} بگیر','با {n} تماس بگیر','با {n} یه تماس بگیر','تماس بگیر با {n}','شماره {n} رو بگیر','{n} رو بگیر','می خوام زنگ بزنی به {n}','میخوام یه تماس با {n} بگیری','لطف می کنی با {n} تماس بگیری','یک زنگ به {n} بزنید']

class ContactTests(unittest.TestCase):
    def test_variants_and_multiword_names(self):
        for name in ['علی','مریم','علی رضایی','محمد رضا']:
            for template in TEMPLATES:
                for prefix in ['', 'رها ', 'لطفا ']:
                    text=prefix+template.format(n=name)
                    self.assertEqual(extract_contact(text),name,text)

    def test_learned_pattern_generalizes(self):
        rule=learn_pattern('الان نوبت تماس علی است','علی')
        self.assertEqual(extract_contact('الان نوبت تماس مریم است',[rule]),'مریم')
        self.assertIsNone(extract_contact('الان نوبت تماس مریم بود',[rule]))

    def test_no_missing_or_invented_names(self):
        for text in ['یه زنگ بزن','با تماس بگیر','زنگ بزن','به علی زنگ بزن بعد موزیک پخش کن']:
            self.assertIsNone(extract_contact(text),text)
        with self.assertRaises(ValueError):learn_pattern('یه زنگ به علی بزن','رضا')

if __name__=='__main__':unittest.main()
