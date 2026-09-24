# مکان‌های منتخب نشان و رها

در تنظیمات نشان، با فعال‌بودن «مسیریابی از طریق رها»، دکمهٔ «مکان‌های منتخب» نشان داده می‌شود. دو نمایندگی پایه ثابت‌اند؛ حداکثر ۱۸ مکان شخصی قابل افزودن، ویرایش و حذف با تأیید هستند. هر مکان یک نام اصلی و حداکثر چهار نام جایگزین دارد؛ جداکننده کامای فارسی یا انگلیسی است. نام اصلی نیز قابل فراخوانی صوتی است. نام‌های تکراری میان مکان‌ها، شامل نام‌های نمایندگی‌ها، پذیرفته نمی‌شوند.

## تنظیم کلیدهای نشان قبل از بیلد

در فایل `local.properties` ریشهٔ پروژهٔ اندروید (کنار `settings.gradle.kts`) این دو مقدار را اضافه کنید؛ تنظیمات موجود SDK را حذف نکنید:

```properties
neshan.mapKey=YOUR_WEB_MAP_KEY
neshan.searchKey=YOUR_SEARCH_API_KEY
```

این فایل از قبل در gitignore است. مسیر مطلق جدیدی در بیلد استفاده نشده. به‌جای فایل، متغیرهای محیطی `NESHAN_MAP_KEY` و `NESHAN_SEARCH_KEY` یا Gradle properties با همان نام‌ها هم پشتیبانی می‌شوند. اولویت با Gradle property، سپس environment، سپس local.properties است. کلیدها در BuildConfig بسته‌بندی می‌شوند؛ این‌ها کلیدهای سمت کلاینت هستند و نباید کلید خصوصی سرور باشند. هیچ کلیدی در این تغییر ساخته/دریافت نشده است.

- کلید اول از نوع **Web SDK** برای نقشهٔ نشان داخل WebView است. مبنای URL صفحه `https://voyah-iran.ir/` است؛ محدودیت دامنهٔ کلید باید با این origin سازگار باشد.
- کلید دوم باید به **Search API v3** دسترسی داشته باشد. طبق مستندات فعلی نشان، فعال‌سازی این سرویس ممکن است نیاز به تیکت پشتیبانی داشته باشد.
- بدون کلید نقشه، فرم پیام آماده‌نبودن نقشه را نشان می‌دهد و موقعیت ساختگی ذخیره نمی‌شود. بدون کلید جستجو، نقشه قابل جابه‌جایی و انتخاب دستی است ولی جستجو غیرفعال می‌ماند.
- انتخاب اولیهٔ فرمِ افزودن خالی است؛ کاربر باید نقطه را لمس کند یا نتیجهٔ جستجو را انتخاب کند. در ویرایش، نقطهٔ قبلی حفظ می‌شود. نقشه، شبکه و کلید معتبر نیاز دارند؛ مکان‌های ذخیره‌شده و تشخیص صوت آن‌ها محلی هستند.

مستندات مبنا:
- https://platform.neshan.org/docs/sdk/web/leaflet/neshan-leaflet-sdk/
- https://platform.neshan.org/docs/api/search-category/search

## Runtime

SharedPreferences stores a single JSON snapshot with stable user UUIDs and coordinates. Built-in dealers stay outside editable storage. Writes validate capacity, coordinates, names and alias collisions before commit. Unknown/deleted IDs fail at navigation execution instead of falling back to a different destination. Navigation still uses explicit Neshan package + existing driving/start automation.

RahaNavigationRecognizer publishes a volatile alias snapshot and revision. Speech grammar is cached by revision; no per-frame grammar rebuild occurs. RahaService loads saved places on creation and restarts idle wake capture on a changed revision, waiting until an active interaction finishes. Longest destination aliases take precedence where names overlap. File corruption is reported without silently overwriting saved entries.

WebView has no JavaScript-to-native interface, no file/content access, and blocks page navigation. A native save action reads only the selected coordinates. Search is explicit (button press), bounded to 30 items/512 KiB, with timeouts and cancellation on closing the dialog. No continuous background map/location polling.

Validation: core tests cover live aliases/grammar, rename/delete, longest match, normalized duplicates, four-alias limit, 18-place limit and invalid coordinates. Android Kotlin changes compile against local dependencies; map/search live operation remains unverified until keys are provided. No release APK produced.
