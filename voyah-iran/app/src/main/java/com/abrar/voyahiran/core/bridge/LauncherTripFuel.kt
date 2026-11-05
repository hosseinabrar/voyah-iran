package com.abrar.voyahiran.core.bridge

/** Since-start fuel display: an initialized journey with the OEM empty average shows zero,
 * matching the cluster before fuel use. Missing parcels and other sentinels remain unknown. */
internal object LauncherTripFuel {
    fun average(raw:Any?,distance:Any?,minutes:Any?):Double? {
        val fuel=(raw as? Number)?.toDouble() ?: return null
        if(fuel.isFinite() && fuel in 0.0..100.0)return fuel
        fun initialized(value:Any?)=(value as? Number)?.toDouble()?.let { it.isFinite() && it in 0.0..9999999.0 }==true
        return if(fuel == -9999.0 && initialized(distance) && initialized(minutes))0.0 else null
    }
}
