package app.luxctrl

import kotlin.math.*

data class Lamp(
    val id: String, val name: String, val on: Boolean, val brightness: Int,
    val color: Int, val reachable: Boolean, val colorCapable: Boolean, val temperatureCapable: Boolean,
    val dimmable: Boolean = true, val minMired: Int = 153, val maxMired: Int = 500
)
data class Scene(val id: String, val name: String, val lights: List<String>, val colors: List<Int>, val locked: Boolean)
data class BridgeState(val lamps: List<Lamp> = emptyList(), val scenes: List<Scene> = emptyList())
data class BridgeConfig(val ip: String, val key: String) {
    fun validate(): String? = when {
        !isLocalIpv4(ip) -> "Enter a local IPv4 address, such as 192.168.1.20."
        !key.matches(Regex("[a-zA-Z0-9_-]{10,128}")) -> "Enter the API key supplied by your Hue bridge."
        else -> null
    }
    companion object {
        fun isLocalIpv4(value: String): Boolean {
            val p = value.split('.')
            if (p.size != 4 || p.any { it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } || (it.length > 1 && it[0] == '0') }) return false
            val n = p.map { it.toInt() }
            if (n.any { it !in 0..255 }) return false
            return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
        }
    }
}

/** Pure color math. Hue xy is CIE 1931, converted with the wide-RGB D65 matrix. */
object HueColor {
    fun rgb(r: Double, g: Double, b: Double): Int = (0xff shl 24) or
        ((r.coerceIn(0.0, 1.0) * 255).roundToInt() shl 16) or
        ((g.coerceIn(0.0, 1.0) * 255).roundToInt() shl 8) or (b.coerceIn(0.0, 1.0) * 255).roundToInt()
    fun fromXy(x: Double, y: Double): Int {
        if (!x.isFinite() || !y.isFinite() || y <= 0) return 0xffdce9ed.toInt()
        val xx = x / y; val z = (1 - x - y) / y
        var r = max(0.0, 1.656492 * xx - 0.354851 - 0.255038 * z)
        var g = max(0.0, -0.707196 * xx + 1.655397 + 0.036152 * z)
        var b = max(0.0, 0.051713 * xx - 0.121364 + 1.011530 * z)
        val scale = max(1.0, max(r, max(g, b))); r /= scale; g /= scale; b /= scale
        fun gamma(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
        return rgb(gamma(r), gamma(g), gamma(b))
    }
    fun fromHsv(hue: Float, saturation: Float): Int {
        val h = ((hue % 360 + 360) % 360) / 60.0
        val s = saturation.toDouble().coerceIn(0.0, 1.0)
        val x = s * (1 - abs(h % 2 - 1)); val m = 1 - s
        val c = when (h.toInt()) { 0 -> doubleArrayOf(s,x,0.0); 1 -> doubleArrayOf(x,s,0.0); 2 -> doubleArrayOf(0.0,s,x); 3 -> doubleArrayOf(0.0,x,s); 4 -> doubleArrayOf(x,0.0,s); else -> doubleArrayOf(s,0.0,x) }
        return rgb(c[0]+m,c[1]+m,c[2]+m)
    }
    fun fromMired(mired: Int): Int {
        val t = 1_000_000.0 / mired.coerceAtLeast(1) / 100
        val r = if (t <= 66) 255.0 else 329.698727446 * (t-60).pow(-0.1332047592)
        val g = if (t <= 66) 99.4708025861 * ln(t)-161.1195681661 else 288.1221695283 * (t-60).pow(-0.0755148492)
        val b = if (t >= 66) 255.0 else if (t <= 19) 0.0 else 138.5177312231 * ln(t-10)-305.0447927307
        return rgb(r/255,g/255,b/255)
    }
}

object WheelMath {
    fun slice(x: Float, y: Float, count: Int): Int {
        if (count == 0) return -1
        val angle = ((atan2(y.toDouble(), x.toDouble()) * 180 / PI + 90 + 360) % 360)
        return (angle / (360.0 / count)).toInt().coerceIn(0,count-1)
    }
    fun brightness(start: Int, radialDelta: Float, band: Float): Int =
        (start + radialDelta / band.coerceAtLeast(1f) * 254).roundToInt().coerceIn(0,254)
}
