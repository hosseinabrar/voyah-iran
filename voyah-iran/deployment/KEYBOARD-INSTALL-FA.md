# نصب اصلاح کیبورد

`services.jar` فایل مدیریت بخشی از سرویس‌های اندروید خودرو است. نسخهٔ اصلی، انتخاب Gboard را بلافاصله به کیبورد کارخانه برمی‌گرداند. نسخهٔ آماده‌شده انتخاب برنامه را برای سرویس نمایشگر وسط می‌پذیرد، مشروط به اینکه آن کیبورد نصب و فعال باشد. کد سرویس کیبورد شاگرد تغییر نکرده است.

ابتدا از پروژهٔ به‌روز، APK بگیر و فایل خروجی را در مسیر قبلی `app/release/app-release.apk` قرار بده. نسخهٔ جدید برنامه `0.2.2-keyboard` است. در این مرحله APK توسط دستیار ساخته نشده است.

خودرو متوقف باشد. اتصال ADB را برقرار کن و این سه دستور را به‌ترتیب در PowerShell اجرا کن. هر دستور باید موفق تمام شود؛ اگر خطا دیدی سراغ دستور بعدی و ریبوت نرو.

۱. برنامه و سرویس دسترسی آن را با نصاب قبلی به‌روز کن:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-VoyahIran.ps1" -Apk "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\app\release\app-release.apk" -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

۲. اصلاح فایل سیستم را نصب کن:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-KeyboardFramework.ps1" -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

۳. بعد از موفقیت هر دو نصب، یک بار ریبوت کن:

```powershell
& "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe" reboot
```

پس از بالا آمدن سیستم، تیک Gboard را آزمایش کن. یک محل تایپ در نمایشگر وسط و سپس شاگرد باز کن. برداشتن تیک هم باید کیبورد قبلی نمایشگر وسط را برگرداند. ماندگاری انتخاب پس از یک ریبوت دیگر باید روی خودرو بررسی شود.

نصب APK به‌تنهایی کافی نیست؛ سرویس دسترسی هم فرمان‌های جدید دارد. فایل سیستم روی لپ‌تاپ با موفقیت ساخته و بازخوانی شده و تست‌های برنامه و کنترل نصب گذشته‌اند، اما بوت و عملکرد روی خودرو هنوز تست نشده‌اند. این فایل در راه‌اندازی اندروید نقش دارد؛ اگر ADB بعد از خرابی بوت در دسترس نباشد، اسکریپت بازگردانی از لپ‌تاپ قابل اجرا نیست و بازیابی سیستم لازم می‌شود.

## بکاپ و بازگردانی

نصاب فقط فایل سیستمی با هش دقیق نسخهٔ بررسی‌شده را قبول می‌کند. قبل از تغییر، اصل JAR روی لپ‌تاپ در زیرپوشهٔ `framework/backups` نصاب ذخیره می‌شود. اصل JAR و فایل‌های پیش‌کامپایل روی خودرو نیز در `/data/local/tmp/voyahiran-framework-backup-v1` باقی می‌مانند. این پوشه را پاک نکن. نصاب هیچ تغییری در SELinux یا verity نمی‌دهد و خودش ریبوت نمی‌کند.

برای بازگرداندن نسخهٔ اصلی، با اتصال ADB این دستور را اجرا کن و فقط پس از موفقیت، ریبوت کن. این دستور منتظر بالا آمدن کامل رابط اندروید نمی‌ماند، ولی به ADB و `su 0` نیاز دارد:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-KeyboardFramework.ps1" -Action Restore -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

اگر تست کیبورد ناموفق بود، برای ثبت وضعیت واقعی هر دو سرویس:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Nahid\Documents\Codex\2026-09-08\x20\outputs\voyah-iran\deployment\Install-KeyboardFramework.ps1" -Action Status -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```
