package app.luxctrl

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.animation.LinearInterpolator
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.*

class LampWheel(context: Context) : View(context) {
    var lamps: List<Lamp> = emptyList()
        private set
    /** Scene changes glide between states; direct gestures (dimming a slice) snap. */
    fun setLamps(value: List<Lamp>, animate: Boolean) {
        if(lamps!=value || holdIndex>=0 || display!=value) dialDirty=true
        lamps = value
        // Once the request has settled, trust the bridge rather than the held gesture value.
        if (interactive) holdIndex = -1
        if (animate && motionAllowed()) animateTo(value) else { animator?.cancel(); display = value; invalidate() }
        updateDescription()
    }
    var ready = false
        set(value) { if (field != value) { field = value; dialDirty=true; invalidate() } }
    /** Touch input is off while a request is in flight, without flickering the hub. */
    var interactive = false
    var onPower: () -> Unit = {}
    var onLamp: (Lamp) -> Unit = {}
    var onBrightness: (Lamp,Int) -> Unit = { _,_ -> }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected = -1
    private var initialR = 0f
    private var startBrightness = 0
    private var preview = 0
    private var downX = 0f; private var downY = 0f
    private var dragged = false; private var powerDown = false
    // After a brightness swipe the released value stays on screen until the bridge confirms it,
    // so the slice never flickers back to the old value and forward again.
    private var holdIndex = -1
    private var holdBrightness = 0
    private var display: List<Lamp> = emptyList()
    private var animator: ValueAnimator? = null
    private val argb = ArgbEvaluator()
    private var orbit: ValueAnimator? = null
    private var phase = 0f
    private var lastFrame = 0L
    private var dialDirty = true
    private var dialTexture: Bitmap? = null
    private var textureCanvas: Canvas? = null
    private val sector = Path()
    private val arc = RectF()
    private var aura: Shader? = null
    private var hub: Shader? = null
    private val sectorShaders = mutableMapOf<Int, Pair<Int, Shader>>()
    private val labelFont = Typeface.create("sans-serif-medium",Typeface.NORMAL)

