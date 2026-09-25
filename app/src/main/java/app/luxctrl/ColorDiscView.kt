package app.luxctrl

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.*

/** A hue ring surrounding a saturation/value disc, drawn locally without image assets. */
class ColorDiscView(context: Context) : View(context) {
    var onColorChanged: (Float, Float, Float) -> Unit = { _, _, _ -> }
    var hue = 0f; private set
    var saturation = 1f; private set
    var brightness = 1f; private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var ringShader: Shader? = null
    private var ringGlass: Shader? = null
    private var bitmap: Bitmap? = null
    private var tones = FloatArray(0)
    private var pixels = IntArray(0)
    private var bitmapHue = Float.NaN
    private val clip = Path()
    private val discBounds = RectF()
    private var pointer = MotionEvent.INVALID_POINTER_ID
    private var gesture = 0 // 1 = hue ring, 2 = inner disc; stays latched throughout a drag.
    private var focusedControl = 1
    private var accessibilityFocus = -1
    private var hoveredControl = -1
    private val cx get() = width/2f
    private val cy get() = height/2f
    private val radius get() = (min(width, height)/2f - Design.dp(context,14f)).coerceAtLeast(1f)
    private val discRadius get() = radius * .61f
    private val accessibility by lazy { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }

    init { isClickable = true; isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun setColor(h: Float, s: Float, v: Float) {
        hue = (h % 360 + 360) % 360
        saturation = s.coerceIn(0f,1f)
        brightness = v.coerceIn(0f,1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        ringShader = SweepGradient(cx, cy, IntArray(7) { Color.HSVToColor(floatArrayOf((360-it*60).toFloat(),1f,1f)) }, null)
        ringGlass=RadialGradient(cx,cy,radius,
            intArrayOf(0x00000000,0x00000000,0x60304a60,0x00ffffff,0x15ffffff,0x75ffffff),
            floatArrayOf(0f,.71f,.73f,.83f,.98f,1f),Shader.TileMode.CLAMP)
        // A bounded texture keeps ring dragging smooth even on older phones.
        val size = min(320, (discRadius*2).roundToInt()).coerceAtLeast(2)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        tones = FloatArray(size*size*2)
        pixels = IntArray(size*size)
        for (y in 0 until size) for (x in 0 until size) {
            val tone = ColorDiscMath.tone(x*2f/(size-1)-1, y*2f/(size-1)-1)
            val offset = (y*size+x)*2
            tones[offset] = tone.saturation; tones[offset+1] = tone.brightness
        }
        bitmapHue = Float.NaN
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.WHITE // Marker accents must not carry their alpha into the hue shader.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius*.28f
        paint.shader = ringShader
        canvas.drawCircle(cx,cy,radius*.86f,paint)
        // A narrow glass bevel gives the ring depth while its central color band stays exact.
        paint.shader=ringGlass; canvas.drawCircle(cx,cy,radius*.86f,paint)
        paint.shader = null
        // Concentric etched scales match the reactor dial without changing its hit regions.
        val dp=resources.displayMetrics.density
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp*.7f
        paint.color=Design.alpha(Design.teal,140)
        canvas.drawCircle(cx,cy,radius+3*dp,paint)
        paint.color=Design.alpha(Design.violet,150)
        canvas.drawCircle(cx,cy,discRadius+4*dp,paint)
        // Optical encoder marks live on the outer lip, away from the hue sampling band.
        for(tick in 0 until 120) {
            val a=tick*PI/60
            val r1=radius*(if(tick%10==0) .95f else .978f)
            paint.color=Design.alpha(Design.bg,if(tick%10==0) 170 else 105)
            canvas.drawLine(cx+cos(a).toFloat()*r1,cy+sin(a).toFloat()*r1,
                cx+cos(a).toFloat()*radius,cy+sin(a).toFloat()*radius,paint)
        }
        for(tick in 0 until 60) {
            val a=tick*PI/30
            val r1=radius+6*dp; val r2=r1+(if(tick%5==0) 4f else 1.5f)*dp
            paint.color=Design.alpha(Design.teal,if(tick%5==0) 160 else 65)
            canvas.drawLine(cx+cos(a).toFloat()*r1,cy+sin(a).toFloat()*r1,
                cx+cos(a).toFloat()*r2,cy+sin(a).toFloat()*r2,paint)
        }
        // Registration marks and a spectral inner scale turn the picker into an optical instrument.
        val scaleR=(discRadius+radius*.70f)/2
        paint.strokeWidth=dp
        for(tick in 0 until 72) {
            val a=tick*PI/36
            val end=scaleR+(if(tick%6==0) 3 else 1)*dp
            paint.color=Design.alpha(Design.ink,if(tick%6==0) 150 else 55)
            canvas.drawLine(cx+cos(a).toFloat()*scaleR,cy+sin(a).toFloat()*scaleR,
                cx+cos(a).toFloat()*end,cy+sin(a).toFloat()*end,paint)
        }
        bitmap?.let { b ->
            if (bitmapHue != hue) {
                val hsv = floatArrayOf(hue,1f,1f)
                for (i in pixels.indices) {
                    hsv[1] = tones[i*2]; hsv[2] = tones[i*2+1]
                    pixels[i] = Color.HSVToColor(hsv)
                }
                b.setPixels(pixels,0,b.width,0,0,b.width,b.height)
                bitmapHue = hue
            }
            canvas.save()
            clip.reset(); clip.addCircle(cx,cy,discRadius,Path.Direction.CW)
            canvas.clipPath(clip)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            discBounds.set(cx-discRadius,cy-discRadius,cx+discRadius,cy+discRadius)
            canvas.drawBitmap(b,null,discBounds,paint)
            canvas.restore()
        }
        val angle = -hue*PI/180
        marker(canvas, cx+cos(angle).toFloat()*radius*.86f, cy+sin(angle).toFloat()*radius*.86f,
            radius*.125f, Color.HSVToColor(floatArrayOf(hue,1f,1f)))
        val p = ColorDiscMath.point(saturation,brightness)
        marker(canvas,cx+p.x*discRadius,cy+p.y*discRadius,Design.dp(context,9f),Color.HSVToColor(floatArrayOf(hue,saturation,brightness)))
        if (isFocused || accessibilityFocus != -1) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = Design.dp(context,1f); paint.color = Design.teal
            val r = if ((if(accessibilityFocus != -1) accessibilityFocus else focusedControl) == 1) radius+Design.dp(context,5f) else discRadius+Design.dp(context,5f)
            canvas.drawCircle(cx,cy,r,paint)
        }
    }

    private fun marker(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.style = Paint.Style.STROKE; paint.color = 0x99000000.toInt(); paint.strokeWidth = Design.dp(context,4f)
        canvas.drawCircle(x,y,r,paint)
        paint.style = Paint.Style.FILL; paint.color = color; canvas.drawCircle(x,y,r,paint)
        paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = Design.dp(context,1.8f)
        canvas.drawCircle(x,y,r,paint)
        val dp=resources.displayMetrics.density
        paint.strokeWidth=dp; paint.color=Design.alpha(Color.WHITE,190)
        for(i in 0..3) {
            val a=i*PI/2
            canvas.drawLine(x+cos(a).toFloat()*(r+3*dp),y+sin(a).toFloat()*(r+3*dp),
                x+cos(a).toFloat()*(r+6*dp),y+sin(a).toFloat()*(r+6*dp),paint)
        }
    }

    private fun region(x: Float,y: Float): Int {
        val distance = hypot(x-cx,y-cy)
        return when {
            distance <= discRadius -> 2
            distance >= radius*.70f && distance <= radius+Design.dp(context,6f) -> 1
            else -> 0
        }
    }

    private fun update(x: Float,y: Float) {
        when(gesture) {
            1 -> if(hypot(x-cx,y-cy) > Design.dp(context,4f)) hue = ColorDiscMath.hue(x-cx,y-cy)
            2 -> ColorDiscMath.tone((x-cx)/discRadius,(y-cy)/discRadius).let { saturation=it.saturation; brightness=it.brightness }
        }
        changed()
    }
    private fun changed() {
        invalidate()
        onColorChanged(hue,saturation,brightness)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = region(event.x,event.y)
                if(gesture == 0) return false // The gap and outside space may still scroll the dialog.
                pointer = event.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x,event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(gesture == 0) return false
                val index = event.findPointerIndex(pointer)
                if(index >= 0) update(event.getX(index),event.getY(index))
                return true
            }
            MotionEvent.ACTION_UP -> {
                if(gesture == 0) return false
                val index = event.findPointerIndex(pointer)
                if(index >= 0) update(event.getX(index),event.getY(index))
                performClick(); endGesture(); return true
            }
            MotionEvent.ACTION_CANCEL -> { endGesture(); return true }
            MotionEvent.ACTION_POINTER_UP -> {
                if(event.getPointerId(event.actionIndex) == pointer) endGesture()
                return true
            }
        }
        return gesture != 0
    }
    private fun endGesture() {
        gesture=0; pointer=MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    // Three adjustable virtual controls keep the two-dimensional picker usable with TalkBack.
    private fun value(id: Int) = when(id) { 1 -> hue; 2 -> saturation*100; else -> brightness*100 }
    private fun title(id: Int) = when(id) { 1 -> "Hue"; 2 -> "Saturation"; else -> "Brightness" }
    private fun adjust(id: Int,v: Float) {
        when(id) { 1 -> hue=(v%360+360)%360; 2 -> saturation=(v/100).coerceIn(0f,1f); 3 -> brightness=(v/100).coerceIn(0f,1f) }
        changed(); accessibilityEvent(id,AccessibilityEvent.TYPE_VIEW_SELECTED)
    }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        when(keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { focusedControl=focusedControl%3+1; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> { adjust(focusedControl,value(focusedControl)+5); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN -> { adjust(focusedControl,value(focusedControl)-5); return true }
        }
        return super.onKeyDown(keyCode,event)
    }
    private fun accessibilityEvent(id: Int,type: Int) {
        if(!accessibility.isEnabled) return
        val event = AccessibilityEvent.obtain(type).apply {
            packageName=context.packageName; className="android.widget.SeekBar"
            setSource(this@ColorDiscView,id); contentDescription="${title(id)} ${value(id).roundToInt()}"
        }
        parent?.requestSendAccessibilityEvent(this,event)
    }
    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if(!accessibility.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        val hit = region(event.x,event.y)
        val id = if(event.actionMasked==MotionEvent.ACTION_HOVER_EXIT || hit==0) -1 else if(hit==1) 1 else if(event.x<cx) 2 else 3
        if(id != hoveredControl) {
            if(id != -1) accessibilityEvent(id,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            if(hoveredControl != -1) accessibilityEvent(hoveredControl,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            hoveredControl=id
        }
        return id != -1
    }
    private val nodes = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if(id == HOST_VIEW_ID) return AccessibilityNodeInfo.obtain(this@ColorDiscView).apply {
                onInitializeAccessibilityNodeInfo(this)
                for(child in 1..3) addChild(this@ColorDiscView,child)
            }
            if(id !in 1..3) return null
            return AccessibilityNodeInfo.obtain().apply {
                packageName=context.packageName; className="android.widget.SeekBar"
                setSource(this@ColorDiscView,id); setParent(this@ColorDiscView)
                contentDescription="${title(id)}, ${value(id).roundToInt()}${if(id==1) " degrees" else " percent"}"
                isEnabled=this@ColorDiscView.isEnabled; isFocusable=true; isVisibleToUser=isShown
                isAccessibilityFocused=accessibilityFocus==id
                rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0f,if(id==1)360f else 100f,value(id))
                val r = if(id==1) radius else discRadius
                val bounds=Rect((cx-r).toInt(),(cy-r).toInt(),(cx+r).toInt(),(cy+r).toInt())
                if(id==2) bounds.right=cx.toInt() else if(id==3) bounds.left=cx.toInt()
                setBoundsInParent(bounds)
                val location=IntArray(2); getLocationOnScreen(location); bounds.offset(location[0],location[1]); setBoundsInScreen(bounds)
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
                addAction(if(isAccessibilityFocused) AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS else AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            }
        }
        override fun performAction(id: Int,action: Int,args: Bundle?): Boolean {
            if(id !in 1..3) return super.performAction(id,action,args)
            when(action) {
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if(accessibilityFocus != -1) accessibilityEvent(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    accessibilityFocus=id; invalidate(); accessibilityEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> { accessibilityFocus=-1; invalidate(); accessibilityEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) }
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> adjust(id,value(id)+5)
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> adjust(id,value(id)-5)
                android.R.id.accessibilityActionSetProgress -> {
                    val v=args?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                    if(!v.isFinite()) return false
                    adjust(id,v)
                }
                else -> return false
            }
            return true
        }
        override fun findFocus(focus: Int): AccessibilityNodeInfo? =
            if(focus==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && accessibilityFocus != -1) createAccessibilityNodeInfo(accessibilityFocus) else null
    }
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = nodes
}
