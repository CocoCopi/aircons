package app.aircons.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import app.aircons.data.Bud
import app.aircons.data.EarState
import app.aircons.data.PodBattery
import app.aircons.data.StemPress
import app.aircons.data.StemEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AapClient(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onLink(message: String?)
        fun onBattery(battery: PodBattery)
        fun onEar(ear: EarState)
        fun onRawMotion(raw: RawMotion)
        fun onStem(event: StemEvent)
        fun onPacketsPerSecond(value: Int)
        fun onFailure(message: String)
    }

    data class RawMotion(
        val o1: Int,
        val o2: Int,
        val o3: Int,
        val accelH: Int,
        val accelV: Int,
        val timestampMs: Long
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var running = false

    @Volatile
    private var alternate = true

    @Volatile
    private var lastHeadPacketAt = 0L

    @Volatile
    private var lastStartSentAt = 0L

    private var socket: BluetoothSocket? = null
    private var worker: Job? = null
    private var watchdog: Job? = null
    private var generation = 0

    @Volatile
    private var socketPath = "none"

    private var packetWindowStart = 0L
    private var packetWindowCount = 0
    private var hiddenApiPrepared = false

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, alternatePackets: Boolean) {
        close()
        val session = ++generation
        running = true
        alternate = alternatePackets
        listener.onLink("Opening L2CAP 4097")
        worker = scope.launch {
            try {
                val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                    ?: throw IllegalStateException("This phone has no Bluetooth adapter.")
                var lastError: Throwable? = null
                var didTimeOut = false
                for (insecure in listOf(true, false)) {
                    if (!running || generation != session) return@launch
                    val newSocket = try {
                        createL2capSocket(adapter, device, insecure)
                    } catch (t: Throwable) {
                        Log.w(TAG, "L2CAP socket creation failed (${modeName(insecure)}): ${t.message}")
                        lastError = t
                        continue
                    }
                    socket = newSocket
                    val timedOut = AtomicBoolean(false)
                    val timeoutJob = scope.launch {
                        delay(CONNECT_TIMEOUT_MS)
                        if (!newSocket.isConnected) {
                            timedOut.set(true)
                            Log.w(TAG, "L2CAP connect timed out (${modeName(insecure)})")
                            runCatching { newSocket.close() }
                        }
                    }
                    try {
                        newSocket.connect()
                    } catch (t: Throwable) {
                        lastError = t
                    } finally {
                        timeoutJob.cancel()
                    }
                    if (!newSocket.isConnected) {
                        runCatching { newSocket.close() }
                        socket = null
                        if (timedOut.get()) didTimeOut = true
                        continue
                    }
                    Log.i(
                        TAG,
                        "AAP connected via $socketPath (${modeName(insecure)})"
                    )
                    listener.onLink("AAP link open")
                    openSession()
                    readLoop(newSocket)
                    return@launch
                }
                if (didTimeOut) {
                    throw IOException(
                        "timeout on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, " +
                            "${Build.MANUFACTURER} ${Build.MODEL})"
                    )
                }
                throw lastError ?: IOException("no L2CAP socket could be opened")
            } catch (e: SecurityException) {
                if (generation == session) {
                    fail("Bluetooth permission missing. Grant Nearby devices and try again.")
                }
            } catch (e: Throwable) {
                if (generation == session && running) {
                    Log.e(
                        TAG,
                        "AAP connection failed: $socketPath, ${e.javaClass.simpleName}: ${e.message}"
                    )
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        fail(timeoutMessage())
                    } else {
                        val detail = e.localizedMessage ?: e.javaClass.simpleName
                        fail(
                            "Could not open the AirPods control link ($detail). " +
                                "Make sure the pods are connected to this phone and not owned by another device."
                        )
                    }
                }
            } finally {
                if (generation == session) running = false
            }
        }
    }

    private fun modeName(insecure: Boolean) = if (insecure) "insecure" else "secure"

    private fun timeoutMessage(): String {
        val api = Build.VERSION.SDK_INT
        val phone = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return if (api >= 37) {
            "Timed out opening the AirPods control link on $phone (Android ${Build.VERSION.RELEASE}, " +
                "API $api). Android 17 should support this. Check that no iPhone is holding the " +
                "AirPods, keep both pods out of the case, then try again. " +
                "Or enable Settings → Phone motion fallback to play now."
        } else {
            "Timed out opening the AirPods control link on $phone (Android ${Build.VERSION.RELEASE}, " +
                "API $api). This Android build appears to be missing Google's March 2026 Bluetooth " +
                "L2CAP fix. AirPods motion needs Android 17, Pixel Android 16 with the April 2026+ " +
                "update, OxygenOS/ColorOS 16, or a rooted device with Xposed. " +
                "Or enable Settings → Phone motion fallback to play now."
        }
    }

    private suspend fun openSession() {
        val socket = socket ?: return
        delay(250)
        write(AapPackets.HANDSHAKE)
        delay(180)
        write(AapPackets.SET_FEATURE_FLAGS)
        delay(120)
        write(AapPackets.REQUEST_NOTIFICATIONS)
        delay(140)
        write(AapPackets.STEM_CUSTOMIZATION)
        delay(140)
        startTracking()
        delay(320)
        write(AapPackets.HANDSHAKE)
        delay(160)
        write(AapPackets.SET_FEATURE_FLAGS)
        delay(160)
        write(AapPackets.REQUEST_NOTIFICATIONS)
        if (!socket.isConnected) throw IOException("Socket closed during handshake")
        startWatchdog()
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive && running) {
                delay(2500)
                if (!running) break
                val now = System.currentTimeMillis()
                if (now - lastHeadPacketAt > 2200) {
                    if (now - lastStartSentAt > 1800) {
                        startTracking()
                    }
                }
            }
        }
    }

    fun startTracking() {
        lastStartSentAt = System.currentTimeMillis()
        write(AapPackets.startTracking(alternate))
    }

    fun stopTracking() {
        write(AapPackets.stopTracking(alternate))
    }

    fun requestBattery() {
        write(AapPackets.REQUEST_NOTIFICATIONS)
    }

    fun close() {
        generation++
        running = false
        watchdog?.cancel()
        worker?.cancel()
        try {
            socket?.let {
                if (it.isConnected) {
                    runCatching { write(AapPackets.stopTracking(alternate)) }
                    it.close()
                }
            }
        } catch (_: Throwable) {
        }
        socket = null
        watchdog = null
        worker = null
    }

    fun destroy() {
        close()
        scope.cancel()
    }

    private fun fail(message: String) {
        running = false
        listener.onFailure(message)
    }

    private fun write(packet: ByteArray): Boolean {
        val socket = socket ?: return false
        return try {
            synchronized(socket) {
                if (!socket.isConnected) return false
                socket.outputStream?.write(packet)
                socket.outputStream?.flush()
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Write failed: ${e.message}")
            if (running) {
                running = false
                listener.onFailure("The AirPods control link closed. Reconnect to continue.")
            }
            false
        }
    }

    private fun readLoop(activeSocket: BluetoothSocket) {
        val buffer = ByteArray(2048)
        val input = activeSocket.inputStream ?: return
        while (running && activeSocket.isConnected) {
            val count = try {
                input.read(buffer)
            } catch (e: Throwable) {
                if (running) {
                    running = false
                    listener.onFailure("The AirPods control link was interrupted.")
                }
                return
            }
            if (count < 0) {
                if (running) {
                    running = false
                    listener.onFailure("The AirPods control link closed.")
                }
                return
            }
            if (count == 0) continue
            try {
                processChunk(buffer.copyOf(count))
            } catch (e: Throwable) {
                Log.w(TAG, "Packet parse failed: ${e.message}")
            }
        }
    }

    private fun processChunk(chunk: ByteArray) {
        var offset = 0
        while (offset + 6 <= chunk.size) {
            if (!isHeaderAt(chunk, offset)) {
                offset++
                continue
            }
            when (chunk[offset + 4].toInt() and 0xFF) {
                0x17 -> {
                    if (offset + 70 > chunk.size) return
                    handleHeadTracking(chunk, offset)
                    offset += 70
                }

                0x04 -> {
                    if (offset + 7 > chunk.size) return
                    val count = chunk[offset + 6].toInt() and 0xFF
                    val length = 7 + (5 * count)
                    if (offset + length > chunk.size) return
                    handleBattery(chunk, offset, count)
                    offset += length
                }

                0x06 -> {
                    if (offset + 8 > chunk.size) return
                    handleEar(chunk, offset)
                    offset += 8
                }

                0x19 -> {
                    if (offset + 8 > chunk.size) return
                    handleStem(chunk, offset)
                    offset += 8
                }

                0x09 -> {
                    if (offset + 11 > chunk.size) return
                    offset += 11
                }

                else -> {
                    return
                }
            }
        }
    }

    private fun isHeaderAt(data: ByteArray, index: Int): Boolean {
        return index + 3 < data.size &&
            data[index] == 0x04.toByte() &&
            data[index + 1] == 0x00.toByte() &&
            data[index + 2] == 0x04.toByte() &&
            data[index + 3] == 0x00.toByte()
    }

    private fun handleHeadTracking(data: ByteArray, base: Int) {
        val now = System.currentTimeMillis()
        lastHeadPacketAt = now
        val o1 = readLe16(data, base + 43)
        val o2 = readLe16(data, base + 45)
        val o3 = readLe16(data, base + 47)
        val accelH = readLe16(data, base + 51)
        val accelV = readLe16(data, base + 53)
        listener.onRawMotion(RawMotion(o1, o2, o3, accelH, accelV, now))

        if (packetWindowStart == 0L) packetWindowStart = now
        packetWindowCount++
        if (now - packetWindowStart >= 1000) {
            val pps = ((packetWindowCount * 1000.0) / (now - packetWindowStart)).toInt()
            packetWindowStart = now
            packetWindowCount = 0
            listener.onPacketsPerSecond(pps)
        }
    }

    private fun handleBattery(data: ByteArray, base: Int, count: Int) {
        var left: Int? = null
        var right: Int? = null
        var case: Int? = null
        var leftCharging = false
        var rightCharging = false
        var caseCharging = false

        var cursor = base + 7
        repeat(count) {
            if (cursor + 4 >= data.size) return@repeat
            val component = data[cursor].toInt() and 0xFF
            val level = data[cursor + 2].toInt() and 0xFF
            val status = data[cursor + 3].toInt() and 0xFF
            val charging = status == 0x01 || status == 0x05
            when (component) {
                0x02 -> {
                    right = level
                    rightCharging = charging
                }

                0x04 -> {
                    left = level
                    leftCharging = charging
                }

                0x08 -> {
                    case = level.takeIf { it > 0 }
                    caseCharging = charging
                }
            }
            cursor += 5
        }
        listener.onBattery(
            PodBattery(
                left = left,
                right = right,
                case = case,
                leftCharging = leftCharging,
                rightCharging = rightCharging,
                caseCharging = caseCharging
            )
        )
    }

    private fun handleEar(data: ByteArray, base: Int) {
        val primaryIn = data[base + 6].toInt() == 0
        val secondaryIn = data[base + 7].toInt() == 0
        listener.onEar(EarState(leftInEar = primaryIn, rightInEar = secondaryIn))
    }

    private fun handleStem(data: ByteArray, base: Int) {
        val type = when (data[base + 6].toInt() and 0xFF) {
            0x05 -> StemPress.SINGLE
            0x06 -> StemPress.DOUBLE
            0x07 -> StemPress.TRIPLE
            0x08 -> StemPress.LONG
            else -> null
        } ?: return
        val bud = when (data[base + 7].toInt() and 0xFF) {
            0x01 -> Bud.LEFT
            0x02 -> Bud.RIGHT
            else -> Bud.UNKNOWN
        }
        listener.onStem(StemEvent(type, bud))
    }

    private fun readLe16(data: ByteArray, index: Int): Int {
        if (index + 1 >= data.size) return 0
        return (data[index].toInt() and 0xFF) or (data[index + 1].toInt() shl 8)
    }

    private data class SocketAttempt(
        val label: String,
        val args: Array<Any?>,
        val types: Array<Class<*>>
    )

    private fun createL2capSocket(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        insecure: Boolean
    ): BluetoothSocket {
        prepareHiddenApi()
        val psm = AapPackets.PSM
        val uuid = ParcelUuid.fromString(AapPackets.SERVICE_UUID)

        tryPublicSocket(device, psm, insecure)?.let {
            socketPath = "public API"
            return it
        }

        val hiddenName = if (insecure) "createInsecureL2capSocket" else "createL2capSocket"
        val hidden = runCatching {
            HiddenApiBypass.invoke(
                BluetoothDevice::class.java,
                device,
                hiddenName,
                psm
            ) as? BluetoothSocket
        }.getOrNull()
        if (hidden != null) {
            socketPath = hiddenName
            return hidden
        }

        val auth = !insecure
        val attempts = listOf(
            socketAttempt("device-first", device, 3, auth, auth, psm, uuid),
            socketAttempt("adapter-first", adapter, device, 3, auth, auth, psm, uuid),
            socketAttempt("old fd-first", 3, -1, auth, auth, device, psm, uuid),
            socketAttempt("old direct", 3, -1, auth, auth, device, psm, null),
            socketAttempt("old fd-one", 3, 1, auth, auth, device, psm, uuid),
            socketAttempt("five-arg", 3, auth, auth, device, psm, uuid)
        )
        val failures = mutableListOf<String>()
        for (attempt in attempts) {
            try {
                val socket = HiddenApiBypass.newInstance(
                    BluetoothSocket::class.java,
                    *attempt.args
                ) as? BluetoothSocket
                if (socket != null) {
                    socketPath = attempt.label
                    return socket
                }
                failures += "${attempt.label}: not a BluetoothSocket"
            } catch (t: Throwable) {
                failures += "${attempt.label}: ${t.javaClass.simpleName}"
            }
        }
        Log.e(TAG, "L2CAP socket attempts (${modeName(insecure)}): ${failures.joinToString("; ")}")
        throw IllegalStateException(
            "Android ${Build.VERSION.RELEASE} blocked the private Classic L2CAP socket. " +
                "Please report this build with a logcat capture."
        )
    }

    private fun tryPublicSocket(
        device: BluetoothDevice,
        psm: Int,
        insecure: Boolean
    ): BluetoothSocket? {
        return runCatching {
            val settingsClass = Class.forName("android.bluetooth.BluetoothSocketSettings")
            val builderClass = Class.forName("android.bluetooth.BluetoothSocketSettings\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setSocketType", Int::class.javaPrimitiveType)
                .invoke(builder, 3)
            builderClass.getMethod("setL2capPsm", Int::class.javaPrimitiveType)
                .invoke(builder, psm)
            builderClass.getMethod("setAuthenticationRequired", Boolean::class.javaPrimitiveType)
                .invoke(builder, !insecure)
            builderClass.getMethod("setEncryptionRequired", Boolean::class.javaPrimitiveType)
                .invoke(builder, !insecure)
            val settings = builderClass.getMethod("build").invoke(builder)
            val create = BluetoothDevice::class.java.getMethod(
                "createUsingSocketSettings",
                settingsClass
            )
            create.invoke(device, settings) as BluetoothSocket
        }.getOrNull()
    }

    private fun prepareHiddenApi() {
        if (hiddenApiPrepared) return
        hiddenApiPrepared = true
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/bluetooth/BluetoothSocket;",
                "Landroid/bluetooth/BluetoothDevice;"
            )
        }.onFailure {
            Log.w(TAG, "Hidden API exemption failed: ${it.message}")
        }
    }

    private fun socketAttempt(label: String, vararg values: Any?): SocketAttempt {
        val types = values.map<Any?, Class<*>> { value ->
            when (value) {
                is Int -> Int::class.javaPrimitiveType!!
                is Boolean -> Boolean::class.javaPrimitiveType!!
                null -> ParcelUuid::class.java
                else -> value.javaClass
            }
        }.toTypedArray()
        return SocketAttempt(label, arrayOf(*values), types)
    }

    companion object {
        private const val TAG = "AirConsAap"
        private const val CONNECT_TIMEOUT_MS = 6_000L
    }
}
