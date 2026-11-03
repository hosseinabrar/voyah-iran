from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 sides=[('سمت فرمان','driver'),('سمت شاگرد جلو','passenger'),('عقب پشت راننده','rearLeft'),('عقب پشت همراه','rearRight'),('جلو سمت بغل','passenger')]
 add('window-opening',(r(t.format(s=s),'SET_WINDOW_POSITION',window=w,percent=n) for t,n in [('باز بودن شیشه {s} رو به چهل درصد برسون',40),('پنجره {s} رو شصت درصد پایین بده تا هوا بیاد',60),('شیشه {s} پنجاه درصد پایین باشه',50),('می خوام پنجره {s} سی درصد باز بشه',30)] for s,w in sides))
 zones=[('سمت فرمان','driver'),('جلو سمت همراه','passenger'),('طرف شاگرد','passenger'),('تمام کابین','front'),('فضای اتاق ماشین','front')]
 add('temperature-fixed',(r(t.format(s=s),'SET_CABIN_TEMPERATURE',zone=z,degreesC=float(n)) for t,n in [('دمای تهویه {s} رو شانزده درجه می خوام',16),('برای {s} درجه بیست و سه رو تنظیمش کن',23),('دمای دلخواه {s} بیست و هفت درجه است بذارش',27),('هوای {s} روی سی درجه تنظیم بشه',30)] for s,z in zones))
 add('temperature-change',(r(t.format(s=s),'ADJUST_CABIN_TEMPERATURE',zone=z,delta=n) for t,n in [('از دمای {s} دو درجه کم کن تا خنک بشه',-2),('دمای {s} پنج درجه بیشتر بشه تا گرم بشه',5),('برای {s} دما سه درجه کمتر از الان بشه',-3),('برای {s} دما چهار درجه بیشتر از الان بشه',4)] for s,z in zones))
 seats=[('راننده','driver'),('همراه','passenger'),('شاگرد','passenger'),('خودم','driver'),('نفر بغلی','passenger')]
 add('seat-stop',(r(t.format(s=s),a,seat=seat,enabled=False,level=0) for t,a in [('باد صندلی {s} رو قطع کن سردکنش خاموش بشه','SET_SEAT_VENTILATION'),('دیگه صندلی {s} گرم نشه گرمکنش رو خاموش بذار','SET_SEAT_HEATING'),('ماساژ صندلی {s} رو تمام کن خاموش بشه','SET_SEAT_MASSAGE'),('صندلی {s} خنک شد سردکنش رو از کار بنداز','SET_SEAT_VENTILATION')] for s,seat in seats))
 nums=[('یک',1),('چهار',4),('شش',6),('هفت',7),('ده',10)]
 add('volume-relative',(r(t.format(n=n),'ADJUST_AUDIO_VOLUME',channel='media',delta=sign*v) for t,sign in [('موسیقی رو {n} درجه کم کن که آروم تر بشه',-1),('برای بهتر شنیدن موسیقی صدا رو {n} درجه زیاد کن',1),('ولوم آهنگ {n} پله کاهش پیدا کنه',-1),('ولوم آهنگ {n} پله افزایش پیدا کنه',1)] for n,v in nums))
 fan_nums=[('صفر',0),('یک',1),('سه',3),('پنج',5),('هفت',7)]
 add('fan-fixed',(r(t.format(n=n),'SET_FAN_SPEED',zone='front',level=v) for t in ['شدت دمنده تهویه روی عدد {n} کار کنه','برای باد داخل ماشین فن {n} رو انتخاب کن','عدد سرعت فن کولر رو {n} قرارش بده','فن تهویه کابین رو با سطح {n} تنظیم کن'] for n,v in fan_nums))
 settings=[('شارژ سر ساعت','SET_SCHEDULED_CHARGING'),('ایزی انتری','SET_EASY_ENTRY'),('ایزی اگزیت','SET_EASY_EXIT'),('پخش عطر داخل ماشین','SET_FRAGRANCE_POWER'),('چراغ های تزئینی داخل','SET_AMBIENT_LIGHT_POWER')]
 add('switch-purpose',(r(t.format(s=s),a,enabled=b) for t,b in [('می خوام از {s} استفاده کنم فعالش کن',True),('از {s} استفاده نمی کنم غیرفعالش کن',False),('گزینه {s} باید روشن باشه',True),('گزینه {s} باید خاموش باشه',False)] for s,a in settings))
 modes=[('اکونومی','eco'),('کامفورت','comfort'),('اسنو','snow'),('اوتینگ','outing'),('پرسونال','personal')]
 add('drive-modes',(r(t.format(m=m),'SET_DRIVE_MODE',mode=v) for t in ['حالت حرکت خودرو رو {m} انتخاب کن','می خوام با مود رانندگی {m} برونم تنظیمش کن','برای رانندگی الان مود {m} رو بذار','تنظیم حالت رانندگی ماشین {m} بشه'] for m,v in modes))
 bad='نورپردازی رو با بنزین شارژ کن|گرمکن پنجره آشپزخونه رو از ماشین فعال کن|ماساژور درخت کنار جاده رو روشن کن|صدای صندلی رو روی بیست درجه گرم کن|قفل کودک رو با رایحه ماساژ بده|پنجره رو روی حالت پخش موسیقی بذار|دمای نقشه رو روی سی تنظیم کن|شارژ زمانبندی رو تا نیمه پایین بکش|پرده سانروف رو با درجه صدا پر از بنزین کن|ترمز پارک رو خوشبو و سرد کن|سطح گرمکن صندلی رو دویست و پنجاه کن|دما رو روی منفی نود درجه بذار|پنجره راننده دویست و ده درصد باز بشه|ولوم مولتی مدیا منفی سی باشه|فن کولر رو عدد چهارصد کن|ایزی اگزیت هم فعال بمونه هم غیرفعال بمونه|فن کولر هم روی صفر باشه هم روی پنج|در صندوق هم کامل باز بمونه هم کامل بسته|همین آهنگ هم پخش بشه هم پخش نشه|نور تزئینی داخل هم روشن باشه هم خاموش|دارم جمله فن کولر رو زیاد کن رو تمرین می کنم|درباره فعال کردن ایزی اگزیت یه توضیح بنویس|جمله پنجره رو باز کن رو فقط به انگلیسی بگو|دیروز گفتم کولر رو روشن کن امروز نمی خوام|این مثال رو بخون گرمکن صندلی رو خاموش کن|می خوام بدونم چرا یکی میگه قفل کودک رو فعال کن|داستانی بنویس که شخصیتش میگه صدا رو کم کن|فقط معنی زمانبندی شارژ رو توضیح بده|همراهم دیروز گفت کمرم درد می کنه الان خوبه کاری نکن|من دارم درباره باز کردن باک حرف می زنم فرمانی ندارم|اون چیزه رو به شکل اون یکی دربیار|یه مقدار بهش بده همون مقداری که قبلا گفتم|سمت اون رو مثل سمت این کن|این کار رو روی همون گزینه انجام بده|اون تنظیم ناشناس رو به حالت دلخواه ببر|خورشید رو روی حالت اقتصادی بذار|ارتفاع آسمون رو با ایزی انتری کم کن|بارون رو با ولوم مسیریابی زیاد کن|با شارژ ماشین درخت ها رو شارژ کن|باد خیابون رو با دمنده کابین خاموش کن'.split('|')
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
