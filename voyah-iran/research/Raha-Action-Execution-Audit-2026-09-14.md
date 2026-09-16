# ممیزی اجرای اکشن‌های رها — ۲۰۲۶/۰۹/۱۴

این ممیزی کد است، نه گواهی تست همهٔ عملیات روی خودرو. مبنا: نسخهٔ استخراج‌شدهٔ H97C در work/raha-oem/can، VehicleAir و VehicleSetting و کارت لانچر چین. فایل‌های استخراج‌شده در ریپو توزیع نمی‌شوند؛ نام متد و شماره خط برای پیگیری نسخهٔ فعلی ثبت شده است.

تفاوت مهم: پاسخ موفق Binder فقط دریافت درخواست را نشان می‌دهد. کنترلر جدید تهویه تغییر بازخورد را انتظار می‌کشد؛ سایر عملیات هنوز الزاماً تأیید پایان فیزیکی ندارند. حرکت مقداری شیشه‌ها طبق گزارش تستر باز است و تغییر نکرد. SET_MAX_COOLING خاموش مستقل ندارد؛ کاربر می‌تواند تهویهٔ خودکار یا دما/فن را انتخاب کند. بار دیگر زدن پالس با بازخورد ثابت ممنوع است؛ چرخهٔ گردش هوا فقط پس از تغییر تأییدشده قدم بعدی می‌رود.

فهرست فعلی 65 نوع اکشن دارد (عدد قدیمی ۶۳ مربوط به مرحلهٔ قبلی توسعه بود).

