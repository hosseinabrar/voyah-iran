package com.abrar.voyahiran.feature.subscription

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import com.abrar.voyahiran.VoyahIranApplication
import kotlinx.coroutines.*

/** Masks presentation only. Saved preferences are never rewritten by entitlement changes. */
open class VipCheckBox @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    CheckBox(context, attrs) {
    private var requestedCheck = false
    private var requestedEnabled = true
    private var observation: CoroutineScope? = null
    protected open val allowed get() = (context.applicationContext as? VoyahIranApplication)?.subscriptions?.vipAllowed == true
    override fun setChecked(checked: Boolean) {
        requestedCheck = checked
        super.setChecked(checked && allowed)
    }
    override fun setEnabled(enabled: Boolean) {
        requestedEnabled = enabled
        super.setEnabled(enabled || !allowed)
    }
    override fun performClick(): Boolean {
        if (!allowed) { VipNotice.show(context); return true }
        return super.performClick()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val app = context.applicationContext as? VoyahIranApplication ?: return
        observation = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch { app.subscriptions.vipState.collect {
                refreshEntitlement()
            } }
        }
    }
    internal fun refreshEntitlement() {
        super.setChecked(requestedCheck && allowed)
        super.setEnabled(requestedEnabled || !allowed)
    }
    override fun onDetachedFromWindow() {
        observation?.cancel(); observation = null
        super.onDetachedFromWindow()
    }
}

object VipNotice {
    fun show(context: Context) {
        android.app.AlertDialog.Builder(context)
            .setMessage("این ویژگی فقط مختص کاربران با اشتراک وی‌آی‌پی هست. لطفا جهت تهیه اشتراک وی‌آی‌پی با اکانت زیر در تلگرام در ارتباط باشید:\n\n@hosseinabrar")
            .setPositiveButton("بستن", null)
            .setCancelable(true)
            .show().setCanceledOnTouchOutside(true)
    }
}
