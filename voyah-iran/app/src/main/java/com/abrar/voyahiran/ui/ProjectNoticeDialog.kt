package com.abrar.voyahiran.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.abrar.voyahiran.R
import kotlin.math.abs

/** A tap dismisses the notice; dragging still allows reading on smaller displays. */
class ProjectNoticeDialog(context: Context) : Dialog(context, R.style.Theme_VoyahIran_Notice) {
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_project_notice)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
    }

    override fun onStart() {
        super.onStart()
        val metrics = context.resources.displayMetrics
        window?.setLayout(
            context.resources.getFraction(R.fraction.notice_width, metrics.widthPixels, metrics.widthPixels).toInt(),
            context.resources.getFraction(R.fraction.notice_height, metrics.heightPixels, metrics.heightPixels).toInt())
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragged = false }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) dragged = true
            MotionEvent.ACTION_UP -> if (!dragged) { dismiss(); return true }
        }
        return super.dispatchTouchEvent(event)
    }
}