| اکشن | نوع | قرارداد اجرای فعلی | شاهد کد OEM | وضعیت |
|---|---|---|---|---|
| SET_CLIMATE_POWER | کلید Toggle | خواندن بازخورد زنده؛ فقط در صورت تفاوت پالس ۱؛ انتظار تغییر؛ عدم تکرار با بازخورد ثابت | AirConditionFrontFragment.setSwitch/setAC/setAuto/setFrontDefrost/setFrontSync؛ H97C:7143–7253 | اصلاح شد؛ تست کنترلر موفق |
| SET_AC_POWER | کلید Toggle | خواندن بازخورد زنده؛ فقط در صورت تفاوت پالس ۱؛ انتظار تغییر؛ عدم تکرار با بازخورد ثابت | AirConditionFrontFragment.setSwitch/setAC/setAuto/setFrontDefrost/setFrontSync؛ H97C:7143–7253 | اصلاح شد؛ تست کنترلر موفق |
| SET_MAX_COOLING | عملیات ترکیبی | روشن‌کردن تهویه، خروج از اتوماتیک، AC روشن، دمای دو سمت ۱۵٫۵، فن۸، گردش داخلی؛ خاموش‌کردن MAX مستقل پشتیبانی ندارد | H97C.setAirConditionState:7189–7268؛ نبود شاخه AC_MAX_SWITCH | اصلاح روشن‌کردن؛ خاموش‌کردن مستقل صریحاً خطا می‌دهد |
| SET_CLIMATE_AUTO | کلید Toggle | خواندن بازخورد زنده؛ فقط در صورت تفاوت پالس ۱؛ انتظار تغییر؛ عدم تکرار با بازخورد ثابت | AirConditionFrontFragment.setSwitch/setAC/setAuto/setFrontDefrost/setFrontSync؛ H97C:7143–7253 | اصلاح شد؛ تست کنترلر موفق |
| ADJUST_CABIN_TEMPERATURE | مقدار مستقیم | دمای دو سمت یا سمت انتخابی ×۱۰؛ محدوده۱۵٫۵ تا۳۰٫۵؛ تغییر نسبی از بازخورد | H97C:7235–7239؛ AirConditionControl.setAllTemp/setLeftTemp | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_FAN_SPEED | مقدار مستقیم | ۱ تا۸؛ ابتدا تضمین روشن‌بودن تهویه بدون Toggle اضافی | H97C:7254–7261 | تطبیق کد؛ نیازمند تست خودرو |
| SET_AIRFLOW_DIRECTION | enum مستقیم | صورت۰، صورت‌وپا۱، پا۲، شیشه‌وپا۳، شیشه۴، همه۶؛ تهویه روشن | H97C:7233؛ AirConditionFrontFragment/updateMode | تطبیق کد؛ نیازمند تست خودرو |
| SET_AIR_RECIRCULATION | چرخه‌ای | بازخورد داخل=۰، بیرون=۱، خودکار=۲؛ حداکثر دو پالس با تأیید تغییر بین آن‌ها | VoiceAirConditionListener(model97):396–439؛ H97C:7231 | اصلاح شد؛ تست کنترلر موفق |
| SET_FRONT_DEFROSTER | کلید Toggle | خواندن بازخورد زنده؛ فقط در صورت تفاوت پالس ۱؛ انتظار تغییر؛ عدم تکرار با بازخورد ثابت | AirConditionFrontFragment.setSwitch/setAC/setAuto/setFrontDefrost/setFrontSync؛ H97C:7143–7253 | اصلاح شد؛ تست کنترلر موفق |
| SET_REAR_DEFROSTER | مقدار مستقیم | روشن=۱، خاموش=۰؛ SDK به درخواست ۲/۱ تبدیل می‌کند | AirConditionFrontFragment:1483؛ H97C:7217 | تطبیق کد؛ نیازمند تست خودرو |
| SET_FRONT_CLIMATE_SYNC | کلید Toggle | خواندن بازخورد زنده؛ فقط در صورت تفاوت پالس ۱؛ انتظار تغییر؛ عدم تکرار با بازخورد ثابت | AirConditionFrontFragment.setSwitch/setAC/setAuto/setFrontDefrost/setFrontSync؛ H97C:7143–7253 | اصلاح شد؛ تست کنترلر موفق |
| SET_CABIN_TEMPERATURE | مقدار مستقیم | دمای دو سمت یا سمت انتخابی ×۱۰؛ محدوده۱۵٫۵ تا۳۰٫۵؛ تغییر نسبی از بازخورد | H97C:7235–7239؛ AirConditionControl.setAllTemp/setLeftTemp | تطبیق کد؛ نیازمند تست خودرو |
| SET_FAN_SPEED | مقدار مستقیم | ۱ تا۸؛ ابتدا تضمین روشن‌بودن تهویه بدون Toggle اضافی | H97C:7254–7261 | تطبیق کد؛ نیازمند تست خودرو |
| SET_SEAT_HEATING | سطح مستقیم / خاموش مستقیم | سطح۱..۳ خودش بیت روشن=۲ را فعال می‌کند؛ SWITCH=۱ خاموش؛ مقدار ذخیره‌شدهٔ سطح الزاماً وضعیت روشن نیست | H97C.setIVI_scuSet:5913–5983؛ SeatFrontManager.sendSeatCommand | تطبیق کد؛ نیازمند تست خودرو |
| SET_SEAT_VENTILATION | سطح مستقیم / خاموش مستقیم | سطح۱..۳ خودش بیت روشن=۲ را فعال می‌کند؛ SWITCH=۱ خاموش؛ مقدار ذخیره‌شدهٔ سطح الزاماً وضعیت روشن نیست | H97C.setIVI_scuSet:5913–5983؛ SeatFrontManager.sendSeatCommand | تطبیق کد؛ نیازمند تست خودرو |
| SET_SEAT_MASSAGE | سطح مستقیم / خاموش مستقیم | سطح۱..۳ خودش بیت روشن=۲ را فعال می‌کند؛ SWITCH=۱ خاموش؛ مقدار ذخیره‌شدهٔ سطح الزاماً وضعیت روشن نیست | H97C.setIVI_scuSet:5913–5983؛ SeatFrontManager.sendSeatCommand | تطبیق کد؛ نیازمند تست خودرو |
| SET_SEAT_MASSAGE_PATTERN | enum مستقیم + روشن | موجی۱، غلتکی/چرخشی۲؛ SDK روشن=۲ و شدت قبلی را همراه الگو می‌فرستد | H97C:5963–5983 | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_SEAT_HEATING | سطح مستقیم / خاموش مستقیم | سطح۱..۳ خودش بیت روشن=۲ را فعال می‌کند؛ SWITCH=۱ خاموش؛ مقدار ذخیره‌شدهٔ سطح الزاماً وضعیت روشن نیست | H97C.setIVI_scuSet:5913–5983؛ SeatFrontManager.sendSeatCommand | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_SEAT_VENTILATION | سطح مستقیم / خاموش مستقیم | سطح۱..۳ خودش بیت روشن=۲ را فعال می‌کند؛ SWITCH=۱ خاموش؛ مقدار ذخیره‌شدهٔ سطح الزاماً وضعیت روشن نیست | H97C.setIVI_scuSet:5913–5983؛ SeatFrontManager.sendSeatCommand | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_SEAT_MASSAGE | سطح مستقیم / خاموش مستقیم | سطح۱..۳ خودش بیت روشن=۲ را فعال می‌کند؛ SWITCH=۱ خاموش؛ مقدار ذخیره‌شدهٔ سطح الزاماً وضعیت روشن نیست | H97C.setIVI_scuSet:5913–5983؛ SeatFrontManager.sendSeatCommand | تطبیق کد؛ نیازمند تست خودرو |
| CONFIGURE_FRAGRANCE | مقدار مستقیم / تایمر | روشن۲ خاموش۱؛ کانال۱..۳، شدت۱..۳؛ مدت۰ نامحدود،۱ سی دقیقه،۲ شصت دقیقه | H97C.setFCM_DurationCtrl:6262؛ setIVI_state_Lin_18:6489–6506 | تطبیق کد؛ نیازمند تست خودرو |
| SET_FRAGRANCE_DURATION | مقدار مستقیم / تایمر | روشن۲ خاموش۱؛ کانال۱..۳، شدت۱..۳؛ مدت۰ نامحدود،۱ سی دقیقه،۲ شصت دقیقه | H97C.setFCM_DurationCtrl:6262؛ setIVI_state_Lin_18:6489–6506 | تطبیق کد؛ نیازمند تست خودرو |
| SET_FRAGRANCE_POWER | مقدار مستقیم / تایمر | روشن۲ خاموش۱؛ کانال۱..۳، شدت۱..۳؛ مدت۰ نامحدود،۱ سی دقیقه،۲ شصت دقیقه | H97C.setFCM_DurationCtrl:6262؛ setIVI_state_Lin_18:6489–6506 | تطبیق کد؛ نیازمند تست خودرو |
| SET_FRAGRANCE_CHANNEL | مقدار مستقیم / تایمر | روشن۲ خاموش۱؛ کانال۱..۳، شدت۱..۳؛ مدت۰ نامحدود،۱ سی دقیقه،۲ شصت دقیقه | H97C.setFCM_DurationCtrl:6262؛ setIVI_state_Lin_18:6489–6506 | تطبیق کد؛ نیازمند تست خودرو |
| SET_FRAGRANCE_INTENSITY | مقدار مستقیم / تایمر | روشن۲ خاموش۱؛ کانال۱..۳، شدت۱..۳؛ مدت۰ نامحدود،۱ سی دقیقه،۲ شصت دقیقه | H97C.setFCM_DurationCtrl:6262؛ setIVI_state_Lin_18:6489–6506 | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_FRAGRANCE_INTENSITY | مقدار مستقیم / تایمر | روشن۲ خاموش۱؛ کانال۱..۳، شدت۱..۳؛ مدت۰ نامحدود،۱ سی دقیقه،۲ شصت دقیقه | H97C.setFCM_DurationCtrl:6262؛ setIVI_state_Lin_18:6489–6506 | تطبیق کد؛ نیازمند تست خودرو |
| SET_WINDOW_POSITION | حرکت و STOP بر اساس بازخورد | خواندن HorizontalSts؛ حرکت۴/۲ و STOP۵؛ دقت مشاهده‌شده روی خودرو مشکل دارد؛ طبق خواسته کاربر تغییر نکرد | WindowPositionController؛ H97C.setIVI_comfortSet | باز: نیازمند لاگ خودرو؛ تأیید نشده |
| ADJUST_WINDOW_POSITION | حرکت و STOP بر اساس بازخورد | خواندن HorizontalSts؛ حرکت۴/۲ و STOP۵؛ دقت مشاهده‌شده روی خودرو مشکل دارد؛ طبق خواسته کاربر تغییر نکرد | WindowPositionController؛ H97C.setIVI_comfortSet | باز: نیازمند لاگ خودرو؛ تأیید نشده |
| ADJUST_SUNROOF_POSITION | درصد مستقیم | ۰..۱۰۰؛ SDK انتخاب سانروف۱ یا پرده۲ را خودش انجام می‌دهد | H97C:6830–6836 | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_SUNSHADE_POSITION | درصد مستقیم | ۰..۱۰۰؛ SDK انتخاب سانروف۱ یا پرده۲ را خودش انجام می‌دهد | H97C:6830–6836 | تطبیق کد؛ نیازمند تست خودرو |
| SET_DOOR_LOCK | مقدار مستقیم | قفل/فعال۲، باز/غیرفعال۱؛ قفل کودک مشترک | FragmentDoorAndWindowBindingImpl:1730–1731؛ H97C:5852–5855 | تطبیق کد؛ نیازمند تست خودرو |
| OPEN_WINDOW | فرمان حرکت | باز۳، بسته۱، تهویه۶؛ انتخاب فیلد شیشه/گروه؛ ACK تضمین موقعیت فیزیکی نیست | H97C.setIVI_comfortSet:5797؛ DoorAndWindowFragment.updateWindowVentState | تطبیق کد؛ نیازمند تست خودرو |
| CLOSE_WINDOW | فرمان حرکت | باز۳، بسته۱، تهویه۶؛ انتخاب فیلد شیشه/گروه؛ ACK تضمین موقعیت فیزیکی نیست | H97C.setIVI_comfortSet:5797؛ DoorAndWindowFragment.updateWindowVentState | تطبیق کد؛ نیازمند تست خودرو |
| VENT_WINDOW | فرمان حرکت | باز۳، بسته۱، تهویه۶؛ انتخاب فیلد شیشه/گروه؛ ACK تضمین موقعیت فیزیکی نیست | H97C.setIVI_comfortSet:5797؛ DoorAndWindowFragment.updateWindowVentState | تطبیق کد؛ نیازمند تست خودرو |
| SET_CHILD_LOCK | مقدار مستقیم | قفل/فعال۲، باز/غیرفعال۱؛ قفل کودک مشترک | FragmentDoorAndWindowBindingImpl:1730–1731؛ H97C:5852–5855 | تطبیق کد؛ نیازمند تست خودرو |
| OPEN_TAILGATE | درخواست حرکت مستقیم | باز۲، بسته۱؛ محدودیت توقف فعلی حفظ شد؛ وضعیت پایان حرکت جداست | TrunkOperateView:137/152؛ H97C:5886 | تطبیق کد؛ نیازمند تست خودرو |
| CLOSE_TAILGATE | درخواست حرکت مستقیم | باز۲، بسته۱؛ محدودیت توقف فعلی حفظ شد؛ وضعیت پایان حرکت جداست | TrunkOperateView:137/152؛ H97C:5886 | تطبیق کد؛ نیازمند تست خودرو |
| OPEN_CHARGE_PORT | درخواست بازکردن | درخواست۱؛ محدودیت توقف؛ نه وضعیت بولی Toggle | H97C:5856/5864؛ OEM port menu | تطبیق کد؛ نیازمند تست خودرو |
| OPEN_FUEL_FLAP | درخواست بازکردن | درخواست۱؛ محدودیت توقف؛ نه وضعیت بولی Toggle | H97C:5856/5864؛ OEM port menu | تطبیق کد؛ نیازمند تست خودرو |
| OPEN_SUNROOF | درصد مستقیم | ۰..۱۰۰؛ SDK انتخاب سانروف۱ یا پرده۲ را خودش انجام می‌دهد | H97C:6830–6836 | تطبیق کد؛ نیازمند تست خودرو |
| CLOSE_SUNROOF | درصد مستقیم | ۰..۱۰۰؛ SDK انتخاب سانروف۱ یا پرده۲ را خودش انجام می‌دهد | H97C:6830–6836 | تطبیق کد؛ نیازمند تست خودرو |
| SET_SUNROOF_POSITION | درصد مستقیم | ۰..۱۰۰؛ SDK انتخاب سانروف۱ یا پرده۲ را خودش انجام می‌دهد | H97C:6830–6836 | تطبیق کد؛ نیازمند تست خودرو |
| SET_SUNSHADE_POSITION | درصد مستقیم | ۰..۱۰۰؛ SDK انتخاب سانروف۱ یا پرده۲ را خودش انجام می‌دهد | H97C:6830–6836 | تطبیق کد؛ نیازمند تست خودرو |
| SET_HEADLIGHT_MODE | کلید انتخاب حالت | بازخورد auto۰ low۱ DRL۲ off۳؛ فقط با تفاوت حالت پالس کلید متناظر؛ خاموش low/auto بهDRL | LightFragment:748–796؛ H97C.setIVI_Set | تطبیق کد؛ نیازمند تست خودرو |
| SET_AUTO_HIGH_BEAM | مقدار مستقیم | فعال۱، غیرفعال۲؛ مستقل از کلید حالت چراغ | FragmentLightBindingImpl:2423؛ H97C:6333 | تطبیق کد؛ نیازمند تست خودرو |
| SET_DAYTIME_RUNNING_LIGHTS | کلید انتخاب حالت | بازخورد auto۰ low۱ DRL۲ off۳؛ فقط با تفاوت حالت پالس کلید متناظر؛ خاموش low/auto بهDRL | LightFragment:748–796؛ H97C.setIVI_Set | تطبیق کد؛ نیازمند تست خودرو |
| SET_AMBIENT_LIGHT_MODE | مقدار مستقیم + تنظیم OEM | روشن۲ خاموش۱؛ موسیقی۲/۱؛ روشنایی۱..۱۰؛ حالت درایومود preference و رنگ متناظر | H97C:6507–6517؛ LightFragment | تطبیق کد؛ نیازمند تست خودرو |
| SET_AMBIENT_LIGHT_POWER | مقدار مستقیم + تنظیم OEM | روشن۲ خاموش۱؛ موسیقی۲/۱؛ روشنایی۱..۱۰؛ حالت درایومود preference و رنگ متناظر | H97C:6507–6517؛ LightFragment | تطبیق کد؛ نیازمند تست خودرو |
| SET_AMBIENT_LIGHT_BRIGHTNESS | مقدار مستقیم + تنظیم OEM | روشن۲ خاموش۱؛ موسیقی۲/۱؛ روشنایی۱..۱۰؛ حالت درایومود preference و رنگ متناظر | H97C:6507–6517؛ LightFragment | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_AMBIENT_LIGHT_BRIGHTNESS | سطح مستقیم | هر فرمان یک سطح کامل از۱۰سطح، نه یک درصد؛ محدودسازی۱..۱۰ | سطح فعلی AMBIENT؛ RahaVehiclePlan | تست نگاشت موفق؛ تست خودرو لازم |
| SET_REAR_FOG_LIGHT | کلید Toggle | getFogRear مقایسه شود؛ فقط در صورت تفاوت پالس۱ | LightFragment؛ H97C.setIVI_Set | تطبیق کد؛ نیازمند تست خودرو |
| SET_RAIN_SENSING_WIPERS | فاقد مسیر تأییدشده | از قبل صریحاً خطا می‌دهد؛ به حساسیت برف‌پاک‌کن نگاشت نشده | RahaVehiclePlan | پشتیبانی اجرایی ندارد |
| SET_SUSPENSION_HEIGHT | سرویس OEM | درایومود۱..۶؛ ارتفاع کم=Sport۳ زیاد=Outing۴؛ وسط از حالت‌های عادی؛ تغییر ارتفاع تغییر مود هم هست | DriveModeIntentService؛ China AirsuspensionCardLargeBindingImpl | مسیر کد تأیید؛ ارتفاع مستقل نیست |
| SET_DRIVE_MODE | سرویس OEM | درایومود۱..۶؛ ارتفاع کم=Sport۳ زیاد=Outing۴؛ وسط از حالت‌های عادی؛ تغییر ارتفاع تغییر مود هم هست | DriveModeIntentService؛ China AirsuspensionCardLargeBindingImpl | مسیر کد تأیید؛ ارتفاع مستقل نیست |
| SET_REGENERATION_LEVEL | enum مستقیم | ضعیف۲، متوسط۳، قوی۴؛ نسبی از HUM_ENERGY_PTREGEN_LEVL_FB | DrivePreferenceFragment:1478–1492؛ H97C:6222 | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_REGENERATION_LEVEL | enum مستقیم | ضعیف۲، متوسط۳، قوی۴؛ نسبی از HUM_ENERGY_PTREGEN_LEVL_FB | DrivePreferenceFragment:1478–1492؛ H97C:6222 | تطبیق کد؛ نیازمند تست خودرو |
| SET_HIGHWAY_SUSPENSION_ADAPTATION | مقدار مستقیم | فعال۲، غیرفعال۱ | FragmentDrivePreferenceBindingImpl:2012؛ H97C:6224 | تطبیق کد؛ نیازمند تست خودرو |
| SET_AUTO_HOLD | انتخاب حالت | automatic=۱؛ deepPedal=۲؛ فرمان خاموش/روشن حذف شد | منوی خودرو؛ AUTO_HOLD | تست نگاشت موفق؛ تست خودرو لازم |
| SET_HILL_DESCENT_CONTROL | مقدار مستقیم | فعال۱، غیرفعال۲؛ ECU ممکن است شرایط دیگری برای اجرای فیزیکی داشته باشد | FragmentDrivePreferenceBindingImpl:2010/8621/8627؛ H97C:6066–6071 | تطبیق کد؛ نیازمند تست خودرو |
| SET_STABILITY_CONTROL | وضعیت مستقیم | روشن۲؛ خاموش۱ مستقل از زبان/نسخه صادراتی؛ انتظار بازخورد ESC_SWITCH حداکثر۳ثانیه | H97C.onESC_state1 / setIVI_ASSISTSet | اصلاح نگاشت و تأیید؛ تست خودرو لازم |
| SET_PARKING_BRAKE | درخواست و انتظار وضعیت | درخواست درگیری۱/آزادسازی۲؛ بازخورد EPB_PARK_STATUS درگیر۱/آزاد۰؛ ثبت سرعت و پدال؛ بدون تکرار درخواست | QuickSettingsFragment.clickStopCar؛ H97C.onESC_EPB | رفع موفقیت کاذب؛ علت عدم اعمال روی خودرو هنوز نیازمند لاگ |
| SET_WIPER_SENSITIVITY | سطح مستقیم | حساسیت۱..۴؛ روشن/خاموش برف‌پاک‌کن نیست | H97C:5830–5832 | تطبیق کد؛ نیازمند تست خودرو |
| ADJUST_WIPER_SENSITIVITY | سطح مستقیم | حساسیت۱..۴؛ روشن/خاموش برف‌پاک‌کن نیست | H97C:5830–5832 | تطبیق کد؛ نیازمند تست خودرو |
| SAVE_SEAT_POSITION | رویداد OEM | saveSeat01/sendSeat01؛ فقط پروفایلDriver؛ سپس showSeatMemory؛ ارسال رویداد تضمین پایان حرکت نیست | SeatMemoryManager.executeSeatByOtherProcess:270–340 | تطبیق کد؛ نیازمند تست خودرو |
| RECALL_SEAT_POSITION | رویداد OEM | saveSeat01/sendSeat01؛ فقط پروفایلDriver؛ سپس showSeatMemory؛ ارسال رویداد تضمین پایان حرکت نیست | SeatMemoryManager.executeSeatByOtherProcess:270–340 | تطبیق کد؛ نیازمند تست خودرو |

## اعتبارسنجی
۱۱۴ تست JUnit موفق؛ کامپایل :app:compileDebugKotlin موفق. تست‌ها شامل هر ترکیب وضعیت اولیه/هدف برای کلیدهای تهویه، درخواست تکراری، گردش سه‌حالته، بازخورد با تأخیر و بازخورد ثابت، مقادیر ترمز پارک و فرمان ترکیبی سرمایش هستند. APK ساخته نشد.


به‌روزرسانی پس از تست خودرو: ۱۱۷ تست موفق؛ کامپایل Kotlin موفق، APK ساخته نشد. صدای رها با STREAM_SYSTEM و بهره۱ پخش می‌شود؛ سطح۱۵ تحمیل نمی‌شود. نمایش صفحه تنظیمات پس از تغییر مود حذف شد. DriveModeViewManager.show نمایش صرف نیست و با تأخیر مود را ارسال می‌کند؛ پاپ‌آپ فیزیکی هنوز متصل نشده است. شیشه تا نیمه همچنان منتظر لاگ RahaWindow است.
