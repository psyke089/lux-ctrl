package app.luxctrl

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
    private lateinit var sceneTag: TextView
    private lateinit var errorText: TextView
    private lateinit var sceneList: LinearLayout
    private lateinit var saveGlyph: GlyphView
    private var sceneSignature = ""
    private var resumed = false
    private var dialog: AlertDialog? = null
    private var intervalSeconds = 10
    private val intervalOptions = listOf(2,5,10,30,60)
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (hasNetworkPermission() && dialog?.isShowing != true && session.config != null) session.refresh(session.state.scenes.isEmpty())
            handler.postDelayed(this, intervalSeconds*1000L)
        }
    }
    private fun dp(v: Int) = (v*resources.displayMetrics.density).roundToInt()
    private fun row() = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun text(s: String,size: Float=12f,color: Int=Design.muted,mono: Boolean=false) = Design.label(this,s,size,color,mono)
    private fun space(height: Int) = View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(height)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ip=prefs.getString("ip","").orEmpty(); val key=prefs.getString("key","").orEmpty()
        intervalSeconds=prefs.getInt("interval",10).coerceIn(2,60)
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
        handler.postDelayed(poll,intervalSeconds*1000L)
    }
    override fun onPause() { resumed=false; handler.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() {
        dialog?.dismiss(); session.listener=null; handler.removeCallbacksAndMessages(null)
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
    private fun restartPoll() {
        handler.removeCallbacks(poll)
        if (resumed) handler.postDelayed(poll,intervalSeconds*1000L)
    }
    private fun buildContent() {
        sceneSignature="!"
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
        brand.addView(text("LUX//CTRL",23f,Design.ink).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); letterSpacing=.13f })
        brand.addView(space(5)); status=text("●  CONNECTING",9f,Design.teal,true); brand.addView(status)
        header.addView(brand,LinearLayout.LayoutParams(0,dp(48),1f))
        val refresh=GlyphView(this,"refresh").apply { contentDescription="Refresh lights and scenes"; setOnClickListener { if(hasNetworkPermission()) session.refresh(true) else requestLocalNetwork() } }
        header.addView(refresh,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(8) })
        saveGlyph=GlyphView(this,"save").apply { setOnClickListener { saveScene() } }
        header.addView(saveGlyph,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(8) })
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
            body.addView(scenes,LinearLayout.LayoutParams(-1,0,.40f))
        }
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root); root.requestApplyInsets(); render()
    }
    private fun lampPanel(): LinearLayout {
        val box=column().apply { setPadding(dp(18),dp(6),dp(18),dp(12)) }
        // The section headline is gone; keep the active-scene status as a slim top-right tag.
        sceneTag=text("",10f,Design.muted,true).apply {
            maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; gravity=Gravity.END
        }
        box.addView(sceneTag,LinearLayout.LayoutParams(-1,-2))
        wheel=LampWheel(this).apply {
            onPower = {
                val turnOn=session.state.lamps.none { it.on }
                session.change(if(turnOn) "All lights on" else "All lights off",edit=true) { api -> api.power(turnOn); null }
            }
            onLamp={ lamp -> lampDialog(lamp) }
            onBrightness={ lamp,bri -> setBrightness(lamp,bri) }
        }
        box.addView(wheel,LinearLayout.LayoutParams(-1,0,1f))
        return box
    }
    private fun scenesPanel(): LinearLayout {
        val box=column().apply { setPadding(dp(22),dp(4),dp(22),dp(4)) }
        sceneList=column()
        box.addView(ScrollView(this).apply { isFillViewport=true; clipToPadding=false; addView(sceneList); isVerticalScrollBarEnabled=false },LinearLayout.LayoutParams(-1,0,1f))
        return box
    }
    private fun render() {
        if (!::wheel.isInitialized || isDestroyed) return
        val state=session.state
        status.text=when {
            session.config==null -> "●  SET UP YOUR BRIDGE"
            !hasNetworkPermission() -> "●  LOCAL ACCESS NEEDED"
            !session.online && session.busy -> "●  CONNECTING TO BRIDGE"
            !session.online -> "●  BRIDGE DISCONNECTED"
            else -> "●  LOCAL BRIDGE CONNECTED"
        }
        status.setTextColor(if(session.online) Design.teal else Design.muted)
        errorText.text=session.error.orEmpty(); errorText.visibility=if(session.error!=null) View.VISIBLE else View.GONE
        wheel.lamps=state.lamps; wheel.ready=session.online; wheel.interactive=session.online && !session.busy
        val active=state.scenes.find { it.id==session.activeId }
        sceneTag.text=when {
            active==null -> if(state.lamps.isEmpty()) "" else "NO SCENE"
            session.dirty -> "● ${active.name} · EDITED"
            else -> "● ${active.name}"
        }
        sceneTag.setTextColor(if(active==null) Design.muted else Design.teal)
        saveGlyph.contentDescription=if(active==null) "Name and save the current lights as a new scene" else "Save the current lights to ${active.name}"
        val canSave=session.online && state.lamps.isNotEmpty() && active?.locked!=true
        saveGlyph.isEnabled=canSave
        saveGlyph.alpha=if(canSave) 1f else .4f
        val sceneKey=state.scenes.toString()+session.activeId+session.dirty+(session.config==null)
        if(sceneKey!=sceneSignature) {
            sceneSignature=sceneKey; sceneList.removeAllViews()
            if(state.scenes.isEmpty()) sceneList.addView(text(if(session.config==null) "Your light, your atmosphere.\nConnect a bridge to get started." else "No scenes yet.\nDial in your lights, then save the mix.",14f).apply { setPadding(dp(5),dp(12),dp(5),dp(18)); setLineSpacing(dp(5).toFloat(),1f) })
            state.scenes.chunked(3).forEach { trio ->
                val r=row().apply { gravity=Gravity.TOP }
                trio.forEachIndexed { i,scene -> r.addView(sceneCard(scene),LinearLayout.LayoutParams(0,dp(58),1f).apply { if(i<2) marginEnd=dp(7); bottomMargin=dp(4) }) }
                for(i in trio.size until 3) r.addView(View(this),LinearLayout.LayoutParams(0,dp(58),1f).apply { if(i<2) marginEnd=dp(7) })
                sceneList.addView(r)
            }
        }
        session.notice?.let { toast(it); session.notice=null }
    }
    private fun sceneCard(scene: Scene): View {
        val selected=session.activeId==scene.id
        val box=column().apply {
            setPadding(dp(9),dp(6),dp(9),dp(6)); gravity=Gravity.CENTER_VERTICAL
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
        box.addView(rail,LinearLayout.LayoutParams(-1,dp(18)))
        box.addView(space(4))
        box.addView(text((if(selected) "● " else "")+scene.name,11f,if(selected) Design.teal else Design.ink).apply {
            maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        })
        return box
    }
    private fun toast(value: String) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show() }
    private fun show(value: AlertDialog) { dialog?.dismiss(); dialog=value; value.show(); value.window?.setBackgroundDrawable(Design.shape(this,Design.panel,Design.line,20f)) }
    private fun dialogBody()=column().apply { setPadding(dp(24),dp(12),dp(24),dp(12)) }
    private fun settings() {
        val body=dialogBody()
        body.addView(text("BRIDGE IP",10f,Design.muted,true))
        val ip=EditText(this).apply { setSingleLine(); hint="192.168.1.20"; setText(session.config?.ip.orEmpty()); inputType=InputType.TYPE_CLASS_PHONE; textSize=16f; contentDescription="Bridge IP address" }
        body.addView(ip,LinearLayout.LayoutParams(-1,dp(52))); body.addView(space(14)); body.addView(text("API KEY",10f,Design.muted,true))
        val key=EditText(this).apply { setSingleLine(); hint="Your bridge API key"; setText(session.config?.key.orEmpty()); inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; textSize=14f; contentDescription="Bridge API key"; if(Build.VERSION.SDK_INT>=26) importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO }
        body.addView(key,LinearLayout.LayoutParams(-1,dp(52)))
        val reveal=CheckBox(this).apply { text="Show API key"; textSize=12f; setTextColor(Design.muted); setOnCheckedChangeListener { _,checked -> key.transformationMethod=if(checked) null else android.text.method.PasswordTransformationMethod.getInstance() } }
        body.addView(reveal); body.addView(space(18))
        body.addView(text("UPDATE INTERVAL",10f,Design.muted,true)); body.addView(space(8))
        val intervals=row()
        val chips=ArrayList<TextView>()
        fun refreshChips() {
            chips.forEachIndexed { i,chip ->
                val chosen=intervalOptions[i]==intervalSeconds
                chip.setTextColor(if(chosen) Design.teal else Design.muted)
                Design.clickable(chip,if(chosen) 0xff193139.toInt() else Design.panel,if(chosen) Design.teal else Design.line,10f)
            }
        }
        intervalOptions.forEach { seconds ->
            val chip=text("${seconds}s",12f,Design.muted).apply {
                gravity=Gravity.CENTER; isClickable=true; isFocusable=true; minHeight=dp(44)
                contentDescription="Refresh every $seconds seconds"
                setOnClickListener { intervalSeconds=seconds; prefs.edit().putInt("interval",seconds).apply(); refreshChips(); restartPoll() }
            }
            chips.add(chip); intervals.addView(chip,LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(6) })
        }
        refreshChips()
        body.addView(intervals)
        val error=text("",12f,Design.amber); body.addView(error)
        val d=AlertDialog.Builder(this).setTitle("SETTINGS").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel",null).setPositiveButton("Connect",null).create()
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
        var pickerLandscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        var bri=if(lamp.on) lamp.brightness else 0
        val hsv=FloatArray(3); Color.colorToHSV(lamp.color,hsv)
        var hue=hsv[0]; var sat=hsv[1]; var temperature=300
        var whiteMode=false
        var closed=false; var retry=0
        var pending: Runnable?=null
        var send: () -> Unit = {}
        val controls=column()
        val preview=View(this).apply { background=Design.shape(context,lamp.color,lamp.color,12f); contentDescription="Selected color preview" }
        val readout=text("",10f,Design.muted,true)
        var picker: ColorDiscView?=null
        fun updatePreview() {
            val color=if(whiteMode) HueColor.fromMired(temperature) else Color.HSVToColor(floatArrayOf(hue,sat,1f))
            preview.background=Design.shape(this,color,color,12f)
            preview.alpha=if(bri==0) .15f else .3f+.7f*bri/254
            readout.text=if(whiteMode) "WHITE · ${(bri/254f*100).roundToInt()}% LIGHT" else "${(sat*100).roundToInt()}% SAT · ${(bri/254f*100).roundToInt()}% LIGHT"
            picker?.alpha=if(whiteMode) .55f else 1f
        }
        fun payload(): JSONObject {
            val update=JSONObject().put("on",bri>0)
            if(lamp.dimmable && bri>0) update.put("bri",bri.coerceAtLeast(1))
            if(lamp.colorCapable) {
                if(whiteMode) update.put("ct",temperature)
                else update.put("hue",(hue/360.0*65535).roundToInt().coerceIn(0,65535)).put("sat",(sat*254).roundToInt().coerceIn(0,254))
            }
            return update
        }
        // Live apply: coalesce rapid disc/slider moves into one request, then send.
        fun schedule(delay: Long=350) {
            pending?.let { handler.removeCallbacks(it) }
            val r=Runnable { pending=null; send() }
            pending=r; handler.postDelayed(r,delay)
        }
        fun flushPending() {
            val r=pending ?: return
            handler.removeCallbacks(r); pending=null; send()
        }
        send = {
            if(!closed && session.online) {
                if(session.busy && retry<3) { retry++; schedule(300) }
                else { retry=0; session.change("${lamp.name} updated",edit=true) { api -> api.setLight(lamp.id,payload()); null } }
            }
        }
        if(lamp.colorCapable) {
            picker=ColorDiscView(this).apply {
                setColor(hue,sat,bri/254f)
                onColorChanged={ h,s,v ->
                    hue=h; sat=s; bri=(v*254).roundToInt(); whiteMode=false
                    updatePreview(); schedule()
                }
            }
            val discPanel=column()
            val discLayout=LinearLayout.LayoutParams(-1,dp(280))
            discPanel.addView(picker,discLayout)
            discPanel.addView(text("RING: HUE · DISC: SATURATION + LIGHT",9f,Design.muted,true).apply { gravity=Gravity.CENTER; setPadding(0,dp(4),0,dp(4)) })
            fun arrangePicker() {
                body.removeAllViews()
                val discSize=if(pickerLandscape) (resources.configuration.screenHeightDp-170).coerceIn(160,240) else 280
                discLayout.height=dp(discSize); picker.layoutParams=discLayout
                body.orientation=if(pickerLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                if(pickerLandscape) {
                    body.addView(discPanel,LinearLayout.LayoutParams(dp(discSize),-2).apply { marginEnd=dp(20) })
                    body.addView(controls,LinearLayout.LayoutParams(0,-2,1f))
                } else {
                    body.addView(discPanel,LinearLayout.LayoutParams(-1,-2))
                    body.addView(controls,LinearLayout.LayoutParams(-1,-2))
                }
            }
            arrangePicker()
            body.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
                val nowLandscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
                if(nowLandscape!=pickerLandscape) {
                    pickerLandscape=nowLandscape
                    arrangePicker()
                }
            }
        } else body.addView(controls)
        controls.addView(preview,LinearLayout.LayoutParams(-1,dp(30)))
        controls.addView(space(10)); controls.addView(readout); controls.addView(space(16))
        fun slider(label: String,max: Int,initial: Int,format: (Int)->String,onChange: (Int)->Unit): SeekBar {
            val t=text("$label  ${format(initial)}",11f,Design.muted,true); controls.addView(t)
            return SeekBar(this).apply {
                this.max=max; progress=initial; contentDescription=label
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) { t.text="$label  ${format(p)}"; if(user) onChange(p) }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                controls.addView(this,LinearLayout.LayoutParams(-1,dp(48)))
            }
        }
        if(!lamp.colorCapable && lamp.dimmable) slider("BRIGHTNESS",254,bri,{ "${(it/254f*100).roundToInt()}%" }) { bri=it; updatePreview(); schedule() }
        else if(!lamp.dimmable) controls.addView(Switch(this).apply { text="Power"; isChecked=lamp.on; setOnCheckedChangeListener { _,on -> bri=if(on)254 else 0; updatePreview(); schedule() } })
        if(lamp.temperatureCapable) {
            temperature=300.coerceIn(lamp.minMired,lamp.maxMired)
            slider("WHITE TEMPERATURE",lamp.maxMired-lamp.minMired,temperature-lamp.minMired,{ "${1_000_000/(it+lamp.minMired)} K" }) { temperature=it+lamp.minMired; whiteMode=true; updatePreview(); schedule() }
        }
        updatePreview()
        val title=row()
        title.addView(text(lamp.name,18f,Design.ink).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,-2,1f))
        title.addView(GlyphView(this,"close").apply { contentDescription="Close lamp controls"; setOnClickListener { dialog?.dismiss() } },LinearLayout.LayoutParams(dp(48),dp(48)))
        val d=AlertDialog.Builder(this).setCustomTitle(title).setView(ScrollView(this).apply { addView(body) }).create()
        d.setOnDismissListener { flushPending(); closed=true }
        show(d)
    }
    private fun saveScene() {
        if(session.busy || !session.online) return
        val active=session.state.scenes.find { it.id==session.activeId }
        if(active?.locked==true) { toast("This scene is locked by the bridge and cannot be edited."); return }
        if(session.state.lamps.isEmpty()) return
        nameDialog(if(active==null) "Save a new scene" else "Save scene",active?.name.orEmpty()) { name ->
            if(active==null) session.change("$name saved",scenes=true) { api -> api.create(name,session.state.lamps.map { it.id }) }
            else session.change("$name saved",scenes=true) { api ->
                if(name!=active.name) api.rename(active.id,name)
                api.overwrite(active.id); active.id
            }
        }
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
