package app.luxctrl

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.*
import android.widget.*
import org.json.JSONObject
import kotlin.math.roundToInt

@android.annotation.SuppressLint("SetTextI18n") // This first version intentionally ships in English.
class MainActivity : Activity() {
    private lateinit var session: BridgeSession
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("bridge", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var wheel: LampWheel
    private lateinit var status: TextView
    private lateinit var lampCount: TextView
    private lateinit var errorText: TextView
    private lateinit var legends: LinearLayout
    private lateinit var sceneList: LinearLayout
    private lateinit var sceneSubtitle: TextView
    private lateinit var save: TextView
    private lateinit var newScene: TextView
    private var sceneSignature = ""
    private var legendSignature = ""
    private var resumed = false
    private var dialog: AlertDialog? = null
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (hasNetworkPermission() && dialog?.isShowing != true && session.config != null) session.refresh(session.state.scenes.isEmpty())
            handler.postDelayed(this,6000)
        }
    }
    private fun dp(v: Int) = (v*resources.displayMetrics.density).roundToInt()
    private fun row() = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun text(s: String,size: Float=12f,color: Int=Design.muted,mono: Boolean=false) = Design.label(this,s,size,color,mono)
    private fun space(height: Int) = View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(height)) }
    private fun action(s: String, onClick: () -> Unit): TextView = text(s,12f,Design.ink).apply {
        gravity=Gravity.CENTER; minHeight=dp(48); setPadding(dp(14),0,dp(14),0)
        isClickable=true; isFocusable=true; Design.clickable(this)
        setOnClickListener { onClick() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ip=prefs.getString("ip","").orEmpty(); val key=prefs.getString("key","").orEmpty()
        val conf=BridgeConfig(ip,key).takeIf { it.validate()==null }
        session = (lastNonConfigurationInstance as? BridgeSession) ?: BridgeSession(conf)
        session.listener = { render() }
        buildContent()
        if (conf == null) status.text="●  SET UP YOUR BRIDGE"
    }
    override fun onRetainNonConfigurationInstance(): Any = session
    override fun onResume() {
        super.onResume(); resumed=true
        if (session.config != null && hasNetworkPermission()) session.refresh(true)
        else if (session.config != null) requestLocalNetwork()
        handler.postDelayed(poll,6000)
    }
    override fun onPause() { resumed=false; handler.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() {
        dialog?.dismiss(); session.listener=null
        if (!isChangingConfigurations) session.close()
        super.onDestroy()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); buildContent() }
    private fun hasNetworkPermission() = Build.VERSION.SDK_INT < 37 || checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED
    private fun requestLocalNetwork() {
        if (Build.VERSION.SDK_INT >= 37) requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"),7)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) session.refresh(true)
        else { errorText.text="Allow nearby device access to connect to the bridge. Tap here to retry."; errorText.visibility=View.VISIBLE }
    }
    private fun buildContent() {
        sceneSignature="!"; legendSignature="!"
        root=column().apply { setBackgroundColor(Design.bg) }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        root.setOnApplyWindowInsetsListener { v,insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            insets
        }
        val header=row().apply { setPadding(dp(22),dp(10),dp(18),dp(10)) }
        val brand=column()
        brand.addView(text("LUX / CTRL",23f,Design.ink).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); letterSpacing=.13f })
        brand.addView(space(5)); status=text("●  CONNECTING",9f,Design.teal,true); brand.addView(status)
        header.addView(brand,LinearLayout.LayoutParams(0,dp(48),1f))
        val refresh=GlyphView(this,"refresh").apply { contentDescription="Refresh lights and scenes"; setOnClickListener { if(hasNetworkPermission()) session.refresh(true) else requestLocalNetwork() } }
        header.addView(refresh,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(8) })
        val settings=GlyphView(this,"settings").apply { contentDescription="Bridge settings"; setOnClickListener { settings() } }
        header.addView(settings,LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(header)
        errorText=text("",12f,Design.amber).apply {
            setPadding(dp(22),dp(9),dp(22),dp(9)); setBackgroundColor(0xff29231e.toInt()); visibility=View.GONE
            isClickable=true; isFocusable=true
            setOnClickListener { if (!hasNetworkPermission()) requestLocalNetwork() else if(session.config==null) settings() else session.refresh(true) }
        }
        root.addView(errorText)
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val body=LinearLayout(this).apply { orientation=if(landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val lamps=lampPanel(); val scenes=scenesPanel()
        if(landscape) {
            body.addView(scenes,LinearLayout.LayoutParams(0, -1, .47f))
            body.addView(View(this).apply { setBackgroundColor(Design.line) },LinearLayout.LayoutParams(dp(1),-1))
            body.addView(lamps,LinearLayout.LayoutParams(0,-1,.53f))
        } else {
            body.addView(lamps,LinearLayout.LayoutParams(-1,0,.60f))
            body.addView(View(this).apply { setBackgroundColor(Design.line) },LinearLayout.LayoutParams(-1,dp(1)).apply { marginStart=dp(22); marginEnd=dp(22) })
            body.addView(scenes,LinearLayout.LayoutParams(-1,0,.40f))
        }
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root); root.requestApplyInsets(); render()
    }
    private fun lampPanel(): LinearLayout {
        val box=column().apply { setPadding(dp(18),dp(10),dp(18),dp(10)) }
        val heading=row(); heading.addView(text("01  /  LIGHT FIELD",10f,Design.muted,true),LinearLayout.LayoutParams(0,-2,1f))
        lampCount=text("—",10f,Design.muted,true); heading.addView(lampCount); box.addView(heading)
        wheel=LampWheel(this).apply {
            onPower = {
                val turnOn=session.state.lamps.none { it.on }
                session.change(if(turnOn) "All lights on" else "All lights off",edit=true) { api -> api.power(turnOn); null }
            }
            onLamp={ lamp -> lampDialog(lamp) }
            onBrightness={ lamp,bri -> setBrightness(lamp,bri) }
        }
        box.addView(wheel,LinearLayout.LayoutParams(-1,0,1f))
        box.addView(text("DRAG TO DIM  ·  TAP TO COLOR",9f,Design.muted,true).apply { gravity=Gravity.CENTER; setPadding(0,0,0,dp(9)) })
        legends=column(); box.addView(legends)
        return box
    }
    private fun scenesPanel(): LinearLayout {
        val box=column().apply { setPadding(dp(22),dp(15),dp(22),dp(8)) }
        val heading=row()
        val title=column(); title.addView(text("02  /  SCENES",10f,Design.muted,true)); title.addView(space(5))
        sceneSubtitle=text("Set the atmosphere.",17f,Design.ink); title.addView(sceneSubtitle)
        heading.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        newScene=action("+ New") { session.activeId=null; session.dirty=false; render(); toast("Current light mix is ready to save as a new scene.") }.apply { contentDescription="Deselect active scene to save a new scene" }
        heading.addView(newScene,LinearLayout.LayoutParams(dp(66),dp(48)))
        box.addView(heading); box.addView(space(12))
        sceneList=column()
        box.addView(ScrollView(this).apply { isFillViewport=true; clipToPadding=false; addView(sceneList); isVerticalScrollBarEnabled=false },LinearLayout.LayoutParams(-1,0,1f))
        save=action("Save scene") { saveScene() }.apply { Design.clickable(this,0xff173437.toInt(),0xff315f63.toInt(),12f); setTextColor(Design.teal) }
        box.addView(save,LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(8) })
        return box
    }
    private fun render() {
        if (!::wheel.isInitialized || isDestroyed) return
        val state=session.state
        status.text=when {
            session.config==null -> "●  SET UP YOUR BRIDGE"
            !hasNetworkPermission() -> "●  LOCAL ACCESS NEEDED"
            session.busy && !session.online -> "●  CONNECTING TO BRIDGE"
            !session.online -> "●  BRIDGE DISCONNECTED"
            session.busy -> "●  SYNCING WITH BRIDGE"
            else -> "●  LOCAL BRIDGE CONNECTED"
        }
        status.setTextColor(if(session.online) Design.teal else Design.muted)
        errorText.text=session.error.orEmpty(); errorText.visibility=if(session.error!=null) View.VISIBLE else View.GONE
        wheel.lamps=state.lamps; wheel.ready=session.online && !session.busy
        lampCount.text="${state.lamps.size.toString().padStart(2,'0')} LAMPS"
        val signature=state.lamps.joinToString { "${it.id}/${it.name}/${it.color}/${it.on}/${it.brightness}/${it.reachable}" }
        if(signature!=legendSignature) {
            legendSignature=signature; legends.removeAllViews()
            if(state.lamps.isEmpty()) {
                legends.addView(action(if(session.config==null) "Connect your Hue bridge" else "Retry connection") { if(session.config==null) settings() else session.refresh(true) })
            } else {
                // Two compact rows scroll horizontally for large bridge inventories.
                val scroller=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
                val grid=column()
                val perRow = if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) state.lamps.size else (state.lamps.size+1)/2
                state.lamps.chunked(perRow).forEachIndexed { line, lamps ->
                    val r=row().apply { gravity=Gravity.TOP }
                    lamps.forEachIndexed { i,lamp ->
                        val index=line*perRow+i+1
                        val chip=text("${index.toString().padStart(2,'0')}  ${lamp.name}${if(!lamp.reachable) " · offline" else ""}",10f,if(lamp.on) lamp.color else Design.muted).apply {
                            gravity=Gravity.CENTER_VERTICAL; setPadding(dp(10),0,dp(10),0); isClickable=true; isFocusable=true
                            contentDescription="${lamp.name}, ${if(!lamp.reachable) "unreachable" else if(lamp.on) "${(lamp.brightness/254f*100).roundToInt()} percent" else "off"}. Open light controls"
                            Design.clickable(this,Design.bg,Design.line,8f)
                            setOnClickListener { session.state.lamps.find { it.id==lamp.id }?.let { lampDialog(it) } }
                        }
                        r.addView(chip,LinearLayout.LayoutParams(dp(112),dp(48)).apply { marginEnd=dp(5); bottomMargin=dp(4) })
                    }
                    grid.addView(r)
                }
                scroller.addView(grid); legends.addView(scroller)
            }
        }
        val active=state.scenes.find { it.id==session.activeId }
        sceneSubtitle.text=if(active==null) "Set the atmosphere." else if(session.dirty) "A new take on ${active.name}" else active.name
        sceneSubtitle.maxLines=1; sceneSubtitle.ellipsize=android.text.TextUtils.TruncateAt.END
        save.text=if(active==null) "＋  Save as a new scene" else "↥  Save changes to scene"
        save.contentDescription=if(active==null) "Save current lights as a new scene" else "Overwrite ${active.name} with current light states"
        save.isEnabled=session.online && !session.busy && state.lamps.isNotEmpty() && active?.locked!=true
        save.alpha=if(save.isEnabled) 1f else .4f
        newScene.isEnabled=state.lamps.isNotEmpty() && !session.busy
        val sceneKey=state.scenes.toString()+session.activeId+session.dirty+(session.config==null)
        if(sceneKey!=sceneSignature) {
            sceneSignature=sceneKey; sceneList.removeAllViews()
            if(state.scenes.isEmpty()) sceneList.addView(text(if(session.config==null) "Your light, your atmosphere.\nConnect a bridge to get started." else "No scenes yet.\nDial in your lights, then save the mix.",14f).apply { setPadding(dp(5),dp(12),dp(5),dp(18)); setLineSpacing(dp(5).toFloat(),1f) })
            state.scenes.chunked(2).forEach { pair ->
                val r=row().apply { gravity=Gravity.TOP }
                pair.forEachIndexed { i,scene -> r.addView(sceneCard(scene),LinearLayout.LayoutParams(0,dp(82),1f).apply { if(i==0) marginEnd=dp(8); bottomMargin=dp(8) }) }
                if(pair.size==1) r.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
                sceneList.addView(r)
            }
        }
        session.notice?.let { toast(it); session.notice=null }
    }
    private fun sceneCard(scene: Scene): View {
        val selected=session.activeId==scene.id
        val box=column().apply {
            setPadding(dp(12),dp(11),dp(12),dp(10)); gravity=Gravity.CENTER_VERTICAL
            Design.clickable(this,if(selected) 0xff193139.toInt() else Design.panel,if(selected) Design.teal else Design.line,12f)
            isClickable=true; isFocusable=true
            contentDescription="${scene.name}${if(selected) ", selected" else ""}. Tap to activate; hold to rename or delete."
            setOnClickListener {
                if (!session.online || session.busy) return@setOnClickListener
                session.change("${scene.name} activated") { api -> api.activate(scene.id); scene.id }
            }
            setOnLongClickListener { sceneOptions(scene); true }
        }
        val colors=scene.colors.ifEmpty { listOf(Design.muted,Design.line) }
        val rail=View(this).apply {
            background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, (if(colors.size==1) colors+colors else colors).toIntArray()).apply { cornerRadius=dp(3).toFloat() }
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        box.addView(rail,LinearLayout.LayoutParams(-1,dp(6)))
        box.addView(space(10))
        box.addView(text(scene.name,13f,Design.ink).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) })
        box.addView(space(5))
        box.addView(text(if(selected) (if(session.dirty) "● EDITED" else "● ACTIVE") else "${scene.lights.size} LIGHTS",8f,if(selected) Design.teal else Design.muted,true))
        return box
    }
    private fun toast(value: String) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show() }
    private fun show(value: AlertDialog) { dialog?.dismiss(); dialog=value; value.show(); value.window?.setBackgroundDrawable(Design.shape(this,Design.panel,Design.line,20f)) }
    private fun dialogBody()=column().apply { setPadding(dp(24),dp(12),dp(24),dp(12)) }
    private fun settings() {
        val body=dialogBody()
        body.addView(text("Connect directly over your local network.\nNo account. No cloud.",13f).apply { setLineSpacing(dp(4).toFloat(),1f) }); body.addView(space(18))
        body.addView(text("BRIDGE IP",10f,Design.muted,true))
        val ip=EditText(this).apply { setSingleLine(); hint="192.168.1.20"; setText(session.config?.ip.orEmpty()); inputType=InputType.TYPE_CLASS_PHONE; textSize=16f; contentDescription="Bridge IP address" }
        body.addView(ip,LinearLayout.LayoutParams(-1,dp(52))); body.addView(space(12)); body.addView(text("API KEY",10f,Design.muted,true))
        val key=EditText(this).apply { setSingleLine(); hint="Your bridge API key"; setText(session.config?.key.orEmpty()); inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; textSize=14f; contentDescription="Bridge API key"; if(Build.VERSION.SDK_INT>=26) importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO }
        body.addView(key,LinearLayout.LayoutParams(-1,dp(52)))
        val reveal=CheckBox(this).apply { text="Show API key"; textSize=12f; setTextColor(Design.muted); setOnCheckedChangeListener { _,checked -> key.transformationMethod=if(checked) null else android.text.method.PasswordTransformationMethod.getInstance() } }
        body.addView(reveal); body.addView(space(6))
        body.addView(text("Use a private IPv4 address. This app uses the local Hue API, including first-generation bridges. Keep your API key private.",12f))
        val error=text("",12f,Design.amber); body.addView(error)
        val d=AlertDialog.Builder(this).setTitle("Bridge settings").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel",null).setPositiveButton("Connect",null).create()
        show(d)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val conf=BridgeConfig(ip.text.toString().trim(),key.text.toString().trim())
            val problem=conf.validate()
            if(problem!=null) { error.text=problem; return@setOnClickListener }
            prefs.edit().putString("ip",conf.ip).putString("key",conf.key).apply()
            d.dismiss()
            if(hasNetworkPermission()) session.configure(conf) else { session.config=conf; requestLocalNetwork() }
        }
    }
    private fun setBrightness(lamp: Lamp,bri: Int) {
        if(session.busy || !session.online) return
        session.change("${lamp.name} · ${(bri/254f*100).roundToInt()}%",edit=true) { api ->
            val payload=JSONObject().put("on",bri>0)
            if(bri>0 && lamp.dimmable) payload.put("bri",bri.coerceIn(1,254))
            api.setLight(lamp.id,payload); null
        }
    }
    private fun lampDialog(lamp: Lamp) {
        if(!lamp.reachable) { toast("${lamp.name} is unreachable. Check its wall switch."); return }
        if(!session.online || session.busy) return
        val body=dialogBody()
        var bri=if(lamp.on) lamp.brightness else 0
        val hsv=FloatArray(3); Color.colorToHSV(lamp.color,hsv)
        var hue=hsv[0].roundToInt(); var sat=(hsv[1]*100).roundToInt(); var temperature=300
        var colorChanged=false; var whiteMode=false
        val preview=View(this).apply { background=Design.shape(context,lamp.color,lamp.color,12f) }
        body.addView(preview,LinearLayout.LayoutParams(-1,dp(38))); body.addView(space(15))
        fun updatePreview() { val color=if(whiteMode) HueColor.fromMired(temperature) else HueColor.fromHsv(hue.toFloat(),sat/100f); preview.background=Design.shape(this,color,color,12f); preview.alpha=if(bri==0) .25f else .35f+.65f*bri/254 }
        fun slider(label: String,max: Int,initial: Int,format: (Int)->String,onChange: (Int)->Unit): SeekBar {
            val t=text("$label  ${format(initial)}",11f,Design.muted,true); body.addView(t)
            return SeekBar(this).apply {
                this.max=max; progress=initial; contentDescription=label
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) { t.text="$label  ${format(p)}"; if(user) onChange(p) }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                body.addView(this,LinearLayout.LayoutParams(-1,dp(48)))
            }
        }
        if(lamp.dimmable) slider("BRIGHTNESS",254,bri,{ "${(it/254f*100).roundToInt()}%" }) { bri=it; updatePreview() }
        else body.addView(Switch(this).apply { text="Power"; isChecked=lamp.on; setOnCheckedChangeListener { _,on -> bri=if(on)254 else 0 } })
        if(lamp.colorCapable) {
            slider("HUE",360,hue,{ "$it°" }) { hue=it; whiteMode=false; colorChanged=true; updatePreview() }
            slider("SATURATION",100,sat,{ "$it%" }) { sat=it; whiteMode=false; colorChanged=true; updatePreview() }
        }
        if(lamp.temperatureCapable) {
            temperature=300.coerceIn(lamp.minMired,lamp.maxMired)
            slider("WHITE TEMPERATURE",lamp.maxMired-lamp.minMired,temperature-lamp.minMired,{ "${1_000_000/(it+lamp.minMired)} K" }) { temperature=it+lamp.minMired; whiteMode=true; colorChanged=true; updatePreview() }
        }
        body.addView(text("Changes apply when you tap Apply.\nBrightness at zero switches this lamp off.",11f))
        val d=AlertDialog.Builder(this).setTitle(lamp.name).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel",null).setNeutralButton(if(lamp.on) "Turn off" else "Turn on") { _,_ -> setBrightness(lamp,if(lamp.on)0 else lamp.brightness.coerceAtLeast(1)) }.setPositiveButton("Apply") { _,_ ->
            val payload=JSONObject().put("on",bri>0)
            if(lamp.dimmable && bri>0) payload.put("bri",bri.coerceAtLeast(1))
            if(colorChanged) {
                if(whiteMode) payload.put("ct",temperature)
                else payload.put("hue",(hue/360.0*65535).roundToInt().coerceIn(0,65535)).put("sat",(sat/100.0*254).roundToInt())
            }
            session.change("${lamp.name} updated",edit=true) { api -> api.setLight(lamp.id,payload); null }
        }.create(); show(d)
    }
    private fun saveScene() {
        if(session.busy || !session.online) return
        val active=session.state.scenes.find { it.id==session.activeId }
        if(active!=null) session.change("${active.name} saved",scenes=true) { api -> api.overwrite(active.id); active.id }
        else nameDialog("Save a new scene","") { name -> session.change("$name saved",scenes=true) { api -> api.create(name,session.state.lamps.map { it.id }) } }
    }
    private fun nameDialog(title: String,initial: String,onSave: (String)->Unit) {
        val body=dialogBody()
        val input=EditText(this).apply { setSingleLine(); hint="Scene name"; setText(initial); filters=arrayOf(InputFilter.LengthFilter(32)); contentDescription="Scene name"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        body.addView(input,LinearLayout.LayoutParams(-1,dp(56)))
        val d=AlertDialog.Builder(this).setTitle(title).setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create(); show(d)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name=input.text.toString().trim()
            if(name.isBlank()) input.error="Give this scene a name." else { d.dismiss(); onSave(name) }
        }
        input.requestFocus(); input.setSelection(input.text.length)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    private fun sceneOptions(scene: Scene) {
        if(session.busy) return
        if(scene.locked) { toast("This scene is locked by the bridge and cannot be edited."); return }
        show(AlertDialog.Builder(this).setTitle(scene.name).setItems(arrayOf("Rename scene","Delete scene")) { _,which ->
            if(which==0) nameDialog("Rename scene",scene.name) { name -> session.change("Scene renamed",scenes=true) { api -> api.rename(scene.id,name); null } }
            else show(AlertDialog.Builder(this).setTitle("Delete ${scene.name}?").setMessage("This removes the scene from your Hue bridge and other Hue apps. Your lights stay as they are.").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ ->
                session.change("Scene deleted",scenes=true) { api -> api.delete(scene.id); if(session.activeId==scene.id) "" else null }
            }.create())
        }.setNegativeButton("Cancel",null).create())
    }
}
