"""Authored second batch, frozen before inference; no vehicle execution."""
from data import normalize

def samples():
    rows=[]
    def add_group(action,positive,noun):
        assert len(positive)==30,(action,len(positive))
        for i,(text,params) in enumerate(positive):
            rows.append(dict(group=action,text=normalize(text),action=action,parameters=params,valid=True,split='holdout' if i%5==4 else 'train',context='pending_call_confirmation' if action=='CANCEL_DIALOG' else 'none'))
        templates=[
          '{} رو انجام نده','اصلا {} رو نمی خوام','لطفا {} رو اجرا نکن','{} لازم نیست','نمی خوام الان {} انجام بشه',
          '{} یعنی چی؟','چرا {} انجام نشد؟','آیا {} ممکنه؟','روش {} چیه؟','درباره {} توضیح بده',
          'اگر بارون اومد {} رو انجام بده','اگه فردا خواستم {} رو اجرا کن','وقتی رسیدیم {} رو انجام بده','بعد از پیاده شدنم {} رو اجرا کن','اگه بچه خوابید {} رو انجام بده',
          'دیروز {} رو انجام دادم','قبلا {} انجام شده بود','علی گفت {} رو انجام داده','من درباره {} حرف می زدم','یادم هست {} رو انجام داده بودم',
          '{} رو سرخ کن','{} رو به سیاره مشتری بفرست','{} رو بخور','{} رو مثل پیتزا بپز','{} رو دانلود کن',
          '{} رو هم انجام بده هم انجام نده','برای {} یه شعر بگو','شاید سال بعد {} رو بخوام','{} رو به رنگ خوشحالی بو کن','من داشتم خواب {} رو می دیدم']
        for i,t in enumerate(templates):
            rows.append(dict(group=action,text=normalize(t.format(noun)),action='UNKNOWN',parameters={},valid=False,split='holdout' if i%5==4 else 'train',context='none'))
    for a,names in [('ADJUST_SUNROOF_POSITION',['سانروف','سقف شیشه ای','شیشه سقف']),('ADJUST_SUNSHADE_POSITION',['پرده سقف','پرده سانروف','سایه بان سقف'])]:
        pos=[]
        for t in ['{} رو یه کم {} کن','{} رو ده درصد بیشتر {} کن','لطفا {} رو کمی {} کن','می خوام {} یه ذره {} بشه','{} رو یک پله {} کن']:
            for n in names:
                for v,d in [('باز',10),('بسته',-10)]:pos.append((t.format(n,v),{'deltaPercent':d}))
        add_group(a,pos,'تنظیم مقدار باز بودن '+names[0])
    pos=[]
    for t in ['شیشه {} رو یه کم {} کن','شیشه {} رو ده درصد بیشتر {} کن','لطفا شیشه {} رو کمی {} کن']:
        for n,w in [('راننده','driver'),('شاگرد','passenger'),('عقب چپ','rearLeft'),('عقب راست','rearRight'),('همراه','passenger')]:
            for v,d in [('باز',10),('بسته',-10)]:pos.append((t.format(n,v),{'window':w,'deltaPercent':d}))
    add_group('ADJUST_WINDOW_POSITION',pos,'تغییر مقدار باز بودن شیشه')
    pos=[]
    for t in ['{} رو یک پله {} کن','{} را کمی {} کن','لطفا {} رو {} کن','می خوام {} یک سطح {} بشه','{} رو یه مقدار {} کن']:
        for n in ['حساسیت برف پاک کن','حساسیت سنسور باران','حساسیت برف پاک کن اتوماتیک']:
            for v,d in [('کم',-1),('زیاد',1)]:pos.append((t.format(n,v),{'delta':d}))
    add_group('ADJUST_WIPER_SENSITIVITY',pos,'تنظیم حساسیت برف پاک کن')
    pos=[(t.format(n),{'contact_query':n}) for t in ['یه زنگ به {} بزن','به {} زنگ بزن','با {} تماس بگیر','شماره {} رو بگیر','لطفا به {} یه زنگ بزن','می خوام با {} تماس بگیری'] for n in ['علی','رضا','مریم','حسین','سارا']]
    add_group('CALL_CONTACT',pos,'تماس با علی')
    cancellations=['لغو','لغوش کن','لغو کن','تماس رو لغو کن','تماس رو کنسل کن','منصرف شدم','بی خیال تماس','تماس نگیر','نه زنگ نزن','نه تماس نگیر','درخواست تماس رو لغو کن','این تماس رو کنسل کن','بی خیالش شو','ولش کن','نمی خوام تماس بگیری']
    add_group('CANCEL_DIALOG',[(p+t,{}) for p in ['', 'رها '] for t in cancellations],'لغو درخواست تماس')
    for a,names in [('CLOSE_SUNROOF',['سانروف','سقف شیشه ای','شیشه سقف']),('CLOSE_TAILGATE',['صندوق عقب','درب صندوق','در صندوق عقب'])]:
        templates=['{} رو ببند','{} را کامل ببند','لطفا {} بسته بشه','ببند {} رو','می خوام {} بسته بشه','{} رو ببندش','{} رو کاملا بسته کن','بی زحمت {} رو ببند','{} باید بسته بشه','رها {} رو کامل ببند']
        add_group(a,[(t.format(n),{}) for t in templates for n in names],'بستن '+names[0])
    pos=[(t.format(n),{'window':w}) for t in ['شیشه {} رو ببند','شیشه {} رو کامل ببند','لطفا شیشه {} رو بکش بالا','شیشه {} کاملا بسته بشه','می خوام شیشه {} رو ببندی'] for n,w in [('راننده','driver'),('همراه','passenger'),('عقب چپ','rearLeft'),('عقب راست','rearRight'),('شاگرد','passenger'),('همه درها','all')]]
    add_group('CLOSE_WINDOW',pos,'بستن شیشه خودرو')
    pos=[]
    for n,c in [('بامبو',1),('پیونی',2),('واتری میست',3)]:
        for t,e in [('رایحه {} رو روشن کن',True),('رایحه {} رو خاموش کن',False),('خوشبوکننده {} رو فعال کن',True),('رایحه {} رو غیر فعال کن',False)]:pos.append((t.format(n),{'channel':c,'enabled':e}))
        for t,l in [('شدت رایحه {} رو کم بذار','low'),('شدت رایحه {} رو متوسط بذار','medium'),('شدت رایحه {} رو زیاد بذار','high')]:pos.append((t.format(n),{'channel':c,'intensity':l}))
        for word,m in [('نیم ساعت',30),('یک ساعت',60)]:pos.append((f'رایحه {n} رو برای {word} روشن کن',{'channel':c,'durationMinutes':m,'enabled':True}))
        pos.append((f'رایحه {n} رو نیم ساعت با شدت زیاد روشن کن',{'channel':c,'durationMinutes':30,'enabled':True,'intensity':'high'}))
    add_group('CONFIGURE_FRAGRANCE',pos,'تنظیم رایحه بامبو')
    assert len(rows)==len({r['text'] for r in rows})==600
    return rows
