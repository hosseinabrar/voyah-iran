# وضعیت اعتبارسنجی

تاریخ بررسی: ۲۰۲۶/۰۹/۰۹

## اصلاحات 0.2.1-bridge — ۲۰۲۶/۰۹/۱۰

- لاگ دستگاه، بوت موفق واسط با UID صفر، بازشدن خودکار صفحه و تغییر ساعت در هر دو جهت را تأیید کرد.
- مقدار روشن ACC در DongfengH97CCanBusComponentImpl برابر ۲ است (ثبت mAccState و ACC_STATUS در onAccStatus)؛ مقایسهٔ اشتباه با ۱ اصلاح شد. READY در onVCU_vehicle به‌صورت بیت ۰/۱ خوانده می‌شود.
- ذخیرهٔ مقدار معتبر و شنود تغییرات، READY لازم ندارد؛ ارسال خودکارِ مقدار متفاوت همچنان به برق روشن، READY و سرعت صفر نیاز دارد.
- IME فرمان‌ها در firmware می‌توانند حتی هنگام ردکردن انتخاب exit=0 بدهند. ثبت پاسخ محدود به IME، بررسی خطاهای متنی و انتظار حداکثر دو ثانیه برای انتشار وضعیت اضافه شد. علت قطعی شکست کیبورد هنوز در لاگ قبلی معلوم نیست و رفع آن تأیید نشده است.
- ۳۹ تست رانندگی، بازیابی و دامنهٔ کیبورد پاس شدند؛ کامپایل Kotlin و پردازش منابع موفق بود. APK ساخته نشد.
- قرارداد و کد اجرایی واسط تغییر نکرده‌اند؛ برای این اصلاح کافی است APK عادی با همان امضا به‌روز شود. نصب مجدد واسط لازم نیست.

## نسخهٔ 0.2.0-bridge

- ۱۵ تست میزبانِ سیاست فرمان، احراز هویت، محدودیت ورودی و تغییر/بازگردانی ساعت پاس شدند.
- کامپایل Kotlin و پردازش منابع موفق بود؛ APK ساخته نشد.
- نحو PowerShell نصاب با Parser بررسی شد. نصاب روی خودرو اجرا نشده است؛ راه‌اندازی app_process، provisioning و دسترسی SELinux باید در تست دستگاه تأیید شوند.
- درخواست‌های ویژهٔ ساعت، کیبورد، مود، بازیابی، دوربین و بازکردن صفحه به واسط منتقل شدند. APK عادی باقی می‌ماند.
- گزارش‌های قدیمی دربارهٔ نیاز مستقیم اپ به su یا نصب privileged، شرح نسخه‌های پیشین‌اند؛ مسیر نصب فعلی در deployment/BRIDGE-INSTALL-FA.md است.

## انجام‌شده

- لاگ تشخیصی قابلیت‌ها، فرایندهای root، اتصال CAN، رویداد ستاره و چرخهٔ بوت/سرویس اضافه شد. کامپایل Kotlin و پردازش منابع پس از این تغییر موفق بود؛ APK ساخته نشد. [راهنمای جمع‌آوری لاگ](docs/LOGCAT.md).

- صفحهٔ گزارش آپولو: کامپایل Kotlin و پردازش منابع موفق؛ ۴ تست مستقل برای وضعیت نامشخص، انقضا، تفاوت نگاشت روشن/خاموش قابلیت‌ها و استقلال تیک‌ها از اشتراک پاس شدند.
- دسترسی‌های سرویس‌های خودرو با Manifest و کد ارائه‌شده تطبیق داده شدند؛ [گزارش دسترسی‌ها](docs/PERMISSIONS.md).

- ۸ تست مستقل Gboard پاس شدند: انتخاب و بازگشت، نبود Gboard، حفظ سابقه، شکست ذخیره، انتخاب قبلی از بیرون، حذف کیبورد قبلی، عدم تأیید و جلوگیری از fallback به کیبورد شاگرد.

- ۷ تست جدید حافظهٔ موقت و درخواست دوربین عقب پاس شدند: دو جهت تغییر، وضعیت از قبل صحیح، DVR ناموجود، ROA ناموجود، وضعیت نامعتبر، نرسیدن بازخورد و شکست مبهم درخواست بدون تکرار toggle. این تست‌ها جدا از ۶۲ تست قبلی اجرا شدند.

- کامپایل Kotlin و پردازش منابع Android موفق: `:app:compileDebugKotlin` و `:app:processDebugResources`.
- این بررسی روی کپی ایزولهٔ سورس در پوشهٔ کار انجام شد؛ تنظیم JDK انتخاب‌شده توسط Android Studio کاربر تغییر نکرد.
- ۶۲ تست JVM پاس شدند: ۱۴ تست ساعت، ۱۰ تست ذخیره/بازیابی نگاشت ستاره، ۶ تست مسیر رویدادهای فرمان، ۱۸ تست ماشین حالت حافظهٔ رانندگی، ۳ تست درخواست سرویس رسمی مود و ۱۱ تست حافظهٔ بازیابی انرژی و ترتیب اعمال.
- تست‌های بازیابی انرژی شامل سه سطح، حفظ مقدار ذخیره‌شده هنگام بوت، استقلال دو گزینه، ترتیب اعمال پس از مود، پارامترهای دقیق Binder و تشخیص خطا یا مهلت تمام‌شده هستند.
- تست‌های فرمان شامل فشار کوتاه، جلوگیری از نشت DOWN/UP به اکشن قبلی، حفظ توالی فشار بلند، جلوگیری از Back اضافه بعد از فشار بلند، فشار دوبل، رویداد نامرتبط، قطع هندلر، خطای ذخیره‌سازی و بازیابی ناموفق هستند.
- رشته‌ها، اندازه‌ها و چیدمان قابلیت جدید در منابع XML قرار دارند و جریان RTL حفظ شده است.
- تست‌های حافظه شامل ثبت مود اولیه، بوت با اکو، حفظ مقدار قبلی تا تأیید، تغییرات کاربر، ACC خاموش/روشن، اتصال مجدد، سرعت نامعتبر، روت ردشده، خطای ذخیره، مهلت تأیید و خاموش‌کردن قابلیت هستند.
- بنا به درخواست کاربر، قابلیت حافظه محدودیت مدل یا بیلد ندارد؛ نسخه فعلاً برای تست فنی دو مدل موردنظر است.

