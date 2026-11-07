"""Small, explicitly synthetic pilot; evaluation phrases are not training templates."""
import re
from pathlib import Path

MODEL = 'HooshvareLab/bert-fa-zwnj-base'

# Normalize only a named cartridge followed by an object marker. In particular,
# «رایحه یک ساعت ...» contains a duration, not a cartridge number.
FRAGRANCE_NAMES = [
    (re.compile(r'(?<!\w)(?:رایحه|عطر|عطر ?پاش|بوگیر|خوشبو ?کننده) (?:بامبو|[1۱١]|یک|اول)(?= (?:رو|را)(?!\w))'), 'رایحه بامبو'),
    (re.compile(r'(?<!\w)(?:رایحه|عطر|عطر ?پاش|بوگیر|خوشبو ?کننده) (?:گل صد ?توما?نی|گل|اسکارلت پی ?یونی|اسکارلت پیونی|پیونی اسکارلت|اسکارلت|پیونی|پی یونی|[2۲٢]|دو|دوم)(?= (?:رو|را)(?!\w))'), 'رایحه گل صد تومانی'),
    (re.compile(r'(?<!\w)(?:رایحه|عطر|عطر ?پاش|بوگیر|خوشبو ?کننده) (?:مه نمناک|مه|نمناک|واتری میست|واتر ی میست|واتر میست|واتر ی|واتری|[3۳٣]|سه|سوم)(?= (?:رو|را)(?!\w))'), 'رایحه مه'),
]

_resources = Path(__file__).resolve().parents[2] / "voyah-iran/raha-core/src/main/resources"
LIGHT_REQUESTS = [
    (re.compile(pattern), command)
    for filename in ("raha-ambient-requests.tsv", "raha-display-requests.tsv")
    for pattern, command in (line.split("\t") for line in (_resources / filename).read_text().splitlines())
]

MEDIA_MUTE = re.compile(r"(?:صدا|صدای (?:موزیک|موسیقی|آهنگ|رسانه))(?: رو| را|و)? (باز(?:ش)? کن|وصل کن|ببند(?:ش)?|قطع کن)")
AIR_INTAKE = re.compile(r'دریچه(?: هوا)?(?: رو| را)? (باز کن|ببند)')
AIRFLOW_REQUEST = re.compile(r'(?:جهت باد|باد(?: کولر| بخاری| تهویه)?|دریچه(?: هوا| باد| کولر| تهویه)?)(?: رو| را)? (?:به )?(?:سمت |جهت |روی )?((?:شیشه جلو|شیشه|صورت|پا|بالا|پایین|وسط|جلو)(?: و (?:شیشه جلو|شیشه|صورت|پا|بالا|پایین|وسط|جلو))*)(?: (?:(?:تنظیم|پخش) کن|بوزه|بوزد|بخوره))?')
OUTLETS = {'صورت':'صورت','وسط':'صورت','جلو':'صورت','پا':'پا','پایین':'پا','شیشه جلو':'شیشه','شیشه':'شیشه','بالا':'شیشه'}

