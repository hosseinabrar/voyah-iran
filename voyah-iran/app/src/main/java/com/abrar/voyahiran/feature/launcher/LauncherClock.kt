package com.abrar.voyahiran.feature.launcher

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Shared date text; ICU formatters are reused and only run when the local day changes. */
object LauncherClock {
    private val persian=SimpleDateFormat("EEEE، d MMMM yyyy",ULocale("fa_IR@calendar=persian"))
    private val gregorian=SimpleDateFormat("d MMMM yyyy",ULocale.ENGLISH)
    private var zone:TimeZone?=null
    private var calendar=Calendar.getInstance()
    private var start=Long.MAX_VALUE
    private var end=Long.MIN_VALUE
    private var persianText=""
    private var gregorianText=""
    private fun update(time:Long) {
        val current=TimeZone.getDefault()
        if(zone?.id!=current.id || zone?.hasSameRules(current)!=true){
            zone=current
            calendar=Calendar.getInstance(current)
            val icu=android.icu.util.TimeZone.getTimeZone(current.id)
            persian.timeZone=icu;gregorian.timeZone=icu
            start=Long.MAX_VALUE;end=Long.MIN_VALUE
        }
        if(time>=start && time<end)return
        calendar.timeInMillis=time
        calendar.set(Calendar.HOUR_OF_DAY,0);calendar.set(Calendar.MINUTE,0)
        calendar.set(Calendar.SECOND,0);calendar.set(Calendar.MILLISECOND,0)
        start=calendar.timeInMillis
        calendar.add(Calendar.DATE,1);end=calendar.timeInMillis
        val date=Date(time)
        persianText=persian.format(date)
        gregorianText=gregorian.format(date).uppercase(java.util.Locale.ENGLISH)
    }
    @Synchronized fun persianDate(time:Long):String {update(time);return persianText}
    @Synchronized fun gregorianDate(time:Long):String {update(time);return gregorianText}
}
