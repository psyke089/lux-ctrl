package app.luxctrl

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.*

/** Procedural artwork only: no textures, downloads, extra views or layout space. */
abstract class ConsoleDrawable : Drawable() {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun setAlpha(alpha: Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter=colorFilter; invalidateSelf() }
    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Deep-space atmosphere with deterministic stars and quiet orbital chart lines. */
class ConsoleBackdrop(context: Context) : ConsoleDrawable() {
    private val dp = context.resources.displayMetrics.density
    private var cyan: Shader? = null
    private var purple: Shader? = null
    private val orbit=RectF()
    override fun onBoundsChange(b: Rect) {
        val w=b.width().toFloat(); val h=b.height().toFloat()
        if(w<=0 || h<=0) return
        cyan=RadialGradient(w*.05f,h*.3f,max(w,h)*.75f,intArrayOf(0xff103244.toInt(),Design.bg),null,Shader.TileMode.CLAMP)
        purple=RadialGradient(w*1.1f,h*.7f,max(w,h)*.6f,intArrayOf(0x452f1e67,0x00201550),null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=cyan; c.drawRect(bounds,paint)
        paint.shader=purple; c.drawRect(bounds,paint); paint.shader=null
        val w=bounds.width().toFloat(); val h=bounds.height().toFloat()
        // Fixed positions avoid twinkling or randomness on redraw.
        for(i in 0 until 95) {
            val x=((i*127+19)%997)/997f*w; val y=((i*263+71)%991)/991f*h
            paint.color=Design.alpha(if(i%4==0) Design.teal else Design.ink,28+i%5*12)
            c.drawCircle(x,y,dp*(if(i%9==0) .85f else .4f),paint)
            if(i%19==0) {
                paint.strokeWidth=dp*.5f
                c.drawLine(x-3*dp,y,x+3*dp,y,paint); c.drawLine(x,y-3*dp,x,y+3*dp,paint)
            }
        }
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.5f
        for(i in 0..3) {
            paint.color=Design.alpha(Design.teal,10+i*2)
            val r=w*(.8f+i*.22f)
            orbit.set(w*.7f-r,h*.28f-r,w*.7f+r,h*.28f+r)
            c.drawOval(orbit,paint)
        }
        // Sparse navigation grid; the content stays in the foreground.
        paint.color=0x0868b8cf
        val step=48*dp
        var x=0f
        while(x<w) { c.drawLine(x,0f,x,h,paint); x+=step }
        var y=0f
        while(y<h) { c.drawLine(0f,y,w,y,paint); y+=step }
        for(side in 0..1) {
            val rail=if(side==0) 7*dp else w-7*dp
            paint.color=Design.alpha(Design.teal,45); paint.strokeWidth=dp
            c.drawLine(rail,h*.18f,rail,h*.83f,paint)
            for(i in 0..13) {
                paint.color=Design.alpha(if(i<3) Design.amber else Design.teal,if(i<3) 170 else 65)
                val yy=h*.2f+i*5*dp
                c.drawLine(rail,yy,rail+(if(side==0) 3 else -3)*dp,yy,paint)
            }
        }
        paint.style=Paint.Style.FILL
    }
}

/** Dark alloy panels with inset glass and small illuminated hardware contacts. */
class NeonPanel(context: Context, private val fill: Int, private val edge: Int, radius: Float) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    private val cut=min(radius,9f)*dp
    private val path=Path()
    private var glass: Shader?=null
    private var border: Shader?=null
    private var focused=false
    override fun isStateful()=true
    override fun onStateChange(state: IntArray): Boolean {
        val next=state.contains(android.R.attr.state_focused) || state.contains(android.R.attr.state_pressed)
        if(next==focused) return false
        focused=next; invalidateSelf(); return true
    }
    override fun onBoundsChange(b: Rect) {
        val l=b.left+dp; val t=b.top+dp; val r=b.right-dp; val bottom=b.bottom-dp
        path.reset(); path.moveTo(l+cut,t); path.lineTo(r,t); path.lineTo(r,bottom-cut)
        path.lineTo(r-cut,bottom); path.lineTo(l,bottom); path.lineTo(l,t+cut); path.close()
        glass=LinearGradient(l,t,r,bottom,intArrayOf(Design.mix(fill,Design.teal,.055f),fill,Design.mix(fill,Color.BLACK,.4f)),null,Shader.TileMode.CLAMP)
        border=LinearGradient(l,t,r,bottom,intArrayOf(Design.mix(edge,Design.teal,.3f),Design.alpha(edge,100),Design.mix(edge,Design.violet,.3f)),null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=glass; c.drawPath(path,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp; paint.shader=border; c.drawPath(path,paint); paint.shader=null
        val active=edge==Design.teal || focused
        if(active) {
            paint.color=Design.alpha(Design.teal,25); paint.strokeWidth=5*dp; c.drawPath(path,paint)
            paint.color=Design.teal; paint.strokeWidth=dp; c.drawPath(path,paint)
        }
        val l=bounds.left+dp; val t=bounds.top+dp; val r=bounds.right-dp; val b=bounds.bottom-dp
        paint.color=Design.alpha(Design.teal,if(active) 255 else 160); paint.strokeWidth=dp*1.5f
        c.drawLine(l,t+cut+7*dp,l,t+cut,paint); c.drawLine(l,t+cut,l+cut,t,paint)
        paint.color=Design.alpha(if(active) Design.teal else Design.violet,180)
        c.drawLine(r-cut,b,r,b-cut,paint)
        paint.strokeWidth=dp*.5f; paint.color=0x19d5f9ff
        c.drawLine(l+cut+4*dp,t+3*dp,r-6*dp,t+3*dp,paint)
        paint.color=Design.alpha(if(active) Design.teal else Design.amber,if(active) 230 else 130)
        paint.strokeWidth=dp
        for(i in 0..2) c.drawLine(r-4*dp,t+(12+i*3)*dp,r-2*dp,t+(11+i*3)*dp,paint)
    }
}

/** A luminous topographic ribbon made from the scene's actual stored colors. */
class SpectrumDrawable(context: Context, colors: List<Int>) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    private val colors=colors.ifEmpty { listOf(Design.muted) }.let { if(it.size==1) it+it else it }.toIntArray()
    private val rect=RectF()
    private val wave=Path()
    private var spectrum: Shader?=null
    override fun onBoundsChange(b: Rect) {
        rect.set(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat())
        spectrum=LinearGradient(rect.left,0f,rect.right.coerceAtLeast(rect.left+1),0f,colors,null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        c.save(); c.clipRect(rect)
        paint.style=Paint.Style.FILL; paint.shader=spectrum; paint.alpha=24; c.drawRect(rect,paint)
        // Layered contour lines form a miniature light field, not a fabricated signal readout.
        for(layer in 0..6) {
            wave.reset()
            for(i in 0..48) {
                val t=i/48f
                val envelope=sin(t*PI).toFloat()
                val y=rect.bottom-rect.height()*(.1f+layer*.105f+envelope*(.18f*sin(t*PI*2+layer*.35f).toFloat()+.15f))
                val x=rect.left+t*rect.width()
                if(i==0) wave.moveTo(x,y) else wave.lineTo(x,y)
            }
            paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*(if(layer==3) 1.25f else .65f)
            paint.alpha=if(layer==3) 255 else 65+layer*20; c.drawPath(wave,paint)
        }
        paint.alpha=255; paint.shader=null; paint.color=0x426ba6b4; paint.strokeWidth=dp*.5f
        c.drawLine(rect.left,rect.bottom-dp,rect.right,rect.bottom-dp,paint)
        for(i in 0..12) {
            val x=rect.left+rect.width()*i/12
            c.drawLine(x,rect.bottom-2*dp,x,rect.bottom,paint)
        }
        c.restore()
    }
}
