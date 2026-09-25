package app.luxctrl

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView

/** Both header rows reserve exactly the same icon and baseline space. */
class IndicatorMark(context: Context, private val scene: Boolean, private val color: Int) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    init { setBounds(0,0,(10*dp).toInt(),(10*dp).toInt()) }
    override fun draw(c: Canvas) {
        val x=bounds.exactCenterX(); val y=bounds.exactCenterY()
        paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeWidth=dp
        paint.color=Design.alpha(color,210)
        if(scene) {
            c.save(); c.rotate(45f,x,y)
            c.drawRect(x-2.6f*dp,y-2.6f*dp,x+2.6f*dp,y+2.6f*dp,paint)
            paint.style=Paint.Style.FILL; paint.color=Design.alpha(color,75)
            c.drawRect(x-1.3f*dp,y-1.3f*dp,x+1.3f*dp,y+1.3f*dp,paint)
            c.restore()
        } else {
            c.drawCircle(x,y,3.3f*dp,paint)
            paint.style=Paint.Style.FILL; paint.color=color
            c.drawCircle(x,y,1.4f*dp,paint)
        }
    }
}

/** A persistent console slot; only its message and fine lifetime rail appear transiently. */
class ActionNotice(context: Context) : TextView(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp=resources.displayMetrics.density
    private var deadline=0L
    private var lifetime=1L
    private var ticker: ValueAnimator?=null
    private var accent=Design.teal
    private val clear=Runnable { text=""; ticker?.cancel(); ticker=null; invalidate() }
    init {
        textSize=11f; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        includeFontPadding=false; gravity=Gravity.CENTER_VERTICAL
        setSingleLine(); ellipsize=TextUtils.TruncateAt.END
        setPadding((32*dp).toInt(),0,(12*dp).toInt(),0)
        setTextColor(Design.ink)
        accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun motionAllowed()=if(Build.VERSION.SDK_INT>=26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
    fun showMessage(value: String, expiresAt: Long, warning: Boolean=false) {
        removeCallbacks(clear); ticker?.cancel(); ticker=null
        deadline=expiresAt; lifetime=(deadline-SystemClock.uptimeMillis()).coerceAtLeast(1L)
        accent=if(warning) Design.amber else Design.teal
        text=if(deadline>SystemClock.uptimeMillis()) value else ""
        if(isAttachedToWindow) resumeMessage()
        invalidate()
    }
    private fun resumeMessage() {
        val remaining=deadline-SystemClock.uptimeMillis()
        if(remaining<=0) { clear.run(); return }
        removeCallbacks(clear); postDelayed(clear,remaining)
        ticker?.cancel(); ticker=null
        if(hasWindowFocus() && motionAllowed() && text.isNotEmpty()) {
            ticker=ValueAnimator.ofFloat(0f,1f).apply {
                duration=remaining; interpolator=LinearInterpolator()
                addUpdateListener { invalidate() }; start()
            }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); resumeMessage() }
    override fun onDetachedFromWindow() {
        removeCallbacks(clear); ticker?.cancel(); ticker=null
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(hasWindowFocus) resumeMessage() else { ticker?.cancel(); ticker=null }
    }
    override fun onDraw(c: Canvas) {
        val active=text.isNotEmpty()
        ink.shader=null; ink.style=Paint.Style.STROKE; ink.strokeWidth=dp*.6f
        ink.color=Design.alpha(Design.teal,if(active) 60 else 30)
        c.drawLine(0f,dp,width.toFloat(),dp,ink)
        if(active) {
            val remaining=(deadline-SystemClock.uptimeMillis()).coerceAtLeast(0)
            val fade=if(motionAllowed()) (remaining/450f).coerceIn(0f,1f) else 1f
            ink.style=Paint.Style.FILL; ink.color=Design.alpha(accent,(12*fade).toInt())
            c.drawRect(0f,dp,width.toFloat(),height.toFloat(),ink)
            ink.color=Design.alpha(accent,(210*fade).toInt())
            c.drawRect(0f,dp,2*dp,height-4*dp,ink)
            val progress=if(motionAllowed()) (remaining.toFloat()/lifetime).coerceIn(0f,1f) else 1f
            ink.color=Design.alpha(accent,(120*fade).toInt())
            c.drawRect(0f,height-dp,width*progress,height.toFloat(),ink)
            ink.style=Paint.Style.STROKE; ink.strokeWidth=dp
            ink.color=Design.alpha(accent,(225*fade).toInt())
            val x=16*dp; val y=height/2f
            c.drawCircle(x,y,6*dp,ink)
            if(accent==Design.amber) {
                c.drawLine(x,y-3*dp,x,y+dp,ink)
                c.drawPoint(x,y+3*dp,ink)
            } else {
                c.drawLine(x-2.5f*dp,y,x-dp,y+2*dp,ink)
                c.drawLine(x-dp,y+2*dp,x+3*dp,y-2.5f*dp,ink)
            }
            val color=Design.alpha(Design.ink,(255*fade).toInt())
            if(currentTextColor!=color) setTextColor(color)
        }
        super.onDraw(c)
    }
}
