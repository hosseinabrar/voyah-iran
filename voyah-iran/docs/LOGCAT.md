# نصب، دسترسی و جمع‌آوری لاگ

## دسترسی پس از نصب

نسخهٔ 0.2.0-bridge به [نصب واسط مستقل](../deployment/BRIDGE-INSTALL-FA.md) نیاز دارد. APK عادی است؛ درخواست‌های دارای دسترسی به سرویس واسط فرستاده می‌شوند و خود اپ su اجرا نمی‌کند. مجوزهای عادی با نصب اعطا می‌شوند و pm grant اضافی لازم نیست. برچسب‌های VI/Bridge و VI/BridgeServer ارتباط، آمادگی سرویس و نتیجهٔ فرمان را ثبت می‌کنند.

نصاب از root در شِل ADB استفاده می‌کند؛ اپ اجازهٔ مدیر روت نمی‌خواهد. برای بررسی محیط:

```powershell
adb shell su 0 id
adb shell am start -n com.abrar.voyahiran/.ui.MainActivity
```

برنامه باید بعد از نصب حداقل یک بار باز شود تا از حالت stopped خارج و دریافت بوت ممکن شود. برای تست ماندن سرویس‌های حافظه/ستاره در پس‌زمینه، معافیت از Doze اختیاری است و جای اجازهٔ root را نمی‌گیرد:

```powershell
adb shell dumpsys deviceidle whitelist +com.abrar.voyahiran
```

برگشت معافیت: `adb shell dumpsys deviceidle whitelist -com.abrar.voyahiran`.

## لاگ زنده در PowerShell

قبل از بازکردن برنامه و تکرار مشکل اجرا کنید:

```powershell
adb logcat -v threadtime | Select-String -Pattern 'VI/|AndroidRuntime' | ForEach-Object { $_.Line } | Tee-Object -FilePath .\voyah-iran-log.txt
```

پس از تکرار مشکل با Ctrl+C متوقف کنید. فایل در پوشهٔ فعلی PowerShell ذخیره می‌شود. فیلتر بر اساس tag است و بعد از مرگ/شروع دوبارهٔ فرایند نیز ادامه می‌یابد. Android Studio Logcat: جست‌وجوی `tag:VI/`؛ برای کرش‌ها AndroidRuntime را هم ببینید.

لاگ موجود در بافر، بدون انتظار:

```powershell
adb logcat -d -v threadtime | Select-String -Pattern 'VI/|AndroidRuntime' | ForEach-Object { $_.Line } | Set-Content -Encoding utf8 .\voyah-iran-log.txt
```

به همراه فایل بگویید کدام گزینه را چه ساعتی تغییر دادید، انتظار چه بود و چه اتفاقی افتاد. برای خطای بدون جزئیات در لاگ برنامه ممکن است لاگ OEM همان بازه نیز لازم شود؛ این فایل فقط برنامه و کرش‌ها را جدا می‌کند.

## محتوای لاگ

در firmware آزمایش‌شده، قالب معتبر روت `su 0 <command>` است؛ `su -c` پشتیبانی نمی‌شود. برنامه مسیرهای `/system/xbin/su` و `/system/bin/su` را بررسی می‌کند. موفقیت `adb shell su 0 id` فقط دسترسی shell را ثابت می‌کند و تضمین دسترسی برنامه نیست. در شکست شروع فرایند، مسیر اجرایی و پیام خطای سیستم (مانند errno مربوط به نبود فایل یا منع دسترسی) نیز ثبت می‌شود؛ این مورد استثنای محدودِ قاعدهٔ عدم ثبت متن کامل خطا در پایین است.

| Tag | محتوا |
| --- | --- |
| VI/App و VI/UI | نسخه، API، UID، نمایشگر و تب انتخاب‌شده |
| VI/Shell | شناسهٔ درخواست، نوع عملیات، timeout، exit code و نشانه‌های خطای مجوز/فرمان |
| VI/TimeZone | مقصد درخواست، نتیجه و نوع خطای ساعت |
| VI/Keyboard | انتخاب درخواستی، نصب‌بودن Gboard و شناسهٔ IME واقعی |
| VI/RearCamera، VI/CameraDVR، VI/CameraROA | تشخیص قابلیت، فرمان تک‌مرحله‌ای و وضعیت واقعی دوربین |
| VI/Steering و VI/StarEvent | وضعیت نگاشت، رویدادهای ستاره، مصرف رویداد و تزریق Back/عملکرد قبلی |
| VI/DriveMemory و VI/Recovery | مقدار جاری/ذخیره، مرحله، انتظار تأیید، ACC، READY و سرعت |
| VI/CAN | اتصال/قطع و شکست transaction |
| VI/Apollo و VI/ApolloSubscription | خطای خواندن و نتیجهٔ گزارش اشتراک؛ بدون payload |
| VI/Apollo/… | وضعیت خوانده‌شدهٔ هر قابلیت |
| VI/Boot، VI/DriveService، VI/StarService | دریافت بوت، شروع/توقف سرویس و درخواست بازکردن Activity |

لاگ‌های عملیاتی در build تست و release فعال هستند. تصویر، صوت، متن تایپ‌شده، VIN، حساب و JSON خام ثبت نمی‌شوند. خروجی کامل shell و متن کامل exception هم برای جلوگیری از نشت داده ثبت نمی‌شود؛ نوع خطا و محل وقوع ثبت می‌شود. این لاگ‌ها در logcat هستند و فایل دائمی روی خودرو ایجاد نمی‌شود. ثبت رویداد ستاره فقط callback کلید ثبت‌شدهٔ فرمان را پوشش می‌دهد، نه متن ورودی کیبورد.