    private fun motionAllowed() = if(Build.VERSION.SDK_INT>=26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
    private fun syncOrbit() {
        val running=isAttachedToWindow && isShown && windowVisibility==VISIBLE && hasWindowFocus() && motionAllowed()
        if(running && orbit==null) {
            orbit=ValueAnimator.ofFloat(0f,1f).apply {
                duration=18000; repeatCount=ValueAnimator.INFINITE; interpolator=LinearInterpolator()
                addUpdateListener {
                    phase=it.animatedValue as Float
                    val now=SystemClock.uptimeMillis()
                    if(now-lastFrame>=32) { lastFrame=now; invalidate() }
                }
                start()
            }
        } else if(!running) { orbit?.cancel(); orbit=null }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncOrbit() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncOrbit() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView,visibility); syncOrbit() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); syncOrbit() }
    override fun onDetachedFromWindow() {
        orbit?.cancel(); orbit=null; animator?.cancel(); animator=null
        dialTexture?.recycle(); dialTexture=null; textureCanvas=null; dialDirty=true
        super.onDetachedFromWindow()
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        sectorShaders.clear()
        dialTexture?.recycle(); dialTexture=null; textureCanvas=null; dialDirty=true
        val r=outer.coerceAtLeast(1f)
        aura=RadialGradient(cx,cy,r*1.12f,intArrayOf(0x00206880,0x00206880,0x20206880,0x00206880),floatArrayOf(0f,.65f,.88f,1f),Shader.TileMode.CLAMP)
        hub=RadialGradient(cx-inner*.3f,cy-inner*.4f,(inner*1.6f).coerceAtLeast(1f),
            intArrayOf(0xff173347.toInt(),0xff090e21.toInt(),0xff15132d.toInt()),null,Shader.TileMode.CLAMP)
    }
    private fun sectorShader(index: Int,color: Int): Shader {
        val cached=sectorShaders[index]
        if(cached?.first==color) return cached.second
        return RadialGradient(cx,cy,outer,intArrayOf(Design.alpha(color,0),Design.alpha(color,12),Design.alpha(color,58)),
            floatArrayOf(0f,.36f,1f),Shader.TileMode.CLAMP).also { sectorShaders[index]=color to it }
    }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val cx get() = width/2f
    private val cy get() = height/2f
    private val outer get() = min(width,height)/2f - Design.dp(context,20f)
    private val inner get() = outer*.36f
    init { isClickable = true; isFocusable = true; updateDescription() }
    private fun updateDescription() {
        val names = lamps.joinToString(", ") { it.name }
        contentDescription = "${if (lamps.any { it.on }) "Turn all lights off" else "Turn all lights on"}. Drag a slice outward to brighten, inward to dim. Tap a slice for color. Lamps: $names"
    }
    private fun shown(): List<Lamp> = if (display.size == lamps.size) display else lamps

    /** Scene changes glide between the old and the new lamp states instead of snapping. */
    private fun animateTo(target: List<Lamp>) {
        animator?.cancel()
        val from = shown()
        if (from.size != target.size || from.isEmpty() || from == target || from.map { it.id } != target.map { it.id }) {
            display = target; invalidate(); return
        }
        animator = ValueAnimator.ofFloat(0f,1f).apply {
            duration = 450
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                display = from.mapIndexed { i, old ->
                    val next = target[i]
                    val b = (old.brightness + (next.brightness-old.brightness)*t).roundToInt()
                    val c = if (old.color == next.color) next.color else argb.evaluate(t,old.color,next.color) as Int
                    next.copy(on = b > 0, brightness = b, color = c)
                }
                dialDirty=true; invalidate()
            }
            start()
        }
    }

    /** Drop a held gesture value, e.g. when the change was never sent. */
    fun clearHold() { if (holdIndex >= 0) { holdIndex = -1; dialDirty=true; invalidate() } }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if(outer<=0) return
        // Only the moving accents redraw at idle. The expensive rings are one cached texture.
        // During a live gesture or a scene transition, draw directly to avoid repeated uploads.
        if(animator?.isRunning==true || (selected>=0 && dragged)) {
            drawDial(c); dialDirty=true
        } else {
            if(dialTexture==null) {
                dialTexture=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                textureCanvas=Canvas(dialTexture!!); dialDirty=true
            }
            if(dialDirty) {
                dialTexture?.eraseColor(Color.TRANSPARENT)
                textureCanvas?.let { drawDial(it) }; dialDirty=false
            }
            paint.shader=null; paint.color=Color.WHITE; paint.style=Paint.Style.FILL
            dialTexture?.let { c.drawBitmap(it,0f,0f,paint) }
        }
        drawMotion(c)
    }

    private fun drawDial(c: Canvas) {
        val r=outer
        val dp=resources.displayMetrics.density
        val list=shown()
        paint.shader=aura; paint.style=Paint.Style.FILL; paint.alpha=255
        c.drawCircle(cx,cy,r*1.12f,paint); paint.shader=null
        paint.strokeCap=Paint.Cap.BUTT
        // The fixed scale and moving outer traces sit inside the original dial footprint.
        for(tick in 0 until 120) {
            val a=tick*PI/60
            val a1=r+8*dp; val a2=a1+(if(tick%10==0) 5 else 2)*dp
            paint.color=if(tick%10==0) Design.alpha(Design.teal,160) else Design.line
            paint.strokeWidth=dp
            c.drawLine(cx+cos(a).toFloat()*a1,cy+sin(a).toFloat()*a1,cx+cos(a).toFloat()*a2,cy+sin(a).toFloat()*a2,paint)
        }
        paint.style=Paint.Style.STROKE; paint.color=Design.line; paint.strokeWidth=dp*.6f
        for(i in 0..5) {
            val rr=inner+(r-inner)*i/5
            c.drawCircle(cx,cy,rr,paint)
        }
        val count=list.size
        if(count>0) for((index,lamp) in list.withIndex()) {
            val step=360f/count; val gap=min(5f,step*.15f)
            val start=-90+index*step+gap/2; val sweep=step-gap
            val bright=when {
                selected==index && dragged -> preview
                index==holdIndex -> holdBrightness
                lamp.on -> lamp.brightness
                else -> 0
            }
            val fillTo=inner+(r-inner)*bright/254f
            val color=if(lamp.reachable) lamp.color else Design.muted
            if(bright>0) {
                // A radial shader puts volume behind the precision brightness rings.
                sector.reset(); arc.set(cx-fillTo,cy-fillTo,cx+fillTo,cy+fillTo)
                sector.arcTo(arc,start,sweep)
                arc.set(cx-inner,cy-inner,cx+inner,cy+inner); sector.arcTo(arc,start+sweep,-sweep); sector.close()
                paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=sectorShader(index,color)
                c.drawPath(sector,paint); paint.shader=null
            }
            val a=(index*step-90)*PI/180
            paint.style=Paint.Style.STROKE; paint.color=Design.line; paint.strokeWidth=dp*.7f
            c.drawLine(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,paint)
            val rows=max(10,((r-inner)/(6*dp)).toInt())
            for(j in 1..rows) {
                val rr=inner+(r-inner)*j/rows
                paint.strokeWidth=dp*1.3f
                paint.color=if(rr<=fillTo) Design.alpha(color,if(lamp.reachable) 150 else 55) else Design.alpha(Design.line,75)
                c.drawArc(cx-rr,cy-rr,cx+rr,cy+rr,start,sweep,false,paint)
            }
            if(bright>0) {
                // Layered strokes provide hardware-accelerated bloom without a blur bitmap.
                for(layer in 3 downTo 0) {
                    paint.strokeWidth=(2f+layer*3f)*dp
                    paint.color=Design.alpha(color,if(layer==0) 245 else 16)
                    c.drawArc(cx-fillTo,cy-fillTo,cx+fillTo,cy+fillTo,start,sweep,false,paint)
                }
                paint.strokeWidth=dp*.7f; paint.color=Design.mix(color,Color.WHITE,.55f)
                c.drawArc(cx-fillTo,cy-fillTo,cx+fillTo,cy+fillTo,start,sweep,false,paint)
            }
            val mid=(start+sweep/2)*PI/180
            paint.style=Paint.Style.FILL; paint.textAlign=Paint.Align.CENTER
            paint.textSize=9*resources.displayMetrics.scaledDensity; paint.typeface=labelFont
            paint.color=if(!lamp.reachable || bright==0) Design.muted else Design.mix(color,Design.ink,.45f)
            val label=if(!lamp.reachable) "—" else lamp.name
            val labelR=r+16*dp
            val tx=cx+cos(mid).toFloat()*labelR; val ty=cy+sin(mid).toFloat()*labelR+paint.textSize*.35f
            val half=paint.measureText(label)/2+2*dp
            c.drawText(label,tx.coerceIn(min(half,width/2f),max(width-half,width/2f)),
                ty.coerceIn(min(paint.textSize,height/2f),max(height-2*dp,height/2f)),paint)
        }
        // Machined power core: glass centre, counter-rotating arcs and a breathing neon glyph.
        val hr=inner-5*dp
        if(hr<=0) return
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; paint.shader=hub; c.drawCircle(cx,cy,hr,paint); paint.shader=null
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp
        paint.color=Design.alpha(Design.teal,if(ready) 100 else 35); c.drawCircle(cx,cy,hr,paint)
        paint.color=Design.alpha(Design.violet,95); c.drawCircle(cx,cy,hr-4*dp,paint)
    }

    private fun drawMotion(c: Canvas) {
        val r=outer
        val hr=inner-5*resources.displayMetrics.density
        if(hr<=0) return
        val dp=resources.displayMetrics.density
        paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.BUTT
        val orbitR=r+4*dp
        for(i in 0..2) {
            paint.color=Design.alpha(if(i==1) Design.violet else Design.teal,100)
            paint.strokeWidth=dp
            c.drawArc(cx-orbitR,cy-orbitR,cx+orbitR,cy+orbitR,phase*360+i*120,28f,false,paint)
        }
        for(i in 0..2) {
            paint.color=Design.alpha(if(i==1) Design.violet else Design.teal,if(ready) 210 else 65)
            paint.strokeWidth=dp*1.5f
            c.drawArc(cx-hr,cy-hr,cx+hr,cy+hr,-phase*360+i*120,40f,false,paint)
        }
        val pr=inner*.32f
        val powerColor=if(ready) Design.teal else Design.muted
        val pulse=if(orbit==null) 1f else .7f+.3f*sin(phase*PI*6).toFloat()
        paint.strokeCap=Paint.Cap.ROUND
        for(layer in 3 downTo 0) {
            paint.strokeWidth=(2.3f+layer*3)*dp
            paint.color=Design.alpha(powerColor,if(layer==0) 255 else (14*pulse).toInt())
            c.drawArc(cx-pr,cy-pr,cx+pr,cy+pr,-48f,276f,false,paint)
            c.drawLine(cx,cy-pr*1.17f,cx,cy,paint)
        }
        if(powerDown && isPressed) {
            paint.color=Design.alpha(Design.teal,45); paint.style=Paint.Style.FILL; c.drawCircle(cx,cy,hr,paint)
        }
        paint.strokeCap=Paint.Cap.BUTT
    }
    override fun performClick(): Boolean {
        super.performClick()
        if (interactive && lamps.isNotEmpty()) onPower()
        return true
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive || lamps.isEmpty()) return false
        val radius = hypot(e.x-cx,e.y-cy)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (radius > outer+Design.dp(context,14f)) return false
                powerDown = radius < inner
                isPressed=true; invalidate()
                selected = if (powerDown) -1 else WheelMath.slice(e.x-cx,e.y-cy,lamps.size)
                initialR = radius; downX=e.x; downY=e.y; dragged=false
                startBrightness = if (selected == holdIndex) holdBrightness
                    else lamps.getOrNull(selected)?.let { if (it.on) it.brightness else 0 } ?: 0
                preview = startBrightness
                holdIndex = -1
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.x-downX,e.y-downY) > slop) dragged=true
                if (selected >= 0 && dragged && lamps[selected].dimmable && lamps[selected].reachable) {
                    preview = WheelMath.brightness(startBrightness,radius-initialR,outer-inner)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (powerDown && !dragged && radius < inner) performClick()
                else lamps.getOrNull(selected)?.let {
                    if (dragged && it.dimmable && it.reachable && preview != startBrightness) {
                        holdIndex = selected; holdBrightness = preview
                        onBrightness(it,preview)
                    } else if (!dragged) onLamp(it)
                }
                selected=-1; isPressed=false; invalidate(); parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { selected=-1; isPressed=false; invalidate(); parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
}
