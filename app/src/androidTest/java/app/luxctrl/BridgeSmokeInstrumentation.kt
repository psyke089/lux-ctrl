package app.luxctrl

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONObject

/** Opt-in hardware integration test; creates only its own scene and restores every lamp. */
class BridgeSmokeInstrumentation : Instrumentation() {
    private var live = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments); live=arguments?.getString("live")=="true"; start()
    }
    override fun onStart() {
        val result=Bundle()
        if(!live) {
            result.putString("stream","Hardware test skipped. Run with -e live true after configuring the app.\n")
            finish(Activity.RESULT_OK,result); return
        }
        val report=StringBuilder()
        var failure: Throwable?=null
        val prefs=targetContext.getSharedPreferences("bridge",0)
        val conf=BridgeConfig(prefs.getString("ip","").orEmpty(),prefs.getString("key","").orEmpty())
        val api=HueApi(conf)
        var created: String?=null
        var snapshot: JSONObject?=null
        try {
            snapshot=api.request("GET","lights") as JSONObject
            targetContext.openFileOutput("bridge-before-test.json",0).use { it.write(snapshot.toString().toByteArray()) }
            val lamps=api.lights(); check(lamps.isNotEmpty()); report.append("PASS read lights\n")
            val scenesBefore=api.scenes(); report.append("PASS read scenes and palettes (${scenesBefore.size})\n")
            try {
                HueApi(conf.copy(key="lux-invalid-"+java.util.UUID.randomUUID().toString())).lights(); error("Invalid key was accepted")
            } catch(e: BridgeException) { check(e.message?.contains("API key")==true) }
            report.append("PASS invalid-key error mapping\n")
            val lamp=lamps.first { it.reachable && it.colorCapable }
            api.setLight(lamp.id,JSONObject().put("on",true).put("bri",94).put("hue",47000).put("sat",180))
            Thread.sleep(600)
            check(api.lights().first { it.id==lamp.id }.brightness==94); report.append("PASS brightness and color write/read\n")
            val id=api.create("Lux test ${System.currentTimeMillis()%100000}",lamps.map { it.id }); created=id
            check(api.scenes().any { it.id==id }); report.append("PASS create scene\n")
            api.rename(id,"Lux smoke renamed")
            check(api.scenes().first { it.id==id }.name=="Lux smoke renamed"); report.append("PASS rename scene\n")
            api.setLight(lamp.id,JSONObject().put("on",true).put("bri",137)); Thread.sleep(600)
            api.overwrite(id)
            val saved=api.request("GET","scenes/$id") as JSONObject
            check(saved.getJSONObject("lightstates").getJSONObject(lamp.id).getInt("bri")==137)
            report.append("PASS overwrite current light states\n")
            api.setLight(lamp.id,JSONObject().put("bri",60)); Thread.sleep(600)
            api.activate(id); Thread.sleep(1100)
            check(api.lights().first { it.id==lamp.id }.brightness==137); report.append("PASS scene recall\n")
            api.power(false); Thread.sleep(1100); check(api.lights().none { it.on })
            api.power(true); Thread.sleep(1100); check(api.lights().all { it.on }); report.append("PASS all-light power\n")
            api.delete(id); created=null
            check(api.scenes().none { it.id==id }); report.append("PASS delete scene\n")
            check(api.scenes().map { it.id }.toSet()==scenesBefore.map { it.id }.toSet())
        } catch(e: Throwable) { failure=e }
        finally {
            try {
                created?.let { api.delete(it) }
                snapshot?.let { all ->
                    all.keys().forEach { id ->
                        val s=all.getJSONObject(id).getJSONObject("state")
                        val restore=JSONObject().put("on",s.getBoolean("on"))
                        if(s.has("bri")) restore.put("bri",s.getInt("bri"))
                        when(s.optString("colormode")) {
                            "ct" -> restore.put("ct",s.getInt("ct"))
                            "xy" -> restore.put("xy",s.getJSONArray("xy"))
                            "hs" -> restore.put("hue",s.getInt("hue")).put("sat",s.getInt("sat"))
                        }
                        if(s.has("effect")) restore.put("effect",s.getString("effect"))
                        api.setLight(id,restore); Thread.sleep(120)
                    }
                    Thread.sleep(600)
                    val restored=api.request("GET","lights") as JSONObject
                    all.keys().forEach { id ->
                        val before=all.getJSONObject(id).getJSONObject("state")
                        val after=restored.getJSONObject(id).getJSONObject("state")
                        check(before.getBoolean("on")==after.getBoolean("on"))
                        if(before.has("bri")) check(before.getInt("bri")==after.getInt("bri"))
                    }
                    report.append("PASS original lamp states restored\n")
                }
            } catch(e: Throwable) { failure=e; report.append("RESTORE FAILED; private snapshot preserved.\n") }
        }
        if(failure!=null) report.append("FAIL ${failure.javaClass.simpleName}: ${failure.message}\n")
        result.putString("stream",report.toString())
        finish(if(failure==null) Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
