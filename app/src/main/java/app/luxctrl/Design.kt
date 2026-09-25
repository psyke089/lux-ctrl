package app.luxctrl

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView

object Design {
    val bg = Color.rgb(3,7,16)
    val panel = Color.rgb(9,18,32)
    val line = Color.rgb(38,65,84)
    val ink = Color.rgb(230,246,255)
    val muted = Color.rgb(142,164,190)
    val teal = Color.rgb(89,247,235)
    val violet = Color.rgb(163,118,255)
    val amber = Color.rgb(255,171,91)
    fun alpha(color: Int, value: Int) = (color and 0x00ffffff) or (value.coerceIn(0,255) shl 24)
    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a)+(Color.red(b)-Color.red(a))*t).toInt(),
        (Color.green(a)+(Color.green(b)-Color.green(a))*t).toInt(),
        (Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).toInt())
    fun dp(c: Context, n: Float) = n * c.resources.displayMetrics.density
    fun shape(c: Context, fill: Int = panel, stroke: Int = line, radius: Float = 14f) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(c,radius); setStroke(dp(c,1f).toInt(),stroke)
    }
    fun clickable(v: View, fill: Int = panel, stroke: Int = line, radius: Float = 14f, accent: Int = teal, matte: Boolean = false) {
        v.background = RippleDrawable(ColorStateList.valueOf(alpha(teal,55)),
            NeonPanel(v.context,fill,stroke,radius,accent,matte),shape(v.context,Color.WHITE,Color.WHITE,radius))
    }
    fun label(c: Context, value: String, size: Float = 12f, color: Int = muted, mono: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = if (mono) Typeface.create("monospace",Typeface.NORMAL) else Typeface.create("sans-serif",Typeface.NORMAL)
        if (mono) letterSpacing = .08f
        includeFontPadding = false
    }
}

class GlyphView @JvmOverloads constructor(context: Context, private val glyph: String = "settings", private val bordered: Boolean = true) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        isClickable = true; isFocusable = true
        // Borderless glyphs stay bare at rest and only show a soft ripple when touched.
        if (bordered) Design.clickable(this,Design.bg,Design.line,14f,matte=true)
        else background = RippleDrawable(ColorStateList.valueOf(0x3052e0d1),null,null)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = Design.dp(context,10f); val cx = width/2f; val cy = height/2f
        p.color = Design.teal; p.strokeWidth = Design.dp(context,1.5f); p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        if (glyph == "settings") {
            for (i in -1..1) {
                val y = cy + i*s*.65f; val x = cx + (if (i == 0) -.4f else .4f)*s
                canvas.drawLine(cx-s,y,cx+s,y,p)
                p.style = Paint.Style.FILL; p.color = Design.bg; canvas.drawCircle(x,y,s*.25f,p)
                p.style = Paint.Style.STROKE; p.color = Design.teal; canvas.drawCircle(x,y,s*.25f,p)
            }
        } else if (glyph == "close") {
            val e = s*.8f
            canvas.drawLine(cx-e,cy-e,cx+e,cy+e,p)
            canvas.drawLine(cx+e,cy-e,cx-e,cy+e,p)
        } else if (glyph == "save") {
            // Floppy disk: body, shutter and label.
            val r = s*.92f
            canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,s*.18f,s*.18f,p)
            canvas.drawLine(cx-s*.36f,cy-r,cx-s*.36f,cy-r*.28f,p)
            canvas.drawLine(cx+s*.36f,cy-r,cx+s*.36f,cy-r*.28f,p)
            canvas.drawLine(cx-s*.36f,cy-r*.28f,cx+s*.36f,cy-r*.28f,p)
            canvas.drawRoundRect(cx-s*.6f,cy+r*.08f,cx+s*.6f,cy+r*.8f,s*.12f,s*.12f,p)
        } else {
            canvas.drawArc(cx-s,cy-s,cx+s,cy+s,35f,285f,false,p)
            canvas.drawLine(cx+s*.76f,cy-s*.67f,cx+s*.8f,cy-s*1.15f,p)
            canvas.drawLine(cx+s*.76f,cy-s*.67f,cx+s*.22f,cy-s*.72f,p)
        }
    }
}
