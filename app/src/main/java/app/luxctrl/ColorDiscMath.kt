package app.luxctrl

import kotlin.math.*

/** Elliptical square/disc mapping: white upper left, pure hue upper right, black below. */
object ColorDiscMath {
    data class Point(val x: Float, val y: Float)
    data class Tone(val saturation: Float, val brightness: Float)

    fun hue(x: Float, y: Float): Float =
        ((-atan2(y.toDouble(), x.toDouble()) * 180 / PI + 360) % 360).toFloat()

    fun point(saturation: Float, brightness: Float): Point {
        val u = saturation.coerceIn(0f, 1f) * 2 - 1
        val v = 1 - brightness.coerceIn(0f, 1f) * 2
        return Point(u * sqrt(1 - v*v/2), v * sqrt(1 - u*u/2))
    }

    fun tone(x: Float, y: Float): Tone {
        val length = max(1.0, hypot(x.toDouble(), y.toDouble()))
        val dx = x / length; val dy = y / length
        val a = 2 + dx*dx - dy*dy
        val b = 2 - dx*dx + dy*dy
        val u = (sqrt(max(0.0, a + 2*sqrt(2.0)*dx)) - sqrt(max(0.0, a - 2*sqrt(2.0)*dx))) / 2
        val v = (sqrt(max(0.0, b + 2*sqrt(2.0)*dy)) - sqrt(max(0.0, b - 2*sqrt(2.0)*dy))) / 2
        return Tone(((u+1)/2).toFloat().coerceIn(0f,1f), ((1-v)/2).toFloat().coerceIn(0f,1f))
    }
}
