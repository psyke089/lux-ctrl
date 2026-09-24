package app.luxctrl

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView

object Design {
    val bg = Color.rgb(12,20,26)
    val panel = Color.rgb(17,29,37)
    val line = Color.rgb(37,57,68)
    val ink = Color.rgb(226,238,242)
    val muted = Color.rgb(136,160,173)
    val teal = Color.rgb(82,224,209)
    val amber = Color.rgb(238,175,113)
    fun dp(c: Context, n: Float) = n * c.resources.displayMetrics.density
    fun shape(c: Context, fill: Int = panel, stroke: Int = line, radius: Float = 14f) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(c,radius); setStroke(dp(c,1f).toInt(),stroke)
    }
    fun clickable(v: View, fill: Int = panel, stroke: Int = line, radius: Float = 14f) {
        v.background = RippleDrawable(ColorStateList.valueOf(0x3052e0d1),shape(v.context,fill,stroke,radius),null)
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
        if (bordered) Design.clickable(this,Design.bg,Design.line,14f)
        else background = RippleDrawable(ColorStateList.valueOf(0x3052e0d1),null,null)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = Design.dp(context,10f); val cx = width/2f; val cy = height/2f
        p.color = Design.ink; p.strokeWidth = Design.dp(context,1.5f); p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        if (glyph == "settings") {
            for (i in -1..1) {
                val y = cy + i*s*.65f; val x = cx + (if (i == 0) -.4f else .4f)*s
                canvas.drawLine(cx-s,y,cx+s,y,p)
                p.style = Paint.Style.FILL; p.color = Design.bg; canvas.drawCircle(x,y,s*.25f,p)
                p.style = Paint.Style.STROKE; p.color = Design.ink; canvas.drawCircle(x,y,s*.25f,p)
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
