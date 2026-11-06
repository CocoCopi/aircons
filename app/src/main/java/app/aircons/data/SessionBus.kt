package app.aircons.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionBus {
    private val _link = MutableStateFlow(LinkState.IDLE)
    val link = _link.asStateFlow()

    private val _linkDetail = MutableStateFlow<String?>(null)
    val linkDetail = _linkDetail.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName = _deviceName.asStateFlow()

    private val _battery = MutableStateFlow(PodBattery())
    val battery = _battery.asStateFlow()

    private val _ear = MutableStateFlow(EarState())
    val ear = _ear.asStateFlow()

    private val _motion = MutableStateFlow(MotionSample.ZERO)
    val motion = _motion.asStateFlow()

    private val _frame = MutableStateFlow(ControlFrame.ZERO)
    val frame = _frame.asStateFlow()

    private val _lastStem = MutableStateFlow<StemEvent?>(null)
    val lastStem = _lastStem.asStateFlow()

    private val _pps = MutableStateFlow(0)
    val pps = _pps.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive = _sessionActive.asStateFlow()

    private val _mode = MutableStateFlow(ControlMode.HEAD)
    val mode = _mode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _script = MutableStateFlow("built-in")
    val script = _script.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationState.IDLE)
    val calibration = _calibration.asStateFlow()

    fun setLink(state: LinkState, detail: String? = null) {
        _link.value = state
        _linkDetail.value = detail
        if (state != LinkState.ERROR) _error.value = null
    }

    fun setError(message: String) {
        _error.value = message
        _link.value = LinkState.ERROR
        _linkDetail.value = message
    }

    fun clearError() {
        _error.value = null
        if (_link.value == LinkState.ERROR) {
            _link.value = LinkState.IDLE
            _linkDetail.value = null
        }
    }

    fun setDevice(name: String?) {
        _deviceName.value = name
    }

    fun setBattery(value: PodBattery) {
        _battery.value = value
    }

    fun setEar(value: EarState) {
        _ear.value = value
    }

    fun setMotion(value: MotionSample) {
        _motion.value = value
    }

    fun setFrame(value: ControlFrame) {
        _frame.value = value
    }

    fun setStem(value: StemEvent) {
        _lastStem.value = value
    }

    fun setPps(value: Int) {
        _pps.value = value
    }

    fun setSessionActive(value: Boolean) {
        _sessionActive.value = value
        if (!value) {
            _frame.value = ControlFrame.ZERO
            _motion.value = MotionSample.ZERO
            _calibration.value = CalibrationState.IDLE
        }
    }

    fun setMode(value: ControlMode) {
        _mode.value = value
    }

    fun setScript(value: String) {
        _script.value = value
    }

    fun setCalibration(value: CalibrationState) {
        _calibration.value = value
    }

    fun resetSession() {
        _link.value = LinkState.IDLE
        _linkDetail.value = null
        _deviceName.value = null
        _battery.value = PodBattery()
        _ear.value = EarState()
        _motion.value = MotionSample.ZERO
        _frame.value = ControlFrame.ZERO
        _pps.value = 0
        _sessionActive.value = false
        _calibration.value = CalibrationState.IDLE
    }
}
