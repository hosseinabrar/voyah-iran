"""Compositional round two: gold labels defined before inference."""
from data import normalize

def samples(known):
 rows=[];seen=set(known)
 def add(text,a,p,f,core,policy='correct'):
  text=normalize(text)
  if text in seen:return
  seen.add(text);rows.append(dict(text=text,action=a,parameters=p,alternatives=[],family=f,core=core,policy=policy))
 def group(name,candidates,n=100):
  before=len(rows)
  for text,a,p,core in candidates:
   add(text,a,p,name,core)
   if len(rows)-before==n:break
  assert len(rows)-before==n,(name,len(rows)-before)
 zones=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('کل کابین','front'),('اتاق ماشین','front')]
 nums=[('دو',2),('سه',3),('چهار',4),('پنج',5)]
 def temps():
  for t in ['{z} سردشه دماش رو {n} درجه بیشتر کن','برای گرم شدن {z} دما رو {n} درجه بالا ببر','{z} گرماشه دماش رو {n} درجه کمتر کن','برای خنک شدن {z} دما رو {n} درجه پایین بیار','رها دمای {z} رو {n} درجه گرم تر کن','رها دمای {z} رو {n} درجه سردتر کن']:
   sign=-1 if any(x in t for x in ['کمتر','پایین','سردتر']) else 1
   for z,zone in zones:
    for n,v in nums:yield t.format(z=z,n=n),'ADJUST_CABIN_TEMPERATURE',dict(zone=zone,delta=sign*v),t
 group('temperature-relative',temps())
 def absolute():
  for t in ['هوای {z} اذیت می کنه دما رو بذار {n} درجه','برای {z} دمای {n} درجه می خوام','رها دمای {z} روی {n} درجه ثابت باشه','لطفا حرارت {z} رو به {n} درجه برسون','تنظیم دمای {z} رو ببر روی {n} درجه']:
   for z,zone in zones:
    for n,v in [('هجده',18),('بیست و یک',21),('بیست و چهار',24),('بیست و هفت',27),('سی',30)]:yield t.format(z=z,n=n),'SET_CABIN_TEMPERATURE',dict(zone=zone,degreesC=float(v)),t
 group('temperature-absolute',absolute())
 def windows():
  for t in ['هوای تازه می خوام پنجره {z} رو {n} درصد باز کن','رها شیشه {z} رو تا {n} درصد پایین بکش','پنجره {z} به اندازه {n} درصد باز بمونه','لطفا شیشه {z} رو {n} درصد بده پایین','برای هوا خوردن پنجره {z} رو {n} درصد بازش کن']:
   for z,w in [('جلو سمت راننده','driver'),('جلو سمت همراه','passenger'),('عقب سمت راننده','rearLeft'),('عقب سمت همراه','rearRight'),('جلو سمت شاگرد','passenger')]:
    for n,v in [('بیست',20),('چهل',40),('پنجاه',50),('هفتاد',70),('هشتاد',80)]:yield t.format(z=z,n=n),'SET_WINDOW_POSITION',dict(window=w,percent=v),t
 group('window-direction-amount',windows())
 def seats():
  for t,a in [('پشت {z} سرده صندلیش رو با شدت {n} گرم کن','SET_SEAT_HEATING'),('نشیمن {z} داغه صندلیش رو با شدت {n} خنک کن','SET_SEAT_VENTILATION'),('رها گرمکن صندلی {z} رو بذار روی سطح {n}','SET_SEAT_HEATING'),('رها سردکن صندلی {z} رو بذار روی سطح {n}','SET_SEAT_VENTILATION'),('لطفا صندلی {z} رو با درجه {n} گرمش کن','SET_SEAT_HEATING'),('لطفا صندلی {z} رو با درجه {n} خنکش کن','SET_SEAT_VENTILATION'),('سرمای صندلی {z} اذیتش می کنه گرمکنش روی {n} باشه','SET_SEAT_HEATING'),('صندلی {z} گرمه تهویه صندلیش روی {n} باشه','SET_SEAT_VENTILATION')]:
   for z,s in [('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('بغل','passenger'),('خودم','driver')]:
    for n,v in [('یک',1),('دو',2),('سه',3)]:yield t.format(z=z,n=n),a,dict(seat=s,enabled=True,level=v),t
 group('seat-temperature-level',seats())
 def massage():
  contexts=['از سفر طولانی','از نشستن زیاد','از رانندگی طولانی','از جاده پر دست انداز','از این همه نشستن','بعد از این سفر','از خستگی','بعد از چند ساعت نشستن']
  for t in ['{c} کمر {z} کوفته شده ماساژ صندلیش رو روشن کن','{c} پشت {z} درد گرفته ماساژورش رو فعال کن','{c} عضلات پشت {z} گرفته صندلیش ماساژ بده','{c} {z} خسته شده ماساژ صندلیش رو راه بنداز']:
   for z,s in [('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('خودم','driver')]:
    for c in contexts:yield t.format(c=c,z=z),'SET_SEAT_MASSAGE',dict(seat=s,enabled=True),t
 group('massage-context-target',massage())
 def audio():
  for t,sign in [('مسافر خوابیده صدای موزیک رو {n} درجه کم کن',-1),('موسیقی رو خوب نمی شنوم صداش رو {n} درجه زیاد کن',1),('دارم با همراه حرف می زنم موزیک رو {n} پله آروم تر کن',-1),('صدای جاده زیاده موسیقی رو {n} پله بلندتر کن',1),('گوشم اذیت میشه بلندی آهنگ رو {n} واحد پایین بیار',-1),('این قطعه رو دوست دارم صداش رو {n} واحد بالاتر ببر',1),('برای صحبت کردن صدای آهنگ رو {n} درجه کمتر کن',-1),('می خوام آهنگ رو بهتر بشنوم صداش رو {n} درجه بیشتر کن',1),('حواسم به جاده باشه موزیک رو {n} پله کم کن',-1),('آهنگ خیلی آرومه {n} پله بلندترش کن',1),('رها صدای موسیقی رو {n} پله کاهش بده',-1)]:
   for n,v in [('یک',1),('دو',2),('سه',3),('چهار',4),('پنج',5),('شش',6),('هفت',7),('هشت',8),('نه',9),('ده',10)]:yield t.format(n=n),'ADJUST_AUDIO_VOLUME',dict(channel='media',delta=sign*v),t
 group('audio-context-amount',audio())
 def fog():
  for t in ['{c} شیشه {z} مه بسته بخارزداش رو روشن کن','{c} پنجره {z} بخار کرده مه زداش رو فعال کن','{c} از شیشه {z} چیزی پیدا نیست بخارش رو پاک کن','{c} شیشه {z} تار شده بخارزدایی رو برای همون شیشه فعال کن','{c} روی شیشه {z} بخار نشسته برطرفش کن','{c} شیشه {z} مه گرفته پاکش کن']:
   for z,a in [('جلو','SET_FRONT_DEFROSTER'),('عقب','SET_REAR_DEFROSTER')]:
    for c in ['توی این بارون','تو هوای سرد','از رطوبت داخل','بعد از سوار شدن','توی این هوای نمناک','از اختلاف دما','الان','رها','لطفا','امروز']:
     yield t.format(c=c,z=z),a,dict(enabled=True),t
 group('visibility-target',fog())
 def convenience():
  pairs=[('برای گذاشتن ساک توی صندوق در عقب صندوق رو باز کن','OPEN_TAILGATE',{}),('بار رو گذاشتم تو صندوق در عقب صندوق رو ببند','CLOSE_TAILGATE',{}),('نازل سوخت آماده است درپوش باک بنزین رو باز کن','OPEN_FUEL_FLAP',{}),('کابل برق دستمه درپوش محل شارژ ماشین رو باز کن','OPEN_CHARGE_PORT',{}),('شیشه سقف رو ببند بارون روی سرمون میریزه','CLOSE_SUNROOF',{}),('برای دیدن آسمون شیشه سقف رو کامل باز کن','OPEN_SUNROOF',{}),('هر دو آینه رو جمع کن جا برای پارک تنگه','SET_MIRROR_FOLD',dict(side='all',folded=True)),('هر دو آینه رو باز کن می خوام از پارک دربیام','SET_MIRROR_FOLD',dict(side='all',folded=False)),('این آهنگ رو رد کن قطعه بعدیش رو بذار','NEXT_MEDIA',{}),('از این قطعه برگرد به آهنگ قبلی','PREVIOUS_MEDIA',{})]
  contexts=['رها الان','لطفا همین حالا','برای من','اگه می تونی','وقتش شده','زحمت بکش','من می خوام','الان لطف کن','رها لطفا برای من','همین الان لطفا','خواهشا']
  for c in contexts:
   for t,a,p in pairs:yield c+' '+t,a,p,t
 group('purpose-action',convenience())
 bad=[
 'برای گرم شدن اتاق خواب گرمکن صندلی خونه رو روشن کن',
 'کمر کیفم گرفته با ماساژور ماشین درستش کن',
 'برای خنک شدن نوشابه شیشه راننده رو گرم کن',
 'صدای آهنگ رو پنج درجه گرم تر کن',
 'باک بنزین رو سی درصد ماساژ بده',
 'سردکن صندلی شاگرد رو روی منفی هفت بذار',
 'دمای کابین رو روی صد و پنجاه درجه بذار',
 'شیشه عقب رو دویست درصد پایین بده',
 'همزمان دمای راننده رو دو درجه زیاد و دو درجه کم کن',
 'هر دو آینه رو هم باز نگه دار هم تا کن',
 'شیشه شاگرد رو کامل ببند و همزمان کامل باز نگه دار',
 'ماساژ صندلی رو هم خاموش کن هم روشن نگه دار',
 'دیروز بهش گفتم صندلی راننده رو گرم کن ولی الان لازم نیست',
 'دارم جمله صدای آهنگ رو زیاد کن رو برایت نقل می کنم',
 'معنی جمله شیشه عقب رو ببند رو توضیح بده',
 'تو داستانم ماشین میگه کولر رو روشن کن این رو ترجمه کن',
 'کدوم گرمکن رو باید روشن کنم فقط راهنمایی می خوام',
 'اون یکی سمتش رو به اندازه همون قبلی تغییر بده',
 'پنجره آشپزخانه سمت راست رو تا نصفه ببند',
 'برای خوابیدن بچه نور ماه بیرون ماشین رو کم کن']
 prefixes=['رها','لطفا','خب','الان','یه لحظه','ببین','راستی','پس','برای من','خواهشا','رها لطفا']
 before=len(rows)
 for c in prefixes:
  for t in bad:add(c+' '+t,'UNKNOWN',{},'reject-combinations',t,'reject')
  if len(rows)-before>=200:break
 assert len(rows)==1000,len(rows)
 return rows
