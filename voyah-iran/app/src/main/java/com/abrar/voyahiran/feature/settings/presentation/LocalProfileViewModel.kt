package com.abrar.voyahiran.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalProfileUiState(val busy: Boolean = false, val text: String = "برای مشاهدهٔ وضعیت، بررسی وضعیت را بزنید.")
class LocalProfileViewModel : ViewModel() {
    private val mutable = MutableStateFlow(LocalProfileUiState())
    val state = mutable.asStateFlow()
    fun run(operation: String) {
        if (mutable.value.busy || operation !in listOf("status", "apply", "restore")) return
        mutable.value = LocalProfileUiState(true, "در حال ارتباط با خودرو…")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su", "0", "local-profile-test", operation), 15)
                }
                val output = result.output
                com.abrar.voyahiran.core.logging.AppLog.info("LocalProfile", "operation=$operation exit=${result.exitCode} timedOut=${result.timedOut} output=${output.take(4000)}")
                val text = when {
                    result.exitCode == 0 && output.contains("login_activity_blocked=true") -> "بازشدن صفحهٔ ورود مسدود است. برای تست خودرو را ریبوت کنید. این تنظیم حساب خودرو را فعال نمی‌کند."
                    result.exitCode == 0 && output.contains("login_activity_blocked=false") -> "صفحهٔ ورود مسدود نیست."

                    result.exitCode == 0 && output.contains("login_dismiss=enabled") -> "بستن خودکار صفحهٔ QR فعال است. هنگام نمایش صفحهٔ ورود، خروج استاندارد خودرو اجرا می‌شود؛ این گزینه حساب را فعال نمی‌کند و پروفایل را تغییر نمی‌دهد."
                    result.exitCode == 0 && output.contains("login_dismiss=disabled") -> "بستن خودکار صفحهٔ QR غیرفعال است."

                    output.contains("Existing non-guest account preserved") || output.contains("Different account detected") -> "حساب موجود خودرو حفظ شد؛ تغییری اعمال نشد."
                    output.contains("No completed backup") -> "نسخهٔ پشتیبان برای بازگردانی وجود ندارد."
                    output.contains("OEM account directory missing") -> "پوشهٔ حساب در این خودرو پیدا نشد."
                    output.contains("Another profile operation") -> "عملیات دیگری در حال اجراست؛ چند ثانیه بعد دوباره امتحان کنید. اگر بعد از ریبوت هم ادامه داشت، گزارش را ارسال کنید."
                    output.contains("Incomplete previous backup") -> "نسخهٔ پشتیبان قبلی ناقص است؛ برای حفظ حساب خودرو عملیات متوقف شد. گزارش را ارسال کنید."
                    result.exitCode == 75 -> "واسط دسترسی مشغول است؛ چند ثانیه بعد دوباره امتحان کنید."
                    result.timedOut || result.exitCode != 0 -> "عملیات تأیید نشد. کد: ${result.exitCode}\n${output.take(1500)}"
                    else -> buildString {
                        if (operation == "apply") {
                            if (output.contains("activation_override=absent"))
                                append("تنظیم پروفایل و عبور از صفحهٔ ورود اعمال شد، اما نشانگر فعال‌سازی ساخته نشد. فعال‌سازی کامل و مموری صندلی تأیید نشده‌اند.\n")
                            else append("تنظیم آزمایشی اعمال شد. کارکرد ورود و مموری صندلی را روی خودرو بررسی کنید.\n")
                        }
                        append(if (output.contains("boot_apply=enabled")) "اعمال خودکار هنگام روشن‌شدن خودرو: فعال\n" else "اعمال خودکار هنگام روشن‌شدن خودرو: غیرفعال\n")
                        append(if (output.contains("login_bypass=enabled")) "عبور از صفحهٔ ورود: فعال\n" else "عبور از صفحهٔ ورود: غیرفعال\n")
                        if (operation == "restore") append("وضعیت قبلی بازگردانده شد.\n")
                        append(if (output.contains("activation_override=present")) "نشانگر ورود محلی: موجود" else "نشانگر ورود محلی: موجود نیست")
                        append("\nپروفایل: ")
                        append(when {
                            output.contains("profile=local_test") -> "آزمایشی وویا ایران"
                            output.contains("profile=guest") -> "مهمان"
                            output.contains("profile=missing") -> "موجود نیست"
                            else -> "حساب موجود خودرو"
                        })
                    }
                }
                mutable.value = LocalProfileUiState(text = text)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = LocalProfileUiState(text = "ارتباط با واسط دسترسی برقرار نشد؛ دوباره بررسی وضعیت را بزنید.") }
        }
    }
}
