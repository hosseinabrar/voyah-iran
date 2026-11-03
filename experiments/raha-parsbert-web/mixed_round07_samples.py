from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20;groups.append((name,items))
 def r(t,a,**p):return t,a,p
 zones=[('سمت راننده','driver'),('سمت شاگرد','passenger'),('سمت همراه','passenger'),('کل فضای کابین','front'),('کل اتاق','front')]
 add('relative-temperature',(r(t.format(s=s),'ADJUST_CABIN_TEMPERATURE',zone=z,delta=n) for t,n in [('به دمای هوای {s} دو درجه اضافه کن',2),('دمای {s} چهار درجه کمتر بشه',-4),('دما برای {s} سه درجه افزایش پیدا کنه',3),('دما برای {s} یک درجه کاهش پیدا کنه',-1)] for s,z in zones))
 add('absolute-temperature',(r(t.format(s=s),'SET_CABIN_TEMPERATURE',zone=z,degreesC=float(n)) for t,n in [('دمای مطلوب {s} بیست و یک درجه باشه',21),('برای {s} بیست و پنج درجه تنظیم کن',25),('دمای مطلوب {s} نوزده درجه باشه',19),('برای {s} سی و دو درجه تنظیم کن',32)] for s,z in zones))
 sides=[('راننده','driver'),('شاگرد','passenger'),('پشت راننده','rearLeft'),('پشت شاگرد','rearRight'),('جلو سمت همراه','passenger')]
 add('windows',(r(t.format(s=s),a,window=w) for t,a in [('پنجره {s} رو تا آخرین حد بازش کن','OPEN_WINDOW'),('شیشه {s} رو کامل بالاش بده','CLOSE_WINDOW'),('شیشه {s} رو تا پایین ترین نقطه ببر','OPEN_WINDOW'),('پنجره {s} رو تا بالا ترین نقطه ببر','CLOSE_WINDOW')] for s,w in sides))
 seats=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('خودم','driver'),('بغل دستی','passenger')]
 add('seat-levels',(r(t.format(s=s),a,seat=seat,enabled=True,level=n) for t,a,n in [('گرمکن صندلی {s} با درجه یک کار کنه','SET_SEAT_HEATING',1),('گرمکن نشیمن {s} با درجه سه کار کنه','SET_SEAT_HEATING',3),('درجه دو سردکن صندلی {s} رو راه بنداز','SET_SEAT_VENTILATION',2),('تهویه صندلی {s} رو روشن بذار روی عدد یک','SET_SEAT_VENTILATION',1)] for s,seat in seats))
 displays=[('مانیتور وسط','central'),('مانیتور همراه','passenger'),('صفحه پشت فرمون','cluster'),('تمام نمایشگرها','all'),('صفحه شاگرد','passenger')]
 add('display',(r(t.format(s=s),'SET_DISPLAY_BRIGHTNESS',display=d,percent=n) for t,n in [('نور پس زمینه {s} رو پنجاه درصد تنظیم کن',50),('روشن بودن {s} رو چهل درصد می خوام',40),('نور پس زمینه {s} هفتاد درصد بشه',70),('شدت روشنایی {s} رو بیست درصد انتخاب کن',20)] for s,d in displays))
 numbers=[('سه',3),('هشت',8),('دوازده',12),('نوزده',19),('بیست و هفت',27)]
 add('audio',(r(t.format(n=n),'SET_AUDIO_VOLUME',channel=c,level=v) for t,c in [('برای موسیقی سطح صدای {n} رو انتخابش کن','media'),('ولوم راهنمای صوتی نقشه رو {n} تنظیمش کن','navigation'),('عدد بلندی صدای سیستم رو {n} بذارش','system'),('تنظیم ولوم مولتی مدیا باید {n} باشه','media')] for n,v in numbers))
 switches=[('زمانبندی شارژ','SET_SCHEDULED_CHARGING'),('ورود راحت','SET_EASY_ENTRY'),('خروج راحت','SET_EASY_EXIT'),('سیستم پخش رایحه','SET_FRAGRANCE_POWER'),('نورپردازی تزئینی','SET_AMBIENT_LIGHT_POWER')]
 add('switches',(r(t.format(s=s),a,enabled=b) for t,b in [('فعالیت {s} رو شروع کن',True),('فعالیت {s} رو قطع کن',False),('{s} رو بذار فعال باقی بمونه',True),('{s} رو بذار غیر فعال باقی بمونه',False)] for s,a in switches))
 pairs=[('بخارزدای شیشه جلو','SET_FRONT_DEFROSTER',{}),('بخارزدای شیشه عقب','SET_REAR_DEFROSTER',{}),('کولر ماشین','SET_AC_POWER',{}),('تهویه خودکار','SET_CLIMATE_AUTO',{'zone':'front'}),('گردش خودکار تهویه','SET_CLIMATE_AUTO',{'zone':'front'})]
 # Use an unambiguous name for automatic climate control, not recirculation.
 pairs[-1]=('حالت اتوماتیک تهویه','SET_CLIMATE_AUTO',{'zone':'front'})
 add('climate-power',(r(t.format(s=s),a,enabled=b,**p) for t,b in [('{s} باید کار کنه راهش بنداز',True),('{s} رو از کار بنداز دیگه نیاز نیست',False),('راه اندازی {s} رو انجام بده روشن بشه',True),('فعالیت {s} رو متوقف کن خاموش بشه',False)] for s,a,p in pairs))
 bad=['دمای صندوق رو با درجه فن زیاد کن', 'گرمکن صندلی رو به حالت پخش آهنگ ببر', 'مسیر خونه رو با سردکن صندلی شارژ کن', 'ولوم قفل کودک رو روی ده بذار', 'نور آینه رو صد درجه سرد کن', 'ایزی انتری رو با رایحه بشور', 'فن پرده سقف رو باز و بسته کن', 'شارژر رو تا آخرین حد پایین بده', 'چراغ خیابون رو با نور صفحه وسط کم کن', 'یخچال خونه رو روی حالت اسپرت ببر', 'دما رو روی منفی پنجاه درجه تنظیم کن', 'شدت سردکن صندلی رو روی دویست بذار', 'بلندی صدای سیستم رو روی هزار بذار', 'نور صفحه پشت فرمان منفی سی درصد باشه', 'پنجره همراه صد و هشتاد درصد باز بمونه', 'گرمکن صندلی همزمان کار کنه و کار نکنه', 'رایحه هم خاموش بمونه هم روشن بمونه', 'پنجره عقب رو هم تا ته باز نگه دار هم تا ته بسته', 'صدا رو در یک لحظه هم روی صفر بذار هم سی', 'فن همزمان درجه دو و هفت باشه', 'دارم جمله صندلی رو گرم کن رو دیکته می کنم', 'دیروز بهت گفتم در باک رو باز کن امروز نیازی نیست', 'فقط جمله نور رو کم کن رو توضیح بده', 'می خوام درباره راه انداختن شارژ زمانبندی مطلب بنویسی', 'مثال بزن که چرا یکی میگه کولر رو روشن کن', 'به انگلیسی بگو در صندوق رو ببند', 'معنی فارسی ایزی انتری رو بگو نه اینکه فعالش کنی', 'دوستم گفته بود شیشه رو باز کن من دارم تعریف می کنم', 'برای انشا داستان خاموش کردن رایحه رو بنویس', 'من فقط دارم می پرسم ماساژ صندلی چیه', 'همون وضعیت رو یه طرفش تغییر بده', 'چیزی که می دونی رو تنظیم کن', 'اون مورد رو مثل چیزی که تو ذهنمه کن', 'به اندازه اونی که گفتم کمترش کن', 'طرفش رو عوض کن همون طرفه', 'ابرها رو با کولر سردتر کن', 'جاده رو با گرمکن صندلی گرم کن', 'تعداد درخت ها رو با ولوم کم کن', 'پنجره آسمون رو با شیشه ماشین ببند', 'ماه رو با ایزی اگزیت پایین بیار']
 assert len(bad)==40
 rows=[];seen=set(known)
 styles=[lambda t:t,lambda t:'رها لطفا '+t,lambda t:'الان لطفا '+t,lambda t:t+' همین الان',lambda t:'برام '+t,lambda t:'لطفا برام '+t,lambda t:t+' ممنون میشم']
 def put(t,a,p,f,core,policy):
  t=normalize(t)
  if t in seen:return False
  seen.add(t);rows.append(dict(text=t,action=a,parameters=p,alternatives=[],family=f,core=normalize(core),policy=policy));return True
 for f,items in groups+[('invalid',[(t,'UNKNOWN',{}) for t in bad])]:
  for t,a,p in items:
   n=0
   for style in styles:
    n+=put(style(t),a,p,f,t,'reject' if a=='UNKNOWN' else 'correct')
    if n==5:break
   assert n==5
 assert len(rows)==1000
 return rows
