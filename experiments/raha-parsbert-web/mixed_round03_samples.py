"""New round: contextual composition with explicit frozen expected commands."""
from data import normalize

def samples(known):
 rows=[];seen=set(known);groups=[]
 def group(name,items):
  assert len(items)==10,(name,len(items));groups.append((name,items))
 def item(t,a,**p):return t,a,p
 group('seat-off-and-recovery',[
 item('دیگه صندلی من گرم نشه گرمکن راننده رو قطع کن','SET_SEAT_HEATING',seat='driver',enabled=False),
 item('گرمای صندلی همراه کافیه گرمکنش رو از کار بنداز','SET_SEAT_HEATING',seat='passenger',enabled=False),
 item('پشتم دیگه عرق نمی کنه سردکن صندلی راننده رو متوقف کن','SET_SEAT_VENTILATION',seat='driver',enabled=False),
 item('سرمای نشیمن شاگرد زیاده تهویه صندلیش رو خاموشش کن','SET_SEAT_VENTILATION',seat='passenger',enabled=False),
 item('کمرم بهتر شد ماساژ صندلی خودم رو قطعش کن','SET_SEAT_MASSAGE',seat='driver',enabled=False),
 item('همراه دیگه ماساژ نمی خواد ماساژ صندلیش رو غیرفعال کن','SET_SEAT_MASSAGE',seat='passenger',enabled=False),
 item('صندلی خودم یخ کرده گرمکنش رو روی درجه دو راه بنداز','SET_SEAT_HEATING',seat='driver',enabled=True,level=2),
 item('نشیمن همراه داغ شده سردکن صندلیش رو روی درجه سه روشن کن','SET_SEAT_VENTILATION',seat='passenger',enabled=True,level=3),
 item('می خوام جای نشستن راننده کمی گرم باشه گرمکنش درجه یک باشه','SET_SEAT_HEATING',seat='driver',enabled=True,level=1),
 item('صندلی خودم باد بخوره سردکن راننده رو درجه دو کن','SET_SEAT_VENTILATION',seat='driver',enabled=True,level=2)])
 group('air-routing',[
 item('هوا مستقیم به پام بخوره جهت باد تهویه رو پا بذار','SET_AIRFLOW_DIRECTION',zone='front',direction='feet'),
 item('برای خنک شدن صورتم باد تهویه رو رو به صورت بفرست','SET_AIRFLOW_DIRECTION',zone='front',direction='face'),
 item('باد تهویه هم به صورتم بخوره هم پام','SET_AIRFLOW_DIRECTION',zone='front',direction='faceAndFeet'),
 item('باد دریچه ها رو به سمت شیشه جلو هدایت کن','SET_AIRFLOW_DIRECTION',zone='front',direction='windshield'),
 item('جهت خروج هوا رو روی صورت و پا با هم قرار بده','SET_AIRFLOW_DIRECTION',zone='front',direction='faceAndFeet'),
 item('بیرون دود هست گردش هوا رو داخلیش کن','SET_AIR_RECIRCULATION',mode='internal'),
 item('هوای بیرون تمیزه گردش هوا رو خارجیش کن','SET_AIR_RECIRCULATION',mode='external'),
 item('بوی دود از بیرون میاد ورودی هوا رو گردش داخل بذار','SET_AIR_RECIRCULATION',mode='internal'),
 item('هوای تازه از بیرون بیاد حالت گردش هوای خارجی رو انتخاب کن','SET_AIR_RECIRCULATION',mode='external'),
 item('تهویه رو بذار فقط هوای داخل رو بچرخونه','SET_AIR_RECIRCULATION',mode='internal')])
 group('music-control-context',[
 item('می خوام حرف همراه رو بشنوم صدای موسیقی رو میوتش کن','SET_AUDIO_MUTE',channel='media',enabled=True),
 item('صحبتم تموم شد صدای موسیقی رو از میوت خارجش کن','SET_AUDIO_MUTE',channel='media',enabled=False),
 item('راهنمای نقشه رو بی صداش کن فقط تصویرش رو ببینم','SET_AUDIO_MUTE',channel='navigation',enabled=True),
 item('صدای راهنمای مسیر دوباره شنیده بشه میوت مسیریابی رو بردار','SET_AUDIO_MUTE',channel='navigation',enabled=False),
 item('به این قطعه علاقه ندارم برو سراغ ترک بعد از این','NEXT_MEDIA'),
 item('قطعه قبلی بهتر بود همون قبلی رو انتخاب کن','PREVIOUS_MEDIA'),
 item('برای چند لحظه پخش موسیقی رو روی توقف موقت بذار','PAUSE_MEDIA'),
 item('حالا همون پخش متوقف موسیقی رو دوباره راه بنداز','PLAY_MEDIA'),
 item('گوش دادن به آهنگ رو ادامه بده از حالت مکث درش بیار','PLAY_MEDIA'),
 item('پخش آهنگ رو نگهش دار می خوام صحبت کنم','PAUSE_MEDIA')])
 group('lighting-and-scent',[
 item('نور داخل همراه ریتم آهنگ عوض بشه حالت موزیک نورپردازی رو بذار','SET_AMBIENT_LIGHT_MODE',mode='music'),
 item('رنگ نور کابین از مود رانندگی پیروی کنه','SET_AMBIENT_LIGHT_MODE',mode='driveMode'),
 item('نورپردازی داخلی به حالت رانندگی ماشین وابسته باشه','SET_AMBIENT_LIGHT_MODE',mode='driveMode'),
 item('نورهای داخل با موسیقی هماهنگ بشن','SET_AMBIENT_LIGHT_MODE',mode='music'),
 item('حالت نورپردازی داخل رو عادی کن','SET_AMBIENT_LIGHT_MODE',mode='normal'),
 item('بوی داخل مونده عطرافشان ماشین رو روشنش کن','SET_FRAGRANCE_POWER',enabled=True),
 item('عطر کافی پخش شد دستگاه رایحه رو قطعش کن','SET_FRAGRANCE_POWER',enabled=False),
 item('همراه به بو حساسه خوشبوکننده ماشین خاموش باشه','SET_FRAGRANCE_POWER',enabled=False),
 item('کابین یه عطر خوب بگیره سیستم رایحه رو فعالش کن','SET_FRAGRANCE_POWER',enabled=True),
 item('پخش بو رو متوقفش کن رایحه دیگه کار نکنه','SET_FRAGRANCE_POWER',enabled=False)])
 group('drive-context',[
 item('این مسیر برفیه مود خودرو رو اسنو بذار','SET_DRIVE_MODE',mode='snow'),
 item('برای مسیر برفی حالت رانندگی زمستون رو انتخابش کن','SET_DRIVE_MODE',mode='snow'),
 item('می خوام آروم و راحت برونم مود کامفورت رو بذار','SET_DRIVE_MODE',mode='comfort'),
 item('رانندگی راحت می خوام حالت آسایش رو انتخابش کن','SET_DRIVE_MODE',mode='comfort'),
 item('مصرف کمتر می خوام مود رانندگی اکونومی باشه','SET_DRIVE_MODE',mode='eco'),
 item('این سفر اقتصادی باشه حالت اکو رو انتخابش کن','SET_DRIVE_MODE',mode='eco'),
 item('وارد خاکی میشیم مود رانندگی اوتینگ رو بذار','SET_DRIVE_MODE',mode='outing'),
 item('برای این راه خاکی حالت آفرود رو انتخابش کن','SET_DRIVE_MODE',mode='outing'),
 item('طبق تنظیم دلخواه خودم برونیم حالت پرسونال رو بذار','SET_DRIVE_MODE',mode='personal'),
 item('ماشین با حالت رانندگی اسپرت حرکت کنه','SET_DRIVE_MODE',mode='sport')])
 group('scheduled-and-access',[
 item('شارژ طبق ساعتی که قبلا گذاشتم انجام بشه زمانبندیش رو فعال کن','SET_SCHEDULED_CHARGING',enabled=True),
 item('شارژ سر وقت قبلی شروع بشه اسکجول شارژ روشن باشه','SET_SCHEDULED_CHARGING',enabled=True),
 item('نمی خوام شارژ منتظر ساعت بمونه زمانبندی شارژ رو خاموش کن','SET_SCHEDULED_CHARGING',enabled=False),
 item('برنامه زمانی شارژ رو از حالت فعال دربیار','SET_SCHEDULED_CHARGING',enabled=False),
 item('برای سوار شدن راحت ورود آسان ماشین فعال باشه','SET_EASY_ENTRY',enabled=True),
 item('تنظیم ورود راحت رو خاموشش کن ایزی انتری نمی خوام','SET_EASY_ENTRY',enabled=False),
 item('موقع پیاده شدن راحت تر باشم ایزی اگزیت رو فعالش کن','SET_EASY_EXIT',enabled=True),
 item('خروج آسان رو از کار بنداز ایزی اگزیت خاموش باشه','SET_EASY_EXIT',enabled=False),
 item('تنظیم ایزی انتری رو روی حالت غیرفعال قرار بده','SET_EASY_ENTRY',enabled=False),
 item('تنظیم ایزی اگزیت رو روی حالت روشن قرار بده','SET_EASY_EXIT',enabled=True)])
 group('temperature-mixed-numbers',[
 item('سمت خودم سردمه دمای راننده رو شش درجه بیشترش کن','ADJUST_CABIN_TEMPERATURE',zone='driver',delta=6),
 item('همراه گرماشه دمای سمت شاگرد رو چهار درجه پایینتر ببر','ADJUST_CABIN_TEMPERATURE',zone='passenger',delta=-4),
 item('کل اتاق گرم بشه دمای کابین رو دو درجه افزایش بده','ADJUST_CABIN_TEMPERATURE',zone='front',delta=2),
 item('هوای کل اتاق خنک بشه دمای کابین رو سه درجه کاهش بده','ADJUST_CABIN_TEMPERATURE',zone='front',delta=-3),
 item('دمای سمت شاگرد برای راحتیش روی بیست و سه درجه تنظیم بشه','SET_CABIN_TEMPERATURE',zone='passenger',degreesC=23.0),
 item('برای خودم دمای راننده بیست و شش درجه باشه','SET_CABIN_TEMPERATURE',zone='driver',degreesC=26.0),
 item('دمای کل کابین رو روی نوزده درجه قرارش بده','SET_CABIN_TEMPERATURE',zone='front',degreesC=19.0),
 item('می خوام سمت راننده دما هجده درجه ثابت باشه','SET_CABIN_TEMPERATURE',zone='driver',degreesC=18.0),
 item('تنظیم دمای همراه رو روی بیست و نه درجه بذارش','SET_CABIN_TEMPERATURE',zone='passenger',degreesC=29.0),
 item('برای گرم شدن کل ماشین دمای اتاق روی سی و یک درجه باشه','SET_CABIN_TEMPERATURE',zone='front',degreesC=31.0)])
 group('windows-location',[
 item('کنار فرمون شیشه رو کامل پایین ببر هوای تازه بیاد','OPEN_WINDOW',window='driver'),
 item('پنجره جلو سمت همراه رو ببند باد اذیتش می کنه','CLOSE_WINDOW',window='passenger'),
 item('شیشه پشت راننده رو کامل بازش کن','OPEN_WINDOW',window='rearLeft'),
 item('پنجره پشت شاگرد رو کامل ببندش','CLOSE_WINDOW',window='rearRight'),
 item('شیشه پشت سر راننده نصفه باز باشه','SET_WINDOW_POSITION',window='rearLeft',percent=50),
 item('پنجره پشت همراه رو تا پنجاه درصد پایینش بده','SET_WINDOW_POSITION',window='rearRight',percent=50),
 item('شیشه راننده رو فقط سی درصد بازش کن','SET_WINDOW_POSITION',window='driver',percent=30),
 item('شیشه جلو سمت شاگرد رو شصت درصد باز نگهش دار','SET_WINDOW_POSITION',window='passenger',percent=60),
 item('پنجره عقب سمت راننده رو هشتاد درصد پایینش بکش','SET_WINDOW_POSITION',window='rearLeft',percent=80),
 item('پنجره عقب سمت شاگرد رو چهل درصد بازش کن','SET_WINDOW_POSITION',window='rearRight',percent=40)])
 wrappers=[lambda s:'رها الان می خوام '+s,lambda s:'لطف کن '+s,lambda s:'خواهش می کنم '+s,lambda s:'همین الان '+s,lambda s:'رها زحمت بکش '+s,lambda s:'ببین الان '+s,lambda s:s+' ممنونم',lambda s:'خب لطف کن '+s,lambda s:'الان لطفا '+s,lambda s:s+' رها',lambda s:'لطفا برای من '+s,lambda s:s+' لطف کن']
 def put(t,a,p,f,core,policy):
  t=normalize(t)
  if t in seen:return False
  seen.add(t);rows.append(dict(text=t,action=a,parameters=p,alternatives=[],family=f,core=normalize(core),policy=policy));return True
 for f,items in groups:
  for t,a,p in items:
   count=0
   for w in wrappers:
    count+=put(w(t),a,p,f,t,'correct')
    if count==10:break
   assert count==10
 negatives=[
 'صندلی راننده رو خوشبو کن ولی رایحه ماشین رو تغییر نده',
 'گرمکن صندلی رو برای یخ زدن نوشیدنی روشن کن',
 'کولر آشپزخونه رو روی حالت برفی ماشین بذار',
 'دمای شاگرد رو ماساژ بده تا خستگیش دربیاد',
 'آهنگ بعدی رو با شیشه راننده باز کن',
 'صندوق عقب رو با صدای بیست پخش کن',
 'درب باک بنزین رو روی نور پایین تنظیم کن',
 'ایزی اگزیت رو صد درجه گرم تر کن',
 'شارژ زمانبندی رو با بوی بنزین ماساژ بده',
 'حالت برفی رو روی پنجره اتاق خواب فعال کن',
 'همزمان گرمکن راننده رو روشن و غیرفعال کن',
 'شارژ زمانبندی هم فعال باشه هم غیرفعال',
 'صدا رو هم بی صدا کن هم از میوت دربیار',
 'شیشه پشت شاگرد هم کاملا بالا باشه هم کاملا پایین',
 'تهویه فقط هوای داخل رو بچرخونه و فقط هوای بیرون رو وارد کنه',
 'آهنگ رو هم رد کن هم روی همین قطعه بمون',
 'دمای راننده رو هم هجده کن هم سی و دو',
 'حالت رانندگی رو هم اکو بذار هم اسپرت',
 'نورپردازی هم فقط عادی باشه هم فقط با موزیک عوض بشه',
 'پرده سقف هم تا آخر باز باشه هم تا آخر بسته',
 'دیروز ورود آسان رو فعال کرده بودم امروز دست نزن',
 'قبلا گفتم صدا رو زیاد کن الان هیچ تغییری نده',
 'همراه گفت صندلیم سرده ولی الان نمی خواد چیزی روشن بشه',
 'داستانم درباره ماشینیه که میگه در باک رو باز کن',
 'فقط تلفظ عبارت ایزی اگزیت رو توضیح بده',
 'جمله ماساژور رو روشن کن رو به انگلیسی ترجمه کن',
 'می پرسم چرا شیشه عقب بخار می کنه فرمانی ندارم',
 'فقط معنی شارژ سر ساعت رو می خوام بدونم',
 'می خوام بدونم حالت اکو چیه چیزی رو فعال نکن',
 'دوستم میگه آینه رو جمع کن ولی من فقط دارم نقل قول می کنم',
 'دمای کل کابین منفی سی درجه بشه',
 'سردکن صندلی راننده رو درجه چهل بذار',
 'شیشه شاگرد رو منفی بیست درصد باز کن',
 'شدت ماساژ صندلی رو صد و بیست کن',
 'بلندی صدای موسیقی رو روی نهصد تنظیم کن',
 'اون تنظیم اون طرف رو مثل اونی کن که گفتم',
 'این یکی رو بذار رو همون قبلیش',
 'به همون اندازه سمت اون رو عوضش کن',
 'چراغ اتاق خواب رو با آهنگ ماشین هماهنگ کن',
 'کمر مبل خونه رو با ایزی انتری ماساژ بده']
 assert len(negatives)==40
 for t in negatives:
  n=0
  for w in wrappers:
   n+=put(w(t),'UNKNOWN',{},'reject-combinations',t,'reject')
   if n==5:break
  assert n==5
 assert len(rows)==1000
 return rows
