# بررسی services.jar نسخهٔ دوم — ۲۰۲۶/۰۹/۱۰

## نتیجه

جایگزینی کامل فایل پچ‌شدهٔ نسخهٔ اول روی نسخهٔ دوم تأیید نمی‌شود. فایل‌ها علاوه بر بخش کیبورد، تفاوت اجرایی در سرویس‌های دیگر دارند؛ جایگزینی کامل، رفتار آن سرویس‌ها را هم تغییر می‌دهد. این بررسی وقوع خرابی مشخصی را اثبات نمی‌کند، اما مبنایی برای تضمین سازگاری کامل وجود ندارد.

کد اصلی مدیریت کیبورد در دو فایل یکسان است. راه مناسب، ساخت پچ جداگانه از خود services.jar نسخهٔ دوم و انتخاب فایل مناسب بر اساس SHA-256 در نصاب است. پچ نسخهٔ دوم در این بررسی ساخته نشده و نصاب تغییر نکرده است.

## فایل‌ها

| فایل | اندازه | SHA-256 |
|---|---:|---|
| نسخهٔ اول استخراج‌شده از system/framework | 14905372 | `0faed7ebf1094c62a4f92b4bc533bbb3c08480a2cd847567cb1cff91b2d1d045` |
| نسخهٔ دوم از Downloads/Telegram Desktop/services.jar | 14903460 | `85f0387dbeb2b30d301eb0b995e0f2dd278aa84f502cd490aa2dcfc9703697ce` |
| پچ موجود، بر پایهٔ نسخهٔ اول | 14909381 | `ede647f69dfdfc4ad86af3a9da6b056a8bff47e6fe9e41d33ab3837b2d26f0ce` |

## روش بررسی

هر دو نسخه با APKTool 3.0.3 در سطح Smali مقایسه شدند. در درخت تحلیل نسخهٔ اول، اصلاح محدود قبلی کیبورد برای مقایسه با نسخهٔ اصلی از متن حذف شد. هر نسخه ۷۹۴۲ کلاس دارد؛ کلاسی اضافه یا حذف نشده است. ۶۸ فایل Smali اختلاف متنی دارند. پس از کنارگذاشتن اطلاعات دیباگ، شماره‌خط‌ها و تفاوت هم‌معنای const-string/const-string-jumbo، تفاوت اجرایی/ساختاری در ۶ کلاس باقی ماند.

هر دو DEX متفاوت‌اند. تفاوت فایل ProcessList_compat_config.xml مربوط به شماره‌خط منبع در metadata است، نه تغییر مقادیر سیاست‌های سازگاری.

## تفاوت‌های واقعی

| کلاس | محل و نوع تفاوت |
|---|---|
| `com.android.server.am.ProcessList` | متد `handleProcessStartedLocked`: نسخهٔ اول، `com.tencent.wecarflow` را نیز وارد شاخهٔ فراخوانی `Process.setProcessMemory(pid, uid)` می‌کند؛ نسخهٔ دوم این مورد را ندارد. |
| `com.android.server.pm.PackageManagerService` | در `scanDirLI` شروط اسکن `baidu_apa` و `baidu_apa_overseas` و بررسی پیکربندی خودرو متفاوت است؛ در `systemReady` نیز فراخوانی اعطای مجوز اختصاصی BeiZhou در نسخهٔ اول وجود دارد و در دوم نیست. |
| `com.android.server.pm.permission.DefaultPermissionGrantPolicy` | نسخهٔ اول متدهای `grantDefaultPermissionsToBeiZhou` و `setAppOpPermissionForPackage` و مجموعهٔ مجوزهای مرتبط را دارد؛ نسخهٔ دوم فاقد آن‌هاست. مسیر نسخهٔ اول به بسته‌های `com.beizhou.bzdesk` و `ru.vk.store` تحت شرط محصول مربوط می‌شود. |
| `com.android.server.pm.permission.PermissionManagerService$PermissionManagerServiceInternalImpl` | واسط اجرای `grantDefaultPermissionsToBeiZhou` فقط در نسخهٔ اول وجود دارد. |
| `com.android.server.pm.permission.PermissionManagerServiceInternal` | امضای abstract همان متد فقط در نسخهٔ اول وجود دارد؛ بنابراین حتی سطح رابط داخلی هم کاملاً یکسان نیست. |
| `com.android.server.wm.RootWindowContainer` | `startHomeOnTaskDisplayArea` و مسیر انتخاب Home/Login/FallbackHome متفاوت است؛ نسخهٔ اول یک متد کمکی `findLoginActivity` هم دارد که در نسخهٔ دوم نیست. |

این تفاوت‌ها نشان می‌دهند جایگزینی JAR صرفاً «اصلاح کیبورد» نخواهد بود. از خود فایل‌ها نمی‌توان مشخص کرد همهٔ این تفاوت‌ها مربوط به فریم‌ور کارخانه‌اند یا تغییرات دیگری که قبلاً روی خودرو اعمال شده‌اند.

## بخش کیبورد

`InputMethodManagerService.updateInputMethodsFromSettingsLocked(boolean)` در دو نسخه، پیش از پچ، دقیقاً یکسان است. SHA-256 متن همان متد پس از نرمال‌سازی پایان‌خط:

`01ffb2d499739858b47d1bc789e0735c1e71cc996f11a5118bf545fcbda5474a`

کلاس اصلی InputMethodManagerService پس از حذف اصلاح خودمان، کلاس InputMethodManagerServiceExt و InputMethodUtils در فهرست اختلاف‌ها نیستند. بنابراین محل و وابستگی‌های مستقیم پچ کیبورد در نسخهٔ دوم هم موجود و یکسان‌اند. برای نسخهٔ دوم باید فقط همان اصلاح کیبورد را اعمال کرد و سایر کلاس‌ها و classes2.dex خود نسخهٔ دوم را نگه داشت.

## اثر بر نصاب فعلی

نصاب C# پیش از نصب APK، هش services.jar را بررسی می‌کند. هش نسخهٔ دوم نه هش اصل نسخهٔ اول است و نه هش پچ موجود؛ بنابراین نصاب فعلی آن را رد می‌کند و وارد نصب نمی‌شود. برای پشتیبانی از نسخهٔ دوم، صرفاً افزودن هش آن به فهرست مجاز کافی نیست؛ فایل پچ و بکاپ/بازگردانی باید متناسب با همان نسخه انتخاب شوند.

این بررسی ایستا است؛ هیچ APK، JAR پچ‌شدهٔ جدید یا EXE ساخته نشد و فرمانی روی خودرو اجرا نشد. تأیید نهایی نسخهٔ دوم همچنان به تست بوت، کیبورد هر دو نمایشگر و عملکردهای خودرو پس از پچ محدود مخصوص خودش نیاز دارد.
