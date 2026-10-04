package com.abrar.voyahiran.feature.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.*
import android.location.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

data class LauncherCompassState(val bearing: Float? = null,val altitude: Double? = null,val slope: Double? = null,val inclination: Double? = null)

/** Same sources as CCC: GNSS RMC heading/altitude, accelerometer + gyro attitude, CAN pressure. */
class LauncherCompass(context:Context, private val changed:(LauncherCompassState)->Unit):SensorEventListener,LocationListener,OnNmeaMessageListener {
    private val sensors=context.getSystemService(SensorManager::class.java)
    private val gps=context.getSystemService(LocationManager::class.java)
    private var running=false
    private var filter=LauncherAttitude()
    private val accel=FloatArray(3); private val gyro=FloatArray(3)
    private var haveAccel=false;private var haveGyro=false
    private var state=LauncherCompassState()
    private var lastEmit=0L
    private var lastFix=0L
    var speed:Int?=null
    @SuppressLint("MissingPermission") fun start() {
        if(running)return
        running=true;filter=LauncherAttitude();haveAccel=false;haveGyro=false
        for(t in listOf(Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE)) sensors.getDefaultSensor(t)?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL) }
        runCatching { gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0f,this,Looper.getMainLooper());gps.addNmeaListener(this,Handler(Looper.getMainLooper())) }
    }
    fun stop() { running=false;sensors.unregisterListener(this);runCatching {gps.removeUpdates(this);gps.removeNmeaListener(this)} }
    fun expire() {
        if(lastFix>0 && SystemClock.elapsedRealtime()-lastFix>15000) { state=state.copy(bearing=null,altitude=null);emit() }
    }
    private fun emit() { if(running)changed(state) }
    override fun onLocationChanged(location:Location) {
        lastFix=SystemClock.elapsedRealtime()
        state=state.copy(altitude=if(location.hasAltitude())location.altitude else null,
            bearing=if(location.hasBearing() && (speed ?: -1)>=4)location.bearing else state.bearing)
        emit()
    }
    override fun onNmeaMessage(message:String,timestamp:Long) {
        val p=message.split(',')
        if(p.size<9 || p[0] !in listOf("\$GNRMC","\$GPRMC")) return
        if(p[2]=="V") {state=state.copy(bearing=null,altitude=null);emit();return}
        if(p[2]!="A")return
        lastFix=SystemClock.elapsedRealtime()
        if((speed ?: -1)>=4) p[8].toFloatOrNull()?.takeIf {it in 0f..360f}?.let {state=state.copy(bearing=it%360);emit()}
    }
    override fun onSensorChanged(event:SensorEvent) {
        if(event.values.size<3)return
        when(event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {event.values.copyInto(accel,0,0,3);haveAccel=true}
            Sensor.TYPE_GYROSCOPE -> {event.values.copyInto(gyro,0,0,3);haveGyro=true}
        }
        if(!haveAccel || !haveGyro)return
        filter.calculateDipAngleSlope(gyro[0],gyro[1],gyro[2],accel[0],accel[1],accel[2])
        state=state.copy(slope=(-filter.mSlope).takeIf {it.isFinite()},inclination=filter.mDipAngle.takeIf {it.isFinite()})
        val now=SystemClock.elapsedRealtime()
        if(now-lastEmit>=100) {lastEmit=now;emit()}
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
    override fun onProviderDisabled(provider:String) {state=state.copy(bearing=null,altitude=null);emit()}
    override fun onProviderEnabled(provider:String) {}
    @Deprecated("Legacy GPS callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
}
