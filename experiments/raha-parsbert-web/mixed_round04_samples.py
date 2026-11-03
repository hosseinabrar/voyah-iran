"""Round four: amounts, explicit stop requests, and contrasting targets."""
from data import normalize

def samples(known):
 groups=[]
 def group(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def row(t,a,**p):return t,a,p
 numbers=[('چهار',4),('یازده',11),('شانزده',16),('بیست و دو',22),('بیست و هشت',28)]
 group('volume-absolute',(row(t.format(n=n),'SET_AUDIO_VOLUME',channel='media',level=v) for t in ['صدای موزیک زیاد نیست تنظیمش کن روی عدد {n}','بلندی آهنگ دقیقا {n} باشه نه بیشتر','برای گوش دادن موسیقی عدد صدا رو {n} انتخاب کن','میزان صدای پخش موسیقی رو برسون به {n}'] for n,v in numbers))
 group('screen-brightness',(row(t.format(d=d),'SET_DISPLAY_BRIGHTNESS',display=display,percent=v) for t,v in [('نور {d} زیاده روشناییش رو چهل درصد کن',40),('نور {d} کمه روشناییش رو هفتاد درصد کن',70),('میزان روشن بودن {d} روی پنجاه درصد باشه',50),('برای دیدن بهتر نور {d} رو هشتاد درصد تنظیم کن',80)] for d,display in [('صفحه مرکزی','central'),('نمایشگر شاگرد','passenger'),('صفحه پشت فرمان','cluster'),('همه نمایشگرها','all'),('مانیتور همراه','passenger')]))
 group('fan-fixed',(row(t.format(n=n),'SET_FAN_SPEED',zone='front',level=v) for t in ['باد تهویه تنده فن کابین رو روی {n} بذارش','سرعت دمیدن هوای کابین رو {n} تنظیمش کن','برای تهویه عدد فن رو دقیقا {n} می خوام','بذار فن اتاق ماشین با درجه {n} کار کنه'] for n,v in [('صفر',0),('دو',2),('چهار',4),('شش',6),('هفت',7)]))
 group('shade-fixed',(row(t.format(n=n),'SET_SUNSHADE_POSITION',position=v) for t in ['پرده زیر شیشه سقف رو {n} درصد بازش نگه دار','برای تنظیم نور پرده سانروف {n} درصد باز باشه','میزان باز بودن سایبان سقف رو روی {n} درصد تنظیمش کن','پرده بالای سر رو به اندازه {n} درصد کنار بزن'] for n,v in [('ده',10),('سی',30),('پنجاه',50),('هفتاد',70),('نود',90)]))
 stops=[('بخارزدای جلو','SET_FRONT_DEFROSTER',{}),('بخارزدای عقب','SET_REAR_DEFROSTER',{}),('کولر','SET_AC_POWER',{}),('تهویه اتوماتیک','SET_CLIMATE_AUTO',{'zone':'front'}),('نورپردازی داخلی','SET_AMBIENT_LIGHT_POWER',{}),('رایحه','SET_FRAGRANCE_POWER',{}),('گرمکن صندلی راننده','SET_SEAT_HEATING',{'seat':'driver'}),('سردکن صندلی شاگرد','SET_SEAT_VENTILATION',{'seat':'passenger'}),('ماساژور صندلی راننده','SET_SEAT_MASSAGE',{'seat':'driver'}),('شارژ زمانبندی شده','SET_SCHEDULED_CHARGING',{})]
 group('explicit-stop',(row(t.format(x=x),a,enabled=False,**p) for t in ['دیگه به {x} احتیاج ندارم خاموشش کن','کار {x} تموم شده بذارش روی غیرفعال'] for x,a,p in stops))
 corrections=[('کولر رو روشن نکن فقط خاموشش کن','SET_AC_POWER',dict(enabled=False)),('ماساژور راننده رو خاموش نکن فقط روشنش کن','SET_SEAT_MASSAGE',dict(enabled=True,seat='driver')),('سردکن صندلی همراه رو روشن نکن فقط خاموشش کن','SET_SEAT_VENTILATION',dict(enabled=False,seat='passenger')),('بخارزدای جلو رو خاموش نکن فقط فعالش کن','SET_FRONT_DEFROSTER',dict(enabled=True)),('نورپردازی داخلی رو روشن نکن فقط غیرفعالش کن','SET_AMBIENT_LIGHT_POWER',dict(enabled=False)),('شارژ زمانبندی رو خاموش نکن فقط فعالش کن','SET_SCHEDULED_CHARGING',dict(enabled=True)),('ایزی انتری رو فعال نکن فقط خاموشش کن','SET_EASY_ENTRY',dict(enabled=False)),('گرمکن صندلی خودم رو خاموش نکن فقط روشنش کن','SET_SEAT_HEATING',dict(enabled=True,seat='driver')),('رایحه رو فعال نکن فقط خاموشش کن','SET_FRAGRANCE_POWER',dict(enabled=False)),('ایزی اگزیت رو خاموش نکن فقط فعالش کن','SET_EASY_EXIT',dict(enabled=True))]
 group('negated-alternative',[(t,a,p) for t,a,p in corrections]+[(t.replace('فقط','در عوض'),a,p) for t,a,p in corrections])
 group('temperature-delta',(row(t.format(n=n),'ADJUST_CABIN_TEMPERATURE',zone=z,delta=sign*v) for t,z,sign in [('هوای طرف راننده سرده دماش {n} درجه بره بالا','driver',1),('هوای طرف همراه گرمه دماش {n} درجه بیاد پایین','passenger',-1),('دمای کل کابین رو {n} درجه از الان گرمترش کن','front',1),('دمای کل اتاق رو {n} درجه از الان سردترش کن','front',-1)] for n,v in [('یک',1),('دو',2),('چهار',4),('شش',6),('هشت',8)]))
 group('child-lock-side',(row(t.format(s=s,op=op),'SET_CHILD_LOCK',side=side,enabled=on) for t in ['قفل کودک {s} رو {op} قرارش بده','تنظیم قفل کودک سمت {s} باید {op} باشه','قفل ایمنی کودک {s} رو به حالت {op} ببر','برای در سمت {s} قفل کودک روی {op} باشه','قفل کودک در {s} رو بذارش {op}'] for s,side in [('چپ','left'),('راست','right')] for op,on in [('فعال',True),('غیرفعال',False)]))
 rows=[];seen=set(known)
 styles=[lambda t:'رها برای من '+t,lambda t:'لطفا الان '+t,lambda t:t+' لطفا رها',lambda t:'خب الان '+t,lambda t:'می خوام '+t,lambda t:'خواهش می کنم '+t,lambda t:t+' ممنون']
 def put(text,a,p,f,core,policy):
  text=normalize(text)
  if text in seen:return False
  seen.add(text);rows.append(dict(text=text,action=a,parameters=p,alternatives=[],family=f,core=normalize(core),policy=policy));return True
 for f,items in groups:
  for t,a,p in items:
   count=0
   for style in styles:
    count+=put(style(t),a,p,f,t,'correct')
    if count==5:break
   assert count==5
 invalid=[
 'بخارزدای صندلی رو هفتاد درصد شارژ کن',
 'در باک رو به جای بنزین با موزیک پر کن',
 'سرعت فن رو با حالت شخصی ماساژ بده',
 'دمای نورپردازی داخلی رو بیست و پنج درجه کن',
 'قفل کودک سمت راست رو با باد سرد خنک کن',
 'پرده سانروف رو روی حالت اقتصادی رانندگی بذار',
 'صدای پخش رو با درجه سردکن صندلی بشور',
 'شیشه راننده رو به اندازه دمای بیرون خوشبو کن',
 'چراغ اتاق پذیرایی رو از نمایشگر شاگرد خاموش کن',
 'پنجره حمام رو با ایزی اگزیت باز کن',
 'فن کابین رو درجه منفی چهار بذار',
 'دمای اتاق ماشین روی هفتصد درجه باشه',
 'پرده سقف رو صد و پنجاه درصد باز کن',
 'روشنایی صفحه مرکزی منفی شصت درصد باشه',
 'شدت گرمکن صندلی رو روی نوزده تنظیم کن',
 'همین الان فن رو هم صفر کن هم درجه هفت',
 'قفل کودک سمت چپ هم فعال باشه هم غیرفعال',
 'رایحه رو همزمان روشن و خاموش نگه دار',
 'دمای همراه رو همزمان چهار درجه بالا و چهار درجه پایین ببر',
 'پرده سقف رو همزمان کامل باز و کامل بسته کن',
 'وقتی میگن بخارزدای جلو رو خاموش کن منظورشون چیه',
 'عبارت قفل کودک سمت راست رو فعال کن رو فقط ترجمه کن',
 'برای داستانم جمله کولر رو روشن کن رو بنویس',
 'می پرسم نورپردازی داخلی چجوری کار می کنه تغییرش نده',
 'همراه قبلا گفته صندلیم سرده ولی الان گرمکن لازم نیست',
 'دیروز سردکن صندلی رو خاموش کردم الان دست نزن',
 'من فقط دارم نحوه بستن پنجره رو توضیح میدم',
 'توی کتاب نوشته در صندوق رو باز کن من دارم می خونمش',
 'درباره افزایش صدای موزیک مثال بزن ولی صدا رو تغییر نده',
 'می خوام بدونم ماساژور صندلی خوبه یا نه روشنش نکن',
 'همین چیز رو اون طرفش یه جور دیگه بذار',
 'مقدار اون رو اندازه این کن',
 'سمت همون قبلی رو به حالت دیگه تغییر بده',
 'اون گزینه که یادم نیست اسمش چیه رو تنظیم کن',
 'همونی که تو ذهنمه رو درستش کن',
 'برای سرد شدن خورشید کولر شهر رو روشن کن',
 'با گرمکن ماشین آب استخر رو داغ کن',
 'موتور ماشین رو با بوی رایحه تبدیل به هواپیما کن',
 'به جای روشنایی نمایشگر تعداد چرخ ها رو زیاد کن',
 'ارتفاع کوه روبرو رو با ایزی انتری کم کن']
 assert len(invalid)==40
 for t in invalid:
  n=0
  for style in styles:
   n+=put(style(t),'UNKNOWN',{},'invalid-ambiguous-or-narrative',t,'reject')
   if n==5:break
  assert n==5
 assert len(rows)==1000
 return rows
