package app.luxctrl

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Procedural artwork only: no textures, downloads, extra views or layout space. */
abstract class ConsoleDrawable : Drawable() {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun setAlpha(alpha: Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter=colorFilter; invalidateSelf() }
    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A quiet luminous atmosphere under a fine circuit grid. Cached when bounds change. */
class ConsoleBackdrop(context: Context) : ConsoleDrawable() {
    private val dp = context.resources.displayMetrics.density
    private var cyan: Shader? = null
    private var purple: Shader? = null
    override fun onBoundsChange(b: Rect) {
        val w=b.width().toFloat(); val h=b.height().toFloat()
        if(w<=0 || h<=0) return
        cyan=RadialGradient(w*.1f,h*.28f,w*.9f,intArrayOf(0xff102e40.toInt(),Design.bg),null,Shader.TileMode.CLAMP)
        purple=RadialGradient(w*.95f,h*.62f,w*.8f,intArrayOf(0x302c1970,0x002c1970),null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=cyan; c.drawRect(bounds,paint)
        paint.shader=purple; c.drawRect(bounds,paint); paint.shader=null
        paint.strokeWidth=dp*.5f; paint.color=0x0b73bfdc
        val step=24*dp
        var x=bounds.left.toFloat()
        while(x<bounds.right) { c.drawLine(x,bounds.top.toFloat(),x,bounds.bottom.toFloat(),paint); x+=step }
        var y=bounds.top.toFloat()
        while(y<bounds.bottom) { c.drawLine(bounds.left.toFloat(),y,bounds.right.toFloat(),y,paint); y+=step }
        // Edge rails frame the console without affecting its content bounds.
        paint.color=Design.alpha(Design.teal,60); paint.strokeWidth=dp
        val inset=8*dp
        c.drawLine(inset,bounds.height()*.18f,inset,bounds.height()*.40f,paint)
        paint.color=Design.alpha(Design.violet,70)
        c.drawLine(bounds.right-inset,bounds.height()*.64f,bounds.right-inset,bounds.height()*.86f,paint)
    }
}

/** Bevelled glass, a chamfered silhouette and illuminated corner contacts. */
class NeonPanel(context: Context, private val fill: Int, private val edge: Int, radius: Float) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    private val cut=min(radius,8f)*dp
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
        glass=LinearGradient(l,t,r,bottom,intArrayOf(Design.mix(fill,Design.violet,.09f),fill,Design.mix(fill,Color.BLACK,.25f)),null,Shader.TileMode.CLAMP)
        border=LinearGradient(l,t,r,bottom,intArrayOf(Design.mix(edge,Design.teal,.4f),edge,Design.mix(edge,Design.violet,.45f)),null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=glass; c.drawPath(path,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp; paint.shader=border; c.drawPath(path,paint); paint.shader=null
        if(focused) { paint.color=Design.teal; paint.strokeWidth=dp*1.5f; c.drawPath(path,paint) }
        val l=bounds.left+dp; val t=bounds.top+dp; val r=bounds.right-dp; val b=bounds.bottom-dp
        paint.color=Design.alpha(if(edge==Design.teal) Design.teal else Design.muted,if(edge==Design.teal) 230 else 110)
        paint.strokeWidth=dp*1.5f
        c.drawLine(l,t+cut+8*dp,l,t+cut,paint); c.drawLine(l,t+cut,l+cut,t,paint)
        c.drawLine(r-cut,b,r,b-cut,paint); c.drawLine(r,b-cut,r,b-cut-8*dp,paint)
        paint.strokeWidth=dp*.5f; paint.color=0x16ffffff
        c.drawLine(l+cut+4*dp,t+2*dp,r-5*dp,t+2*dp,paint)
    }
}

/** Scene palettes become backlit spectrum cartridges, retaining the actual bridge colors. */
class SpectrumDrawable(context: Context, colors: List<Int>) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    private val colors=(if(colors.size==1) colors+colors else colors).toIntArray()
    private val rect=RectF()
    private var spectrum: Shader?=null
    private var glass: Shader?=null
    override fun onBoundsChange(b: Rect) {
        rect.set(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat())
        spectrum=LinearGradient(rect.left,0f,rect.right.coerceAtLeast(rect.left+1),0f,colors,null,Shader.TileMode.CLAMP)
        glass=LinearGradient(0f,rect.top,0f,rect.bottom.coerceAtLeast(rect.top+1),
            intArrayOf(0x65ffffff,0x05ffffff,0x6600071b),floatArrayOf(0f,.36f,1f),Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=spectrum; c.drawRoundRect(rect,2*dp,2*dp,paint)
        paint.shader=glass; c.drawRoundRect(rect,2*dp,2*dp,paint); paint.shader=null
        c.save(); c.clipRect(rect)
        paint.color=0x30050918; paint.strokeWidth=dp*.6f
        var x=rect.left+3*dp
        while(x<rect.right) { c.drawLine(x,rect.top,x,rect.bottom,paint); x+=4*dp }
        paint.color=0x9affffff.toInt(); paint.strokeWidth=dp*.65f
        c.drawLine(rect.left+3*dp,rect.top+dp,rect.right-3*dp,rect.top+dp,paint)
        c.restore()
    }
}
