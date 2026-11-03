"""Targeted elliptical imperatives, gold frozen before inference; not all unseen."""
from data import normalize

def samples(known):
 cores={}
 def add(text,a,**p):
  text=normalize(text)
  if text in cores:assert cores[text]==(a,p)
  cores[text]=(a,p)
 for obj in ['پرده','پرده سقف','پرده سانروف','سایبان']:
  for state,value in [('باز',100),('بسته',0),('کامل باز',100),('کاملا بسته',0),('نصفه باز',50)]:add(obj+' '+state,'SET_SUNSHADE_POSITION',position=value)
 for obj in ['نور داخل','نور داخل ماشین','نور کابین','نورپردازی داخلی','نورپردازی']:
  for state,v in [('کم',-1),('کمتر',-1),('زیاد',1),('بیشتر',1)]:add(obj+' '+state,'ADJUST_AMBIENT_LIGHT_BRIGHTNESS',delta=v)
  for state,v in [('روشن',True),('خاموش',False),('فعال',True),('غیر فعال',False)]:add(obj+' '+state,'SET_AMBIENT_LIGHT_POWER',enabled=v)
  for state,v in [('نصف',50),('پنجاه درصد',50),('بیست درصد',20)]:add(obj+' '+state,'SET_AMBIENT_LIGHT_BRIGHTNESS',percent=v)
 for obj,a in [('کولر','SET_AC_POWER'),('رایحه','SET_FRAGRANCE_POWER'),('خوشبو کننده','SET_FRAGRANCE_POWER'),('ایزی انتری','SET_EASY_ENTRY'),('ورود آسان','SET_EASY_ENTRY'),('ایزی اگزیت','SET_EASY_EXIT'),('خروج آسان','SET_EASY_EXIT'),('شارژ زمان بندی شده','SET_SCHEDULED_CHARGING')]:
  for state,v in [('روشن',True),('خاموش',False),('فعال',True),('غیر فعال',False)]:add(obj+' '+state,a,enabled=v)
 for obj,a in [('گرمکن صندلی','SET_SEAT_HEATING'),('سردکن صندلی','SET_SEAT_VENTILATION'),('ماساژور صندلی','SET_SEAT_MASSAGE')]:
  for side,seat in [('', 'driver'),('راننده','driver'),('شاگرد','passenger'),('همراه','passenger')]:
   base=(obj+' '+side).strip()
   for state,v in [('روشن',True),('خاموش',False)]:add(base+' '+state,a,seat=seat,**({'enabled':True} if v else {'enabled':False,'level':0}))
   for state,v in [('یک',1),('دو',2),('سه',3)]:add(base+' سطح '+state,a,seat=seat,enabled=True,level=v)
 for obj,window in [('شیشه راننده','driver'),('پنجره راننده','driver'),('شیشه شاگرد','passenger'),('شیشه پشت راننده','rearLeft'),('شیشه عقب راست','rearRight')]:
  for state,a in [('باز','OPEN_WINDOW'),('بسته','CLOSE_WINDOW')]:add(obj+' '+state,a,window=window)
 for obj in ['سانروف','سان روف']:
  for state,a in [('باز','OPEN_SUNROOF'),('بسته','CLOSE_SUNROOF')]:add(obj+' '+state,a)
 for obj,side in [('آینه راننده','driver'),('آینه شاگرد','passenger'),('آینه ها','all')]:
  for state,v in [('باز',False),('جمع',True),('تا شده',True)]:add(obj+' '+state,'SET_MIRROR_FOLD',side=side,folded=v)
 for obj in ['فن','فن کولر','فن تهویه']:
  for state,v in [('کم',-1),('کمتر',-1),('زیاد',1),('بیشتر',1)]:add(obj+' '+state,'ADJUST_FAN_SPEED',zone='front',delta=v)
  for state,v in [('یک',1),('سه',3),('پنج',5),('هفت',7)]:add(obj+' '+state,'SET_FAN_SPEED',zone='front',level=v)
 for obj in ['صدای موزیک','بلندی موسیقی','ولوم آهنگ']:
  for state,v in [('کم',-1),('کمتر',-1),('زیاد',1),('بیشتر',1)]:add(obj+' '+state,'ADJUST_AUDIO_VOLUME',channel='media',delta=v)
  for state,v in [('پنج',5),('پانزده',15),('بیست',20)]:add(obj+' '+state,'SET_AUDIO_VOLUME',channel='media',level=v)
 for obj,zone in [('دمای کابین','front'),('دمای راننده','driver'),('دمای شاگرد','passenger')]:
  for state,v in [('گرم تر',1),('خنک تر',-1),('سردتر',-1)]:add(obj+' '+state,'ADJUST_CABIN_TEMPERATURE',zone=zone,delta=v)
  for state,v in [('بیست',20.),('بیست و سه',23.),('بیست و پنج',25.)]:add(obj+' '+state,'SET_CABIN_TEMPERATURE',zone=zone,degreesC=v)
 # Explicit incompatible targets, contradictory states, incomplete targets, questions/negation.
 for obj in ['سانروف','پرده','آینه راننده','شیشه شاگرد']:
  for bad in ['گرمکن روشن','ماساژ روشن','رایحه زیاد','صدا پانزده','هم باز هم بسته']:add(obj+' '+bad,'UNKNOWN')
 for obj in ['پیتزا','پرده اتاق خواب','لامپ آشپزخانه','یخچال','پنکه خانه','بخاری خانه','شیشه مربا','درب کمد']:
  for state in ['باز','بسته','روشن','خاموش']:add(obj+' '+state,'UNKNOWN')
 for text in ['کم','بیشتر','باز','خاموش','پرده','نور داخل','فن کولر','شیشه شاگرد','گرمکن صندلی','نور داخل هم کم هم زیاد','فن کولر هم سه هم هفت','دمای کابین پنجاه','صدای موزیک صد','کولر روشن خاموش','ماساژور صندلی باز','پرده روشن','سانروف خنک','آینه راننده گرم','کولر روشن نکن','پرده باز نکن','آیا پرده باز است','آیا کولر روشن است','چرا نور داخل کم است','اگر هوا گرم شد کولر روشن','اگر شب شد نور داخل کم']:add(text,'UNKNOWN')
 rows=[]
 for core,(a,p) in cores.items():
  for t in [core,'رها '+core]:
   rows.append(dict(text=t,core=core,family=a,policy='reject' if a=='UNKNOWN' else 'correct',action=a,parameters=p,previously_seen=normalize(t) in known))
 return rows
