package app.luxctrl

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

class BridgeException(message: String) : IOException(message)

/** Only local IPv4 endpoints; no discovery service, DNS, cloud, redirects or third-party SDK. */
class HueApi(private val config: BridgeConfig) {
    init { require(config.validate() == null) { "Invalid bridge settings" } }

    internal fun request(method: String, path: String, body: JSONObject? = null): Any {
        val c = URL("http://${config.ip}/api/${config.key}/$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 4000; c.readTimeout = 5000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) throw BridgeException("Bridge returned HTTP ${c.responseCode}.")
            val result = c.inputStream.bufferedReader().use { JSONTokener(it.readText()).nextValue() }
            if (result is JSONArray) for (i in 0 until result.length()) {
                val error = result.optJSONObject(i)?.optJSONObject("error") ?: continue
                throw BridgeException(when(error.optInt("type")) {
                    1 -> "The bridge rejected this API key. Check it in Settings."
                    3 -> "This lamp or scene no longer exists. Refresh and try again."
                    201 -> "This light is unreachable. Check its wall switch."
                    901 -> "The bridge is busy. Please try again."
                    else -> "The bridge could not complete this change (error ${error.optInt("type")})."
                })
            }
            return result
        } finally { c.disconnect() }
    }
    private fun objectAt(path: String) = request("GET", path) as? JSONObject ?: throw BridgeException("Unexpected bridge response.")
    fun lights(): List<Lamp> {
        val all = objectAt("lights")
        return all.keys().asSequence().toList().sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })).map { id ->
            val light = all.getJSONObject(id); val s = light.getJSONObject("state")
            val ct = light.optJSONObject("capabilities")?.optJSONObject("control")?.optJSONObject("ct")
            Lamp(id,light.optString("name","Light $id"),s.optBoolean("on"),s.optInt("bri",254),color(s),s.optBoolean("reachable",true),s.has("xy") || s.has("hue"),s.has("ct"),s.has("bri"),ct?.optInt("min",153) ?: 153,ct?.optInt("max",500) ?: 500)
        }
    }
    fun scenes(): List<Scene> {
        val all = objectAt("scenes")
        return all.keys().asSequence().toList().map { id ->
            val s = objectAt("scenes/${segment(id)}")
            val states = s.optJSONObject("lightstates") ?: JSONObject()
            val ids = s.optJSONArray("lights") ?: JSONArray()
            Scene(id,s.optString("name","Untitled scene"),(0 until ids.length()).map { ids.getString(it) },
                states.keys().asSequence().toList().map { color(states.getJSONObject(it)) },s.optBoolean("locked"))
        }.sortedBy { it.name.lowercase() }
    }
    fun setLight(id: String, body: JSONObject) { request("PUT","lights/${segment(id)}/state",body) }
    fun power(on: Boolean) { request("PUT","groups/0/action",JSONObject().put("on",on)) }
    fun activate(id: String) { request("PUT","groups/0/action",JSONObject().put("scene",id)) }
    fun create(name: String, ids: List<String>): String {
        require(ids.isNotEmpty())
        val result = request("POST","scenes",JSONObject().put("name",name).put("lights",JSONArray(ids)).put("recycle",false)) as? JSONArray
        return result?.optJSONObject(0)?.optJSONObject("success")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: throw BridgeException("The bridge did not return a new scene ID.")
    }
    fun overwrite(id: String) { request("PUT","scenes/${segment(id)}",JSONObject().put("storelightstate",true)) }
    fun rename(id: String,name: String) { request("PUT","scenes/${segment(id)}",JSONObject().put("name",name)) }
    fun delete(id: String) { request("DELETE","scenes/${segment(id)}") }
    private fun segment(id: String): String = java.net.URLEncoder.encode(id,"UTF-8")
    companion object {
        private const val OFF_GREY = 0xff40505b.toInt()
        internal const val WARM_WHITE = 0xffefd5b0.toInt()

        /** Picks the authoritative colour from a Hue v1 state; see [color]. */
        internal fun pick(mode: String,xyColor: Int?,hsColor: Int?,ctColor: Int?): Int = when (mode) {
            "hs" -> hsColor ?: xyColor
            "ct" -> ctColor ?: xyColor
            "xy" -> xyColor ?: hsColor
            // Scene light states carry no colormode; keep the previous xy-first preference.
            else -> xyColor ?: hsColor ?: ctColor
        } ?: WARM_WHITE

        /**
         * Hue v1 names the authoritative fields in "colormode". A first-generation bridge keeps
         * serving a stale "xy" after a hue/sat write, so the mode has to win over any field order.
         */
        fun color(s: JSONObject): Int {
            if (s.has("on") && !s.optBoolean("on")) return OFF_GREY
            val xy = s.optJSONArray("xy")
            val xyColor = if (xy != null && xy.length() == 2) HueColor.fromXy(xy.optDouble(0),xy.optDouble(1)) else null
            val hsColor = if (s.has("hue")) HueColor.fromHsv(s.optInt("hue") * 360f / 65535,s.optInt("sat",254) / 254f) else null
            val ctColor = if (s.has("ct")) HueColor.fromMired(s.optInt("ct",300)) else null
            return pick(s.optString("colormode"),xyColor,hsColor,ctColor)
        }
    }
}
