from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 scenes=[('کمپینگ','camp'),('کارواش','carWash'),('مراقبت از کودک','babyCare'),('چرت زدن','nap'),('مهمانی','party')]
 add('scenarios',(r(t.format(s=s),'ACTIVATE_SCENE',scene=v) for t in ['حالت مخصوص {s} رو تو ماشین فعالش کن','می خوام سناریوی {s} همین الان اجرا بشه','تنظیمات سناریوی {s} رو اعمال کن','سناریو رو بذار روی {s}'] for s,v in scenes))
 energy=[('برقی','electric'),('بنزینی','fuel'),('هیبرید','hybrid'),('حفظ شارژ','save'),('هوشمند','smart')]
 add('energy',(r(t.format(s=s),'SET_ENERGY_STRATEGY',strategy=v) for t in ['برای انرژی خودرو حالت {s} رو در نظر بگیر و فعال کن','از حالت {s} برای تامین انرژی ماشین استفاده کن','گزینه {s} رو تو مدیریت انرژی انتخاب کن','حالت مصرف خودرو {s} بشه'] for s,v in energy))
 lights=[('خودکار','auto'),('نور پایین','lowBeam'),('چراغ کوچک','position'),('خاموش','off')]
 add('headlights',(r(t.format(s=s),'SET_HEADLIGHT_MODE',mode=v) for t in ['تنظیم روشنایی چراغ جلو رو {s} کن','برای چراغ اصلی ماشین {s} رو انتخابش کن','حالت چراغ جلو باید {s} بشه','چراغ های جلویی ماشین رو بذار وضعیت {s}','از تنظیم {s} برای چراغ جلو استفاده کن'] for s,v in lights))
 levels=[('کم','low'),('متوسط','medium'),('زیاد','high'),('میانه','medium')]
 add('regeneration',(r(t.format(s=s),'SET_REGENERATION_LEVEL',level=v) for t in ['قدرت برگشت انرژی به باتری رو {s} انتخاب کن','ریکاوری خودرو در سطح {s} تنظیم بشه','بازیابی انرژی رو بذار روی سطح {s}','برای احیای انرژی درجه {s} رو می خوام تنظیم کن','وقتی پام از گاز برداشته میشه بازیابی {s} باشه'] for s,v in levels))
 sides=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('هر دو طرف','all'),('دو سمت ماشین','all')]
 add('mirrors',(r(t.format(s=s),'SET_MIRROR_FOLD',side=v,folded=b) for t,b in [('آینه {s} رو تا کن به بدنه نزدیک بشه',True),('آینه {s} رو باز کن که بتونم پشت رو ببینم',False),('آینه {s} رو به سمت بدنه جمعش کن',True),('آینه {s} رو از کنار بدنه باز کن',False)] for s,v in sides))
 switches=[('رادار نقطه کور','SET_BLIND_SPOT_MONITORING'),('ترمز اضطراری خودکار','SET_AUTOMATIC_EMERGENCY_BRAKING'),('چراغ مه شکن عقب','SET_REAR_FOG_LIGHT'),('ایزی انتری','SET_EASY_ENTRY'),('شارژ زمان بندی شده','SET_SCHEDULED_CHARGING')]
 add('assistance-switches',(r(t.format(s=s),a,enabled=b) for t,b in [('می خوام از {s} استفاده کنم فعالش کن',True),('دیگه از {s} استفاده نمی کنم غیرفعالش کن',False),('تیک مربوط به {s} رو روشنش کن',True),('تیک مربوط به {s} رو خاموشش کن',False)] for s,a in switches))
 memory_templates=[('همین جای صندلی راننده رو به مموری {n} بده','SAVE_SEAT_POSITION'),('وضعیت فعلی صندلی خودم رو جای حافظه {n} ثبت کن','SAVE_SEAT_POSITION'),('جای نشستن الان من توی مموری {n} ذخیره بشه','SAVE_SEAT_POSITION'),('تنظیم صندلی خودم رو از حافظه {n} برگردون','RECALL_SEAT_POSITION'),('جای صندلی راننده طبق مموری {n} تنظیم بشه','RECALL_SEAT_POSITION')]
 # Four expressions refer to the two explicitly numbered factory memory slots.
 add('seat-memory',(r(t.format(n=n),a,seat='driver',slot=v) for t,a in memory_templates for n,v in [('دو','2'),('سه','3'),('شماره دو','2'),('شماره سه','3')]))
 media=[('وسط پخش {s} مکث کن','PAUSE_MEDIA'),('مکث {s} رو بردار پخش ادامه پیدا کنه','PLAY_MEDIA'),('پخش رو ببر به {s} بعدی','NEXT_MEDIA'),('یه {s} برگرد عقب توی فهرست پخش','PREVIOUS_MEDIA'),('فعلا پخش {s} متوقف بمونه','PAUSE_MEDIA')]
 add('media',(r(t.format(s=s),a) for t,a in media for s in ['آهنگ','موزیک','قطعه موسیقی','ترک موسیقی']))
 bad=['درب باک رو با آهنگ بعدی خنک کن', 'پنجره رو به حافظه شارژ تبدیل کن', 'چراغ جلو رو در حالت ماساژ موجی بذار', 'آینه شاگرد رو تا دمای بیست گرم کن', 'شدت بنزین رو مثل صدای آهنگ کم کن', 'سناریوی کودک رو روی سطح تهویه صندلی بذار', 'ترمز اضطراری رو تا نصفه با رایحه باز کن', 'حافظه صندلی رو روی نور پایین تنظیم کن', 'شارژ زمان بندی شده رو با برف پاک کن بمال', 'ریکاوری انرژی رو به پنجره تبدیل کن', 'تنظیم دمای کابین روی چهارصد درجه باشه', 'مموری شماره هشتاد صندلی رو بازیابی کن', 'سطح فن روی منفی بیست تنظیم بشه', 'بلندی صدا روی دویست و پنجاه باشه', 'شیشه همراه تا ششصد درصد باز بشه', 'چراغ اصلی جلو همزمان هم خودکار باشه هم خاموش', 'آینه شاگرد رو در یک لحظه هم جمع نگه دار هم باز', 'همین آهنگ هم پخش بشه هم پخش نشه', 'ایزی انتری هم روشن بمونه هم خاموش', 'ریکاوری انرژی هم کم باشه هم زیاد در یک زمان', 'صرفا جمله آینه شاگرد رو باز کن رو برام هجی کن', 'دارم تمرین تلفظ می کنم چراغ جلو رو خاموش کن', 'توی قصه بنویس ماشین رو بذار تو حالت برقی', 'این پیام رو فقط بخون سناریوی کمپینگ رو فعال کن', 'دوستم دیروز گفت ترمز اضطراری رو روشن کن دارم تعریف می کنم', 'درباره فرمان حافظه صندلی رو ذخیره کن توضیح می خوام', 'معنی انگلیسی چراغ جلو رو خودکار کن چیه', 'جمله شارژ زمان بندی شده رو خاموش کن چند کلمه داره', 'فقط متن رادار نقطه کور روشن رو تکرار کن', 'سناریوی کودک رو فعال کن عنوان یک متنه اجرا نکن', 'تنظیم اون یکی چیزی که نگفتم رو تغییر بده', 'یه وضعیت نامشخص رو برام انتخاب کن', 'هر چی تو فکر من هست همونو اجرا کن', 'اون طرفی که اسمش رو نمی دونم تنظیم بشه', 'همون گزینه فراموش شده رو اعمال کن', 'پیتزا رو تو حافظه صندلی ذخیره کن', 'چراغ خواب اتاق خونه رو با نور پایین روشن کن', 'بنزین ستاره ها رو تو باک خورشید بریز', 'تلویزیون همسایه رو به حالت هیبرید ببر', 'آینه حمام رو با رادار نقطه کور جمع کن']
 assert len(bad)==40
 rows=[];seen=set(known)
 styles=[lambda t:t,lambda t:'رها لطف کن '+t,lambda t:'الان برام '+t,lambda t:t+' لطفا رها',lambda t:'خواهش می کنم '+t,lambda t:t+' همین حالا انجام بده',lambda t:'ببین لطفا '+t]
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
