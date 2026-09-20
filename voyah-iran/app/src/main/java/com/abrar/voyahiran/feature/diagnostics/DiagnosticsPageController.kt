package com.abrar.voyahiran.feature.diagnostics

import android.app.AlertDialog
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.R
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.databinding.PageDiagnosticsBinding
import kotlinx.coroutines.*

internal class DiagnosticsPageController(private val activity: ComponentActivity, private val page: PageDiagnosticsBinding) {
    private var generation = 0
    private var busy = false
    private var readings = emptyList<DtcReading>()
    init {
        page.readErrors.setOnClickListener { read(false) }
        page.readLocal.setOnClickListener { read(true) }
        page.clearErrors.setOnClickListener {
            if (busy) return@setOnClickListener
            val targets = readings.filter { it.faults.isNotEmpty() }.map { it.entity }
            if (targets.isEmpty()) {
                page.status.setText(R.string.diagnostics_need_read)
            } else {
                val token = generation
                AlertDialog.Builder(activity).setTitle(R.string.diagnostics_clear)
                    .setMessage(activity.getString(R.string.diagnostics_clear_confirm, targets.joinToString("\n") { it.label }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.diagnostics_clear) { _, _ ->
                        if (token == generation && !busy) clear(targets)
                    }.show()
            }
        }
    }
    fun reset() {
        generation++
        readings = emptyList()
        page.results.text = ""
        page.status.setText(R.string.diagnostics_hint)
        // In-flight work may finish, but cannot repopulate a newly entered page.
    }
    private fun read(localOnly: Boolean) = runOperation(if (localOnly) "read-local" else "read-ethernet") { client ->
        client.read(localOnly)
    }
    private fun clear(targets: List<DoipEntity>) = runOperation("clear") { client ->
        val fresh = mutableListOf<DtcReading>(); val failures = mutableListOf<String>()
        targets.forEach { entity ->
            try {
                client.clear(entity)
                AppLog.info("Diagnostics", "clear acknowledged ${entity.label}; verifying by read")
                try { fresh += client.readEntity(entity) }
                catch (e: Exception) { failures += "${entity.label}: حذف تأیید شد، اما بازخوانی ناموفق بود: ${e.message}" }
            } catch (e: Exception) {
                failures += "${entity.label}: حذف تأیید نشد؛ برای بررسی دوباره بخوانید: ${e.message}"
                AppLog.info("Diagnostics", "clear unconfirmed ${entity.label}: ${e.message}")
            }
        }
        DiagnosticResult(fresh, failures)
    }
    private fun runOperation(name: String, operation: (DoipDiagnostics) -> DiagnosticResult) {
        if (busy) return
        busy = true
        val token = ++generation
        readings = emptyList(); page.results.text = ""
        page.status.setText(R.string.diagnostics_busy)
        setBusy(true)
        activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val id = AppLog.nextId()
                    AppLog.info("Diagnostics", "request=$id operation=$name start")
                    val taskContext = currentCoroutineContext()
                    val result = operation(DoipDiagnostics(log = { AppLog.info("Diagnostics", "request=$id $it") }, checkActive = { taskContext.ensureActive() }))
                    AppLog.info("Diagnostics", "request=$id done units=${result.readings.size} failures=${result.failures.size}")
                    result
                }
                if (token == generation) {
                    readings = result.readings
                    page.status.text = "پاسخ معتبر: ${readings.size} واحد — خطای ارتباط/خواندن: ${result.failures.size}"
                    page.results.text = buildString {
                        readings.forEach { reading ->
                            appendLine(reading.entity.label)
                            if (reading.faults.isEmpty()) appendLine("در این واحد خطایی با وضعیت قابل گزارش دریافت نشد.")
                            reading.faults.forEach { fault ->
                                appendLine("DTC 0x${fault.code}  |  status=0x%02X".format(fault.status))
                                appendLine("توضیح سازنده در دسترس نیست؛ اهمیت خطا نامشخص است.")
                                if (fault.status and 1 != 0) appendLine("آزمون فعلی این خطا ناموفق است.")
                                if (fault.status and 0x80 != 0) appendLine("ECU درخواست روشن‌شدن نشانگر هشدار داده است.")
                            }
                            appendLine()
                        }
                        result.failures.forEach { appendLine(it) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppLog.error("Diagnostics", "operation=$name failed ${e.message}", e)
                if (token == generation) page.status.text = "ارتباط ناموفق: ${e.message}"
            } finally { busy = false; setBusy(false) }
        }
    }
    private fun setBusy(value: Boolean) {
        page.progress.visibility = if (value) View.VISIBLE else View.GONE
        page.readErrors.isEnabled = !value
        page.readLocal.isEnabled = !value
        page.clearErrors.isEnabled = !value
    }
}