def normalize(text):
    text = re.sub(r'\s+', ' ', text.replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک')).strip()
    text = re.sub(r'(?<!\w)فی(?:و)? ول فرست(?!\w)', 'فیول فرست', text)
    for pattern, command in LIGHT_REQUESTS:
        if pattern.fullmatch(text):
            return command
    mute = MEDIA_MUTE.fullmatch(text)
    if mute:
        return "صدای موزیک رو از میوت دربیار" if mute[1].startswith(("باز", "وصل")) else "صدای موسیقی رو میوت کن"
    intake = AIR_INTAKE.fullmatch(text)
    if intake:
        return "گردش هوای بیرونی رو فعال کن" if intake[1] == "باز کن" else "هوای داخل رو بچرخون"
    airflow = AIRFLOW_REQUEST.fullmatch(text)
    if airflow:
        outlets = {OUTLETS[word] for word in airflow[1].split(' و ')}
        return 'جهت باد رو روی '+' و '.join(word for word in ('صورت','پا','شیشه') if word in outlets)+' تنظیم کن'
    text = re.sub(r'(?<!\w)سان روف(?!\w)', 'سانروف', text)
    text = re.sub(r'(?<!\w)(?:یدونه|(?:یه|یک) دونه)(?!\w)', 'یک', text)
    text = re.sub(r'(?<!\w)(دو|سه)تا(?!\w)', r'\1 تا', text)
    for pattern, name in FRAGRANCE_NAMES:
        text = pattern.sub(name, text)
    return re.sub(r'(?<!\w)چهارتا(?!\w)', 'چهار', text)


TOPICS = {
    'seat_heat': ('گرمکن صندلی', ['گرمکن صندلی', 'گرم کن صندلی', 'گرمایش صندلی']),
    'seat_cool': ('سردکن صندلی', ['سردکن صندلی', 'سرد کن صندلی', 'تهویه صندلی']),
    'seat_massage': ('ماساژ صندلی', ['ماساژ صندلی', 'ماساژور صندلی']),
    'pedestrian': ('هشدار عابر پیاده', ['هشدار عابر پیاده', 'صدای عابر پیاده', 'هشدار صوتی عابر']),
    'blind_spot': ('رادار نقطه کور', ['رادار نقطه کور', 'نقطه کور', 'هشدار نقطه کور']),
    'climate': ('تهویه', ['تهویه', 'کولر', 'سیستم تهویه', 'بخاری']),
    'fragrance': ('رایحه', ['رایحه', 'خوشبو کننده', 'خوشبوکننده']),
}
TITLES = {'unknown': 'نامفهوم / خارج از دامنه', 'window_half': 'شیشه تا نصفه', 'window_open': 'باز کردن شیشه', 'window_close': 'بستن شیشه', 'doors_lock': 'قفل درها', 'doors_unlock': 'باز کردن قفل درها', 'hybrid': 'مصرف هیبرید', 'electric': 'مصرف برقی', 'comfort': 'مود کامفورت', 'eco': 'مود اکو', 'play': 'پخش موسیقی', 'pause': 'توقف موسیقی', 'next': 'آهنگ بعدی', 'previous': 'آهنگ قبلی'}
for key, (title, _) in TOPICS.items():
    TITLES[key+'_on'] = title + ' — روشن'
    TITLES[key+'_off'] = title + ' — خاموش'
LABELS = sorted(TITLES)

def training_data():
    rows = []
    def add(label, texts):
        for text in texts:
            for prefix in ['', 'رها ', 'لطفا ']:
                rows.append({'text': normalize(prefix+text), 'label': label})
    for key, (_, nouns) in TOPICS.items():
        for state, verbs in [('on', ['روشن کن', 'فعال کن', 'راه بنداز']), ('off', ['خاموش کن', 'غیر فعال کن', 'قطع کن'])]:
            for noun in nouns:
                targets = ['', ' راننده', ' شاگرد'] if key.startswith('seat') else ['']
                for target in targets:
                    add(key+'_'+state, [noun+target+' رو '+v for v in verbs] + [v+' '+noun+target+' رو' for v in verbs])
    groups = {
        'window_half': ['شیشه رو تا نصفه بده پایین', 'شیشه رو تا نصفه بکش پایین', 'پنجره رو نیمه باز کن', 'شیشه راننده رو تا نصف باز کن', 'شیشه شاگرد رو پنجاه درصد باز کن'],
        'window_open': ['شیشه رو باز کن', 'پنجره رو بده پایین', 'شیشه شاگرد رو کامل بکش پایین', 'شیشه راننده رو باز کن'],
        'window_close': ['شیشه رو ببند', 'پنجره رو بکش بالا', 'شیشه شاگرد رو کامل بده بالا', 'شیشه راننده رو ببند'],
        'doors_lock': ['درها رو قفل کن', 'درب ماشین رو قفل کن', 'قفل درها رو ببند', 'در خودرو رو قفل کن'],
        'doors_unlock': ['قفل درها رو باز کن', 'درها رو از قفل در بیار', 'قفل ماشین رو باز کن'],
        'hybrid': ['ماشین رو بذار تو هیبرید', 'حالت مصرف رو هیبرید کن', 'مود مصرف هیبرید بشه', 'برو روی هیبرید'],
        'electric': ['ماشین رو برقی کن', 'حالت مصرف رو برقی کن', 'برو روی حالت الکتریکی', 'مصرف فقط برق باشه'],
        'comfort': ['مود ماشین رو کامفورت کن', 'حالت رانندگی رو راحت کن', 'ماشین رو بذار روی کامفورت'],
        'eco': ['مود ماشین رو اکو کن', 'حالت رانندگی اقتصادی باشه', 'برو روی اکو'],
        'play': ['موزیک رو پخش کن', 'آهنگ بذار', 'موسیقی رو پلی کن', 'پخش آهنگ رو شروع کن'],
        'pause': ['موزیک رو پاز کن', 'پخش موسیقی رو متوقف کن', 'آهنگ رو نگه دار', 'موزیک رو قطع کن'],
        'next': ['آهنگ بعدی رو پخش کن', 'برو ترک بعد', 'موزیک بعدی', 'بعدی رو بذار'],
        'previous': ['آهنگ قبلی رو پخش کن', 'برو ترک قبل', 'موزیک قبلی', 'قبلی رو بذار'],
        'unknown': ['سلام', 'امروز هوا خوبه', 'ساعت چنده', 'تهران کجاست', 'یه جوک بگو', 'گرمکن', 'صندلی', 'ماشین', 'روشن کن', 'خاموش کن', 'پنجره', 'هیبرید چیه', 'چرا موزیک پخش نمیشه', 'صدای موسیقی رو زیاد کن', 'دما رو بیست کن', 'برام به علی زنگ بزن', 'برو خونه', 'رنگ چراغ رو سبز کن', 'پرده رو باز کن', 'صندوق رو ببند', 'خسته شدم', 'خیلی گرمه', 'اسم تو چیه', 'کولر رو روشن نکن', 'درها رو قفل نکن', 'اگر بارون اومد شیشه رو ببند', 'ماساژ روشنه', 'من دیروز کولر رو روشن کردم', 'شیشه رو بیست درصد باز کن', 'اول موزیک بذار بعد شیشه رو باز کن']
    }
    for label, texts in groups.items():
        add(label, texts)
    return list({(r['text'], r['label']): r for r in rows}.values())

# Fixed development probe, never used in gradient updates. Not a real-world benchmark.
EVAL = [
    ('گرم کن صندلیم رو خاموش کن', 'seat_heat_off'),
    ('رها لطف می کنی گرمکن صندلی رو روشن کنی', 'seat_heat_on'),
    ('سردکن صندلی رو فعالش کن', 'seat_cool_on'),
    ('دیگه نمی خوام سردکن صندلی کار کنه', 'seat_cool_off'),
    ('ماساژور رو روشن کن', 'seat_massage_on'),
    ('ماساژ صندلیم رو خاموشش کن', 'seat_massage_off'),
    ('صدای هشدار عابر پیاده رو خاموشش کن', 'pedestrian_off'),
    ('هشدار عابر رو فعالش کن', 'pedestrian_on'),
    ('رادار نقطه کور رو غیر فعالش کن', 'blind_spot_off'),
    ('نقطه کور رو روشنش کن', 'blind_spot_on'),
    ('کولر ماشین رو روشنش کن', 'climate_on'),
    ('سیستم تهویه رو خاموشش کن', 'climate_off'),
    ('خوشبو کننده ماشین رو روشنش کن', 'fragrance_on'),
    ('رایحه رو خاموشش کن', 'fragrance_off'),
    ('شیشه کنارم رو تا نیمه پایین بیار', 'window_half'),
    ('پنجره سمت راننده رو کامل پایین بیار', 'window_open'),
    ('شیشه سمت شاگرد رو بالا ببر', 'window_close'),
    ('همه درهای ماشین رو قفلشون کن', 'doors_lock'),
    ('قفل در ماشین رو بازش کن', 'doors_unlock'),
    ('مصرف ماشین رو ببر روی هیبرید', 'hybrid'),
    ('می خوام مصرف ماشین برقی باشه', 'electric'),
    ('رانندگی رو ببر روی کامفورت', 'comfort'),
    ('حالت ماشین رو بذار اقتصادی', 'eco'),
    ('یه موسیقی برام پخش کن', 'play'),
    ('آهنگی که پخشه رو متوقف کن', 'pause'),
    ('این آهنگ رو رد کن برو بعدی', 'next'),
    ('برگرد به آهنگ قبلی', 'previous'),
    ('فردا بارون میاد', 'unknown'), ('چراغ پارکینگ رو روشن کن', 'unknown'),
    ('گرمکن صندلی روشن است؟', 'unknown'), ('قفل نکن', 'unknown'),
    ('شیشه رو سی درصد پایین بیار', 'unknown'),
]
