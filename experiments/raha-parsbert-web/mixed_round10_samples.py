from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 scenes=[('کمپینگ','camp'),('کارواش','carWash'),('مراقبت از کودک','babyCare'),('چرت زدن','nap'),('مهمانی','party')]
 add('scenarios',(r(t.format(s=s),'ACTIVATE_SCENE',scene=v) for t in ['سناریوی {s} رو برای ماشین راه بنداز','برای {s} سناریوی خودش رو فعال کن','سناریوی {s} شروع به کار کنه','ماشین رو با سناریوی {s} تنظیم کن'] for s,v in scenes))
 energy=[('برقی','electric'),('بنزینی','fuel'),('هیبرید','hybrid'),('حفظ شارژ','save'),('هوشمند','smart')]
 add('energy',(r(t.format(s=s),'SET_ENERGY_STRATEGY',strategy=v) for t in ['مصرف انرژی رو به روش {s} تنظیمش کن','حالت {s} برای مصرف انرژی انتخاب بشه','می خوام انرژی ماشین به صورت {s} مصرف بشه','مدیریت مصرف انرژی خودرو رو بذار {s}'] for s,v in energy))
 lights=[('خودکار','auto'),('نور پایین','lowBeam'),('چراغ کوچک','position'),('خاموش','off')]
 add('headlights',(r(t.format(s=s),'SET_HEADLIGHT_MODE',mode=v) for t in ['چراغ اصلی جلو در وضعیت {s} قرار بگیره','می خوام حالت {s} رو برای چراغ جلو بذاری','برای چراغ جلو وضعیت {s} مناسبه تنظیمش کن','وضعیت چراغ اصلی رو {s} انتخابش کن','چراغ جلو رو با حالت {s} تنظیمش کن'] for s,v in lights))
 levels=[('کم','low'),('متوسط','medium'),('زیاد','high'),('میانه','medium')]
 add('regeneration',(r(t.format(s=s),'SET_REGENERATION_LEVEL',level=v) for t in ['بازیافت انرژی موقع کم کردن سرعت {s} باشه','درجه ریکاوری انرژی رو {s} می خوام','هنگام برداشتن پا از گاز ریکاوری {s} انجام بشه','سطح {s} رو برای بازیابی انرژی بذار','تنظیم ریکاوری ماشین رو به {s} تغییر بده'] for s,v in levels))
 sides=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('هر دو طرف','all'),('دو سمت ماشین','all')]
 add('mirrors',(r(t.format(s=s),'SET_MIRROR_FOLD',side=v,folded=b) for t,b in [('آینه {s} بیرون مونده به داخل تا بزن',True),('آینه {s} جمعه بازش بذار',False),('برای کم شدن عرض ماشین آینه {s} رو جمع کن',True),('برای دیدن مسیر آینه {s} رو بازش کن',False)] for s,v in sides))
 switches=[('رادار نقطه کور','SET_BLIND_SPOT_MONITORING'),('ترمز اضطراری خودکار','SET_AUTOMATIC_EMERGENCY_BRAKING'),('چراغ مه شکن عقب','SET_REAR_FOG_LIGHT'),('ایزی انتری','SET_EASY_ENTRY'),('شارژ زمان بندی شده','SET_SCHEDULED_CHARGING')]
 add('assistance-switches',(r(t.format(s=s),a,enabled=b) for t,b in [('سیستم {s} از الان روشن باشه',True),('سیستم {s} از الان خاموش باشه',False),('فعال بودن {s} رو تنظیم کن',True),('غیرفعال بودن {s} رو تنظیم کن',False)] for s,a in switches))
 memory_templates=[('موقعیت الان صندلی من تو حافظه {n} ذخیره بشه','SAVE_SEAT_POSITION'),('مموری {n} راننده رو با جای فعلی صندلی عوض کن','SAVE_SEAT_POSITION'),('حافظه {n} صندلی خودم رو دوباره ذخیره کن','SAVE_SEAT_POSITION'),('موقعیت ذخیره شده {n} صندلی راننده رو اجرا کن','RECALL_SEAT_POSITION'),('صندلی من بره به جای ثبت شده تو مموری {n}','RECALL_SEAT_POSITION')]
 # Four expressions refer to the two explicitly numbered factory memory slots.
 add('seat-memory',(r(t.format(n=n),a,seat='driver',slot=v) for t,a in memory_templates for n,v in [('دو','2'),('سه','3'),('شماره دو','2'),('شماره سه','3')]))
 media=[('{s} رو پاز کن دارم صحبت می کنم','PAUSE_MEDIA'),('{s} روی مکث مونده ادامه اش بده','PLAY_MEDIA'),('{s} بعد از این رو انتخابش کن','NEXT_MEDIA'),('برگرد به {s} قبل از این','PREVIOUS_MEDIA'),('{s} رو همین جا موقتا متوقفش کن','PAUSE_MEDIA')]
 add('media',(r(t.format(s=s),a) for t,a in media for s in ['آهنگ','موزیک','قطعه موسیقی','ترک موسیقی']))
 bad=['ترمز اضطراری رو با فن سردش کن', 'آینه رو تو حالت شارژ سریع بذار', 'مصرف سوخت صندلی رو اقتصادی کن', 'حافظه دو صندلی رو تا نصفه باز کن', 'چراغ جلو رو توی لیوان بریز', 'رایحه رو با نور بالا پخش موسیقی کن', 'پنجره رو به سناریوی ناهار خوری تبدیل کن', 'استراتژی انرژی رو با پیتزا گرم کن', 'در باک رو به درجه نور پایین برسون', 'قفل کودک رو مثل آهنگ پخش کن', 'مموری صندلی شماره صد و بیست رو اجرا کن', 'شدت گرمکن روی منفی پانزده باشه', 'دمای شاگرد روی دویست درجه باشه', 'شیشه راننده هزار درصد باز بشه', 'صدای موزیک منفی هفتاد تنظیم بشه', 'آینه راننده هم داخل جمع باشه هم بیرون باز', 'حالت انرژی هم فقط برقی باشه هم فقط هیبرید', 'چراغ جلو هم خاموش باشه هم نور پایین روشن', 'رادار نقطه کور هم فعال بشه هم غیرفعال بشه', 'آهنگ رو هم نگه دار هم ادامه بده در یک زمان', 'فقط عبارت چراغ جلو رو خاموش کن رو ترجمه کن', 'داستانی بگو که توش میگن سناریوی کمپ رو فعال کن', 'من دارم جمله قفل کودک رو فعال کن رو تمرین می کنم', 'دیروز گفتی آینه رو جمع کن الان کاری ندارم', 'معنی جمله مصرف انرژی رو بنزینی کن رو توضیح بده', 'درباره ذخیره کردن حافظه صندلی مقاله بنویس', 'سوال دارم چرا باید چراغ مه شکن رو روشن کرد', 'این نقل قول رو بخون صدا رو کم کن', 'به زبان انگلیسی بگو ترمز اضطراری رو خاموش کن', 'درباره سناریوی چرت زدن توضیح بده اجراش نکن', 'اون حالت نامعلوم رو یه جوری عوض کن', 'همون سمتی که می دونی رو تغییر بده', 'این مورد مثل اون یکی مورد تنظیم بشه', 'به اون چیزه همون مقدار رو بده', 'تنظیمی که اسمش رو نمی گم رو فعال کن', 'ستاره ها رو با چراغ جلو خاموش کن', 'خیابون رو تو حافظه صندلی ذخیره کن', 'جاده رو با ترمز اضطراری ماساژ بده', 'آسمون رو با شارژ خودرو پر کن', 'کوه رو با حالت حفظ شارژ کوچک کن']
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
