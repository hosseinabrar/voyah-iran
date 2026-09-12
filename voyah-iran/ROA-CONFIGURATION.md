# پیکربندی پایش عقب

مسیر بررسی‌شده در APK استخراج‌شدهٔ Settings:

- `VehicleInfoSettingActivity` تنظیم `VehicleOnlineState.RPM` را در لیست پیکربندی ثبت می‌کند.
- `VehicleInfoSettingActivity$ConfigItem` با `VehicleConfigHelper.writeOnlineConfigItem(state, value)` آن را می‌نویسد.
- در APK دوربین، `SupportUtils.isSupportRoa()` مقدار RPM برابر ۱ را بررسی می‌کند. این گزینه با `REAR_CAMERA` متفاوت است.
- در مسیرهای H97، H97C و H56 بررسی‌شده، RPM بیت ۶۵ است؛ بااین‌حال برنامه بایت‌ها را دستی تغییر نمی‌دهد و متد SDK همان Settings نصب‌شده را فراخوانی می‌کند.

واسط فرمان محدود `roa-config enable` را می‌پذیرد. قبل از تغییر از خروجی `getVehicleTypeSUB()` نسخهٔ تشخیصی در `config-backups` زیر پوشهٔ واسط نگه می‌دارد. overload بدون Context استفاده می‌شود تا broadcast ریبوت خودکار OEM صادر نشود. برگشت صفر از متد OEM پذیرش نوشتن است؛ فعال‌شدن عملی دوربین فقط بعد از ریبوت و تست خودرو مشخص می‌شود.

منطق قبلی تشخیص DVR، وجود دوربین، کنترل CAN و hook حفظ شده است. پس از درخواست فعال‌سازی موفق، پاپ‌آپ ریبوت نمایش داده می‌شود. غیرفعال‌کردن گزینه همچنان مسیر قبلی را انجام می‌دهد و پیکربندی سخت‌افزاری RPM را پاک نمی‌کند.

برای تست، APK جدید باید با نصاب نصب شود تا نسخهٔ واسط نیز به‌روزرسانی شود. تست واقعی با RPM ابتدا خاموش، سپس انتخاب فعال‌سازی در برنامه و ریبوت لازم است. گزینهٔ «بعداً» نباید ریبوت کند. اگر نوشتن پیکربندی شکست خورد، لاگ `VI/RearCameraConfig` و `VI/RearCamera` را بررسی کنید.

بررسی محلی: helper جدید به‌طور مستقل کامپایل شد؛ ۹ تست موجود repository دوربین پاس شدند و XMLها بررسی شدند. APK ساخته نشد و تست واقعی روی خودرو انجام نشده است.


## OEM restart correction
The confirmation click in VehicleInfoSettingActivity$2.onClick calls
DeviceUtils.rebootH56_97System, which sets sys.powerctl to reboot,power
(DeviceUtils.smali lines 1199–1216 in the supplied settings APK).
The ROA confirmation now requests the exact allowlisted vehicle-config/restart
operation, translated to /system/bin/setprop sys.powerctl reboot,power by the bridge.
Ordinary settings reboot remains unchanged. No automatic retry or fallback reboot.
This confirms the command difference, not a full-vehicle ECU reset or a verified
fix on hardware. Reinstall/update the bridge from the updated APK before testing.
