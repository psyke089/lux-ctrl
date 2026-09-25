package app.luxctrl

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
    /** Bridge state is authoritative; a power request only charges the hub until it settles. */
    fun setLamps(value: List<Lamp>, animate: Boolean) {
        val previous=shown()
        val arrival=onlineArrival && ready && value.isNotEmpty()
        val power=powerRequest != null && interactive
        if(lamps!=value || holdIndex>=0 || display!=value) dialDirty=true
        val changed=lamps!=value
        lamps=value
        if(interactive) holdIndex=-1
        if(arrival || power) {
            val up=if(arrival) true else powerRequest==true
            onlineArrival=false; powerRequest=null
            val from=if(arrival) value.map { it.copy(on=false,brightness=0) } else previous
            animateTo(from,value,true,up)
        } else if(changed || (animator==null && display!=value)) {
            if(animate) animateTo(previous,value) else finishTransition()
        }
        updateDescription(); invalidate()
    }
    var ready = false
        set(value) {
            if(field==value) return
            field=value; dialDirty=true
            if(value) onlineArrival=true
            else { onlineArrival=false; powerRequest=null; finishTransition() }
            invalidate()
        }
    private var onlineArrival=false
    private var powerRequest: Boolean?=null
    private var transitionProgress=1f
    private var powerTransition=false
    private var poweringUp=true
    fun preparePowerTransition(turnOn: Boolean) {
        powerRequest=turnOn
        invalidate()
    }
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
    private var shell: Shader? = null
    private var coreHalo: Shader? = null
    private var coreSpectrum: Shader? = null
    private val iris=Path()
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
        } else if(!running) { orbit?.cancel(); orbit=null; finishTransition() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncOrbit() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncOrbit() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView,visibility); syncOrbit() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); syncOrbit() }
    override fun onDetachedFromWindow() {
        orbit?.cancel(); orbit=null; finishTransition()
        dialTexture?.recycle(); dialTexture=null; textureCanvas=null; dialDirty=true
        super.onDetachedFromWindow()
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        sectorShaders.clear()
        dialTexture?.recycle(); dialTexture=null; textureCanvas=null; dialDirty=true
        val r=outer.coerceAtLeast(1f)
        coreHalo=RadialGradient(cx,cy,inner.coerceAtLeast(1f),
            intArrayOf(0x1038fff0,0x0838fff0,0x4038fff0,0x0038fff0),
            floatArrayOf(0f,.4f,.65f,1f),Shader.TileMode.CLAMP)
        coreSpectrum=SweepGradient(cx,cy,intArrayOf(Design.teal,Design.violet,Design.teal,Design.violet,Design.teal),null)
        aura=RadialGradient(cx,cy,r*1.12f,intArrayOf(0x00206880,0x00206880,0x20206880,0x00206880),floatArrayOf(0f,.65f,.88f,1f),Shader.TileMode.CLAMP)
        shell=RadialGradient(cx,cy,r*1.02f,
            intArrayOf(0xff020711.toInt(),0xff081725.toInt(),0xff050b17.toInt(),0xff193348.toInt()),
            floatArrayOf(0f,.4f,.94f,1f),Shader.TileMode.CLAMP)
    }
    private fun sectorShader(index: Int,color: Int): Shader {
        val cached=sectorShaders[index]
        if(cached?.first==color) return cached.second
        return RadialGradient(cx,cy,outer,intArrayOf(Design.alpha(color,0),Design.alpha(color,12),Design.alpha(color,62)),
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

    private fun finishTransition() {
        animator?.cancel(); animator=null
        display=lamps; powerTransition=false; transitionProgress=1f
        dialDirty=true; invalidate()
    }

    /** Longer radial ignition/quench for global power; scene changes retain their short glide. */
    private fun animateTo(from: List<Lamp>, target: List<Lamp>, power: Boolean=false, up: Boolean=true) {
        animator?.cancel(); animator=null
        if(!motionAllowed() || !isAttachedToWindow || !isShown || !hasWindowFocus() ||
            from.isEmpty() || from.map { it.id }!=target.map { it.id }) {
            finishTransition(); return
        }
        poweringUp=up; powerTransition=power; transitionProgress=0f
        display=from
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=if(power) 1350 else 450
            interpolator=LinearInterpolator()
            addUpdateListener { a ->
                val t=a.animatedValue as Float
                transitionProgress=t
                display=from.mapIndexed { i,old ->
                    val next=target[i]
                    // A short sequential offset starts at twelve o'clock, then fans around.
                    val delay=if(power) i.toFloat()/target.size*.18f else 0f
                    val local=((t-delay)/(1f-delay)).coerceIn(0f,1f)
                    val eased=local*local*(3f-2f*local)
                    val b=WheelMath.transitionBrightness(old.on,old.brightness,next.on,next.brightness,eased)
                    val color=if(old.color==next.color) next.color else argb.evaluate(eased,old.color,next.color) as Int
                    next.copy(on=b>0,brightness=b,color=color)
                }
                dialDirty=true; invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if(animator===animation) animator=null
                    display=target; powerTransition=false; transitionProgress=1f; dialDirty=true; invalidate()
                }
            })
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
        paint.shader=shell; paint.style=Paint.Style.FILL; paint.color=Color.WHITE
        c.drawCircle(cx,cy,r+2*dp,paint)
        paint.shader=aura; paint.style=Paint.Style.FILL; paint.alpha=255
        c.drawCircle(cx,cy,r*1.12f,paint); paint.shader=null
        paint.strokeCap=Paint.Cap.BUTT
        // A square targeting frame anchors the circular instrument in the console.
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp
        val frameR=min(r+17*dp,min(width,height)/2f-dp)
        for(sx in listOf(-1,1)) for(sy in listOf(-1,1)) {
            val x=cx+sx*frameR; val y=cy+sy*frameR
            paint.color=Design.alpha(Design.teal,85)
            c.drawLine(x,y,x-sx*13*dp,y,paint); c.drawLine(x,y,x,y-sy*13*dp,paint)
            paint.color=Design.alpha(Design.amber,160)
            c.drawLine(x-sx*3*dp,y-sy*3*dp,x-sx*6*dp,y-sy*3*dp,paint)
        }
        // The fixed scale and moving outer traces sit inside the original dial footprint.
        for(tick in 0 until 120) {
            val a=tick*PI/60
            val a1=r+8*dp; val a2=a1+(if(tick%10==0) 5 else 2)*dp
            paint.color=if(tick%10==0) Design.alpha(Design.teal,160) else Design.line
            paint.strokeWidth=dp
            c.drawLine(cx+cos(a).toFloat()*a1,cy+sin(a).toFloat()*a1,cx+cos(a).toFloat()*a2,cy+sin(a).toFloat()*a2,paint)
        }
        // Machined collar: dark slots, bright bevels and fine index marks.
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*4; paint.color=0xff07101d.toInt()
        c.drawCircle(cx,cy,r+3*dp,paint)
        for(i in 0 until 48) {
            paint.strokeWidth=dp*(if(i%4==0) 2f else .7f)
            paint.color=Design.alpha(if(i%12==0) Design.amber else Design.teal,if(i%4==0) 150 else 55)
            c.drawArc(cx-r-3*dp,cy-r-3*dp,cx+r+3*dp,cy+r+3*dp,i*7.5f,4.5f,false,paint)
        }
        paint.style=Paint.Style.STROKE; paint.color=Design.line; paint.strokeWidth=dp*.6f
        for(i in 0..3) {
            val rr=inner+(r-inner)*i/3
            paint.color=Design.alpha(Design.line,120)
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
            // Segmented energy cells keep brightness readable while adding depth to each sector.
            val rows=max(8,((r-inner)/(8*dp)).toInt())
            val columns=max(2,(sweep/8).toInt())
            val cellSweep=sweep/columns
            for(j in 1..rows) {
                val rr=inner+(r-inner)*j/rows
                for(k in 0 until columns) {
                    val lit=rr<=fillTo
                    val angle=start+k*cellSweep
                    val strength=if(lamp.reachable) 90+j*85/rows else 40
                    paint.strokeWidth=dp*3.3f
                    paint.color=if(lit) Design.alpha(color,strength) else Design.alpha(Design.line,65)
                    c.drawArc(cx-rr,cy-rr,cx+rr,cy+rr,angle,cellSweep-1.6f,false,paint)
                }
            }
            // Permanent color key at the rim, independent of the current brightness.
            paint.strokeWidth=dp*2
            paint.color=Design.alpha(color,if(bright>0) 200 else 45)
            c.drawArc(cx-r,cy-r,cx+r,cy+r,start,sweep,false,paint)
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
        // Matte ceramic core: a recessed face, precision ticks and an open annular track.
        val hr=inner-5*dp
        if(hr<=0) return
        paint.shader=null; paint.style=Paint.Style.FILL; paint.color=0xff08131e.toInt()
        c.drawCircle(cx,cy,hr,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp
        paint.color=Design.alpha(Design.teal,if(ready) 85 else 28)
        c.drawCircle(cx,cy,hr,paint)
        paint.color=Design.alpha(Design.line,140); paint.strokeWidth=dp*.6f
        c.drawCircle(cx,cy,hr*.62f,paint)
        for(i in 0 until 60) {
            val a=i*PI/30
            val r1=hr*(if(i%5==0) .88f else .91f); val r2=hr*.95f
            paint.color=Design.alpha(if(i%5==0) Design.teal else Design.line,if(ready) 120 else 45)
            c.drawLine(cx+cos(a).toFloat()*r1,cy+sin(a).toFloat()*r1,
                cx+cos(a).toFloat()*r2,cy+sin(a).toFloat()*r2,paint)
        }
    }

    private fun drawMotion(c: Canvas) {
        val r=outer
        val dp=resources.displayMetrics.density
        val hr=inner-5*dp
        if(hr<=0) return
        val moving=orbit!=null
        val list=shown()
        val powered=ready && list.any { it.on }
        val coreColor=if(!ready) Design.muted else if(powered) Design.teal else Design.violet
        paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.BUTT
        val orbitR=r+4*dp
        for(i in 0..2) {
            paint.color=Design.alpha(if(i==1) Design.violet else Design.teal,if(ready) 95 else 25)
            paint.strokeWidth=dp
            val start=phase*360+i*120
            c.drawArc(cx-orbitR,cy-orbitR,cx+orbitR,cy+orbitR,start,28f,false,paint)
        }
        // Only lamps at full output get a quiet traveling highlight and breathing rim.
        if(ready && list.isNotEmpty()) for((i,lamp) in list.withIndex()) {
            val brightness=when {
                selected==i && dragged -> preview
                holdIndex==i -> holdBrightness
                lamp.on -> lamp.brightness
                else -> 0
            }
            if(!lamp.reachable || brightness<254) continue
            val step=360f/list.size; val gap=min(5f,step*.15f)
            val start=-90+i*step+gap/2; val sweep=step-gap
            val cycle=if(moving) (phase*4+i*.13f)%1f else .5f
            val breath=if(moving) (.5f+.5f*sin(cycle*PI*2)).toFloat() else .5f
            paint.color=Design.alpha(lamp.color,(18+breath*24).toInt()); paint.strokeWidth=dp*5
            c.drawArc(cx-r+dp*4,cy-r+dp*4,cx+r-dp*4,cy+r-dp*4,start,sweep,false,paint)
            val length=min(9f,sweep*.18f)
            paint.color=Design.alpha(Design.mix(lamp.color,Design.ink,.45f),(sin(cycle*PI)*115).toInt())
            paint.strokeWidth=dp*1.2f
            c.drawArc(cx-r+dp*4,cy-r+dp*4,cx+r-dp*4,cy+r-dp*4,start+cycle*(sweep-length),length,false,paint)
        }
        if(powerTransition) drawPowerWave(c)
        // An illuminated iris surrounds the glyph: broad apertures, fine opposing tracks,
        // and a spectrum collar. All surfaces stay matte; light comes from the seams.
        val charge=powerRequest!=null && moving
        val rotation=phase*(if(charge) 2160 else 360)
        val breath=if(moving) .82f+.18f*sin(phase*PI*4).toFloat() else 1f
        paint.style=Paint.Style.FILL; paint.shader=coreHalo
        paint.color=Color.WHITE; paint.alpha=if(ready) (215*breath).toInt() else 30
        c.drawCircle(cx,cy,hr,paint); paint.shader=null
        c.save(); c.rotate(rotation*.5f,cx,cy)
        for(i in 0 until 8) {
            val angle=i*45f
            iris.reset()
            for(j in 0..3) {
                val radius=hr*(if(j==0 || j==3) .53f else .82f)
                val a=(angle+when(j) { 1 -> 22f; 2 -> 49f; 3 -> 27f; else -> 0f })*PI/180
                val x=cx+cos(a).toFloat()*radius
                val y=cy+sin(a).toFloat()*radius
                if(j==0) iris.moveTo(x,y) else iris.lineTo(x,y)
            }
            iris.close()
            val color=if(i%2==0) coreColor else Design.violet
            paint.style=Paint.Style.FILL; paint.color=Design.alpha(color,if(ready) 24 else 6)
            c.drawPath(iris,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.65f
            paint.color=Design.alpha(color,if(ready) 120 else 25); c.drawPath(iris,paint)
        }
        c.restore()
        paint.style=Paint.Style.STROKE
        c.save(); c.rotate(-rotation,cx,cy)
        paint.shader=coreSpectrum; paint.color=Color.WHITE
        for(i in 0 until 8) {
            val rr=hr*.84f
            for(layer in 1 downTo 0) {
                paint.strokeWidth=dp*(if(layer==0) 2.2f else 6f)
                paint.alpha=if(ready) (if(layer==0) 235 else 24) else (if(layer==0) 45 else 5)
                c.drawArc(cx-rr,cy-rr,cx+rr,cy+rr,i*45f,32f,false,paint)
            }
        }
        paint.shader=null; c.restore()
        val rr=hr*.54f
        for(i in 0..2) {
            paint.strokeWidth=dp; paint.color=Design.alpha(coreColor,if(ready) 210 else 40)
            c.drawArc(cx-rr,cy-rr,cx+rr,cy+rr,rotation*2f+i*120,75f,false,paint)
            val a=(rotation*2f+i*120)*PI/180
            paint.style=Paint.Style.FILL; paint.color=Design.alpha(Design.ink,if(ready) 235 else 55)
            c.drawCircle(cx+cos(a).toFloat()*rr,cy+sin(a).toFloat()*rr,dp*1.2f,paint)
            paint.style=Paint.Style.STROKE
        }
        // A quiet dark center keeps the luminous power mark legible at small sizes.
        paint.style=Paint.Style.FILL; paint.color=0xff07131f.toInt()
        c.drawCircle(cx,cy,hr*.46f,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.7f
        paint.color=Design.alpha(coreColor,if(ready) 90 else 30)
        c.drawCircle(cx,cy,hr*.46f,paint)
        val pr=hr*.32f
        paint.strokeCap=Paint.Cap.ROUND
        for(layer in 2 downTo 0) {
            paint.strokeWidth=dp*(if(layer==0) 2.7f else 3f+layer*3)
            paint.color=Design.alpha(coreColor,if(layer==0) (if(ready) 255 else 105) else (if(powered) (22*breath/layer).toInt() else 7))
            c.drawArc(cx-pr,cy-pr,cx+pr,cy+pr,-48f,276f,false,paint)
            c.drawLine(cx,cy-pr*1.2f,cx,cy-pr*.05f,paint)
        }
        if(powerDown && isPressed) {
            paint.color=Design.alpha(coreColor,28); paint.style=Paint.Style.FILL; c.drawCircle(cx,cy,hr,paint)
        }
        paint.strokeCap=Paint.Cap.BUTT
    }

    /** Three shock fronts expand on ignition and collapse on quench, with radial sparks. */
    private fun drawPowerWave(c: Canvas) {
        val dp=resources.displayMetrics.density
        val t=transitionProgress
        paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.BUTT
        for(front in 0..2) {
            val local=((t-front*.09f)/.82f).coerceIn(0f,1f)
            if(local<=0f || local>=1f) continue
            val travel=if(poweringUp) local else 1f-local
            val rr=inner*.72f+(outer-inner*.72f)*travel
            val fade=sin(local*PI).toFloat()*(1f-front*.23f)
            for(layer in 1 downTo 0) {
                paint.strokeWidth=dp*(if(layer==0) 1.2f else 7f)
                paint.color=Design.alpha(if(front==1) Design.violet else Design.teal,((if(layer==0) 185 else 20)*fade).toInt())
                c.drawCircle(cx,cy,rr,paint)
            }
            for(i in 0 until 24) {
                val a=(i*15f+(if(poweringUp) t else -t)*22)*PI/180
                val tail=(rr-(if(poweringUp) 1 else -1)*dp*12*fade).coerceIn(inner*.6f,outer)
                paint.strokeWidth=dp; paint.color=Design.alpha(Design.ink,(125*fade).toInt())
                c.drawLine(cx+cos(a).toFloat()*tail,cy+sin(a).toFloat()*tail,
                    cx+cos(a).toFloat()*rr,cy+sin(a).toFloat()*rr,paint)
            }
        }
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
