package com.abrar.voyahaudiotest

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var client: ProbeClient
    private lateinit var status: TextView
    private lateinit var output: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private val actions = mutableListOf<View>()
    private val testButtons = mutableMapOf<String, Button>()
    private var busy = false
    private var alive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.rgb(19, 27, 38))
        }
        val scroll = ScrollView(this); scroll.addView(root); setContentView(scroll)
        fun text(value: String, size: Float = 17f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.LTGRAY); setPadding(0, 10, 0, 8); root.addView(this)
        }
        text("VOYAH Audio Lab — X / C", 25f)
        text("فقط در حالت توقف و با صدای اولیهٔ کم تست کنید. با خروج از برنامه بازیابی شروع می‌شود. حفاظت حرارتی، قطع صدا و هشدارهای ایمنی تغییر نمی‌کنند.")
        status = text("متصل نیست — ابتدا اتصال و بررسی قابلیت‌ها را بزنید")
        fun button(parent: LinearLayout, label: String, action: () -> Unit): Button = Button(this).apply {
            this.text = label; isAllCaps = false
            parent.addView(this, LinearLayout.LayoutParams(0, 60, 1f)); actions.add(this)
            setOnClickListener { action() }
        }
        fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; root.addView(this) }
        button(row(), "اتصال و بررسی قابلیت‌ها") { runTask { client.connect() } }
        val tools = row()
        button(tools, "بازیابی همه") { runTask { client.restoreAll() } }
        button(tools, "قطع اتصال و بازیابی") { runTask { client.close() } }
        button(tools, "ثبت وضعیت کامل صدا") { runTask { client.diagnostics() } }
        val controls = listOf(
            Triple("duck", "حذف افت موقت بلوتوث", "gain مثبتِ کمتر از ۱ به ۱ برمی‌گردد. سکوت واقعی دست‌نخورده است. ابتدا بعد از اتصال ابزار، بلوتوث را پخش/متصل کنید تا مقدار اصلی ثبت شود."),
            Triple("absolute", "نادیده‌گرفتن ولوم ورودی گوشی", "فقط فرمان Absolute Volume ورودی از گوشی مسدود می‌شود؛ این کلید خاموش‌کردن کامل همگام‌سازی دوطرفه نیست."),
            Triple("navigation", "پایان حالت میکس ناوبری کارخانه", "اثر حالت ناوبری OEM جدا تست می‌شود؛ این گزینه تمام سیاست‌های اعلان و دستیار را غیرفعال نمی‌کند."),
            Triple("dts", "خاموش‌کردن DTS", "حذف پردازش DTS برای مقایسه؛ الزاماً باعث بلندتر شدن صدا نمی‌شود."),
            Triple("atmos", "خاموش‌کردن Atmos", "تنها در صورت وجود API خواندن و نوشتن روی رام نصب‌شده."),
            Triple("loudness", "خاموش‌کردن Loudness", "برای مقایسهٔ پردازش صدا؛ ممکن است صدا کمتر شود.")
        )
        for ((key, title, description) in controls) {
            text(title, 20f); text(description, 14f)
            val line = row()
            testButtons[key] = button(line, "شروع تست — حداکثر ۶۰ ثانیه") {
                confirm(title, "ممکن است صدا ناگهان بیشتر شود. صدای گوشی/موسیقی را کم کرده‌اید؟") { runTask { client.apply(key) } }
            }.apply { isEnabled = false }
            button(line, "برگرداندن همین مورد") { runTask { client.restore(key) } }
        }
        text("Native media — ارسال مستقیم عدد", 21f)
        text("فقط کانال مدیا؛ بازهٔ آزمایش ۰ تا ۴۰. مقدار درخواستی، پاسخ Native و مقدار خوانده‌شده ثبت می‌شوند. بازیابی خودکار پس از ۳ ثانیه. عدد ۴۰ تضمین افزایش صدا نیست.", 15f)
        val number = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setText("10"); setTextColor(Color.WHITE); root.addView(this)
        }
        val nativeRow = row()
        testButtons["native"] = button(nativeRow, "ارسال مستقیم — ۳ ثانیه") {
            val value = number.text.toString().toIntOrNull()
            if (value == null || value !in 0..40) Toast.makeText(this, "عدد ۰ تا ۴۰ وارد کنید", Toast.LENGTH_SHORT).show()
            else confirm("ارسال مستقیم $value", "این مسیر ممکن است از سقف معمول نرم‌افزار عبور کند. احتمال صدای ناگهانی و اعوجاج وجود دارد. پخش را با منبع کم‌صدا شروع کنید؛ ادامه می‌دهید؟") {
                runTask { client.apply("native", value) }
            }
        }.apply { isEnabled = false }
        button(nativeRow, "بازگشت فوری Native") { runTask { client.restore("native") } }
        text("بازیابی‌های باقی‌مانده در حافظه نگه داشته می‌شوند؛ در صورت خطا، دوباره متصل شوید و بازیابی همه را بزنید. برنامه را تا تأیید بازیابی حذف نکنید.", 15f)
        button(row(), "ذخیرهٔ فایل کامل لاگ") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "voyah-audio-lab.txt")
            }, 10)
        }
        output = text("", 12f).apply { setTextIsSelectable(true) }
        client = ProbeClient(this) { line -> runOnUiThread {
            if (alive) {
                output.text = (output.text.toString() + "\n" + line).takeLast(18000)
                refresh()
            }
        } }
        refresh()
    }
    private fun refresh() {
        if (!::client.isInitialized) return
        status.text = "بازیابی‌های باقی‌مانده: ${client.pending().joinToString().ifEmpty { "هیچ‌کدام" }}"
        actions.forEach { it.isEnabled = !busy }
        testButtons.forEach { (key, view) -> view.isEnabled = !busy && client.available(key) }
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("لغو", null).setPositiveButton("تأیید و اجرا") { _, _ -> action() }.show()
    }
    private fun runTask(task: () -> Unit) {
        if (busy) return
        busy = true; refresh()
        worker.execute {
            try { task() } catch (e: Exception) { client.log("ERROR: ${e.message}") }
            finally { runOnUiThread { busy = false; if (alive) refresh() } }
        }
    }
    override fun onStart() { super.onStart(); if (::client.isInitialized) client.foreground = true }
    override fun onStop() {
        if (::client.isInitialized) {
            client.foreground = false // watchdog loses lease immediately even if worker is occupied
            worker.execute { client.close() }
        }
        super.onStop()
    }
    override fun onDestroy() { alive = false; worker.shutdown(); super.onDestroy() }
    @Deprecated("Legacy activity result for this standalone diagnostic")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) data?.data?.let { uri ->
            worker.execute {
                runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(client.logText()) } }
                    .onFailure { client.log("Export failed: $it") }
            }
        }
    }
}
