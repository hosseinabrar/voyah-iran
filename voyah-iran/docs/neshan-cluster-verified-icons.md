# نگاشت تأییدشدهٔ تصاویر نشان به کلاستر

منبع: تست تصویری کاربر روی خودرو، ۲۰۲۶-۰۹-۲۱. این اعداد شناسهٔ خام setTbtIconId هستند، نه ordinal اینام عمومی Qinggan.

| تصویر نشان | کد کلاستر |
|---|---:|
| ARRIVE | 24 |
| LEFT | 7 |
| SHARP_LEFT | 6 |
| SLIGHT_LEFT | 8 |
| RIGHT | 3 |
| SHARP_RIGHT | 4 |
| SLIGHT_RIGHT | 2 |
| DEPART | 24 |
| STRAIGHT | 1 |
| ROUNDABOUT | 9 |
| ROTARY_EXIT_1 | 5 |
| ROTARY_EXIT_2 | 4 |
| ROTARY_EXIT_3 | 3 |
| ROTARY_EXIT_4 | 2 |
| ROTARY_EXIT_5 | 10 |
| ROTARY_EXIT_6 | 8 |
| ROTARY_EXIT_7 | 7 |
| ROTARY_EXIT_8 | 6 |
| NEW_EXIT_ROUNDABOUT | 12 |
| U_TURN | 5 |

انتخاب تصویر مطابق t10.g در نشان 14.12.0.3 است: ARRIVE/DEPART/ROUNDABOUT/NEW_EXIT_ROUNDABOUT اولویت دارند. ROTARY از StepInfo.j() یا turnIndex استفاده می‌کند؛ مقدار null یا صفر تصویر میدان و ۱ تا ۸ تصاویر جدول را انتخاب می‌کنند. بقیهٔ مانورها از modifier استفاده می‌کنند. شمارهٔ گام مسیر با turnIndex متفاوت است. جهت UNKNOWN طبق نشان به مستقیم برمی‌گردد. برای حالت‌های ویژهٔ میدان، مسیر نمایش عادی با فعال بودن تصاویر میدان مبناست.

دادهٔ turnIndex اکنون در اسکریپت Frida و نسخهٔ تعبیه‌شده منتقل می‌شود. برای نصب روی خودرو، APK و واسط روت باید با هم از این نسخه به‌روز شوند؛ مسیر نصب کامل اینستالر را اجرا کنید. بیلد ریلیز در این تغییر انجام نشده است.
