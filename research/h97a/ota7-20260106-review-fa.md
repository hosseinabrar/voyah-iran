# بررسی H97A آپدیت OTA7 — ۲۰۲۶-۱۰-۱۴

درخواست: بررسی خطای نصب 2.0.3 روی دامپ `Desktop/voyah h97a 20260106/system`.
برنچ هنگام بررسی و افزودن پشتیبانی: `version-2.0.3`.

## علت قطعی توقف نصب

لاگ 17:31:53 روت UID=0 و remount موفق را نشان می‌دهد. services.jar همان فایل ۱۸۳ بایتی قبلی است؛ در 17:31:54 نصاب هش services.vdex را ناشناخته می‌بیند و پیش از نصب APK یا جایگزینی framework متوقف می‌شود. این مورد خطای دانلود/نصب PackageManager یا کرش launcher نیست.

| مشخصه | دامپ قبلی | دامپ جدید |
|---|---|---|
| SDK / دستگاه | 28 / f0506h | 28 / f0506h |
| fingerprint suffix | `jenkins-PRJ_H97a_8155_OTA_1010-9` | `jenkins-PRJ_H97a_8155_SOP_OTA7.0_1030-37` |
| services.jar | ۱۸۳ بایت؛ بدون کد DEX | همان هش و ساختار |
| services.vdex اندازه | 9,772,532 | 9,775,852 |
| تعداد کلاس‌های بازسازی‌شده | 4,713 | 4,714 |

JAR SHA256: `1c16f5976318fadcf7add92c518a93896f99e2a78f742b7347d8539103264882`

VDEX قبلی: `a9303263150cc12ee9e941c9d295a3bf0aa8282284b9c5b15adc6efd59c41c06`

VDEX جدید: `664c053504beb67f977132419b2a8080b207965f958530b9366d84758ead7299`

DEX استاندارد بازسازی‌شدهٔ جدید:
`d0b537189af5389b272bebbb444f85af8d7105ff9929f923d5415979e893b931`.

## تغییرات کد framework

با حذف اطلاعات debug/شماره خطوط از مقایسه، کلاس جدید:
`com.android.server.audio.PlaybackActivityMonitor$AudioSessEvent`.

پنج کلاس تغییر کرده‌اند:

- ActivityStarter: `moveStackOrBroadcastOnlyIfNeeded`، منطق جابه‌جایی Activity بین نمایشگرها.
- AudioService و PlaybackActivityMonitor: `playerSession`، مدیریت session صدا.
- InputManagerService: سازنده و مسیرهای تزریق ورودی/لمس، متد جدید `getPassengerScreenDeviceId` و فیلد `passageScreenDeviceId`.
- AppTransition: `loadAnimation`.

هفت متد هدف پچ فعلی همگی با template قبلی یکسان‌اند: IME updateInputMethodsFromSettingsLocked؛ ActivityRecord updateOverrideConfiguration/getRequestedOrientation؛ AppWindowToken getOrientation/getOrientationIgnoreVisibility/layoutLetterbox؛ WindowState applyGravityAndUpdateFrame.

## بررسی ساخت پچ روی لپ‌تاپ

در مسیر تحقیقاتی `x20/work/h97a-ota7-review`، یک کپی آزمایشی Android9Patch.java با هش‌های جدید و تعداد کلاس 4714/4718 ساخته شد. ابزار بازسازی موجود VDEX019/compact DEX را بدون تغییر الگوریتم خواند؛ 1,569 opcode بهینه‌شده و 1,068 quickening index پردازش شدند.

ساخت، disassemble مجدد و مقایسهٔ کامل کلاس‌ها/متدها موفق بود؛ خروجی `VOYAH_PATCH_VERIFIED` صادر شد. هش patched.jar آزمایشی:
`6fe4ac2a29620c14276e7102c1581a7ed58d2e1f5338bf8d02a91e4bbbb96afa`.

این صرفاً بررسی محلی است؛ نصب/بوت/کیبورد/لمس روی خودرو انجام نشده. فایل ساخته‌شده خودکار برای نصب منتشر نشده است.

## تغییرات سایر اجزا و حدود بررسی

هش boot-QGAPI.vdex، VehicleSetting، Setting و Calendar با موجودی قدیمی فرق دارد. تغییر هش به‌تنهایی تغییر قرارداد Binder را ثابت نمی‌کند؛ قراردادهای این اجزا در این بررسی کامل بازخوانی نشده‌اند. برای رفع همین توقف نصاب، اولویت با افزودن پشتیبانی دقیق framework جدید است. فایل‌های system جدید جایگزین فایل‌های قدیمی نشوند؛ هر دو دامپ برای مقایسه نگه داشته شوند.

پس از درخواست کاربر برای اجرا، پشتیبانی افزوده شد: InstallEngine از Android9Firmware برای پذیرش دو هش بررسی‌شده استفاده می‌کند؛ Android9Patch هر VDEX را به هش DEX متناظر نگاشت می‌کند و تعداد کلاس‌های 4713/4714 و حالت پچ‌شدهٔ 4717/4718 را می‌پذیرد. بررسی متدهای دقیق، helperها و عدم تغییر سایر کلاس‌ها حفظ شده است. هش قدیمی حذف نشده و نسخهٔ ناشناخته همچنان رد می‌شود. چهار تست پذیرش دو نسخه/رد هش ناشناخته/خالی اضافه شده‌اند.

شواهد تفصیلی خارج گیت: `x20/work/h97a-ota7-review/framework-diff.json`، `components-diff.json`، `services-ota7.dex`، `smali-new` و `local-patch-check`.

## نتیجهٔ اجرای پشتیبانی

۱۱۰ بررسی core نصاب پاس شدند. اجرای کد نهایی Android9Patch روی هر دو دامپ قدیمی و OTA7 با `VOYAH_PATCH_VERIFIED` موفق بود؛ اجرای دوباره روی patched.jar جدید نیز `already=true` داد و فایل را بدون تغییر کپی کرد. کد OTA7 داخل bundle.zip نهایی بررسی شد. انتشار WindowsSingleFile نسخهٔ 2.0.3 موفق بود؛ APK با پروتکشن فعال و کلید اصلی امضا شد. خروجی در `voyah-iran-installer/dist/voyah-iran-installer_2.0.3.exe` است. این بررسی‌ها شامل نصب یا بوت خودرو نیستند.
