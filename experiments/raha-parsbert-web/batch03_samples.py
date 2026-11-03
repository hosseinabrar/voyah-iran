"""Frozen command/noisy-transcript cases, authored before model evaluation."""
from data import normalize

def samples(groups_override=None, include_heater=True):
    groups=[
      ('CONFIRM_CALL',{},'تایید تماس',['بله تماس بگیر','آره زنگ بزن','بله تایید می کنم','همین شماره رو بگیر','تماس رو تایید کن','بله زنگ بزن','آره تماس بگیر','شماره انتخاب شده رو بگیر','تاییدش کن','بله همین مخاطب رو بگیر']),
      ('DEACTIVATE_SCENE',{'scene':'current'},'خروج از سناریو',['سناریو فعلی رو خاموش کن','از حالت کمپ خارج شو','حالت کارواش رو غیر فعال کن','سناریوی فعلی رو متوقف کن','حالت مراقبت از کودک رو خاموش کن','از حالت مهمانی خارج شو','سناریو رو غیر فعال کن','حالت چرت رو خاموش کن','از سناریوی کمپینگ خارج شو','سناریوی فعال رو ببند']),
      ('NAVIGATE_TO_SAVED_PLACE',None,'مسیریابی به مقصد ذخیره شده',['تعمیرگاه شاه پری رو مسیریابی کن','مسیر تعمیرگاه شاه پری رو نشون بده','بریم تعمیرگاه شاه پری','مسیریابی به تعمیرگاه شاه پری رو شروع کن','منو ببر تعمیرگاه شاه پری','نمایندگی مرکزی سروش رو مسیریابی کن','مسیر نمایندگی مرکزی سروش رو نشون بده','بریم نمایندگی مرکزی سروش','مسیریابی به نمایندگی مرکزی سروش رو شروع کن','منو ببر نمایندگی مرکزی سروش']),
      ('NEXT_MEDIA',{},'رفتن به آهنگ بعدی',['آهنگ بعدی رو پخش کن','برو آهنگ بعدی','ترک بعدی رو بذار','موزیک بعدی رو پخش کن','آهنگ رو ببر بعدی','این آهنگ رو رد کن','برو ترک بعد','قطعه بعدی رو پخش کن','موسیقی بعدی رو بذار','پخش رو ببر روی آهنگ بعد']),
      ('OPEN_CAMERA',{'view':'default'},'باز کردن دوربین خودرو',['دوربین رو باز کن','دوربین ماشین رو نشون بده','تصویر دوربین رو بیار','صفحه دوربین رو باز کن','اپ دوربین خودرو رو باز کن','برو تو دوربین','دوربین ماشین رو بیار بالا','نمای دوربین رو نشون بده','برنامه دوربین رو اجرا کن','دوربین خودرو رو نمایش بده']),
      ('OPEN_CHARGE_PORT',{},'باز کردن درگاه شارژ',['درگاه شارژ رو باز کن','در شارژر رو باز کن','دریچه شارژ رو باز کن','می خوام ماشین رو شارژ کنم','می خوام شارژر رو وصل کنم','درپوش شارژ رو باز کن','در پورت شارژ رو باز کن','می خوام باتری ماشین رو شارژ کنم','درب شارژ خودرو رو باز کن','پورت شارژ رو باز کن']),
      ('OPEN_DEFAULT_MAP',{},'باز کردن برنامه نقشه',['برنامه نقشه رو باز کن','نقشه رو بیار بالا','اپ نقشه رو اجرا کن','برو تو برنامه نقشه','برنامه مسیریابی رو باز کن','نقشه پیشفرض رو باز کن','نقشه رو نشون بده','اپ مسیریابی رو اجرا کن','صفحه نقشه رو باز کن','برنامه نقشه رو بیار']),
      ('OPEN_DEFAULT_MUSIC',{},'باز کردن برنامه موسیقی',['برنامه موزیک رو باز کن','اپ موسیقی رو بیار بالا','برو تو برنامه موسیقی','موزیک پلیر رو باز کن','برنامه پخش موسیقی رو اجرا کن','اپ موزیک پیشفرض رو باز کن','صفحه برنامه موسیقی رو نشون بده','برنامه موزیک رو اجرا کن','اپ پخش آهنگ رو باز کن','برنامه موسیقی رو بیار']),
      ('OPEN_FUEL_FLAP',{},'باز کردن در باک',['در باک رو باز کن','درب باک بنزین رو باز کن','می خوام بنزین بزنم','می خوام به ماشین بنزین بزنم','دریچه باک رو باز کن','درپوش باک رو باز کن','درب سوخت خودرو رو باز کن','باک بنزین رو باز کن','در باک ماشین رو باز کن','می خوام سوخت گیری کنم']),
      ('OPEN_OEM_RADIO',{},'باز کردن رادیو',['رادیو رو باز کن','برنامه رادیو رو اجرا کن','رادیوی ماشین رو بیار بالا','برو تو رادیو','اپ رادیو رو باز کن','صفحه رادیو رو نشون بده','رادیو خودرو رو اجرا کن','برنامه رادیو رو بیار','رادیوی ماشین رو باز کن','برو تو برنامه رادیو'])]
    groups=groups_override or groups
    rows=[];seen=set()
    def add(action,text,params,policy,kind,held=False,group=None,alternatives=None):
        text=normalize(text)
        if text in seen:return False
        seen.add(text);rows.append(dict(group=group or action,text=text,action=action,parameters=params,policy=policy,kind=kind,split='holdout' if held else 'train',context='pending_call_confirmation' if action=='CONFIRM_CALL' and policy!='reject' else 'none',alternatives=alternatives or []));return True
    neg=['{} رو انجام نده','نمی خوام {} انجام بشه','{} رو اجرا نکن','{} یعنی چی؟','چرا {} انجام نشد؟','درباره {} توضیح بده','آیا {} ممکنه؟','اگر بارون اومد {} رو انجام بده','اگه فردا خواستم {} رو اجرا کن','وقتی رسیدیم {} رو انجام بده','دیروز {} رو انجام دادم','قبلا {} انجام شده بود','علی گفت {} رو انجام داده','من درباره {} حرف می زدم','{} رو سرخ کن','{} رو بخور','{} رو مثل پیتزا بپز','{} رو هم انجام بده هم انجام نده','برای {} یه شعر بگو','{} رو به رنگ خوشحالی بو کن']
    for action,params,noun,phrases in groups:
        start=len(rows)
        for i,text in enumerate(phrases):
            p=params[i] if isinstance(params,list) else params if params is not None else {'destination':'shahpary' if i<5 else 'soroush_central'}
            nop=text.replace(' رو ',' ').replace(' را ',' ')
            variants=[text,nop,'لطفا '+nop,'رها '+nop,'بی زحمت '+nop,'میشه '+text]
            n=0
            for v in variants:
                if add(action,v,p,'correct','particle-variant',i%5==4):n+=1
                if n==4:break
            # A lost verb or کن→کم may be rejected; never perform a different action.
            noise=[text.rsplit(' ',1)[0], text.replace('کن','کم') if 'کن' in text else text+' کم']
            for j,v in enumerate(noise):
                if not add(action,v,p,'correct_or_reject','asr-noise',i%5==4):add(action,'خب '+v,p,'correct_or_reject','asr-noise',i%5==4)
        for i,t in enumerate(neg):add('UNKNOWN',t.format(noun),{},'reject','invalid',i%5==4,group=action)
        extra=0
        while len(rows)-start<80:
            p=params[extra%10] if isinstance(params,list) else params if params is not None else {'destination':'shahpary' if extra%10<5 else 'soroush_central'}
            add(action,'الان '+phrases[extra%10],p,'correct','particle-variant');extra+=1
        assert len(rows)-start==80,(action,len(rows)-start)
    on={'seat':'driver','enabled':True};off={'seat':'driver','enabled':False,'level':0}
    heat=['گرمکن رو روشن کن','گرمکن روشن کن','گرم کن رو روشن کن','گرم کن روشن کن','گرمکن رو روی روشن بذار','گرمکن روی روشن بذار','گرمکن روشن','گرمکن روشن کم','گرم کن روشن کم','گرمکن رو روشن کم','گرمکن صندلی روشن','گرمکن صندلی روشن کم','گرمکن رو لطفا روشن کن','لطفا گرمکن روشن','گرمکن صندلی راننده رو روشن کن','گرمکن صندلی راننده روشن کن','گرمکن رو خاموش کن','گرمکن خاموش کن','گرمکن خاموش','گرمکن خاموش کم']
    for i,t in enumerate(heat if include_heater else []):
        p=off if 'خاموش' in t else on
        alternatives=[] if 'خاموش' in t else [{**on,'level':l} for l in [1,2,3,'min','max']]
        add('SET_SEAT_HEATING',t,p,'correct_or_reject','heater-not-climate',False,alternatives=alternatives)
    assert len(rows)==80*len(groups)+(20 if include_heater else 0)
    return rows
if __name__=='__main__':print(len(samples()))
