from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 scenes=[('کمپینگ','camp'),('کارواش','carWash'),('مراقبت از کودک','babyCare'),('چرت زدن','nap'),('مهمانی','party')]
 add('scenarios',(r(t.format(s=s),'ACTIVATE_SCENE',scene=v) for t in ['می خوام از سناریوی {s} استفاده کنم فعالش کن','سناریوی مناسب {s} رو اجراش کن','ماشین بره توی سناریوی {s}','برای الان حالت سناریوی {s} رو انتخاب کن'] for s,v in scenes))
 energy=[('برقی','electric'),('بنزینی','fuel'),('هیبرید','hybrid'),('حفظ شارژ','save'),('هوشمند','smart')]
 add('energy',(r(t.format(s=s),'SET_ENERGY_STRATEGY',strategy=v) for t in ['روش مصرف انرژی ماشین روی {s} باشه','استراتژی مصرف سوخت رو {s} انتخابش کن','برای مصرف انرژی حالت {s} می خوام','تنظیم مصرف انرژی خودرو رو به {s} تغییر بده'] for s,v in energy))
 lights=[('خودکار','auto'),('نور پایین','lowBeam'),('چراغ کوچک','position'),('خاموش','off')]
 add('headlights',(r(t.format(s=s),'SET_HEADLIGHT_MODE',mode=v) for t in ['حالت چراغ جلوی خودرو رو {s} بذار','برای چراغ های جلو حالت {s} رو انتخاب کن','تنظیم چراغ جلو روی {s} قرار بگیره','چراغ های جلوی ماشین در حالت {s} باشن','می خوام وضعیت چراغ جلو {s} بشه'] for s,v in lights))
 levels=[('کم','low'),('متوسط','medium'),('زیاد','high'),('میانه','medium')]
 add('regeneration',(r(t.format(s=s),'SET_REGENERATION_LEVEL',level=v) for t in ['شدت بازیافت انرژی رو روی {s} بذارش','میزان ریکاوری باتری رو {s} انتخاب کن','وقتی گاز رو رها می کنم بازیابی انرژی {s} باشه','سطح بازپس گیری انرژی رو {s} تنظیم کن','بازیابی انرژی ماشین در حد {s} کار کنه'] for s,v in levels))
 sides=[('راننده','driver'),('شاگرد','passenger'),('همراه','passenger'),('هر دو طرف','all'),('دو سمت ماشین','all')]
 add('mirrors',(r(t.format(s=s),'SET_MIRROR_FOLD',side=v,folded=b) for t,b in [('آینه {s} رو به داخل جمعش کن',True),('آینه {s} رو از حالت جمع شده بازش کن',False),('برای پارک آینه {s} رو تا کن',True),('برای دید بهتر آینه {s} رو باز بذار',False)] for s,v in sides))
 switches=[('رادار نقطه کور','SET_BLIND_SPOT_MONITORING'),('ترمز اضطراری خودکار','SET_AUTOMATIC_EMERGENCY_BRAKING'),('چراغ مه شکن عقب','SET_REAR_FOG_LIGHT'),('ایزی انتری','SET_EASY_ENTRY'),('شارژ زمان بندی شده','SET_SCHEDULED_CHARGING')]
 add('assistance-switches',(r(t.format(s=s),a,enabled=b) for t,b in [('امکان {s} رو روشن می خوام تنظیمش کن',True),('قابلیت {s} رو خاموش می خوام تنظیمش کن',False),('{s} باید فعال باقی بمونه',True),('{s} باید غیرفعال باقی بمونه',False)] for s,a in switches))
 memory_templates=[('جای فعلی صندلی راننده رو در حافظه {n} ثبت کن','SAVE_SEAT_POSITION'),('تنظیم فعلی صندلی خودم رو تو مموری {n} ذخیره کن','SAVE_SEAT_POSITION'),('حافظه صندلی {n} رو با جای الان راننده به روز کن','SAVE_SEAT_POSITION'),('صندلی راننده رو به موقعیت حافظه {n} برگردون','RECALL_SEAT_POSITION'),('مموری {n} صندلی راننده رو فراخوانی کن','RECALL_SEAT_POSITION')]
 # Four expressions refer to the two explicitly numbered factory memory slots.
 add('seat-memory',(r(t.format(n=n),a,seat='driver',slot=v) for t,a in memory_templates for n,v in [('دو','2'),('سه','3'),('شماره دو','2'),('شماره سه','3')]))
 media=[('پخش {s} رو برای صحبت کردن نگهش دار','PAUSE_MEDIA'),('{s} متوقفه دوباره پخشش کن','PLAY_MEDIA'),('از این {s} عبور کن برو بعدیش','NEXT_MEDIA'),('{s} قبلی رو انتخاب کن اینو نمی خوام','PREVIOUS_MEDIA'),('پخش {s} رو موقتا مکث بده','PAUSE_MEDIA')]
 add('media',(r(t.format(s=s),a) for t,a in media for s in ['آهنگ','موزیک','قطعه موسیقی','ترک موسیقی']))
 bad='پنجره رو با سناریوی مهمانی پر از بنزین کن|حافظه صندلی رو به حالت ابر بارونی ببر|مه شکن عقب رو مثل نوشابه سرد کن|چراغ کوچک رو تا نصفه ماساژ بده|شدت ریکاوری پیتزا رو زیاد کن|آینه همراه رو شارژ زمانبندی کن|سناریوی کارواش رو توی لیوان چای روشن کن|قفل کودک رو روی شماره آهنگ بذار|ترمز اضطراری رو با رایحه خوشبو کن|استراتژی مصرف سوخت تلویزیون خونه رو هیبرید کن|حافظه صندلی شماره نود و نه رو اجرا کن|چراغ جلو دویست درصد روشن بشه|دمای اتاق ماشین هزار و دویست درجه باشه|شیشه عقب رو منفی چهل درصد باز کن|فن رو روی درجه نهصد و ده بذار|آینه هر دو طرف رو همزمان جمع و باز نگه دار|حالت مصرف سوخت فقط برقی باشه و همزمان فقط بنزینی|چراغ مه شکن عقب هم روشن باشه هم خاموش|ترمز اضطراری هم فعال بمونه هم غیرفعال|پخش موسیقی هم ادامه داشته باشه هم متوقف باشه|فقط تفاوت حالت برقی و بنزینی رو توضیح بده|معنی سناریوی کارواش رو بگو اجراش نکن|جمله آینه رو جمع کن رو به انگلیسی ترجمه کن|دارم عبارت ترمز اضطراری رو خاموش کن رو دیکته می کنم|دیروز گفتم حافظه دو رو اجرا کن امروز نمی خوام|درباره روشن کردن مه شکن یک داستان بنویس|دوستم گفته مصرف سوخت رو برقی کن من فقط نقل می کنم|برای آموزش زبان بگو آهنگ بعدی رو پخش کن|چرا مردم میگن بازیابی انرژی رو زیاد کن فقط سواله|درباره خاموش کردن رادار نقطه کور توضیح بده تغییرش نده|همون حافظه ای که منظورمه رو اجرا کن|حالت اون مورد رو به اون چیزی که گفتم ببر|یه سمتش رو همون قدر تغییر بده|چیزی که اسمش یادم نیست رو تنظیم کن|اون گزینه رو مثل گزینه قبلیش کن|جاذبه زمین رو با ریکاوری ماشین کم کن|ماه رو با چراغ مه شکن خاموش کن|ابرها رو تو مموری صندلی ذخیره کن|جاده رو با سناریوی کارواش جابه جا کن|خورشید رو با مصرف برقی شارژ کن'.split('|')
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
