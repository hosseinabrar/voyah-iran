# نصب دارای مجوز سیستمی برای ساعت

**آرشیوی: برای نسخهٔ 0.2.0-bridge اجرا نشود.** مسیر فعلی [نصاب واسط مستقل](BRIDGE-INSTALL-FA.md) است و APK در priv-app قرار نمی‌گیرد. مطالب پایین مربوط به طرح قبلی هستند.

این تغییر برای مسیر جدید ساعت است؛ مسیرهای فعلی رانندگی، بازیابی انرژی و کیبورد هنوز به su متکی هستند. قراردادن برنامه در priv-app به معنی UID سیستم، root یا domain سیستمی SELinux نیست.

## تغییر انجام‌شده

تنظیمات OEM در `com.qinggan.app.setting.utils.TimeUtils.setTimezone(String, boolean, float)` سرویس AlarmManager را صدا می‌زند. وویا ایران اکنون همان سرویس را با مجوز SET_TIME_ZONE استفاده می‌کند. برای Asia/Tehran از API عمومی و برای GMT+03:30 از transaction شمارهٔ ۳ IAlarmManager در framework استخراج‌شده استفاده می‌شود؛ سرویس همچنان مجوز UID برنامه را بررسی می‌کند. دلیل مسیر دوم، رد شدن شناسهٔ ثابت GMT توسط wrapper عمومی اندروید است.

تنها auto_time_zone تغییر می‌کند؛ auto_time و UTC دست‌کاری نمی‌شوند. مسیرهای TBox و ساعت کلاستر در متد OEM در این تغییر کپی نشده‌اند. تأیید عملی تازه‌شدن ساعت نمایشگر و دیگر اجزا روی خودرو باقی است.

## پیش‌نیاز

- APK جدید همین پروژه را با **همان کلید امضای نسخهٔ نصب‌شده** بسازید. APK در این مرحله توسط دستیار ساخته نشده است.
- دستگاه از طریق ADB قابل دسترسی باشد و `adb shell su 0 id` موفق باشد.
- پارتیشن system باید قابل نوشتن باشد. اگر remount شکست خورد، ادامه ندهید؛ این راهنما verity یا SELinux را غیرفعال نمی‌کند.
- APK و فایل XML مجوز باید هر دو در همان پارتیشن system نصب شوند. کپی APK تنها در `/system/app` کافی نیست.

## دستورات PowerShell

از پوشهٔ platform-tools اجرا کنید. دو مسیر ویندوز را به فایل APK تازه‌ساخته‌شده و XML همین پوشه تغییر دهید:

```powershell
./adb push "C:\PATH\app-release.apk" /data/local/tmp/voyahiran-new.apk
./adb push "C:\PATH\privapp-permissions-com.abrar.voyahiran.xml" /data/local/tmp/voyahiran-permissions.xml
./adb shell su 0 cat /proc/mounts
```

در خروجی mount، اگر `/system` نقطهٔ اتصال جداگانه دارد، دستور اول؛ اگر system بخشی از ریشه است، دستور دوم را اجرا کنید (فقط مورد متناسب با خروجی):

```powershell
./adb shell su 0 mount -o remount,rw /system
# یا برای system-as-root:
./adb shell su 0 mount -o remount,rw /
```

**در صورت خطای remount متوقف شوید.** پس از موفقیت، ابتدا بررسی کنید مقصد قبلاً متعلق به نصب دیگری نباشد:

```powershell
./adb shell su 0 ls -ld /system/priv-app/VoyahIran /system/etc/permissions/privapp-permissions-com.abrar.voyahiran.xml
```

در نصب اول نبودن این دو مسیر طبیعی است. اگر وجود دارند، قبل از جایگزینی از آن‌ها بکاپ بگیرید. سپس هر دستور را جداگانه اجرا کنید و در صورت خطا ادامه ندهید:

```powershell
./adb shell su 0 mkdir -p /system/priv-app/VoyahIran
./adb shell su 0 cp /data/local/tmp/voyahiran-new.apk /system/priv-app/VoyahIran/VoyahIran.apk
./adb shell su 0 cp /data/local/tmp/voyahiran-permissions.xml /system/etc/permissions/privapp-permissions-com.abrar.voyahiran.xml
./adb shell su 0 chown root:root /system/priv-app/VoyahIran /system/priv-app/VoyahIran/VoyahIran.apk /system/etc/permissions/privapp-permissions-com.abrar.voyahiran.xml
./adb shell su 0 chmod 755 /system/priv-app/VoyahIran
./adb shell su 0 chmod 644 /system/priv-app/VoyahIran/VoyahIran.apk /system/etc/permissions/privapp-permissions-com.abrar.voyahiran.xml
./adb shell su 0 restorecon -RF /system/priv-app/VoyahIran
./adb shell su 0 restorecon /system/etc/permissions/privapp-permissions-com.abrar.voyahiran.xml
./adb shell su 0 sync
```

اندازه/وجود APK و متن XML مقصد را پیش از ریبوت بررسی کنید؛ هر دو مجوز باید در XML باشند:

```powershell
./adb shell su 0 ls -lZ /system/priv-app/VoyahIran/VoyahIran.apk
./adb shell su 0 cat /system/etc/permissions/privapp-permissions-com.abrar.voyahiran.xml
./adb reboot
```

ریبوت برای شناسایی **نصب سیستمی و مجوزها** لازم است؛ در طراحی جدید، تغییرات بعدی ساعت از طریق سرویس سیستم انجام می‌شوند.

## تأیید پس از بوت

```powershell
./adb shell dumpsys package com.abrar.voyahiran | Select-String 'codePath|SYSTEM|PRIVILEGED|SET_TIME_ZONE|WRITE_SECURE_SETTINGS'
```

انتظار: وضعیت privileged و هر دو مجوز با `granted=true`. وجود نسخهٔ به‌روز‌شده در `/data/app` به‌تنهایی نشانهٔ شکست نیست؛ flags و مجوزها ملاک‌اند. اگر امضای APK با نسخهٔ قبلی متفاوت بود یا مجوزها اعطا نشدند، APK دیگری را تصادفی نصب یا داده‌های اپ را حذف نکنید؛ خروجی PackageManager را بررسی کنید.

هنگام تیک ساعت، لاگ جدید باید این مسیر را نشان بدهد:

```text
VI/TimeZone: backend=system-service SET_TIME_ZONE=true WRITE_SECURE_SETTINGS=true
```

اگر false باشد، برنامه پیش از تغییر تنظیمات خطای مجوز می‌دهد. `pm grant SET_TIME_ZONE` روی اپ عادی جایگزین نصب privileged نیست؛ این مجوز در framework خودرو signature|privileged است. هیچ تغییری در sharedUserId، کلید امضای OEM، فایل su یا سیاست SELinux لازم نیست.
