"""Round five, frozen compositional gold before inference."""
from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 sides=[('کنار فرمان','driver'),('جلو طرف شاگرد','passenger'),('پشت سر راننده','rearLeft'),('پشت سر شاگرد','rearRight'),('جلو طرف همراه','passenger')]
 add('window-synonyms', (r(t.format(s=s),a,window=w) for t,a in [('پنجره {s} رو تا انتها بکش پایین','OPEN_WINDOW'),('شیشه {s} رو کامل بکش بالا تا بسته بشه','CLOSE_WINDOW'),('پنجره {s} بسته است بازش کن تا آخر','OPEN_WINDOW'),('شیشه {s} باز مونده تا آخر ببندش','CLOSE_WINDOW')] for s,w in sides))
 add('window-percent',(r(t.format(s=s),'SET_WINDOW_POSITION',window=w,percent=n) for t,n in [('پنجره {s} سی درصد باز بمونه',30),('شیشه {s} رو تا شصت درصد بده پایین',60),('پنجره {s} هفتاد درصد باز بشه',70),('شیشه {s} رو بیست درصد پایین بکش',20)] for s,w in sides))
 seats=[('من','driver'),('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('بغل دستی','passenger')]
 add('seat-synonyms',(r(t.format(s=s),a,seat=seat,enabled=True,level=n) for t,a,n in [('گرمکن نشیمن {s} روی سطح سه کار کنه','SET_SEAT_HEATING',3),('سردکن نشیمن {s} روی سطح یک کار کنه','SET_SEAT_VENTILATION',1),('صندلی {s} با درجه دو گرم بشه','SET_SEAT_HEATING',2),('صندلی {s} با درجه سه خنک بشه','SET_SEAT_VENTILATION',3)] for s,seat in seats))
 zones=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('کل کابین','front'),('کل اتاق ماشین','front')]
 add('temperature-target',(r(t.format(s=s),'SET_CABIN_TEMPERATURE',zone=z,degreesC=float(n)) for t,n in [('درجه هوای {s} رو هفده انتخاب کن',17),('برای {s} دمای بیست و هشت می خوام تنظیمش کن',28),('دمای {s} رو روی بیست و دو ببر',22),('هوای {s} بیست و چهار درجه بشه',24)] for s,z in zones))
 nums=[('دو',2),('پنج',5),('نه',9),('هفده',17),('بیست و شش',26)]
 add('audio-channels',(r(t.format(n=n),'SET_AUDIO_VOLUME',channel=c,level=v) for t,c in [('بلندی صوت مولتی مدیا رو روی {n} قرار بده','media'),('صدای راهنمای مسیریابی روی {n} تنظیم بشه','navigation'),('درجه صدای سیستم رو بذار عدد {n}','system'),('ولوم موزیک دقیقا روی {n} باشه','media')] for n,v in nums))
 displays=[('وسط','central'),('شاگرد','passenger'),('پشت فرمان','cluster'),('همه صفحه ها','all'),('همراه','passenger')]
 add('display-synonyms',(r(t.format(s=s),'SET_DISPLAY_BRIGHTNESS',display=d,percent=n) for t,n in [('روشنایی نمایشگر {s} رو به بیست درصد برسون',20),('نور صفحه {s} روی شصت درصد تنظیم بشه',60),('روشنایی صفحه {s} نود درصد باشه',90),('نور نمایشگر {s} رو دقیقا سی درصد بذار',30)] for s,d in displays))
 settings=[('ورود آسان','SET_EASY_ENTRY'),('خروج آسان','SET_EASY_EXIT'),('شارژ برنامه ریزی شده','SET_SCHEDULED_CHARGING'),('خوشبوکننده کابین','SET_FRAGRANCE_POWER'),('نور تزئینی کابین','SET_AMBIENT_LIGHT_POWER')]
 add('toggle-paraphrases',(r(t.format(s=s),a,enabled=b) for t,b in [('تنظیم {s} رو از خاموش به روشن تغییر بده',True),('تنظیم {s} رو از روشن به خاموش تغییر بده',False),('حالت فعال رو برای {s} انتخاب کن',True),('حالت غیر فعال رو برای {s} انتخاب کن',False)] for s,a in settings))
 controls=[('شیشه جلویی','SET_FRONT_DEFROSTER'),('شیشه عقبی','SET_REAR_DEFROSTER')]
 phrases=[('مه روی {s} رو با بخارزدا برطرف کن',True),('برای {s} مه زدایی رو راه بنداز',True),('بخارزدایی {s} رو به کار بنداز',True),('بخار {s} پاک شد بخارزداش رو قطع کن',False),('مه زدای {s} دیگه کار نکنه خاموشش کن',False),('حالت روشن رو برای بخارزدای {s} بذار',True),('حالت خاموش رو برای بخارزدای {s} بذار',False),('بخارزدای {s} روی غیرفعال قرار بگیره',False),('مه گرفته {s} بخارزدایی رو فعالش کن',True),('شفاف شده {s} بخارزدایی رو متوقف کن',False)]
 add('defogging-synonyms',(r(t.format(s=s),a,enabled=b) for t,b in phrases for s,a in controls))
 rows=[];seen=set(known)
 styles=[lambda t:'رها همین حالا '+t,lambda t:'لطف می کنی '+t,lambda t:t+' لطفا انجامش بده',lambda t:'خب برای من '+t,lambda t:'خواهشا الان '+t,lambda t:'لطفا این کار رو بکن '+t,lambda t:t+' رها لطفا']
 def put(t,a,p,f,core,policy):
  t=normalize(t)
  if t in seen:return False
  seen.add(t);rows.append(dict(text=t,action=a,parameters=p,alternatives=[],family=f,core=normalize(core),policy=policy));return True
 for f,items in groups:
  for t,a,p in items:
   n=0
   for st in styles:
    n+=put(st(t),a,p,f,t,'correct')
    if n==5:break
   assert n==5
 bad=[
 'شارژر ماشین رو تا نصفه ماساژ بده',
 'بخارزدای شیشه عقب رو روی آهنگ بعدی بذار',
 'نور نمایشگر همراه رو با بنزین پر کن',
 'دمای قفل کودک رو روی بیست درجه تنظیم کن',
 'پرده سقف رو سه درجه سردتر کن',
 'شدت رایحه رو با ترمز پارک بشور',
 'ولوم پنجره عقب رو بیست و دو بذار',
 'صندلی آشپزخونه رو با گرمکن ماشین گرم کن',
 'چراغ یخچال خونه رو از ایزی انتری روشن کن',
 'گرمکن درخت کنار جاده رو روشن کن',
 'صدای مسیریابی رو روی عدد منفی هفت تنظیم کن',
 'شیشه عقب سمت راننده رو سیصد درصد باز کن',
 'نور صفحه شاگرد روی منفی ده درصد باشه',
 'گرمکن صندلی من روی سطح هفتاد باشه',
 'دمای کابین رو دویست درجه تنظیم کن',
 'ایزی انتری هم روشن باشه هم خاموش',
 'شارژ برنامه ریزی شده هم فعال باشه هم نباشه',
 'شیشه جلو سمت شاگرد هم کامل بسته بمونه هم کامل باز',
 'دمای راننده رو همزمان هجده و بیست و هفت کن',
 'نور همه نمایشگرها هم صفر درصد باشه هم صد درصد',
 'فقط درباره روش روشن کردن مه زدای عقب توضیح بده',
 'منظور از صندلی رو خنک کن چیه فقط معنی کن',
 'دارم ازت درباره شارژ زمانبندی می پرسم فعالش نکن',
 'دوستم دیروز گفت پنجره رو باز کن امروز چیزی نمی خوام',
 'تو متن کتاب نوشته صدای موزیک رو زیاد کن فقط دارم می خونم',
 'جمله گرمکن راننده رو قطع کن رو ترجمه کن',
 'یه مثال درباره فعال کردن قفل کودک بنویس',
 'فرق گردش داخلی و خارجی هوا چیه تغییرش نده',
 'داستانی تعریف کن که توش یکی میگه در صندوق رو ببند',
 'چرا کسی میگه کولر رو خاموش کن من فقط سوال دارم',
 'این مورد رو مثل اون مورد تنظیم کن',
 'اون سمت رو به همون مقداری که فکر می کنم برسون',
 'چیزی که قبلا گفتم رو برعکسش کن',
 'یه چیزی رو یه طرفش کم کن',
 'اون گزینه نامعلوم رو از این حالت دربیار',
 'با رایحه ماشین آسمون رو خوشبو کن',
 'باد کوهستان رو با فن ماشین زیاد کن',
 'طول خیابون رو با تنظیم صندلی کم کن',
 'بارون بیرون رو با برف پاک کن متوقف کن',
 'تعداد ستاره های آسمون رو با نورپردازی تغییر بده']
 assert len(bad)==40
 for t in bad:
  n=0
  for st in styles:
   n+=put(st(t),'UNKNOWN',{},'invalid-near-command',t,'reject')
   if n==5:break
  assert n==5
 assert len(rows)==1000
 return rows
