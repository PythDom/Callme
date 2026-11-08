package com.pythdom.callme.call

import com.pythdom.callme.data.CallerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CallState {
    data object Idle : CallState
    data class Ringing(val caller: CallerInfo) : CallState
    /** [connectedAt] is SystemClock.elapsedRealtime() when the call was answered. */
    data class Active(
        val caller: CallerInfo,
        val connectedAt: Long,
        val speaker: Boolean = false,
        val muted: Boolean = false,
    ) : CallState
    data class Ended(val caller: CallerInfo, val durationMs: Long) : CallState
}

/**
 * In-process bridge between the UI / notification actions and the running [CallService].
 */
object CallManager {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    internal var service: CallService? = null

    internal fun update(state: CallState) {
        _state.value = state
    }

    fun isBusy() = _state.value !is CallState.Idle

    fun answer() = service?.answer()
    fun decline() = service?.decline()
    fun hangUp() = service?.hangUp()
    fun silenceRinger() = service?.silenceRinger()
    fun toggleSpeaker() = service?.toggleSpeaker()
    fun toggleMute() = service?.toggleMute()
}
