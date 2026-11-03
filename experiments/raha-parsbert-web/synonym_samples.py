"""New colloquial/synonym families; gold labels are authored before predictions."""
import json,itertools,re
from pathlib import Path
from data import normalize

def candidates():
 rows=[]
 def add(a,p,*texts):
  for t in texts:
   rows.append(dict(action=a,parameters=p,text=t))
 def toggle(a,nouns,key='enabled',extra=None):
  for n in nouns.split('|'):
   for v,op in [(True,'راه بنداز'),(True,'فعال بذار'),(False,'از کار بنداز'),(False,'غیرفعال بذار')]:
    add(a,{**(extra or {}),key:v},f'{n} رو {op}')
 for a,n in {
 'SET_AUTOMATIC_EMERGENCY_BRAKING':'ترمز خودکار در مواقع اضطراری|سیستم ترمزگیری اضطراری|سامانه امرجنسی بریک',
 'SET_AUTO_HIGH_BEAM':'نوربالای هوشمند|تعویض خودکار نور بالا|حالت خودکار نوربالا',
 'SET_BATTERY_PRECONDITIONING':'گرم کردن اولیه باتری|آماده سازی حرارتی باتری|پیش گرمکن باتری',
 'SET_BLIND_SPOT_MONITORING':'مراقبت از نقطه های کور|هشدار ناحیه کور|نظارت روی نقطه کور',
 'SET_DAYTIME_RUNNING_LIGHTS':'چراغ مخصوص روز|چراغ های روزتاب|دی لایت ماشین',
 'SET_DOOR_OPEN_WARNING':'اخطار باز شدن درها|هشدار خطر هنگام باز کردن در|سامانه هشدار در باز',
 'SET_DRIVER_GESTURES':'فرمان دادن با حرکت دست|کنترل ژستی راننده|شناسایی اشاره دست',
 'SET_DRIVER_MONITORING':'سیستم مراقبت از راننده|نظارت بر هوشیاری راننده|پایش هوشیاری راننده',
 'SET_EASY_ENTRY':'آسان وارد شدن به ماشین|قابلیت ورود راحت به خودرو|ایزی اینتری',
 'SET_EASY_EXIT':'راحت پیاده شدن از ماشین|قابلیت خروج راحت از خودرو|ایزی اگزیت صندلی',
 'SET_FORWARD_COLLISION_WARNING':'اخطار تصادف جلویی|هشدار برخورد از روبرو|اخطار برخورد با ماشین جلو',
 'SET_FRAGRANCE_POWER':'عطرافشان داخل ماشین|پخش عطر کابین|خوشبوکننده کابین',
 'SET_FRONT_CLIMATE_SYNC':'هماهنگ بودن دمای دو سمت جلو|یکسان سازی دمای راننده و همراه|سینک دمای کابین',
 'SET_FRONT_CROSS_TRAFFIC_WARNING':'اخطار عبور ماشین از عرض جلوی خودرو|هشدار عبور متقاطع جلویی|اخطار ترافیک عرضی جلویی',
 'SET_HIGHWAY_SUSPENSION_ADAPTATION':'تنظیم ارتفاع اتوماتیک در اتوبان|تعلیق تطبیقی اتوبان|کاهش ارتفاع خودکار موقع رانندگی در اتوبان',
 'SET_HILL_DESCENT_CONTROL':'کنترل پایین رفتن از شیب|کمک رانندگی در سراشیبی|کنترل حرکت توی سرپایینی',
 'SET_LEAD_VEHICLE_DEPARTURE_ALERT':'اخطار راه افتادن خودروی جلویی|اعلان شروع حرکت جلویی|هشدار موقع حرکت ماشین جلویی',
 'SET_MAX_COOLING':'خنک کردن با نهایت قدرت|حالت ماکس کولینگ|سرمایش با قدرت حداکثر',
 'SET_PEDESTRIAN_WARNING_SOUND':'صدای اخطار برای پیاده ها|صدای هشداردهنده به عابر|هشدار صوتی مخصوص عابر',
 'SET_POWER_KEEP_ENGINE_CHARGING':'شارژ باتری توسط موتور در پاور هولد|شارژ از موتور موقع حفظ برق|شارژ با موتور بنزینی در حالت حفظ برق',
 'SET_RAIN_SENSING_WIPERS':'پاک کردن خودکار شیشه در باران|برف پاک کن باران شناس|حالت خودکار پاک کننده شیشه',
 'SET_REAR_COLLISION_WARNING':'اخطار تصادف پشتی|هشدار برخورد از پشت سر|اخطار برخورد خودروی پشت سری',
 'SET_REAR_CROSS_TRAFFIC_WARNING':'هشدار عبور ماشین از عرض پشت خودرو|اخطار ترافیک عرضی پشتی|هشدار عبور متقاطع پشتی',
 'SET_REAR_DISPLAY_LOCK':'قفل صفحه نمایش سرنشین عقب|قفل مانیتور پشت|قفل نمایشگر ردیف عقب',
 'SET_REAR_FOG_LIGHT':'چراغ مخصوص مه در عقب|چراغ مه شکن پشتی|مه شکن انتهای ماشین',
 'SET_SCHEDULED_CHARGING':'شارژ طبق ساعت قبلی|شارژ طبق برنامه زمانی|شارژ طبق زمان از پیش تعیین شده',
 'SET_STABILITY_CONTROL':'سیستم حفظ پایداری خودرو|کنترل ثبات ماشین|سامانه پایداری الکترونیکی',
 'SET_TRAFFIC_SIGN_RECOGNITION':'خواندن تابلوهای جاده|شناسایی تابلوهای کنار مسیر|تشخیص علائم کنار جاده',
 'SET_AC_POWER':'کمپرسور کولر|ای سی تهویه|کمپرسور ای سی',
 'SET_AMBIENT_LIGHT_POWER':'چراغ های تزئینی داخل کابین|نورهای محیطی داخل ماشین|روشنایی تزئینی کابین',
 }.items():toggle(a,n)
 toggle('SET_CLIMATE_AUTO','حالت اتوماتیک تنظیم دما|کنترل خودکار تهویه کابین|اتوماتیک تهویه کابین',extra={'zone':'front'})
 for a,targets in [('SET_FRONT_DEFROSTER',['شیشه جلو','شیشه','پنجره جلو']),('SET_REAR_DEFROSTER',['شیشه عقب','پنجره پشت','شیشه پشتی'])]:
  for target in targets:
   add(a,{'enabled':True},f'{target} مه گرفته',f'{target} بخار کرده',f'بخار {target} رو برطرف کن',f'مه روی {target} رو بگیر',f'بخارزدای {target} رو فعال کن')
   add(a,{'enabled':False},f'مه زدای {target} رو از کار بنداز')
 for seat,noun in [('driver','صندلی من'),('passenger','صندلی نفر بغل')]:
  for a,feature in [('SET_SEAT_HEATING','گرمکن'),('SET_SEAT_VENTILATION','خنک کن'),('SET_SEAT_MASSAGE','ماساژور')]:
   add(a,{'seat':seat,'enabled':True},f'{feature} {noun} رو راه بنداز',f'{noun} رو با {feature} فعال کن')
   add(a,{'seat':seat,'enabled':False,'level':0},f'{feature} {noun} رو از کار بنداز',f'{feature} {noun} رو قطع کن')
   add(a,{'seat':seat,'enabled':True,'level':2},f'{feature} {noun} رو درجه دو بذار')
  for a,feature in [('ADJUST_SEAT_HEATING','گرمکن'),('ADJUST_SEAT_VENTILATION','خنک کن'),('ADJUST_SEAT_MASSAGE','ماساژور')]:
   add(a,{'seat':seat,'delta':1},f'{feature} {noun} رو یه پله قوی تر کن',f'قدرت {feature} {noun} رو یک درجه بیشتر کن')
   add(a,{'seat':seat,'delta':-1},f'{feature} {noun} رو یه پله ضعیف تر کن',f'قدرت {feature} {noun} رو یک درجه کمتر کن')
  for pattern,alias in [('wave','موج دار'),('rolling','غلتکی')]:add('SET_SEAT_MASSAGE_PATTERN',{'seat':seat,'pattern':pattern},f'ماساژ {noun} رو {alias} بذار',f'شیوه ماساژ {noun} رو {alias} کن')
 for a,noun,p in [('ADJUST_AMBIENT_LIGHT_BRIGHTNESS','نورهای تزئینی کابین',{}),('ADJUST_AUDIO_VOLUME','صدای آهنگ',{'channel':'media'}),('ADJUST_CABIN_TEMPERATURE','دمای فضای داخل',{'zone':'front'}),('ADJUST_FAN_SPEED','سرعت دمنده تهویه',{'zone':'front'}),('ADJUST_FRAGRANCE_INTENSITY','شدت بوی عطر',{}),('ADJUST_REGENERATION_LEVEL','شدت ریکاوری انرژی',{}),('ADJUST_WIPER_SENSITIVITY','حساسیت باران سنج برف پاک کن',{})]:
  for d,v in [(1,'بیشتر'),(-1,'کمتر')]:
   value=float(d) if a=='ADJUST_CABIN_TEMPERATURE' else d
   add(a,{**p,'delta':value},f'{noun} رو یه درجه {v} کن',f'{noun} رو یک پله {v} بذار',f'مقدار {noun} رو یک درجه {v} کن')
 for a,noun,p in [('ADJUST_SUNROOF_POSITION','سقف شیشه ای',{}),('ADJUST_SUNSHADE_POSITION','پوشش زیر سقف شیشه ای',{}),('ADJUST_WINDOW_POSITION','پنجره راننده',{'window':'driver'})]:
  for d,v in [(10,'باز'),(-10,'بسته')]:add(a,{**p,'deltaPercent':d},f'{noun} رو ده درصد بیشتر {v} کن',f'{noun} رو یک پله {v}تر کن',f'{noun} رو یک پله بیشتر {v} کن')
 for a,noun in [('OPEN_SUNROOF','سقف شیشه ای'),('CLOSE_SUNROOF','سقف شیشه ای'),('OPEN_TAILGATE','در صندوق بار'),('CLOSE_TAILGATE','در صندوق بار'),('OPEN_CHARGE_PORT','دریچه اتصال شارژر'),('OPEN_FUEL_FLAP','درپوش ورودی بنزین')]:
  ops=['باز کن','بگشا','بازش کن'] if a.startswith('OPEN') else ['ببند','بسته کن','ببندش']
  for op in ops:add(a,{},f'{noun} رو {op}')
 for w,n in [('driver','پنجره من'),('passenger','پنجره بغل'),('rearRight','پنجره پشت همراه')]:
  add('OPEN_WINDOW',{'window':w},f'{n} رو کامل بده پایین',f'{n} رو تا ته پایین بکش',f'{n} رو کامل باز کن')
  add('CLOSE_WINDOW',{'window':w},f'{n} رو کامل بکش بالا',f'{n} رو تا آخر بالا بده',f'{n} رو کامل ببند')
  add('VENT_WINDOW',{'window':w},f'{n} رو روی حالت هواخوری بذار',f'{n} رو برای رد شدن هوا حالت تهویه کن')
  add('SET_WINDOW_POSITION',{'window':w,'percent':50},f'{n} رو تا میانه بده پایین',f'{n} رو نیمه باز نگه دار')
 for a,n,k in [('SET_SUNROOF_POSITION','سقف شیشه ای','percent'),('SET_SUNSHADE_POSITION','پوشش زیر سقف','position')]:
  for v,num in [(25,'بیست و پنج'),(50,'پنجاه'),(75,'هفتاد و پنج')]:add(a,{k:v},f'{n} رو {num} درصد باز بذار',f'{n} رو {num} درصد باز نگه دار')
 for a,n,k,vals in [('SET_AMBIENT_LIGHT_BRIGHTNESS','چراغ تزئینی کابین','percent',[(25,'بیست و پنج درصد'),(50,'نصف'),(75,'هفتاد و پنج درصد')]),('SET_BATTERY_RESERVE_TARGET','ذخیره شارژ باتری','percent',[(25,'بیست و پنج درصد'),(50,'پنجاه درصد'),(75,'هفتاد و پنج درصد')]),('SET_FRAGRANCE_DURATION','مدت عطرافشانی','durationMinutes',[(30,'نیم ساعت'),(60,'یک ساعت'),(0,'صفر دقیقه')]),('SET_POWER_KEEP_DURATION','زمان نگه داشتن برق خودرو','minutes',[(60,'یک ساعت'),(120,'دو ساعت'),(150,'صد و پنجاه دقیقه')])]:
  for value,words in vals:add(a,{k:value},f'{n} رو {words} بذار',f'{n} رو روی {words} قرار بده')
 for a,n,k in [('SET_FRAGRANCE_INTENSITY','قدرت عطرافشانی','intensity'),('SET_REGENERATION_LEVEL','میزان ریکاوری باتری','level'),('SET_SUSPENSION_HEIGHT','ارتفاع بدنه ماشین','level')]:
  for v,word in [('low','کم'),('medium','میانه'),('high','زیاد')]:add(a,{k:v},f'{n} رو روی {word} قرار بده',f'{n} رو در سطح {word} نگه دار')
 for v,word in [(1,'یک'),(2,'دو'),(3,'سه')]:
  add('SET_WIPER_SENSITIVITY',{'level':v},f'حساسیت باران سنج رو درجه {word} بذار',f'حساسیت پاک کننده شیشه رو سطح {word} بذار')
 for v,word in [(2,'دو'),(4,'چهار'),(6,'شش')]:add('SET_FAN_SPEED',{'zone':'front','level':v},f'دور دمنده کولر رو {word} کن',f'درجه دمنده تهویه رو روی {word} بذار')
 for z,n in [('driver','جای من'),('passenger','جای همراه'),('front','کل اتاق')]:
  add('SET_CABIN_TEMPERATURE',{'zone':z,'degreesC':22.0},f'دمای {n} رو بیست و دو درجه بذار',f'حرارت {n} رو روی بیست و دو تنظیم کن')
 for channel,n in [('media','آهنگ'),('navigation','راهنمای مسیر'),('system','سیستم ماشین')]:
  add('SET_AUDIO_VOLUME',{'channel':channel,'level':10},f'بلندی {n} رو ده بذار',f'صدای {n} رو روی ده تنظیمش کن')
  add('SET_AUDIO_MUTE',{'channel':channel,'enabled':True},f'صدای {n} رو ساکت کن',f'صدای {n} رو میوت بذار')
  add('SET_AUDIO_MUTE',{'channel':channel,'enabled':False},f'صدای {n} رو از بی صدایی دربیار')
 for display,n in [('central','صفحه وسط'),('passenger','صفحه بغل'),('cluster','صفحه پشت فرمون')]:add('SET_DISPLAY_BRIGHTNESS',{'display':display,'percent':50},f'نور {n} رو تا نصف بذار',f'روشنایی {n} رو پنجاه درصد کن')
 for mode,n in [('day','روزانه'),('night','شبانه'),('auto','خودکار')]:add('SET_DISPLAY_THEME_MODE',{'mode':mode},f'ظاهر صفحه ها رو {n} کن',f'تم صفحه ها رو حالت {n} بذار')
 for s,n in [('driver','سمت من'),('passenger','سمت همراه'),('all','هر دو طرف')]:
  add('SET_MIRROR_FOLD',{'side':s,'folded':True},f'آینه {n} رو تا بزن',f'آینه {n} رو جمعش کن')
  add('SET_MIRROR_FOLD',{'side':s,'folded':False},f'آینه {n} رو بازش کن')
 for v,op in [(True,'قفلشون کن'),(False,'از قفل خارج کن')]:add('SET_DOOR_LOCK',{'door':'all','locked':v},f'درهای ماشین رو {op}',f'همه درب ها رو {op}')
 for s,n in [('left','سمت چپ'),('right','سمت همراه'),('both','دو طرف')]:toggle('SET_CHILD_LOCK',f'قفل ایمنی کودک {n}',extra={'side':s})
 for v,op in [(True,'درگیرش کن'),(False,'آزادش کن')]:add('SET_PARKING_BRAKE',{'engaged':v},f'ترمز پارک ماشین رو {op}',f'ترمز دستی الکترونیکی رو {op}')
 for mode,n in [('internal','توی اتاق'),('external','از بیرون')]:add('SET_AIR_RECIRCULATION',{'mode':mode},f'گردش هوا رو {n} بذار',f'حالت هوارسانی رو {n} تنظیم کن')
 for direction,n in [('face','صورت'),('feet','پا')]:
  pass
 # Values for airflow are read from the existing contract, not inferred from UI labels.
 add('SET_AIRFLOW_DIRECTION',{'direction':'face','zone':'front'},'باد کولر رو سمت صورت بفرست','خروجی باد رو به صورتم تنظیم کن','باد تهویه رو به سمت بالاتنه بده')
 for mode,n in [('normal','معمولی'),('music','هماهنگ با آهنگ'),('driveMode','هماهنگ با حالت رانندگی')]:add('SET_AMBIENT_LIGHT_MODE',{'mode':mode},f'نورهای تزئینی رو {n} بذار',f'حالت نور محیطی رو {n} کن')
 for mode,n in [('automatic','اتوماتیک'),('deepPedal','فشار عمیق روی ترمز')]:add('SET_AUTO_HOLD',{'mode':mode},f'نحوه اتوهلد رو {n} بذار',f'حالت نگه داشتن ترمز رو روی {n} قرار بده')
 for mode,n in [('comfort','راحتی'),('eco','کم مصرف'),('snow','زمستونی'),('outing','جاده خاکی'),('personal','شخصی سازی شده'),('sport','اسپرت')]:add('SET_DRIVE_MODE',{'mode':mode},f'سبک رانندگی رو {n} بذار',f'مود ماشین رو {n} تنظیم کن')
 for strategy,n in [('electric','برق اول'),('fuel','بنزین اول'),('hybrid','ترکیبی'),('save','حفظ باتری'),('smart','هوشمند')]:add('SET_ENERGY_STRATEGY',{'strategy':strategy},f'روش مصرف انرژی رو {n} بذار',f'استراتژی مصرف ماشین رو {n} کن')
 for mode,n in [('auto','اتوماتیک'),('lowBeam','نور کوتاه'),('off','خاموش'),('position','چراغ کوچک')]:add('SET_HEADLIGHT_MODE',{'mode':mode},f'چراغ های جلویی رو حالت {n} بذار',f'حالت روشنایی جلو رو {n} کن')
 for mode,n in [('keeping','نگه داشتن ماشین بین خط ها'),('warning','فقط اخطار'),('off','خاموش')]:add('SET_LANE_SUPPORT_MODE',{'mode':mode},f'کمک بین خطوط رو {n} بذار',f'عملکرد سیستم بین خطوط رو {n} کن')
 for typ,n in [('vibration','لرزش'),('voice','اخطار صوتی')]:add('SET_LANE_DEPARTURE_ALERT_TYPE',{'type':typ},f'شیوه هشدار بیرون رفتن از خط رو {n} بذار',f'هشدار رد شدن از خط رو به شکل {n} بده')
 for ch,n in [(1,'بامبوی تازه'),(2,'گل صد تومانی'),(3,'مه آبی')]:
  add('SET_FRAGRANCE_CHANNEL',{'channel':ch},f'عطر کابین رو بذار روی {n}',f'رایحه {n} رو انتخابش کن')
 add('CONFIGURE_FRAGRANCE',{'channel':1,'enabled':True,'durationMinutes':30},'بوی بامبو رو برای نیم ساعت پخش کن','عطر بامبو رو سی دقیقه راه بنداز','عطرافشانی بامبو رو نیم ساعت فعال کن')
 for scene,n in [('camp','اردو'),('carWash','شستن ماشین'),('babyCare','مراقبت بچه')]:add('ACTIVATE_SCENE',{'scene':scene},f'سناریوی {n} رو راه بنداز',f'ماشین رو توی سناریوی {n} بذار')
 add('DEACTIVATE_SCENE',{'scene':'current'},'سناریویی که الان فعاله رو ببند','از سناریوی در حال اجرا بیرون بیا','سناریوی روشن رو متوقفش کن')
 for a,n in [('SAVE_SEAT_POSITION','ثبت کن'),('RECALL_SEAT_POSITION','فراخوانی کن')]:
  for slot,word in [('driver','یک'),('2','دو'),('3','سه')]:add(a,{'seat':'driver','slot':slot},f'حافظه شماره {word} صندلی رو {n}',f'مموری {word} صندلی من رو {n}')
 add('CALL_CONTACT',{'contact_query':'علی'},'به اسم علی یه تماس برقرار کن','یه تلفن به علی بکن','با علی تماس تلفنی بگیر')
 add('CONFIRM_CALL',{},'آره همونه شماره رو بگیر','قبوله تماس رو برقرار کن','همون شخصه زنگش بزن')
 add('CANCEL_DIALOG',{},'نه منصرف شدم تماس نگیر','بی خیال تماس شدم لغوش کن','نمی خوام تماس بگیری لغو کن')
 add('NAVIGATE_TO_SAVED_PLACE',{'destination':'shahpary'},'راه تعمیرگاه شاه پری رو پیدا کن','منو تا تعمیرگاه شاه پری راهنمایی کن','مسیرم رو به تعمیرگاه شاه پری تنظیم کن')
 simple={
 'NEXT_MEDIA':({},['قطعه بعد از این رو بذار','بزن ترک بعدیش','این موزیک رو ردش کن']),
 'PREVIOUS_MEDIA':({},['قطعه قبل از این رو بذار','بزن ترک قبلیش','برگرد به آهنگ پیشین']),
 'PLAY_MEDIA':({},['آهنگ رو ادامه ش بده','پخش آهنگ رو از سر بگیر','موزیک رو دوباره به پخش بنداز']),
 'PAUSE_MEDIA':({},['آهنگ رو یه مکث بده','پخش آهنگ رو نگهش دار','موزیک رو فعلا متوقفش کن']),
 'OPEN_CAMERA':({'view':'default'},['صفحه تصویر دوربین رو بیار جلو','دوربین ماشین رو برام بالا بیار','نمای دوربین خودرو رو بازش کن']),
 'OPEN_DEFAULT_MAP':({},['برنامه راه یابی رو بیار جلو','صفحه نرم افزار نقشه رو بیار','اپ مسیریاب پیشفرض رو بالا بیار']),
 'OPEN_DEFAULT_MUSIC':({},['نرم افزار آهنگ رو بیار جلو','پخش کننده موزیک رو بالا بیار','صفحه موزیک پلیر رو بیار']),
 'OPEN_OEM_RADIO':({},['برنامه موج رادیو رو بیار','صفحه رادیوی خودرو رو بیار جلو','اپ رادیوی ماشین رو بالا بیار']),
 'START_VIDEO_RECORDING':({'view':'default'},['شروع کن فیلم بگیر','ضبط تصویر ویدیویی رو راه بنداز','ویدیو گرفتن رو راه بنداز']),
 'STOP_VIDEO_RECORDING':({},['دیگه فیلم نگیر ضبط رو قطع کن','به ضبط تصویر ویدیویی خاتمه بده','ویدیو گرفتن رو تمومش کن']),
 'TAKE_PHOTO':({'view':'default'},['یه عکس بنداز','با دوربین یه عکس بنداز','یه تصویر با دوربین ثبت کن']),
 'SWITCH_CAMERA':({},['تصویر رو به دوربین دیگه سوئیچ کن','از دوربین دیگه تصویر بده','دوربین فعلی رو تعویض کن']),
 'SWAP_DISPLAY_APPS':({},['جای اپ های دو صفحه رو با هم جابه جا کن','اپ های دو نمایشگر رو با هم معاوضه کن','برنامه وسط و برنامه شاگرد رو جاشونو عوض کن']),
 'STOP_CHARGING':({},['به شارژ باتری خاتمه بده','دیگه باتری رو شارژ نکن','فرایند شارژ خودرو رو خاتمه بده']),
 'SET_CLIMATE_POWER':({'enabled':False,'zone':'front'},['کل سیستم هوارسانی کابین رو خاموش کن','تهویه کل کابین رو متوقف کن','سیستم تهویه داخل رو از کار بنداز'])}
 for a,(p,ts) in simple.items():add(a,p,*ts)
 return rows

def samples(known):
 from collections import defaultdict
 buckets=defaultdict(list)
 for row in candidates():buckets[row['action']].append(row)
 out=[];seen=set(known)
 styles=[lambda t:t,lambda t:'میشه '+t,lambda t:t+' برام',lambda t:'می تونی '+t+'؟',lambda t:'لطفا '+t,lambda t:'بی زحمت '+t]
 for action,rows in sorted(buckets.items()):
  chosen=[]
  for style in styles:
   for i,r in enumerate(rows):
    text=normalize(style(r['text']))
    if text in seen:continue
    seen.add(text)
    chosen.append(dict(group=action,text=text,action=action,parameters=r['parameters'],policy='correct',kind='colloquial-synonym',split='unseen',context='pending_call_confirmation' if action in ['CONFIRM_CALL','CANCEL_DIALOG'] else 'none',alternatives=[],family=normalize(r['text'])))
    if len(chosen)==12:break
   if len(chosen)==12:break
  if len(chosen)!=12:raise ValueError((action,len(chosen)))
  out.extend(chosen)
 assert len(out)==1368
 return out
