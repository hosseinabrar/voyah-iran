from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 sides=[('سمت فرمان','driver'),('سمت شاگرد جلو','passenger'),('عقب پشت راننده','rearLeft'),('عقب پشت همراه','rearRight'),('جلو سمت بغل','passenger')]
 add('window-opening',(r(t.format(s=s),'SET_WINDOW_POSITION',window=w,percent=n) for t,n in [('شیشه {s} رو تنظیم کن چهل درصدش باز بمونه',40),('بازشدگی پنجره {s} شصت درصد باشه',60),('پنجره {s} رو تا نیمه پایین ببر',50),('برای شیشه {s} سی درصد بازشدگی انتخاب کن',30)] for s,w in sides))
 zones=[('سمت فرمان','driver'),('جلو سمت همراه','passenger'),('طرف شاگرد','passenger'),('تمام کابین','front'),('فضای اتاق ماشین','front')]
 add('temperature-fixed',(r(t.format(s=s),'SET_CABIN_TEMPERATURE',zone=z,degreesC=float(n)) for t,n in [('تهویه {s} رو روی دمای شانزده درجه تنظیمش کن',16),('درجه حرارت {s} رو بیست و سه بذارش',23),('دمای {s} بیست و هفت درجه تنظیم بشه',27),('برای گرم شدن {s} دما رو روی سی بذار',30)] for s,z in zones))
 add('temperature-change',(r(t.format(s=s),'ADJUST_CABIN_TEMPERATURE',zone=z,delta=n) for t,n in [('دمای {s} رو نسبت به الان دو درجه پایین تر تنظیم کن',-2),('دمای {s} رو نسبت به الان پنج درجه بالاتر تنظیم کن',5),('دمای {s} سه درجه کاهش پیدا کنه',-3),('دمای {s} چهار درجه افزایش پیدا کنه',4)] for s,z in zones))
 seats=[('راننده','driver'),('همراه','passenger'),('شاگرد','passenger'),('خودم','driver'),('نفر بغلی','passenger')]
 add('seat-stop',(r(t.format(s=s),a,seat=seat,enabled=False,level=0) for t,a in [('کار سردکن صندلی {s} رو متوقف کن','SET_SEAT_VENTILATION'),('گرمکن صندلی {s} دیگه کار نکنه خاموشش کن','SET_SEAT_HEATING'),('ماساژور صندلی {s} رو خاموش می خوام','SET_SEAT_MASSAGE'),('تهویه صندلی {s} رو خاموش بذارش','SET_SEAT_VENTILATION')] for s,seat in seats))
 nums=[('یک',1),('چهار',4),('شش',6),('هفت',7),('ده',10)]
 add('volume-relative',(r(t.format(n=n),'ADJUST_AUDIO_VOLUME',channel='media',delta=sign*v) for t,sign in [('از بلندی صدای آهنگ {n} پله کمش کن',-1),('به بلندی صدای آهنگ {n} پله اضافه کن',1),('درجه صدای موسیقی رو {n} تا کمتر کن',-1),('درجه صدای موسیقی رو {n} تا بیشتر کن',1)] for n,v in nums))
 fan_nums=[('صفر',0),('یک',1),('سه',3),('پنج',5),('هفت',7)]
 add('fan-fixed',(r(t.format(n=n),'SET_FAN_SPEED',zone='front',level=v) for t in ['دمنده کابین با درجه {n} کار کنه','سطح {n} رو برای فن داخل اتاق انتخابش کن','دور فن تهویه روی {n} تنظیم بشه','تنظیم شدت فن کابین عدد {n} باشه'] for n,v in fan_nums))
 settings=[('شارژ سر ساعت','SET_SCHEDULED_CHARGING'),('ایزی انتری','SET_EASY_ENTRY'),('ایزی اگزیت','SET_EASY_EXIT'),('پخش عطر داخل ماشین','SET_FRAGRANCE_POWER'),('چراغ های تزئینی داخل','SET_AMBIENT_LIGHT_POWER')]
 add('switch-purpose',(r(t.format(s=s),a,enabled=b) for t,b in [('برای {s} وضعیت فعال رو انتخابش کن',True),('برای {s} وضعیت غیرفعال رو انتخابش کن',False),('تنظیم {s} رو در وضعیت روشن بذارش',True),('تنظیم {s} رو در وضعیت خاموش بذارش',False)] for s,a in settings))
 modes=[('اکونومی','eco'),('کامفورت','comfort'),('اسنو','snow'),('اوتینگ','outing'),('پرسونال','personal')]
 add('drive-modes',(r(t.format(m=m),'SET_DRIVE_MODE',mode=v) for t in ['مود حرکت ماشین رو ببر روی {m}','برای حالت رانندگی {m} رو انتخابش کن','تنظیم رانندگی روی مود {m} قرار بگیره','از مود {m} توی رانندگی استفاده کن'] for m,v in modes))
 bad=['سردکن صندلی رو برای جوش آوردن آب روشن کن', 'سانروف رو با فن هفت ماساژ بده', 'چراغ سقف رو با دمای صندلی تنظیم کن', 'گرمکن صندلی رو تا هفتاد درصد مثل پنجره پایین بکش', 'نورپردازی رو تو باک پر از گاز کن', 'فن ماشین رو به حافظه سوم صندلی تبدیل کن', 'پنجره شاگرد رو روی موج رادیویی تنظیم کن', 'ولوم آهنگ رو روی دمای سردکن صندلی بذار', 'مصرف سوخت شیشه رو با ماساژ کم کن', 'حالت رانندگی رو به درجه فن تبدیل کن', 'درجه فن داخل کابین روی صد و بیست باشه', 'دمای سمت راننده روی منفی هشتاد تنظیم بشه', 'بلندی موسیقی روی هزار و یک باشه', 'پنجره پشت راننده هزار درصد پایین بره', 'سطح ماساژ صندلی روی چهارصد باشه', 'دمای کابین همین الان هم شانزده باشه هم سی و دو', 'فن در یک لحظه هم صفر باشه هم هفت', 'سردکن صندلی هم فعال بمونه هم غیرفعال', 'پنجره شاگرد همزمان کامل بسته و کامل باز بمونه', 'مود رانندگی همزمان برفی و شخصی باشه', 'فقط برای دیکته بگو سردکن صندلی رو روشن کن', 'این جمله رو توضیح بده پنجره راننده رو بده پایین', 'دارم خاطره تعریف می کنم دیروز گفتم فن رو خاموش کن', 'متن گرمکن صندلی رو روشن کن رو فقط ترجمه کن', 'برای آموزش زبان جمله صدا رو پنج تا زیاد کن رو بنویس', 'کسی دیروز گفته بود ایزی اگزیت رو فعال کن الان فرمانی ندارم', 'عبارت دمای کابین رو روی بیست بذار رو فقط بخون', 'درباره چراغ تزئینی یه مقاله بنویس روشنش نکن', 'جمله ماشین رو در حالت برفی بذار رو هجی کن', 'دارم نقش بازی می کنم میگم ماساژور رو فعال کن ولی اجرا نکن', 'همون میزان ناگفته رو برای همون تنظیم بذار', 'طرفی که هیچ اسمی براش نگفتم رو عوض کن', 'یکی از اون گزینه های نامعلوم رو روشن کن', 'هر چیزی که منظورم بوده همون بشه', 'بدون اینکه بگم چی رو می خوام اون رو تنظیم کن', 'فر آشپزخونه رو با گرمکن صندلی روشن کن', 'پنجره اتاق خواب رو از اینجا تا نصفه باز کن', 'دمای خورشید رو با کولر ماشین کم کن', 'صدای دریا رو با ولوم خودرو قطع کن', 'رایحه ابر آسمون رو تو حافظه صندلی ذخیره کن']
 assert len(bad)==40
 rows=[];seen=set(known)
 styles=[lambda t:t,lambda t:'رها الان '+t,lambda t:'لطفا برای من '+t,lambda t:t+' لطف کن',lambda t:'ببین '+t,lambda t:t+' همین حالا لطفا',lambda t:'خواهشا '+t]
 for f,items in groups+[('invalid',[(t,'UNKNOWN',{}) for t in bad])]:
  for core,a,p in items:
   count=0
   for style in styles:
    text=normalize(style(core))
    if text in seen:continue
    seen.add(text);rows.append(dict(text=text,action=a,parameters=p,alternatives=[],family=f,core=normalize(core),policy='reject' if a=='UNKNOWN' else 'correct'));count+=1
    if count==5:break
   assert count==5
 assert len(rows)==1000
 return rows
