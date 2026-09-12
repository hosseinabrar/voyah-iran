# علت بازگشت کیبورد — ۲۰۲۶/۰۹/۱۰

لاگ کامل کاربر: `C:/Users/Nahid/Desktop/New Text Document (2).txt`.

در 12:00:15.224 سرویس سیستم Gboard را با موفقیت انتخاب و در default_input_method ثبت می‌کند. در 12:00:15.240 همان سرویس، پس از پیام `inputmethod now get lanuage is english or china`، کیبورد کارخانه را انتخاب می‌کند. تلاش دوم هم همین رفتار را دارد. بنابراین خطای تأیید اپ درست است؛ فرمان اجرا می‌شود ولی انتخاب پس گرفته می‌شود.

منبع تطبیق: `C:/Users/Nahid/Desktop/voyah/system/framework/services.jar`.

در `com.android.server.inputmethod.InputMethodManagerService.updateInputMethodsFromSettingsLocked(boolean)`، برای Locale.US، ENGLISH، UK یا CHINA مقدار id به‌صورت ثابت برابر `com.qinggan.app.qgime/.QingganIME` است؛ برای زبان‌های دیگر `com.android.inputmethod.latin/.LatinIME` می‌شود. سپس setInputMethodLocked با همان مقدار فراخوانی می‌شود. مسیر معمول خواندن انتخاب کاربر فقط در شاخهٔ خالی‌بودن id قرار دارد، در حالی که این مقادیر ثابت خالی نیستند.

کلاس جداگانهٔ `InputMethodManagerServiceExt` سرویس `input_method_ext` را منتشر می‌کند و رفتار متناظر برای `com.qinggan.app.qgime.second/com.qinggan.app.qgime.QingganIME` دارد. هر دو observer مربوط به default_input_method دارند؛ بنابراین اصلاح سادهٔ یک مقدار Secure یا اعمال پیاپی فرمان راه‌حل پایدار نیست. تعیین اثر دقیق روی نمایشگرها و اشتراک تنظیمات باید در هر اصلاح سیستم لحاظ شود.

نتیجه: مشکل تأخیر یا دسترسی نیست. هیچ property قابل تنظیمی در این شرط یافت نشد. اصلاح پایدار باید اجبار انتخاب در لایهٔ OEM را مدیریت کند و رفتار نمایشگر شاگرد را حفظ کند. صرفاً تغییر locale، غیرفعال‌کردن APK کارخانه یا تکرار فرمان به‌عنوان راه‌حل در اپ پیاده نشده است.

پس از اجازهٔ کاربر، کپی اصلاح‌شدهٔ services.jar و نصاب آن در deployment/framework آماده شد. فایل اصلی استخراج‌شده تغییر نکرد و هیچ دستوری روی خودرو اجرا نشد. راهنمای نصب و بازگردانی در deployment/KEYBOARD-INSTALL-FA.md است. اعتبارسنجی میزبان، جای تست بوت و عملکرد روی خودرو را نمی‌گیرد.
