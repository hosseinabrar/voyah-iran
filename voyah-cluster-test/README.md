# Voyah Cluster Test

اپ مستقل برای آزمایش شناسهٔ خام فلش کلاستر. پروژه را در Android Studio باز و APK آزمایشی را روی خودرو نصب کنید. نیازی به تغییر واسط وویا ایران یا اینستالر ندارد. چون SELinux خودرو اجرای مستقیم su از اپ را منع می‌کند، واسط موقت از لپ‌تاپ با ADB اجرا می‌شود.

پس از نصب، این دستور را در پوشهٔ پروژه اجرا کنید؛ مسیر adb.exe را مطابق سیستم خود بدهید:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-ClusterTest.ps1 -Adb "C:\Users\Nahid\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
```

اسکریپت اپ را باز می‌کند و واسط را راه می‌اندازد؛ سپس تست‌ها از داخل اپ انجام می‌شوند. پس از ریبوت خودرو اسکریپت را مجدداً اجرا کنید. واسط پس از یک ساعت بدون اتصال خارج می‌شود. ارتباط فقط روی loopback با توکن تصادفی در پوشهٔ خصوصی اپ است. هیچ فایل سیستم یا سرویس دائمی تغییر نمی‌کند.

1. در وویا ایران نمایش ناوبری نشان در کلاستر را خاموش کنید تا دو فرستنده تداخل نکنند.
2. جهت و نوع مانور نشان را انتخاب کنید؛ enumهای کامل APK نسخهٔ 14.12.0.3 در `research` با هش منبع ثبت شده‌اند.
3. عدد پیشنهادی فقط حدس مبتنی بر ترتیب خام Baidu است، نه نگاشت تأییدشده. آن را ویرایش کنید و «اعمال» بزنید.
4. همان عدد دقیق به `setTbtIconId` ارسال می‌شود؛ حتی صفر در حالت GUIDING تست می‌شود. نوع و جهت انتخابی فقط برچسب تست هستند؛ نوع مانور مقدار عددی را پنهانی تغییر نمی‌دهد.
5. دادهٔ تست: `Hemmat`، ۱۰۰ متر. ارسال هر ثانیه و حداکثر ۶۰ ثانیه پس از آخرین اعمال است. توقف، خروج از صفحه یا قطع پردازش رابط، MAN_STOP ارسال می‌کند؛ در صورت خطای Binder پاک‌شدن تضمین‌شده نیست و خطا ثبت می‌شود.
6. نتیجهٔ بصری را در جدول `research/results.csv` ثبت کنید. پاسخ Binder اثبات نمایش صحیح نیست.

لاگ کامل با تگ `VoyahClusterTest` در logcat و روی صفحه نمایش داده می‌شود. تست روی خودرو هنوز انجام نشده است. برای دورزدن مشکل فونت فارسی کلاستر، نام آزمایشی لاتین ارسال می‌شود.

مسیر SDK تنها در local.properties است (وارد گیت نمی‌شود). روی سیستم دیگر Android Studio مسیر SDK محلی را تنظیم می‌کند. Release عمداً minify ندارد تا نقطهٔ ورود پردازش root حفظ شود. هیچ خودراه‌اندازی یا تغییری در فایل‌های سیستم انجام نمی‌شود.

## تست فارسی — نسخهٔ 0.2.0
پس از نصب APK جدید، اسکریپت Start-ClusterTest.ps1 را دوباره اجرا کنید. این نسخه فقط واسط موقت خود اپ تست را دوباره راه‌اندازی می‌کند تا کد قبلی باقی نماند؛ به واسط وویا ایران دست نمی‌زند.

نمایش ناوبری نشان در وویا ایران خاموش باشد. عدد فلش ۱ کافی است. نمونه‌های همت، مدیریت، حروف اختصاصی فارسی، العربية و Hemmat 123 را با روش ۱ تست کنید. اگر فارسی خالی یا جدا/برعکس بود، روش‌های ۲ و ۳ را هم امتحان کنید. همین مراحل را با زبان عربی خودرو تکرار کنید. روش‌های ۲ و ۳ آزمایش Presentation Forms هستند؛ حروف اصلی تغییر شکل یونیکد می‌دهند و نتیجه به فونت کلاستر وابسته است.

برای هر نتیجه، زبان خودرو، متن نمونه و شمارهٔ روش را همراه عکس کلاستر و لاگ ارسال کنید. در لاگ BINDER_RETURNED، codepoints همان رشتهٔ ارسال‌شده به سرویس کارخانه است. رشته‌ها روی ارتباط محلی به صورت UTF-8/Base64 جابه‌جا می‌شوند تا تنظیم زبان پردازش آن‌ها را به علامت سؤال تبدیل نکند. برنامه زبان خودرو را تغییر نمی‌دهد.


## Map presentation probe — 0.3.0

Existing arrow IDs, Persian/Arabic text tests and the 100 m TBT stream are preserved. The additional renderer is an **animated synthetic map**, not Neshan or a live GPS map. This isolates cluster layout/transport from routing-app behavior.

Install the new APK and rerun `Start-ClusterTest.ps1` (the helper must also be updated). Keep the test control screen visible; pausing due to focus on another display is allowed, leaving the screen stops the session. Disable other cluster navigation senders first and test while parked.

1. Tap **وضعیت و شنود** to connect and log cluster callbacks.
2. Optionally use the original **اعمال** button to start the arrow/text/100 m stream.
3. Test **نقشه کوچک**, then **نقشه بزرگ**, then **نقشه در ناحیه موسیقی** separately. Each closes the previous presentation request. The last option temporarily places the synthetic map in the OEM music virtual display.
4. Cycle the cluster pages with the steering-wheel control. Record whether the cluster layout changes, which corner labels appear, the reported pixel dimensions, and whether the yellow dot/time counter move.
5. If the image stays blank, try **اعلام آمادگی تصویر — تست دستی** once. This explicitly tests firmware that hides the surface until the presentation-ready handshake; it does not claim a frame was already visible.
6. **توقف تصویر (حفظ فلش)** closes only the image test. The original stop button closes both tests. Every map session expires independently after 60 seconds; another arrow update does not extend it.

The renderer is launched via the installed OEM `startClusterActivity` API with NAVI or MEDIA category, never a guessed display number. `requestPresentation` uses NAVI_SMALL / NAVI_FULL / MEDIA_SMALL. Automatic `notifyReadyToShow` follows the matching OEM presentation callback and a rendered frame. The manual Ready button is separately logged.

Diagnostics (`VoyahClusterTest` logcat tag): `PRESENTATION_REQUEST` means a request was sent, `PRESENTATION_CALLBACK` is an OEM response, `ACTIVITY_STATE` contains OEM visibility/bounds, and `RENDER` reports the Android display ID, dimensions, density and frame timestamp. None alone proves the cluster panel displays the image; compare with a photo/video. A missing frame is reported after 10 seconds. App rendering on display 0 is refused.

No persistent navigation-card settings, vehicle configuration, firmware, or raw bus packets are changed. Stop requests close the selected presentation and remove the test activity via a short-lived lease. It does not restore the exact previous cluster page or claim to reconstruct unknown OEM readiness state. If a service call fails, the failure is logged; returning to the original cluster page with the steering wheel may be necessary.

This first image probe targets the `qg.cluster.service` API verified in the available H97X/H97C dumps. It does not implement H97A's separate legacy Presentation backend. Missing APIs fail explicitly; it never guesses that another physical display is the cluster. The existing root helper/TBT backend already requires this service.
