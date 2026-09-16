package com.abrar.raha.feedback

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/** Luminous, audio-reactive halo. Shared unchanged by phone preview and vehicle. */
class RahaOrbView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var phase = FeedbackState.WAITING
    var level = 0f
    private var energy = 0f
    private var previousFrame = 0L
    private var rotation = 0f
    private var motion = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbon = Path()
    private val matrix = Matrix()
    private val cyan = context.getColor(R.color.raha_cyan)
    private val violet = context.getColor(R.color.raha_violet)
    private val green = context.getColor(R.color.raha_success)
    private val red = context.getColor(R.color.raha_failure)
    private var radius = 1f
    private lateinit var activeGradient: Shader
    private lateinit var successGradient: Shader
    private lateinit var failureGradient: Shader
    private lateinit var coreShade: Shader

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val x=w/2f; val y=h/2f
        radius=min(w,h)*.30f
        activeGradient=SweepGradient(x,y,intArrayOf(cyan,violet,cyan,violet,cyan),null)
        successGradient=SweepGradient(x,y,intArrayOf(green,0xFFADFFE1.toInt(),green),null)
        failureGradient=SweepGradient(x,y,intArrayOf(red,0xFFFFB3C2.toInt(),red),null)
        coreShade=RadialGradient(x,y,radius*1.3f,
            intArrayOf(0xEB070D20.toInt(),0xC9091026.toInt(),0x00091026),
            floatArrayOf(0f,.63f,1f),Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(width==0 || height==0)return
        val now=SystemClock.uptimeMillis()
        val dt=if(previousFrame==0L)1f/60 else ((now-previousFrame)/1000f).coerceIn(0f,.05f)
        previousFrame=now
        val target=if(phase==FeedbackState.LISTENING)sqrt(level.coerceIn(0f,1f)) else 0f
        energy+=(target-energy)*(1-exp(-dt/if(target>energy).045f else .18f))
        rotation=(rotation+dt*12f)%360f
        motion=(motion+dt*(.65f+energy*3.2f))%(2*PI.toFloat())
        val cx=width/2f; val cy=height/2f
        val r=radius*(1f+energy*.12f)
        val gradient=when(phase) {
            FeedbackState.SUCCESS->successGradient
            FeedbackState.FAILURE->failureGradient
            else->activeGradient
        }
        matrix.setRotate(rotation,cx,cy);gradient.setLocalMatrix(matrix)
        paint.style=Paint.Style.FILL;paint.shader=coreShade;paint.alpha=255
        canvas.drawCircle(cx,cy,radius*1.3f,paint)

        paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND
        paint.shader=gradient
        // Layered translucent strokes supply glow without software blur or bitmap allocations.
        for(layer in 0..6) {
            paint.strokeWidth=r*(.23f-layer*.03f)
            paint.alpha=12+layer*4
            canvas.drawCircle(cx,cy,r,paint)
        }
        // Idle ripple reduced by 20%; listening amplitude and base ring size are unchanged.
        // Keep the outermost stroke within the view at full microphone level.
        val rippleAmplitude = when (phase) {
            FeedbackState.WAITING -> .0712f
            FeedbackState.LISTENING -> .089f + energy * .344f
            else -> .04f
        }
        for(line in 0..8) {
            ribbon.reset()
            for(step in 0..160) {
                val a=step*2*PI.toFloat()/160
                val ripple=sin(a*3+motion+line*.36f)*.65f+
                    sin(a*7-motion*2+line*.19f)*.35f
                val rr=r*(1+(line-4)*.009f+ripple*rippleAmplitude)
                val x=cx+cos(a)*rr;val y=cy+sin(a)*rr
                if(step==0)ribbon.moveTo(x,y) else ribbon.lineTo(x,y)
            }
            ribbon.close()
            paint.strokeWidth=r*(if(line==4).026f else .012f)
            paint.alpha=if(line==4)240 else 65+line*12
            canvas.drawPath(ribbon,paint)
        }
        // Scale around the icon center using the same attack/release-smoothed audio energy.
        canvas.save()
        val microphoneScale=1f+energy*.45f
        canvas.scale(microphoneScale,microphoneScale,cx,cy)
        paint.shader=null
        paint.color=when(phase) { FeedbackState.SUCCESS->green;FeedbackState.FAILURE->red;else->cyan }
        paint.alpha=235;paint.style=Paint.Style.FILL
        canvas.drawRoundRect(cx-r*.105f,cy-r*.23f,cx+r*.105f,cy+r*.10f,r*.105f,r*.105f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=r*.027f
        canvas.drawArc(cx-r*.17f,cy-r*.12f,cx+r*.17f,cy+r*.20f,0f,180f,false,paint)
        canvas.drawLine(cx-r*.17f,cy-r*.12f,cx-r*.17f,cy+r*.04f,paint)
        canvas.drawLine(cx+r*.17f,cy-r*.12f,cx+r*.17f,cy+r*.04f,paint)
        canvas.drawLine(cx,cy+r*.20f,cx,cy+r*.31f,paint)
        canvas.restore()
        if(isAttachedToWindow && visibility==VISIBLE)postInvalidateOnAnimation()
    }
    override fun onDetachedFromWindow() {
        previousFrame=0
        super.onDetachedFromWindow()
    }
}