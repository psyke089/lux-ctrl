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
    private var horizon: Shader? = null
    private val trajectory=Path()
    private val orbit=RectF()
    private var texture: Bitmap?=null
    private val texturePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onBoundsChange(b: Rect) {
        texture?.recycle(); texture=null
        val w=b.width().toFloat(); val h=b.height().toFloat()
        if(w<=0 || h<=0) return
        cyan=RadialGradient(w*.05f,h*.3f,max(w,h)*.75f,intArrayOf(0xff091d2d.toInt(),Design.bg),null,Shader.TileMode.CLAMP)
        purple=RadialGradient(w*1.1f,h*.7f,max(w,h)*.6f,intArrayOf(0x65301e68,0x00201550),null,Shader.TileMode.CLAMP)
        horizon=RadialGradient(w*.3f,h*.42f,max(w,h)*.5f,intArrayOf(0x173ecade,0x003ecade),null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        if(bounds.isEmpty) return
        if(texture==null) {
            // The atmospheric background is static. Bound its texture to 1280 px so the
            // render thread submits one image, not hundreds of star/chart paths, per frame.
            val scale=min(1f,1280f/max(bounds.width(),bounds.height()))
            val w=(bounds.width()*scale).roundToInt().coerceAtLeast(1)
            val h=(bounds.height()*scale).roundToInt().coerceAtLeast(1)
            texture=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { bitmap ->
                val cached=Canvas(bitmap)
                cached.scale(w.toFloat()/bounds.width(),h.toFloat()/bounds.height())
                cached.translate(-bounds.left.toFloat(),-bounds.top.toFloat())
                drawArtwork(cached)
            }
        }
        texture?.let { c.drawBitmap(it,null,bounds,texturePaint) }
    }
    private fun drawArtwork(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=cyan; c.drawRect(bounds,paint)
        paint.shader=purple; c.drawRect(bounds,paint)
        paint.shader=horizon; c.drawRect(bounds,paint); paint.shader=null
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
        // Long curved trajectories give the star chart a sense of depth.
        for(i in 0..8) {
            trajectory.reset(); trajectory.moveTo(-w*.15f,h*(.18f+i*.035f))
            trajectory.cubicTo(w*.18f,h*(.42f+i*.025f),w*.6f,h*(.03f+i*.06f),w*1.12f,h*(.56f+i*.025f))
            paint.color=Design.alpha(if(i%3==0) Design.teal else Design.violet,if(i%3==0) 13 else 7)
            paint.strokeWidth=dp*.65f; c.drawPath(trajectory,paint)
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
class NeonPanel(context: Context, private val fill: Int, private val edge: Int, radius: Float, private val accent: Int = Design.teal) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    private val cut=min(radius,9f)*dp
    private val path=Path()
    private var glass: Shader?=null
    private var border: Shader?=null
    private var aura: Shader?=null
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
        glass=LinearGradient(l,t,r,bottom,intArrayOf(Design.mix(fill,accent,.08f),fill,Design.mix(fill,Color.BLACK,.4f)),null,Shader.TileMode.CLAMP)
        aura=RadialGradient(r,t,(b.width()*.8f).coerceAtLeast(1f),intArrayOf(Design.alpha(accent,25),Design.alpha(accent,0)),null,Shader.TileMode.CLAMP)
        border=LinearGradient(l,t,r,bottom,intArrayOf(Design.mix(edge,Design.teal,.3f),Design.alpha(edge,100),Design.mix(edge,Design.violet,.3f)),null,Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=glass; c.drawPath(path,paint)
        paint.shader=aura; c.drawPath(path,paint)
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

/** Miniature orbital vistas; all light colors come from the scene's stored palette. */
class SpectrumDrawable(context: Context, colors: List<Int>, private val seed: Int = 0) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    private val palette=colors.ifEmpty { listOf(Design.muted) }
    private val colors=palette.let { if(it.size==1) it+it else it }.toIntArray()
    private val rect=RectF()
    private val orbital=RectF()
    private val contour=Path()
    private var spectrum: Shader?=null
    private var atmosphere: Shader?=null
    private var planet: Shader?=null
    private var px=0f; private var py=0f; private var pr=0f
    private val variant=(seed.toLong() and 0x7fffffff).toInt()
    override fun onBoundsChange(b: Rect) {
        rect.set(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat())
        val w=rect.width().coerceAtLeast(1f); val h=rect.height().coerceAtLeast(1f)
        spectrum=LinearGradient(rect.left,rect.top,rect.right.coerceAtLeast(rect.left+1),rect.bottom,colors,null,Shader.TileMode.CLAMP)
        px=rect.left+w*(.23f+(variant%3)*.08f); py=rect.top+h*.46f; pr=h*.35f
        atmosphere=RadialGradient(px,py,(pr*2.4f).coerceAtLeast(1f),
            intArrayOf(Design.alpha(colors[0],125),Design.alpha(colors[0],24),Design.alpha(colors[0],0)),null,Shader.TileMode.CLAMP)
        planet=RadialGradient(px-pr*.45f,py-pr*.65f,(pr*1.9f).coerceAtLeast(1f),
            intArrayOf(Design.mix(colors[0],Color.WHITE,.5f),Design.mix(colors[0],Design.bg,.2f),Design.bg),
            floatArrayOf(0f,.38f,1f),Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        if(rect.width()<=0 || rect.height()<=0) return
        c.save(); c.clipRect(rect)
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=spectrum; paint.alpha=16; c.drawRect(rect,paint)
        paint.alpha=255; paint.shader=atmosphere; c.drawRect(rect,paint); paint.shader=null
        // Fine distant stars make the previews distinct without inventing scene data.
        for(i in 0..17) {
            val x=rect.left+((i*67L+variant)%193)/193f*rect.width()
            val y=rect.top+((i*43L+variant/7)%97)/97f*rect.height()
            paint.color=Design.alpha(Design.ink,55+i%4*30); c.drawCircle(x,y,dp*(if(i%7==0) .7f else .35f),paint)
        }
        // A perspective light lattice recedes into the horizon behind the planet.
        paint.shader=spectrum; paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.45f
        for(i in 0..5) {
            val t=i/5f
            val y=rect.top+rect.height()*(.55f+t*t*.45f)
            paint.alpha=35+i*13; c.drawLine(rect.left,y,rect.right,y,paint)
        }
        for(i in -3..9) {
            paint.alpha=60
            c.drawLine(rect.left+rect.width()*.62f,rect.top+rect.height()*.5f,rect.left+i*rect.width()/6,rect.bottom,paint)
        }
        // Colored horizon ribbons and bloom, cached by the Android drawable display list.
        contour.reset()
        for(i in 0..48) {
            val t=i/48f
            val x=rect.left+t*rect.width()
            val y=rect.top+rect.height()*(.57f+.12f*sin(t*PI*2+variant%5).toFloat())
            if(i==0) contour.moveTo(x,y) else contour.lineTo(x,y)
        }
        for(layer in 3 downTo 0) {
            paint.strokeWidth=(.7f+layer*2.5f)*dp; paint.alpha=if(layer==0) 230 else 16
            c.drawPath(contour,paint)
        }
        paint.alpha=255; paint.shader=planet; paint.style=Paint.Style.FILL; c.drawCircle(px,py,pr,paint); paint.shader=null
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.65f; paint.color=Design.alpha(Design.ink,190)
        c.drawArc(px-pr,py-pr,px+pr,py+pr,195f,115f,false,paint)
        c.save(); c.rotate(-22f-(variant%3)*8,px,py)
        orbital.set(px-pr*1.7f,py-pr*.36f,px+pr*1.7f,py+pr*.36f)
        for(layer in 2 downTo 0) {
            paint.strokeWidth=(.7f+layer*2)*dp
            paint.color=Design.alpha(colors[colors.lastIndex],if(layer==0) 220 else 22)
            c.drawArc(orbital,0f,180f,false,paint)
        }
        c.restore()
        paint.style=Paint.Style.FILL; paint.color=Design.alpha(Design.ink,180)
        c.drawCircle(px-pr*.4f,py-pr*.72f,dp*.7f,paint)
        // Short palette samples remain legible even for nearly monochrome scenes.
        val cell=rect.width()*.25f/colors.size.coerceAtMost(6)
        for(i in 0 until colors.size.coerceAtMost(6)) {
            paint.color=colors[i]
            val x=rect.right-rect.width()*.25f+i*cell
            c.drawRect(x,rect.top+dp,x+cell-dp,rect.top+2*dp,paint)
        }
        paint.color=0x526ba6b4; paint.strokeWidth=dp*.5f
        c.drawLine(rect.left,rect.bottom-dp,rect.right,rect.bottom-dp,paint)
        c.restore()
    }
}

/** Etched section rails occupy the existing heading bounds. */
class SectionRail(context: Context) : ConsoleDrawable() {
    private val dp=context.resources.displayMetrics.density
    override fun draw(c: Canvas) {
        val y=bounds.bottom-dp
        paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.5f
        paint.color=Design.alpha(Design.teal,40); c.drawLine(bounds.left.toFloat(),y,bounds.right.toFloat(),y,paint)
        paint.color=Design.amber; paint.strokeWidth=dp*1.5f
        c.drawLine(bounds.left.toFloat(),y,bounds.left+13*dp,y,paint)
        paint.color=Design.alpha(Design.teal,170)
        c.drawLine(bounds.right-26*dp,y,bounds.right-8*dp,y,paint)
    }
}
