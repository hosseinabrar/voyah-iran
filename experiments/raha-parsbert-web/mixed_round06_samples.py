from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20;groups.append((name,items))
 def r(t,a,**p):return t,a,p
 zones=[('سمت راننده','driver'),('سمت شاگرد','passenger'),('سمت همراه','passenger'),('کل فضای کابین','front'),('کل اتاق','front')]
 add('relative-temperature',(r(t.format(s=s),'ADJUST_CABIN_TEMPERATURE',zone=z,delta=n) for t,n in [('هوای {s} رو دو درجه نسبت به الان گرم ترش کن',2),('برای {s} چهار درجه دما رو کاهش بده',-4),('سه درجه به دمای {s} اضافه کن',3),('از دمای {s} یک درجه کمش کن',-1)] for s,z in zones))
 add('absolute-temperature',(r(t.format(s=s),'SET_CABIN_TEMPERATURE',zone=z,degreesC=float(n)) for t,n in [('به دمای {s} عدد بیست و یک بده',21),('تنظیم درجه {s} بیست و پنج باشه',25),('برای هوای {s} دمای نوزده رو انتخاب کن',19),('حرارت هوای {s} رو روی سی و دو قرار بده',32)] for s,z in zones))
 sides=[('راننده','driver'),('شاگرد','passenger'),('پشت راننده','rearLeft'),('پشت شاگرد','rearRight'),('جلو سمت همراه','passenger')]
 add('windows',(r(t.format(s=s),a,window=w) for t,a in [('شیشه {s} رو جمع کن پایین تا کامل باز بشه','OPEN_WINDOW'),('پنجره {s} رو بالا ببر و کامل ببند','CLOSE_WINDOW'),('می خوام از پنجره {s} هوا بیاد کامل بازش کن','OPEN_WINDOW'),('از پنجره {s} باد میاد کامل بسته بشه','CLOSE_WINDOW')] for s,w in sides))
 seats=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('خودم','driver'),('بغل دستی','passenger')]
 add('seat-levels',(r(t.format(s=s),a,seat=seat,enabled=True,level=n) for t,a,n in [('گرمای نشیمن {s} رو سطح یک فعال کن','SET_SEAT_HEATING',1),('گرمای نشیمن {s} رو سطح سه فعال کن','SET_SEAT_HEATING',3),('باد خنک صندلی {s} رو سطح دو فعال کن','SET_SEAT_VENTILATION',2),('تهویه نشیمن {s} با سطح یک روشن بشه','SET_SEAT_VENTILATION',1)] for s,seat in seats))
 displays=[('مانیتور وسط','central'),('مانیتور همراه','passenger'),('صفحه پشت فرمون','cluster'),('تمام نمایشگرها','all'),('صفحه شاگرد','passenger')]
 add('display',(r(t.format(s=s),'SET_DISPLAY_BRIGHTNESS',display=d,percent=n) for t,n in [('درصد روشنایی {s} پنجاه باشه',50),('برای {s} نور چهل درصد مناسبه تنظیمش کن',40),('نور {s} رو ببر روی هفتاد درصد',70),('می خوام {s} با نور بیست درصد نمایش بده',20)] for s,d in displays))
 numbers=[('سه',3),('هشت',8),('دوازده',12),('نوزده',19),('بیست و هفت',27)]
 add('audio',(r(t.format(n=n),'SET_AUDIO_VOLUME',channel=c,level=v) for t,c in [('بلندی پخش آهنگ رو عدد {n} کن','media'),('صدای اعلام مسیر رو روی عدد {n} بذار','navigation'),('درجه صوت سیستم ماشین {n} باشه','system'),('برای گوش دادن به موزیک ولوم {n} رو تنظیم کن','media')] for n,v in numbers))
 switches=[('زمانبندی شارژ','SET_SCHEDULED_CHARGING'),('ورود راحت','SET_EASY_ENTRY'),('خروج راحت','SET_EASY_EXIT'),('سیستم پخش رایحه','SET_FRAGRANCE_POWER'),('نورپردازی تزئینی','SET_AMBIENT_LIGHT_POWER')]
 add('switches',(r(t.format(s=s),a,enabled=b) for t,b in [('می خوام {s} کار کنه فعالش کن',True),('می خوام {s} متوقف بشه خاموشش کن',False),('تنظیم مربوط به {s} رو روشن بذار',True),('تنظیم مربوط به {s} رو غیر فعال بذار',False)] for s,a in switches))
 pairs=[('بخارزدای شیشه جلو','SET_FRONT_DEFROSTER',{}),('بخارزدای شیشه عقب','SET_REAR_DEFROSTER',{}),('کولر ماشین','SET_AC_POWER',{}),('تهویه خودکار','SET_CLIMATE_AUTO',{'zone':'front'}),('گردش خودکار تهویه','SET_CLIMATE_AUTO',{'zone':'front'})]
 # Use an unambiguous name for automatic climate control, not recirculation.
 pairs[-1]=('حالت اتوماتیک تهویه','SET_CLIMATE_AUTO',{'zone':'front'})
 add('climate-power',(r(t.format(s=s),a,enabled=b,**p) for t,b in [('کار {s} رو شروع کن روشن باشه',True),('به {s} نیازی ندارم خاموشش بذار',False),('فعال بودن {s} رو می خوام روشنش کن',True),('{s} کارش تمومه غیرفعالش کن',False)] for s,a,p in pairs))
 bad=[
 'دمای پنجره آشپزخونه رو از ماشین زیاد کن','سرمایش در صندوق رو روی پنج بذار','شارژر رو با موسیقی خوشبو کن','گرمکن صندلی رو به جای در باک باز کن','صفحه پشت فرمان رو تا نصفه ماساژ بده','قفل کودک رو درجه بیست گرم کن','رایحه رو به جای صندلی عقب ببر','ولوم شیشه سقف رو تنظیم کن','تهویه گلدون خونه رو روشن کن','ایزی اگزیت دوچرخه رو فعال کن',
 'دمای ماشین روی هزار درجه باشه','ولوم آهنگ روی منفی بیست باشه','روشنایی مانیتور روی دویست درصد باشه','گرمکن صندلی روی هشتاد باشه','پنجره رو منفی پنجاه درصد باز کن',
 'قفل کودک رو همزمان روشن و خاموش کن','کولر رو همزمان فعال و غیرفعال نگه دار','پنجره راننده هم تا آخر پایین باشه هم تا آخر بالا','نور صفحه وسط هم بیست درصد باشه هم نود درصد','صدا هم بی صدا باشه هم روی آخرین درجه',
 'چرا بعضیا میگن شیشه رو بده پایین فقط توضیح بده','معنی دستور گرمکن رو خاموش کن رو بگو','من فقط جمله کولر رو روشن کن رو نقل می کنم','دیروز ماساژور رو روشن کردم الان کاری نکن','همراهم قبلا سردش بود الان گرمکن نمی خواد','یه داستان درباره بستن صندوق بنویس','عبارت شارژ زمانبندی رو فعال کن رو ترجمه کن','فقط بگو ایزی انتری به چه درد می خوره','دیروز گفتم صدا رو زیاد کن الان تغییر نده','می خوام بدونم چرا شیشه مه می گیره فرمانی ندارم',
 'اون چیز رو از اون طرف عوض کن','همون تنظیم رو به اون حالتی که می خوام ببر','این یکی هم اندازه اون بشه','مقدار چیزی که میگم رو بیشترش کن','اون سمتش رو مثل قبلش کن',
 'خورشید رو با گرمکن صندلی خاموش کن','با فن ماشین گردباد بیرون رو نگه دار','رنگ درخت ها رو با نورپردازی عوض کن','خیابون رو با شیشه عقب ببند','ابرها رو با بخارزدای جلو پاک کن']
 assert len(bad)==40
 rows=[];seen=set(known)
 styles=[lambda t:t,lambda t:'رها لطفا '+t,lambda t:'اگه ممکنه '+t,lambda t:t+' همین الان',lambda t:'برام '+t,lambda t:'لطفا برام '+t,lambda t:t+' ممنون میشم']
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
