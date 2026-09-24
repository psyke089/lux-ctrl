package app.luxctrl

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.*

class LampWheel(context: Context) : View(context) {
    var lamps: List<Lamp> = emptyList()
        set(value) { field = value; invalidate(); updateDescription() }
    var ready = false
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
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val cx get() = width/2f
    private val cy get() = height/2f
    private val outer get() = min(width,height)/2f - Design.dp(context,25f)
    private val inner get() = outer*.355f
    init { isClickable = true; isFocusable = true; updateDescription() }
    private fun updateDescription() { contentDescription = "${if (lamps.any { it.on }) "Turn all lights off" else "Turn all lights on"}. Individual controls are below the dial." }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val r = outer
        if (r <= 0) return
        val dp = resources.displayMetrics.density
        paint.strokeCap = Paint.Cap.BUTT
        // Fine instrument ticks around the dial.
        for (tick in 0 until 120) {
            val a = tick * PI / 60
            val a1 = r + 10*dp; val a2 = a1 + (if (tick % 10 == 0) 5 else 2)*dp
            paint.color = if (tick % 10 == 0) 0xff56717e.toInt() else Design.line
            paint.strokeWidth = dp
            c.drawLine(cx+cos(a).toFloat()*a1,cy+sin(a).toFloat()*a1,cx+cos(a).toFloat()*a2,cy+sin(a).toFloat()*a2,paint)
        }
        paint.style = Paint.Style.STROKE; paint.color = Design.line; paint.strokeWidth = dp
        for (i in 0..5) {
            val rr = inner+(r-inner)*i/5
            c.drawCircle(cx,cy,rr,paint)
        }
        val count = lamps.size
        if (count > 0) for ((index,lamp) in lamps.withIndex()) {
            val step = 360f/count; val gap = min(5f, step*.15f)
            val start = -90+index*step+gap/2; val sweep = step-gap
            val bright = if (selected == index && dragged) preview else if (lamp.on) lamp.brightness else 0
            val fillTo = inner+(r-inner)*bright/254f
            val a = (index*step-90)*PI/180
            paint.style = Paint.Style.STROKE; paint.color = Design.line; paint.strokeWidth = dp
            c.drawLine(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,paint)
            val rows = max(10,((r-inner)/(5*dp)).toInt())
            for (j in 1..rows) {
                val rr = inner+(r-inner)*j/rows
                if (rr > fillTo) continue
                paint.strokeWidth = if (j == rows || rr+((r-inner)/rows) > fillTo) 2.7f*dp else 1.7f*dp
                paint.color = lamp.color; paint.alpha = if (!lamp.reachable) 60 else if (j == rows || rr+((r-inner)/rows) > fillTo) 255 else 170
                c.drawArc(cx-rr,cy-rr,cx+rr,cy+rr,start,sweep,false,paint)
            }
            paint.alpha = 255
            val mid = (start+sweep/2)*PI/180
            val labelR = if (bright == 0 || !lamp.reachable) inner+(r-inner)*.54f else r+21*dp
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 9*resources.displayMetrics.scaledDensity
            paint.typeface = Typeface.MONOSPACE; paint.color = if (!lamp.reachable || bright == 0) Design.muted else lamp.color
            val label = if (!lamp.reachable) "—" else if (selected == index && dragged) "${(bright/254f*100).roundToInt()}%" else if (bright == 0) "OFF" else (index+1).toString().padStart(2,'0')
            c.drawText(label,cx+cos(mid).toFloat()*labelR,cy+sin(mid).toFloat()*labelR+paint.textSize*.35f,paint)
        }
        // Power hub.
        paint.style = Paint.Style.FILL; paint.color = Design.panel; c.drawCircle(cx,cy,inner-5*dp,paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp; paint.color = if (lamps.any { it.on } && ready) 0xff396b71.toInt() else Design.line
        c.drawCircle(cx,cy,inner-5*dp,paint)
        paint.color = if (ready) Design.teal else Design.muted; paint.strokeWidth = 2.5f*dp; paint.strokeCap = Paint.Cap.ROUND
        val pr = inner*.32f; val py = cy
        c.drawArc(cx-pr,py-pr,cx+pr,py+pr,-48f,276f,false,paint)
        c.drawLine(cx,py-pr*1.17f,cx,py,paint)
        paint.strokeCap = Paint.Cap.BUTT
    }
    override fun performClick(): Boolean {
        super.performClick()
        if (ready && lamps.isNotEmpty()) onPower()
        return true
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!ready || lamps.isEmpty()) return false
        val radius = hypot(e.x-cx,e.y-cy)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (radius > outer+Design.dp(context,14f)) return false
                powerDown = radius < inner
                selected = if (powerDown) -1 else WheelMath.slice(e.x-cx,e.y-cy,lamps.size)
                initialR = radius; downX=e.x; downY=e.y; dragged=false
                startBrightness = lamps.getOrNull(selected)?.let { if (it.on) it.brightness else 0 } ?: 0
                preview = startBrightness
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
                    if (dragged && it.dimmable && it.reachable && preview != startBrightness) onBrightness(it,preview)
                    else if (!dragged) onLamp(it)
                }
                selected=-1; invalidate(); parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { selected=-1; invalidate(); parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
}
