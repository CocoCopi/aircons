package app.aircons.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
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
import java.io.IOException

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

    private var packetWindowStart = 0L
    private var packetWindowCount = 0

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
                val newSocket = createL2capSocket(adapter, device)
                socket = newSocket
                newSocket.connect()
                listener.onLink("AAP link open")
                openSession()
                readLoop(newSocket)
            } catch (e: SecurityException) {
                if (generation == session) {
                    fail("Bluetooth permission missing. Grant Nearby devices and try again.")
                }
            } catch (e: Throwable) {
                if (generation == session && running) {
                    val detail = e.localizedMessage ?: e.javaClass.simpleName
                    fail(
                        "Could not open the AirPods control link ($detail). " +
                            "Make sure the pods are connected to this phone and not owned by another device."
                    )
                }
            } finally {
                if (generation == session) running = false
            }
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
                    offset = chunk.size
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
            val charging = status == 0x01
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
        return (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
    }

    private fun createL2capSocket(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
        val uuid = ParcelUuid.fromString(AapPackets.SERVICE_UUID)
        val type = 3
        val specs = listOf(
            arrayOf(adapter, device, type, true, true, AapPackets.PSM, uuid),
            arrayOf(device, type, true, true, AapPackets.PSM, uuid),
            arrayOf(device, type, 1, true, true, AapPackets.PSM, uuid),
            arrayOf(type, 1, true, true, device, AapPackets.PSM, uuid),
            arrayOf(type, true, true, device, AapPackets.PSM, uuid)
        )
        var last: Throwable? = null
        for (params in specs) {
            try {
                val types = params.map {
                    it::class.javaPrimitiveType ?: it::class.java
                }.toTypedArray()
                val constructor = BluetoothSocket::class.java.getDeclaredConstructor(*types)
                constructor.isAccessible = true
                return constructor.newInstance(*params) as BluetoothSocket
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("No compatible L2CAP socket constructor")
    }

    companion object {
        private const val TAG = "AirConsAap"
    }
}
