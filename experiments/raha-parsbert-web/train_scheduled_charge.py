"""Reviewed toggle-only corpus, including explicit unsupported/contradictory requests.

No appointment times are supported. UNKNOWN examples are learned by the model,
not counted as successes merely because the experimental rule guard rejects them.
"""
import asyncio
import train_easy_access as training
from data import normalize

easy_examples = training.examples
SCHEDULE_ALIASES = [
    'شارژ زمانبندی شده', 'شارژ زمان بندی شده', 'زمانبندی شارژ', 'زمان بندی شارژ',
    'شارژ برنامه ریزی شده', 'شارژ سر ساعت', 'شارژ در ساعت مشخص',
    'شارژ در ساعت تعیین شده', 'اسکجول شارژ', 'اسکجولد شارژ', 'اسک جول شارژ',
    'scheduled charge', 'scheduled charging',
]

def examples():
    rows = easy_examples()
    def add(text, action='SET_SCHEDULED_CHARGING', enabled=True):
        rows.append(dict(text=normalize(text), action=action,
                         parameters={} if action == 'UNKNOWN' else {'enabled': enabled}))
    for noun in SCHEDULE_ALIASES:
        for enabled, states in [(True, ['روشن', 'فعال']), (False, ['خاموش', 'غیر فعال', 'غیرفعال'])]:
            for state in states:
                for template in ['{n} رو {s} کن', '{n} را {s} کن', 'لطفا {n} رو {s} کن',
                                 '{n} رو بذار تو حالت {s}', '{n} رو روی حالت {s} بذار',
                                 'می تونی {n} رو {s} کنی', 'رها {n} رو {s} کن']:
                    add(template.format(n=noun, s=state), enabled=enabled)
        for template in ['{n} رو لغو کن', '{n} رو غیر فعالش کن']:
            add(template.format(n=noun), enabled=False)
    for subject in ['', 'ماشین رو ', 'خودرو رو ', 'باتری ماشین رو ']:
        for timing in ['سر ساعت', 'سر ساعت تعیین شده', 'در ساعت مشخص', 'بر اساس زمانبندی',
                       'بر اساس زمان بندی', 'طبق زمانبندی', 'طبق برنامه ذخیره شده']:
            for verb in ['شارژ کن', 'شارژش کن']:
                add(subject + timing + ' ' + verb)
    for noun in ['scheduled charging', 'scheduled charge']:
        for verb, enabled in [('enable', True), ('disable', False), ('turn on', True), ('turn off', False)]:
            add(verb+' '+noun, enabled=enabled)
    # Explicit non-vehicle objects, conflicting toggles, unsupported time edits,
    # statements/questions: never reinterpret these as an implicit toggle.
    for noun in SCHEDULE_ALIASES + sum(training.ALIASES.values(), []):
        for template in ['{n} رو روشن و خاموش کن', '{n} رو فعال و غیر فعال کن',
                         '{n} رو نصف کن', '{n} رو گرم کن', '{n} رو پنج درجه زیاد کن',
                         '{n} یعنی چی', 'دیروز {n} رو فعال کردم', '{n} رو روی وسط بذار']:
            add(template.format(n=noun), 'UNKNOWN')
        for value in ['یک', 'سه', 'هفت', 'ده']:
            add(noun+' رو '+value+' درجه کم کن', 'UNKNOWN')
            add(noun+' رو روی '+value+' تنظیم کن', 'UNKNOWN')
        for target in ['گوشی', 'موبایل', 'تلفن همراه', 'لپ تاپ', 'کامپیوتر', 'مرورگر', 'حساب بانکی']:
            add(noun+' رو برای '+target+' فعال کن', 'UNKNOWN')
            add(noun+' '+target+' رو روشن کن', 'UNKNOWN')
        add(noun+' رو هم روشن کن هم خاموش کن', 'UNKNOWN')
    for target in ['گوشی', 'گوشیم', 'موبایلم', 'تلفن همراه', 'لپ تاپ', 'تبلت']:
        for timing in ['طبق زمانبندی', 'سر ساعت', 'بر اساس زمان بندی', 'در ساعت مشخص']:
            add(target+' رو '+timing+' شارژ کن', 'UNKNOWN')
    for hour in ['یک', 'سه', 'شش', 'ده', 'دوازده']:
        add('ساعت شروع شارژ رو روی '+hour+' تنظیم کن', 'UNKNOWN')
        add('زمانبندی شارژ رو روی ساعت '+hour+' تنظیم کن', 'UNKNOWN')
    for text in [
        'شارژ رو ساعت سه شروع کن', 'ساعت شارژ رو بذار روی دو', 'زمان شارژ رو عوض کن',
        'شارژ زمانبندی شده رو از ساعت سه تا پنج تنظیم کن', 'فردا ساعت هشت ماشین رو شارژ کن',
        'شارژ گوشی رو زمانبندی کن', 'اسکجول شارژ موبایل رو فعال کن',
        'لپ تاپ رو سر ساعت شارژ کن', 'خروج آسان از برنامه رو فعال کن',
        'ورود آسان به سایت رو فعال کن', 'ایزی اگزیت بازی رو روشن کن',
        'تلویزیون رو با ورود آسان روشن کن', 'درخت رو ایزی اگزیت کن',
        'سانروف رو با شارژ زمانبندی شده گرم کن', 'شارژ سر ساعت غذا رو فعال کن',
        'ورود و خروج آسان رو فعال و خاموش کن',
    ]:
        add(text, 'UNKNOWN')
    # Politeness must not turn a contradictory/unsupported request into a toggle.
    base = list(rows)
    rows.extend(dict(r, text=normalize('لطفا '+r['text']+' برام')) for r in base
                if not r['text'].startswith(('رها', 'لطفا', 'میشه', 'می تونی')))
    return list({r['text']:r for r in rows}.values())

if __name__ == '__main__':
    training.examples = examples
    asyncio.run(training.main(steps=4000, report_name='scheduled-charge-report.json'))