## انجام‌نشده

- درخواست ساخت APK توسط کاربر لغو شد. تلاش پیشین ساخت کامل به خطای دسترسی فایل‌های محیط میزبان خورد؛ APK تحویل یا نصب نشده است. کامپایل سورس/منابع آپولو جداگانه موفق شد.
- گزارش آپولو هنوز با وضعیت واقعی دستگاه و provider نسخهٔ نصب‌شده تطبیق داده نشده است.

- انتخاب واقعی Gboard، مجوز روت برای فرمان ime و باقی‌ماندن کیبورد کارخانه روی نمایشگر شاگرد هنوز روی خودرو آزمایش نشده‌اند؛ [جزئیات](docs/GBOARD.md).

- فعال‌سازی ROA و نمایش تصویر روی خودرو هنوز تست نشده‌اند؛ گزارش ورودی نیز نتیجهٔ نهایی تصویر را تأیید نمی‌کند. برنامهٔ آزمون: [دوربین عقب](docs/REAR_CAMERA.md).

- APK ساخته یا نصب نشده است؛ مطابق درخواست کاربر.
- عملکرد فیزیکی ستاره، مجوز واقعی سرویس Qinggan، نگاشت افزونه‌های OEM و رفتار خواب/بیداری روی خودرو آزمایش نشده‌اند.
- مسیر فرمان درایو مود طبق گزارش کاربر و تأیید او روی دو مدل آزمایش شده؛ اما حافظه، ترتیب پیام‌های بوت، بازشدن خودکار Activity و تأیید مود در نسخهٔ جدید این برنامه هنوز روی خودرو آزموده نشده‌اند.
- تست‌های تصویری Robolectric و رندر Android Studio در این مرحله اجرا و تأیید نشده‌اند. کامپایل منابع، تضمینِ درست‌بودن Preview نیست.
- این نتیجه معادل اجرای کامل build، lint و آزمون دستگاه نیست.
- مسیر جدید نوشتن سطح بازیابی انرژی و تأیید بازخورد آن هنوز روی خودرو آزمایش نشده‌اند.

برنامهٔ آزمون خودرو: [ساعت](docs/DEVICE_TEST.md)، [دکمهٔ فرمان](docs/STEERING_BACK.md)، [حافظهٔ رانندگی](docs/DRIVE_MEMORY.md) و [حافظهٔ بازیابی انرژی](docs/RECOVERY_MEMORY.md).

### Passenger physical power button (2026-10-30)

OEM H97X PowerManager `VoyahPolicy` (decoded `bd`, lines 699–705) sets
`persist.second.screen.disable=true` and installs its `m` allowed-key list when
central stays on and passenger enters display-off. That list includes STAR
(`0xad`) but omits QG BACK (`0x21`). KeyManagerSinkCollector.push drops restricted
keys before sink delivery. Replaying synthesized Back through QG therefore loses
it; changing its display ID alone cannot fix this. The authenticated bridge now
injects only our synthetic short Back directly on display 0 (API 28 and 30).
Other original/long-press actions keep their OEM transport and restrictions.
Android 11 uses KeyEvent.setDisplayId, matching KtAndroidSink.Companion.

The same OEM property prevents our swap from sending apps to the powered-off
passenger display, including revalidation immediately before moves. PASSENGER_OFF
is consumed rather than falling through to the OEM swap. Startup, passenger Home,
drawer and screen-lift redirects defer while off. A physical power-on transition
reconciles only passenger Home/drawer; it does not replace a foreground app or
navigate the central display. This property is independent of global SCREEN_ON
and of the motorized display lift state.

Verification: targeted StarBackTransport/StarEventPolicy/ScreenSwap JVM tests and
`node deployment/tests/passenger-power.test.js`. The latter executes the shipped
injected scripts for off, wake to Home, wake to drawer, repeated wake checks and
wake over another app. Vehicle verification remains required: center Back with
passenger off, denied swap, and passenger off/on from Home, drawer and another app.

Screensaver follow-up: OEM H97X StandbyUIForVoyah (`by`) creates two
WindowManager windows, type 2021, titled PowerManager-StandbyUI-1/2 and adds/removes
views; it does not create or move an activity stack. Our swap uses activity-stack
inventory, not the focused overlay window. No screensaver-only transfer block was
added: source inspection found no stack-transfer conflict. Physical vehicle
validation of transfers behind that overlay remains outstanding.
