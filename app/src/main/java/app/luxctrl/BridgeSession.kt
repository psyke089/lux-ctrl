package app.luxctrl

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** One request queue; all published state and callbacks live on the main thread. */
class BridgeSession(var config: BridgeConfig?) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var listener: (() -> Unit)? = null
    var state = BridgeState()
    var busy = false; private set
    var online = false; private set
    var error: String? = null; private set
    var notice: String? = null
    var activeId: String? = null
    var dirty = false
    private var generation = 0
    fun configure(value: BridgeConfig) {
        generation++; config = value; state = BridgeState(); activeId = null; dirty = false
        busy = false; online = false; error = null
        refresh(true)
    }
    fun refresh(includeScenes: Boolean = false) {
        run(includeScenes, null, false) { null }
    }
    fun change(message: String, scenes: Boolean = false, edit: Boolean = false, operation: (HueApi) -> String?) {
        run(scenes, message, edit, operation)
    }
    private fun run(scenes: Boolean, message: String?, edit: Boolean, operation: (HueApi) -> String?) {
        val conf = config ?: return
        if (busy) return
        val version = generation
        busy = true; listener?.invoke()
        val previous = state
        worker.execute {
            try {
                val api = HueApi(conf)
                val selected = operation(api)
                val lamps = api.lights()
                val sceneList = if (scenes) api.scenes() else previous.scenes
                main.post {
                    if (version != generation) return@post
                    state = BridgeState(lamps,sceneList); busy = false; online = true; error = null
                    if (selected != null) activeId = selected.ifBlank { null }
                    if (activeId != null && sceneList.none { it.id == activeId }) activeId = null
                    if (edit && activeId != null) dirty = true
                    if (selected != null) dirty = false
                    notice = message; listener?.invoke()
                }
            } catch (e: Exception) {
                main.post {
                    if (version != generation) return@post
                    busy = false; online = false
                    error = when(e) {
                        is BridgeException -> e.message
                        is java.net.SocketTimeoutException -> "Bridge timed out. Check Wi-Fi and the bridge address."
                        is java.net.ConnectException, is java.net.NoRouteToHostException -> "Bridge unavailable. Connect to the same local network."
                        else -> "Could not reach the bridge. Check your network and Settings."
                    }
                    listener?.invoke()
                }
            }
        }
    }
    fun close() { generation++; listener = null; worker.shutdownNow() }
}
