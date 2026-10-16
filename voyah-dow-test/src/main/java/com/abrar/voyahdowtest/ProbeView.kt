package com.abrar.voyahdowtest
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

class ProbeView(context: Context) : LinearLayout(context) {
    val target = EditText(context).apply { setText("3"); inputType = 2; textSize = 24f; setSingleLine() }
    val apply = Button(context).apply { text = "Apply value" }
    val export = Button(context).apply { text = "Save log" }
    val share = Button(context).apply { text = "Share log" }
    val status = TextView(context).apply { textSize = 22f; text = "Waiting for CAN…"; setTextColor(Color.WHITE) }
    val warning = TextView(context).apply {
        text = "DOW MATCH\nExperimental — not a confirmed danger signal"
        gravity = Gravity.CENTER; textSize = 28f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(220, 25, 35)); alpha = .12f
    }
    val logs = TextView(context).apply { textSize = 13f; typeface = Typeface.MONOSPACE; setTextColor(0xffb6c7d9.toInt()) }
    init {
        orientation = VERTICAL; setPadding(24, 12, 24, 12); setBackgroundColor(0xff101923.toInt())
        addView(TextView(context).apply { text = "VOYAH · DOW read-only probe"; textSize = 26f; setTextColor(Color.WHITE) })
        addView(TextView(context).apply {
            text = "Test 3, 2, 1 first. 4 = OEM fault. Match is NOT proof of danger. No vehicle writes."
            textSize = 16f; setTextColor(0xffffcb76.toInt())
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply { text = "Target state: "; textSize = 20f }, LayoutParams(160, 64))
            addView(target, LayoutParams(100, 64))
            addView(apply, LayoutParams(170, 64)); addView(export, LayoutParams(170, 64)); addView(share, LayoutParams(170, 64))
        })
        addView(status, LayoutParams(-1, 72))
        addView(warning, LayoutParams(-1, 0, 1f))
        addView(ScrollView(context).apply { addView(logs) }, LayoutParams(-1, 130))
    }
}
