package com.abrar.raha.feedback

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/** Approved single-rim design. Shaders are cached; animation uses the display frame clock. */
class RahaOrbView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var phase = FeedbackState.WAITING
        set(value) {
            if (field == value) return
            val receiving = setOf(FeedbackState.WAITING, FeedbackState.LISTENING)
            if (field !in receiving || value !in receiving) {
                // Capture the actual visible frame, including an interrupted transition.
                val snapshot = if (width > 0 && height > 0) Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    draw(Canvas(it))
                } else null
                outgoing?.recycle(); outgoing = snapshot
                transitionStarted = SystemClock.uptimeMillis()
                started = transitionStarted
            }
            field=value; updateShaders(); invalidate()
        }
    private var outgoing: Bitmap? = null
    private var transitionStarted = 0L
    private val composite = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heights = FloatArray(7) { 8f }
    private val processingHeights = FloatArray(7) { 8f }
    private var visualPulse = .94f
    var light = false
        set(value) { if (field != value) { field=value; updateShaders(); invalidate() } }
    // Null keeps the initial waiting orb still until the READY cue is played.
    var heartbeatStartedAt: Long? = null
    var level = 0f
    private var energy = 0f
    private var previousFrame = 0L
    private var started = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Path()
    private val clip = Path().apply { addCircle(0f, 0f, 87f, Path.Direction.CW) }
    private val arc = RectF(-87f,-87f,87f,87f)
    private var accent = 0
    private lateinit var face: Shader
    private lateinit var glow: Shader
    private lateinit var inward: Shader
    private lateinit var bottom: Shader
    private lateinit var wave: Shader
    init { updateShaders() }
    private fun color(hex: String) = Color.parseColor(hex)
    private fun alpha(value: Int, a: Int) = (value and 0x00ffffff) or (a shl 24)
    private fun updateShaders() {
        val success=phase==FeedbackState.SUCCESS; val failure=phase==FeedbackState.FAILURE
        accent=color(if(success) { if(light) "#1ab58d" else "#72dec2" }
            else if(failure) { if(light) "#c84f52" else "#f79798" }
            else if(light) "#3983fa" else "#73caff")
        val tones=if(success) { if(light) arrayOf("#f1faf6","#e3f5ed","#d2efe3") else arrayOf("#173834","#174039","#20594d") }
            else if(failure) { if(light) arrayOf("#fff7f5","#fbe8e6","#f7dddb") else arrayOf("#352530","#422b36","#5c3540") }
            else if(light) arrayOf("#f8fbff","#f0f6ff","#e1edff") else arrayOf("#17263e","#182941","#243c60")
        face=LinearGradient(0f,-87f,0f,87f,tones.map(::color).toIntArray(),floatArrayOf(0f,.65f,1f),Shader.TileMode.CLAMP)
        glow=RadialGradient(0f,0f,121f,intArrayOf(alpha(accent,0),alpha(accent,if(light)64 else 96),alpha(accent,0)),floatArrayOf(0f,86f/121f,1f),Shader.TileMode.CLAMP)
        inward=RadialGradient(0f,0f,87f,intArrayOf(alpha(accent,0),alpha(accent,0),alpha(accent,12),alpha(accent,if(light)101 else 133)),floatArrayOf(0f,65f/87f,77.1f/87f,1f),Shader.TileMode.CLAMP)
        bottom=RadialGradient(0f,95f,110f,intArrayOf(alpha(accent,if(light)50 else 69),alpha(accent,0)),null,Shader.TileMode.CLAMP)
        wave=LinearGradient(-20f,-30f,20f,30f,(if(light) arrayOf("#1055ff","#358bff","#83d4ff") else arrayOf("#2997ee","#54baff","#9ce5ff")).map(::color).toIntArray(),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(width==0 || height==0 || phase==FeedbackState.HIDDEN)return
        val now=SystemClock.uptimeMillis()
        val mix=RahaMotion.ease((now-transitionStarted)/280f)
        val old=outgoing
        val layer=if(old!=null && mix<1f) {
            composite.alpha=((1f-mix)*255).toInt();canvas.drawBitmap(old,0f,0f,composite)
            canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(mix*255).toInt())
        } else { outgoing?.recycle();outgoing=null; -1 }
        val age=(now-started)/1000f
        val dt=if(previousFrame==0L)1f/60 else ((now-previousFrame)/1000f).coerceIn(0f,.05f)
        previousFrame=now
        val target=if(phase==FeedbackState.LISTENING)sqrt(level.coerceIn(0f,1f)) else 0f
        energy=target
        val beat=heartbeatStartedAt?.let { RahaMotion.heartbeat((now-it).coerceAtLeast(0L)/1000f) } ?: 0f
        val pulseTarget=if(phase==FeedbackState.WAITING).94f+.10f*beat else 1.04f
        visualPulse+=(pulseTarget-visualPulse)*(1-exp(-dt/.10f))
        val pulse=visualPulse
        val unit=min(width,height)/256f
        val shake=if(phase==FeedbackState.FAILURE)sin(age*26)*exp(-age*4)*7 else 0f
        canvas.save();canvas.translate(width/2f+shake*unit,height/2f);canvas.scale(unit*pulse,unit*pulse)
        paint.style=Paint.Style.FILL;paint.alpha=255;paint.shader=glow;canvas.drawCircle(0f,0f,121f,paint)
        paint.shader=face;canvas.drawCircle(0f,0f,87f,paint)
        canvas.save();canvas.clipPath(clip)
        paint.shader=bottom;canvas.drawRect(-88f,-88f,88f,88f,paint)
        paint.shader=inward;canvas.drawCircle(0f,0f,87f,paint);canvas.restore()
        paint.shader=null;paint.color=accent;paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
        paint.alpha=if(phase==FeedbackState.WAITING)((.72f+.23f*beat)*255).toInt() else 242
        canvas.drawCircle(0f,0f,87f,paint);paint.alpha=255
        paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        when(phase) {
            FeedbackState.PROCESSING -> {
                // Animated accent shares the single rim instead of adding another ring.
                paint.color=if(light)0xffa4dcff.toInt() else 0xffd0f2ff.toInt()
                for(i in 0 until 40) { paint.alpha=(255*(i+1)/40);canvas.drawArc(arc,age*342f+i*2.3f,2.5f,false,paint) }
                paint.alpha=255;paint.shader=wave
                if(age<.32f) {
                    val p=RahaMotion.ease(age/.32f)
                    for(i in 0..6) { val h=processingHeights[i]*(1-p)+10*p;val w=8+6*p
                        paint.style=Paint.Style.FILL;canvas.drawRoundRect((i-3)*13-w/2,-h/2,(i-3)*13+w/2,h/2,4f,4f,paint) }
                } else {
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=10f
                    val a=RahaMotion.lineStart(age);val b=RahaMotion.lineEnd(age)
                    canvas.drawLine(a,0f,max(a+.01f,b),0f,paint)
                }
            }
            FeedbackState.SUCCESS -> {
                paint.strokeWidth=9f;glyph.reset();glyph.moveTo(-25f,0f);glyph.lineTo(-7f,18f);glyph.lineTo(29f,-22f)
                val scale=.9f+.1f*(age*3).coerceAtMost(1f);canvas.save();canvas.scale(scale,scale);canvas.drawPath(glyph,paint);canvas.restore()
            }
            FeedbackState.FAILURE -> {
                paint.strokeWidth=9f;canvas.drawLine(0f,-25f,0f,9f,paint);paint.style=Paint.Style.FILL;canvas.drawCircle(0f,28f,5f,paint)
            }
            else -> {
                paint.style=Paint.Style.FILL;paint.shader=wave
                for(i in 0..6) {
                    val targetHeight=if(phase==FeedbackState.LISTENING)RahaMotion.barHeight(i,now/1000f,energy) else 8f
                    heights[i]=targetHeight
                    processingHeights[i]=heights[i]
                    val h=heights[i]
                    val x=(i-3)*13f;canvas.drawRoundRect(x-4,-h/2,x+4,h/2,4f,4f,paint) }
            }
        }
        paint.shader=null;canvas.restore()
        if(layer!=-1) canvas.restoreToCount(layer)
        if(isAttachedToWindow && isShown)postInvalidateOnAnimation()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView,visibility)
        if(visibility==VISIBLE) { previousFrame=0;invalidate() }
    }
    override fun onDetachedFromWindow() { outgoing?.recycle();outgoing=null;previousFrame=0;super.onDetachedFromWindow() }
}
