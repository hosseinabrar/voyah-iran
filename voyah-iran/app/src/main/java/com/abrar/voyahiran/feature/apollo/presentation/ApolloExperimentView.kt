package com.abrar.voyahiran.feature.apollo.presentation

import android.view.View
import android.widget.*
import com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment

/** Experimental controls are kept separate from the approved main section. */
internal object ApolloExperimentView {
    fun render(container: LinearLayout, state: ApolloUiState, enabled: Boolean, change: (ApolloExperiment, Int) -> Unit) {
        val context = container.context
        container.removeAllViews()
        fun label(text: String, size: Float = 16f) = TextView(context).apply {
            this.text = text; textSize = size
            setPadding(0, 14, 0, 10)
            container.addView(this)
        }
        label("تنظیمات آزمایشی آپولو", 21f)
        label("وضعیت و عدد هر گزینه از خودرو خوانده می‌شود. گزینه‌های پشتیبانی‌نشده قابل تغییر نیستند.")
        ApolloExperiment.entries.forEach { setting ->
            val actual = state.report.experiments[setting]
            val known = actual != null && setting.accepts(actual)
            val active = enabled && !state.busy && known
            val status = if (known) "${setting.choices[actual]} ($actual)" else "در دسترس نیست (${actual ?: "—"})"
            if (setting.on != null) {
                container.addView(CheckBox(context).apply {
                    text = "${setting.title} — $status"
                    isChecked = actual == setting.on
                    isEnabled = active
                    alpha = if (active) 1f else 0.45f
                    setOnClickListener { change(setting, if (isChecked) setting.on else setting.off!!) }
                })
            } else {
                label("${setting.title} — $status")
                setting.choices.entries.chunked(3).forEach { row ->
                    val group = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    row.forEach { (value, title) ->
                        group.addView(ToggleButton(context).apply {
                            textOn = title; textOff = title; isChecked = actual == value
                            isEnabled = active
                            alpha = if (active) 1f else 0.45f
                            setOnClickListener { change(setting, value) }
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = (4 * context.resources.displayMetrics.density).toInt()
                        })
                    }
                    container.addView(group)
                }
            }
        }
        label("پارک و فراخوانی خودرو (APA / RPA / HPP / HAVP)، کروز ACC و TLC: در کد بررسی‌شده، سوییچ یا مود مستقل با مقادیر تأییدشده پیدا نشد. مجوز این قابلیت‌ها همچنان همراه فعال‌سازی آپولو ارسال می‌شود؛ فرمان شروع عملیات به‌عنوان سوییچ ارائه نشده است.")
    }
}
