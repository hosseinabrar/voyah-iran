from data import normalize

def samples(known):
 groups=[]
 def add(name,items):
  items=list(items);assert len(items)==20,(name,len(items));groups.append((name,items))
 def r(t,a,**p):return t,a,p
 sides=[('سمت فرمان','driver'),('سمت شاگرد جلو','passenger'),('عقب پشت راننده','rearLeft'),('عقب پشت همراه','rearRight'),('جلو سمت بغل','passenger')]
 add('window-opening',(r(t.format(s=s),'SET_WINDOW_POSITION',window=w,percent=n) for t,n in [('پنجره {s} رو چهل درصد باز نگهش دار',40),('شیشه {s} رو تا شصت درصد بازش کن',60),('نصف پنجره {s} باز بمونه',50),('پنجره {s} به اندازه سی درصد پایین بره',30)] for s,w in sides))
 zones=[('سمت فرمان','driver'),('جلو سمت همراه','passenger'),('طرف شاگرد','passenger'),('تمام کابین','front'),('فضای اتاق ماشین','front')]
 add('temperature-fixed',(r(t.format(s=s),'SET_CABIN_TEMPERATURE',zone=z,degreesC=float(n)) for t,n in [('برای تهویه {s} دمای شانزده رو انتخاب کن',16),('دمای هوای {s} رو ببر روی بیست و سه',23),('تنظیم حرارت {s} روی بیست و هفت باشه',27),('دمای تهویه {s} روی عدد سی باشه',30)] for s,z in zones))
 add('temperature-change',(r(t.format(s=s),'ADJUST_CABIN_TEMPERATURE',zone=z,delta=n) for t,n in [('دمای هوای {s} رو دو درجه کمش کن',-2),('دمای هوای {s} رو پنج درجه زیادش کن',5),('برای {s} سه درجه از دمای فعلی کم کن',-3),('برای {s} چهار درجه به دمای فعلی اضافه کن',4)] for s,z in zones))
 seats=[('راننده','driver'),('همراه','passenger'),('شاگرد','passenger'),('خودم','driver'),('نفر بغلی','passenger')]
 add('seat-stop',(r(t.format(s=s),a,seat=seat,enabled=False,level=0) for t,a in [('سردکن صندلی {s} دیگه روشن نمونه خاموشش کن','SET_SEAT_VENTILATION'),('حرارت صندلی {s} کافیه گرمکن رو خاموشش کن','SET_SEAT_HEATING'),('ماساژ صندلی {s} کافیه متوقفش کن','SET_SEAT_MASSAGE'),('سیستم تهویه صندلی {s} خاموش بشه','SET_SEAT_VENTILATION')] for s,seat in seats))
 nums=[('یک',1),('چهار',4),('شش',6),('هفت',7),('ده',10)]
 add('volume-relative',(r(t.format(n=n),'ADJUST_AUDIO_VOLUME',channel='media',delta=sign*v) for t,sign in [('صدای پخش موسیقی رو {n} واحد کمش کن',-1),('صدای پخش موسیقی رو {n} واحد زیادش کن',1),('بلندی آهنگ به اندازه {n} پله کم بشه',-1),('بلندی آهنگ به اندازه {n} پله زیاد بشه',1)] for n,v in nums))
 fan_nums=[('صفر',0),('یک',1),('سه',3),('پنج',5),('هفت',7)]
 add('fan-fixed',(r(t.format(n=n),'SET_FAN_SPEED',zone='front',level=v) for t in ['فن اتاق ماشین با سرعت {n} تنظیم بشه','درجه {n} رو برای دمنده تهویه می خوام','برای فن کابین سرعت {n} رو بذارش','دمندگی کولر روی سطح {n} قرار بگیره'] for n,v in fan_nums))
 settings=[('شارژ سر ساعت','SET_SCHEDULED_CHARGING'),('ایزی انتری','SET_EASY_ENTRY'),('ایزی اگزیت','SET_EASY_EXIT'),('پخش عطر داخل ماشین','SET_FRAGRANCE_POWER'),('چراغ های تزئینی داخل','SET_AMBIENT_LIGHT_POWER')]
 add('switch-purpose',(r(t.format(s=s),a,enabled=b) for t,b in [('قابلیت {s} رو بذار فعال بمونه',True),('قابلیت {s} رو بذار غیرفعال بمونه',False),('{s} در حالت روشن قرار بگیره',True),('{s} در حالت خاموش قرار بگیره',False)] for s,a in settings))
 modes=[('اکونومی','eco'),('کامفورت','comfort'),('اسنو','snow'),('اوتینگ','outing'),('پرسونال','personal')]
 add('drive-modes',(r(t.format(m=m),'SET_DRIVE_MODE',mode=v) for t in ['مود رانندگی {m} رو روی خودرو اعمال کن','ماشین با حالت رانندگی {m} کار کنه','حالت رانندگی رو به {m} تبدیلش کن','انتخابم برای مود رانندگی {m} هست تنظیمش کن'] for m,v in modes))
 bad=['صندلی رو با شماره آهنگ بنزین بزن', 'رادار رو با درجه پنجره گرم کن', 'آینه رو به حالت بخار غذای گرم ببر', 'سانروف رو روی سردکن صندلی تنظیم کن', 'شدت ماساژ باک بنزین رو کم کن', 'شارژر رو با کولر پخش کن', 'نور داخل ماشین رو مثل شیشه پایین بکش', 'ولوم رو به حالت قفل صندوق ببر', 'فن تهویه رو به آینه عقب تبدیل کن', 'دمای کابین رو با شماره مخاطب قفل کن', 'درجه گرمکن صندلی روی هشتاد باشه', 'سرعت فن روی سیصد و چهار باشه', 'بلندی صدا رو منفی پنجاه انتخاب کن', 'دمای همراه روی پانصد تنظیم بشه', 'شیشه سمت راننده منفی صد درصد باز بشه', 'گرمکن صندلی راننده همین لحظه هم روشن هم خاموش باشه', 'دمای همراه همزمان روی بیست و روی سی باشه', 'فن همزمان با درجه دو و درجه هفت کار کنه', 'شارژ سر ساعت هم فعال هم غیر فعال بمونه', 'شیشه راننده رو همزمان تا آخر بالا و تا آخر پایین ببر', 'فقط بنویس دمای کابین رو دو درجه کم کن', 'برای تمرین خواندن بگو صدا رو کم کن', 'این نقل قول رو تکرار کن گرمکن صندلی رو خاموش کن', 'درباره جمله ماساژور رو روشن کن حرف می زنم اجرا نکن', 'معنی فارسی ایزی انتری رو فعال کن رو توضیح بده', 'داستان دیروزه گفته بودم پنجره رو باز کن الان کاری نکن', 'جمله فن رو روی سه بذار چند بخش داره', 'متن شیشه راننده رو ببند رو صرفا ترجمه کن', 'دارم دیکته میگم مود رانندگی رو برفی کن', 'فقط کاربرد دستور رایحه رو خاموش کن رو توضیح بده', 'چیزی که نه اسمش رو گفتم نه مقدارش رو تنظیم کن', 'یه گزینه نامعلوم رو به حالت نامعلوم ببر', 'همون طرف نامشخص رو تغییرش بده', 'دقیقا چیزی که فقط تو ذهنمه اجرا کن', 'اون بخش بی اسم رو روی مقدار نامعلوم بذار', 'پنکه اتاق خواب خونه رو روی سه بذار', 'صدای تلویزیون خونه رو از تو ماشین زیاد کن', 'درخت باغ رو با سردکن صندلی خنک کن', 'فن خورشید رو روی درجه دو روشن کن', 'شیشه آسمون رو با پنجره ماشین ببند']
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
