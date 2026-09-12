# بررسی دسترسی ویژه — ۹ سپتامبر ۲۰۲۶

## یافته‌های قطعی

- لاگ نصب جدید وویا ایران اجرای `/system/xbin/su` را با `error=13, Permission denied` ثبت می‌کند؛ تغییر مسیر در APK نصب‌شده وجود دارد. شکست پیش از اجرای فرمان رخ می‌دهد.
- فایل استخراج‌شدهٔ `system/etc/selinux/plat_file_contexts:415` مسیر su را با نوع `su_exec` برچسب می‌زند.
- در `system/etc/selinux/plat_sepolicy.cil:7933` اجرای su برای domainهای خارج از `su_exec_violators` ممنوع تعریف شده است.
- در `system/vendor/etc/selinux/vendor_sepolicy.cil:336` اعضای آن استثنا `shell_30_0` و `system_app_30_0` هستند. خطوط 8450 و 9309 مجوز اجرا را مشخص می‌کنند؛ خط 7934 برای platform_app فقط getattr دارد.
- این سیاست استخراج‌شده با موفقیت ADB و شکست اپ عادی سازگار است. وضعیت زندهٔ SELinux و مجوز فایل هنوز خوانده نشده؛ علت انحصاری errno را نمی‌توان قطعی دانست.

## Voyah Tweaks 2.8.2

منبع: `C:/Users/Nahid/Desktop/VT_2.8.2.apk`؛ خروجی موجود در `C:/Users/Nahid/Documents/Codex/2026-09-06/j/work/VT_2.8.2-apktool`.

- Manifest برنامه `ru.kachalin.voyahtweaks`، application wrapper برابر `v7ff5548e.l7ff5548e` و MainApplication واقعی در metadata است.
- wrapper رشتهٔ virbox و متدهای native دارد؛ خروجی smali فقط چهار فایل wrapper است. بدنهٔ منطق اصلی در این خروجی قابل مشاهده نیست.
- Manifest مجوز WRITE_SECURE_SETTINGS را درخواست می‌کند. همچنین WRITE_CANBUS را با protectionLevel normal تعریف و درخواست می‌کند. درخواست/تعریف با اثبات مجوز اعطاشده یا اثبات مسیر فراخوانی برابر نیست.
- سرویس OEM در `CanBusService$7.setVehicleState` برای فراخوانندهٔ معمولی WRITE_CANBUS را بررسی می‌کند. مسیر Binder دارای مجوز، گزینه‌ای مستقل از اجرای su است؛ استفادهٔ واقعی تویکس از آن در بدنهٔ محافظت‌شده تأیید نشده است.
- جست‌وجوی متن خوانا در smali، فایل‌های model1 تا model7، sentry.dex و کتابخانه‌های so مدرکی برای `su 0`، `su -c`، `/system/xbin/su`، AdbConnection، Shizuku یا RootShell نشان نداد. نبود رشته اثبات نبود قابلیت نیست.
- رشتهٔ app_process64 در کتابخانهٔ محافظ دیده شد؛ این به‌تنهایی مدرک وجود سرویس روت یا پل ADB نیست.

## نتیجه

هنوز روش دقیق اجرای فرمان‌های ویژهٔ تویکس از APK محافظت‌شده معلوم نشده است. فرض «همهٔ قابلیت‌ها حتماً از su مستقیم استفاده می‌کنند» تأیید ندارد. برای وویا ایران، تغییر قالب و مسیر su محدودیت دسترسی اپ را رفع نمی‌کند. صرف افزودن permission به Manifest نیز معادل گرفتن root نیست. هیچ سیاست امنیتی، APK کاربر یا کد برنامه در این مرحله تغییر داده نشد.
