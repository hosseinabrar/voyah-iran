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
                val text = when {
                    output.contains("Existing non-guest account preserved") || output.contains("Different account detected") -> "حساب موجود خودرو حفظ شد؛ تغییری اعمال نشد."
                    output.contains("No completed backup") -> "نسخهٔ پشتیبان برای بازگردانی وجود ندارد."
                    output.contains("OEM account directory missing") -> "پوشهٔ حساب در این خودرو پیدا نشد."
                    result.timedOut || result.exitCode != 0 -> "عملیات تأیید نشد. واسط دسترسی را با نصاب به‌روز کنید و دوباره وضعیت را بررسی کنید. کد: ${result.exitCode}"
                    else -> buildString {
                        if (operation == "apply") append("تنظیم آزمایشی اعمال شد. کارکرد ورود و مموری صندلی را روی خودرو بررسی کنید.\n")
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
