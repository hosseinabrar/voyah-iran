# نصب واسط سیستمی وویا ایران — نسخهٔ 0.2.0-bridge

APK همچنان اپ عادی است. نصاب ویندوزی PowerShell با ADB یک سرویس مستقل init آماده می‌کند. سرویس با root و domain شِل اجرا می‌شود و فقط فرمان‌های مجاز برنامه را می‌پذیرد. فایل‌های load.bin و init.logcat.sh تویکس تغییر نمی‌کنند. این مسیر جایگزین راهنمای قبلی نصب priv-app است.

## نصب

1. پروژهٔ به‌روز را با همان کلید امضای قبلی بیلد کنید. در این تحویل APK ساخته نشده است.
2. هر سه فایل `Install-VoyahIran.ps1`، `install-bridge.sh` و `voyahiran-bridge.rc` را کنار هم نگه دارید (همین پوشه).
3. خودرو روشن و متوقف باشد؛ فقط یک دستگاه مجاز به ADB وصل باشد. در PowerShell اجرا کنید و مسیر APK تازه و adb.exe را جایگزین کنید:

```powershell
& "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-VoyahIran.ps1" -Apk "C:\PATH\app-release.apk" -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

اگر اجرای فایل PowerShell توسط سیاست اجرای اسکریپت مسدود شد، می‌توان همین اسکریپت بررسی‌شده را در یک فرایند جدا اجرا کرد؛ سیاست دائمی ویندوز تغییر نمی‌کند:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-VoyahIran.ps1" -Apk "C:\PATH\app-release.apk" -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

نصاب وجود کلاس واسط در APK را بررسی می‌کند، APK را با حفظ داده نصب می‌کند، و SHA256 نسخهٔ نصب‌شده را تطبیق می‌دهد. ADB باید `su 0` داشته باشد؛ برنامه به su دسترسی نمی‌خواهد. اگر remount ممکن نباشد، نصب سرویس متوقف می‌شود و باید همان خروجی بررسی شود؛ نصاب verity یا SELinux را خاموش نمی‌کند.

پس از پیام `VOYAH_BRIDGE_INSTALLED` یک بار ریبوت کنید:

```powershell
.\adb reboot
```

نصاب خودکار ریبوت نمی‌کند. پس از بوت:

```powershell
& "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-VoyahIran.ps1" -Action Status -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

انتظار: `running` و لاگ `VI/BridgeServer: ready protocol=1 uid=0`. با بازکردن وویا ایران باید نسخهٔ `0.2.0-bridge` در لاگ برنامه دیده شود. موفقیت تست درخواست ساعت باید `backend=authenticated-bridge ready=true` و تأیید وضعیت واقعی را نشان بدهد. فرمان pm grant یا نصب priv-app لازم نیست.

## مسیرهای نصب و احراز هویت

- سرویس بوت: `/system/etc/init/voyahiran-bridge.rc`
- APK واسط و رمز سمت سرویس: `/data/local/tmp/voyahiran-bridge/` با مالک root و حالت 0700؛ فایل‌ها 0600 هستند.
- رمز تصادفی ۲۵۶بیتی در فضای خصوصی خود اپ ذخیره می‌شود. receiver تأمین رمز، مجوز سیستمی INSTALL_PACKAGES را برای فرستنده الزامی می‌کند؛ اپ‌های عادی نمی‌توانند آن را فراخوانی کنند.
- ارتباط فقط با `127.0.0.1:38743` است. رمز در لاگ نوشته نمی‌شود. هیچ رابط اجرای shell عمومی، تغییر اشتراک آپولو یا فرمان CAN دلخواه وجود ندارد.
- درخواست‌های هم‌زمان busy دریافت می‌کنند و صف اجرای دیرهنگام ایجاد نمی‌شود. قطع ارتباط پس از ارسال به معنی نتیجهٔ نامشخص است؛ فرمان خودکار تکرار نمی‌شود.

## قابلیت‌های متصل

ساعت از سرویس AlarmManager، کیبورد از ime، مود از DriveModeIntentService کارخانه، بازیابی انرژی و دوربین از فرمان محدود CAN و بازکردن صفحه در بوت از ActivityManager استفاده می‌کنند. نگاشت کوتاه/بلند دکمهٔ ستاره و گزارش فقط‌خواندنی آپولو تغییر نکرده‌اند. پیش از درخواست‌های بوت، اتصال واسط برای مدت محدود با handshake فقط‌خواندنی بررسی می‌شود.

## به‌روزرسانی و خطا

وقتی کد واسط یا قرارداد آن تغییر می‌کند، نصاب را با APK جدید دوباره اجرا کنید؛ نصب دستی APK به‌تنهایی فایل مستقل واسط را به‌روز نمی‌کند. حذف اپ یا پاک‌کردن داده‌هایش، رمز اتصال را از بین می‌برد و نیازمند اجرای مجدد نصاب است.

نصاب پیش از تغییر فایل‌های خودش، بکاپی با نام `/data/local/tmp/voyahiran-backup-<timestamp>` می‌سازد و مسیر را چاپ می‌کند. در صورت خطای نصب، ادامه ندهید یا ریبوت نکنید؛ خروجی نصاب و مسیر بکاپ را نگه دارید. برنامه یا اطلاعاتش خودکار حذف نمی‌شوند.

برای غیرفعال‌کردن فوری واسط، بدون حذف هیچ فایل:

```powershell
.\adb shell su 0 setprop ctl.stop voyahiran_bridge
```

این توقف تا بوت بعدی است. برای حذف ماندگار، پس از remount موفق، فقط فایل اختصاصی `/system/etc/init/voyahiran-bridge.rc` را حذف و ریبوت کنید؛ فایل‌های OEM و تویکس را تغییر ندهید. پوشهٔ اختصاصی واسط و بکاپ‌ها را پس از اطمینان از حذف می‌توان جداگانه پاک کرد.

## اعتبارسنجی

تست‌های میزبان برای whitelist فرمان‌ها، احراز هویت، محدودیت اندازهٔ درخواست و رفتار ساعت اجرا شده‌اند. اجرای app_process، سیاست SELinux فعلی، remount، دریافت نتیجهٔ provisioning و عملکرد واقعی امکانات باید روی خودرو تست شوند؛ وضعیت running به‌تنهایی تأیید کارکرد تمام امکانات نیست. مقادیر ACC/READY گزارش‌شدهٔ قبلی همچنان باید در حالت روشن واقعی خودرو بررسی شوند.
