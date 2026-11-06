package app.aircons.core

import android.content.Context
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.PluginInfo
import app.aircons.data.PluginManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object PluginManager {
    const val RAW_BASE = "https://raw.githubusercontent.com/CocoCopi/aircons/main/"
    const val REPO_URL = "https://github.com/CocoCopi/aircons"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()

    private data class Compiled(val function: Function, val scope: Scriptable)

    private lateinit var appContext: Context
    private var active: Compiled? = null
    private var activeId: String? = null

    private val BUILTIN = PluginInfo(
        id = "builtin",
        name = "Built-in mapping",
        version = "native",
        description = "The tuned native curve. Always available, zero latency.",
        file = ""
    )

    private val _available = MutableStateFlow(listOf(BUILTIN))
    val available = _available.asStateFlow()

    private val _manifestVersion = MutableStateFlow(0)
    val manifestVersion = _manifestVersion.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val _status = MutableStateFlow("Built-in mapping")
    val status = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _activeName = MutableStateFlow("Built-in mapping")
    val activeName = _activeName.asStateFlow()

    val builtin = BUILTIN

    fun init(context: Context) {
        appContext = context.applicationContext
        loadCachedManifest()
    }

    fun syncNow() {
        if (_syncing.value) return
        _syncing.value = true
        scope.launch {
            log("Syncing scripts from GitHub…")
            try {
                val manifestText = httpText(RAW_BASE + "plugins/manifest.json")
                val root = JSONObject(manifestText)
                val plugins = root.optJSONArray("plugins") ?: throw IllegalStateException("No plugins in manifest")
                val parsed = mutableListOf<PluginInfo>()
                for (i in 0 until plugins.length()) {
                    val item = plugins.getJSONObject(i)
                    val id = item.getString("id")
                    val file = item.getString("file")
                    if (!safeSegment(id) || !safeSegment(file)) {
                        log("Skipped unsafe script entry '${id}/${file}'.")
                        continue
                    }
                    val info = PluginInfo(
                        id = id,
                        name = item.getString("name"),
                        version = item.optString("version", "1"),
                        description = item.optString("description", ""),
                        file = file
                    )
                    val source = httpText(RAW_BASE + "plugins/" + info.file)
                    val target = File(scriptsDir(), info.id + ".js")
                    target.writeText(source)
                    parsed.add(info)
                }
                val manifest = PluginManifest(root.optInt("version", 1), parsed)
                appContext.getSharedPreferences("aircons.plugins", Context.MODE_PRIVATE)
                    .edit()
                    .putString("manifest", manifestText)
                    .apply()
                _manifestVersion.value = manifest.version
                _available.value = listOf(BUILTIN) + parsed + customInfo()
                log("Synced ${parsed.size} scripts (manifest v${manifest.version}).")
            } catch (t: Throwable) {
                log("Sync failed: ${t.localizedMessage ?: t.javaClass.simpleName}")
            } finally {
                _syncing.value = false
            }
        }
    }

    fun activate(id: String?): Boolean {
        val targetId = id ?: BUILTIN.id
        if (targetId == BUILTIN.id) {
            synchronized(lock) {
                active = null
                activeId = null
            }
            _activeName.value = "Built-in mapping"
            _status.value = "Built-in mapping"
            return true
        }
        if (targetId == activeId && active != null) return true

        val file = File(scriptsDir(), "$targetId.js")
        if (!file.exists()) {
            _status.value = "Script '$targetId' is not downloaded."
            log("Activate failed: ${file.name} missing.")
            return false
        }
        return compileInternal(file.readText(), file.name, targetId)
    }

    fun activateSource(source: String, label: String = "custom.js"): Boolean {
        val file = File(scriptsDir(), "custom.js")
        return try {
            file.parentFile?.mkdirs()
            file.writeText(source)
            val info = PluginInfo(
                id = "custom",
                name = "Live custom",
                version = "local",
                description = "Written in the AirCons lab.",
                file = "custom.js"
            )
            val current = _available.value.filterNot { it.id == "custom" }
            _available.value = current + info
            compileInternal(source, label, "custom")
        } catch (t: Throwable) {
            _status.value = "Could not save: ${t.localizedMessage}"
            false
        }
    }

    fun readScript(id: String?): String {
        val target = id ?: BUILTIN.id
        if (target == BUILTIN.id) return DEFAULT_SCRIPT
        val file = File(scriptsDir(), "$target.js")
        return if (file.exists()) file.readText() else DEFAULT_SCRIPT
    }

    fun map(
        mode: ControlMode,
        yaw: Float,
        pitch: Float,
        roll: Float,
        accelH: Float,
        accelV: Float,
        trigger: Boolean
    ): ControlFrame? {
        val compiled = synchronized(lock) { active } ?: return null
        return try {
            synchronized(lock) {
                val cx = RhinoContext.enter()
                try {
                    val input = cx.newObject(compiled.scope)
                    input.put("mode", input, mode.key)
                    input.put("yaw", input, yaw.toDouble())
                    input.put("pitch", input, pitch.toDouble())
                    input.put("roll", input, roll.toDouble())
                    input.put("accelH", input, accelH.toDouble())
                    input.put("accelV", input, accelV.toDouble())
                    input.put("trigger", input, trigger)
                    val result = compiled.function.call(
                        cx, compiled.scope, compiled.scope, arrayOf<Any>(input)
                    )
                    if (result !is Scriptable) return null
                    val lookX = number(result, "lookX")
                    val lookY = number(result, "lookY")
                    val steer = number(result, "steer")
                    val throttle = number(result, "throttle")
                    val fire = RhinoContext.toBoolean(ScriptableObject.getProperty(result, "fire")) || trigger
                    ControlFrame(
                        lookX = lookX.coerceIn(-1f, 1f),
                        lookY = lookY.coerceIn(-1f, 1f),
                        steer = steer.coerceIn(-1f, 1f),
                        throttle = throttle.coerceIn(-1f, 1f),
                        fire = fire,
                        active = true
                    )
                } finally {
                    RhinoContext.exit()
                }
            }
        } catch (t: Throwable) {
            _status.value = "Script error: ${t.localizedMessage ?: t.javaClass.simpleName}"
            log("Script error: ${t.localizedMessage ?: t.javaClass.simpleName}")
            synchronized(lock) {
                active = null
                activeId = null
            }
            null
        }
    }

    private fun compileInternal(source: String, label: String, id: String): Boolean {
        return try {
            val cx = RhinoContext.enter()
            try {
                cx.setOptimizationLevel(-1)
                val newScope = cx.initSafeStandardObjects()
                cx.evaluateString(newScope, source, label, 1, null)
                val fn = newScope.get("map", newScope)
                if (fn !is Function) throw IllegalArgumentException("script must define map(input)")
                synchronized(lock) {
                    active = Compiled(fn, newScope)
                    activeId = id
                }
                _activeName.value = "$label · ${id}"
                _status.value = "Loaded $label"
                log("Loaded $label")
                true
            } finally {
                RhinoContext.exit()
            }
        } catch (t: Throwable) {
            _status.value = "Load failed: ${t.localizedMessage ?: t.javaClass.simpleName}"
            log("Load failed: ${t.localizedMessage ?: t.javaClass.simpleName}")
            false
        }
    }

    private fun number(source: Scriptable, name: String): Float {
        val value = ScriptableObject.getProperty(source, name)
        return if (value is Number) value.toFloat() else 0f
    }

    private fun loadCachedManifest() {
        val custom = File(scriptsDir(), "custom.js")
        val customInfo = if (custom.exists()) {
            PluginInfo(
                id = "custom",
                name = "Live custom",
                version = "local",
                description = "Written in the AirCons lab.",
                file = "custom.js"
            )
        } else {
            null
        }
        val text = appContext.getSharedPreferences("aircons.plugins", Context.MODE_PRIVATE)
            .getString("manifest", null)
        if (text == null) {
            if (customInfo != null) _available.value = listOf(BUILTIN, customInfo)
            return
        }
        try {
            val root = JSONObject(text)
            val plugins = root.optJSONArray("plugins") ?: return
            val parsed = mutableListOf<PluginInfo>()
            for (i in 0 until plugins.length()) {
                val item = plugins.getJSONObject(i)
                val info = PluginInfo(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    version = item.optString("version", "1"),
                    description = item.optString("description", ""),
                    file = item.getString("file")
                )
                if (File(scriptsDir(), info.id + ".js").exists()) parsed.add(info)
            }
            if (customInfo != null) parsed.add(customInfo)
            if (parsed.isNotEmpty()) {
                _manifestVersion.value = root.optInt("version", 1)
                _available.value = listOf(BUILTIN) + parsed
            }
        } catch (_: Throwable) {
        }
    }

    private fun customInfo(): List<PluginInfo> {
        val custom = File(scriptsDir(), "custom.js")
        if (!custom.exists()) return emptyList()
        return listOf(
            PluginInfo(
                id = "custom",
                name = "Live custom",
                version = "local",
                description = "Written in the AirCons lab.",
                file = "custom.js"
            )
        )
    }

    private fun safeSegment(value: String): Boolean =
        value.isNotBlank() && value.length <= 40 && value.matches(Regex("[A-Za-z0-9._-]+")) &&
            !value.contains("..")

    private fun scriptsDir(): File = File(appContext.filesDir, "plugins").also { it.mkdirs() }

    private fun httpText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AirCons-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun log(line: String) {
        _logs.value = (_logs.value + "[${System.currentTimeMillis() % 100000}] $line").takeLast(40)
    }

    val DEFAULT_SCRIPT = """
        // AirCons live mapping.
        // Return lookX/lookY for head + sidearm, steer/throttle for wheel.
        function map(input) {
          var yaw = input.yaw / 30.0;
          var pitch = input.pitch / 24.0;
          var roll = input.roll / 28.0;
          return {
            lookX: Math.max(-1, Math.min(1, yaw)),
            lookY: Math.max(-1, Math.min(1, pitch)),
            steer: input.mode === "wheel" ? Math.max(-1, Math.min(1, roll)) : 0,
            throttle: 0,
            fire: input.trigger
          };
        }
    """.trimIndent()

}
