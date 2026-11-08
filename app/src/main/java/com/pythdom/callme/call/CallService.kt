package com.pythdom.callme.call

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import com.pythdom.callme.Config
import com.pythdom.callme.R
import com.pythdom.callme.data.CallerInfo
import com.pythdom.callme.data.ContactsRepository
import com.pythdom.callme.data.ScheduledCall
import com.pythdom.callme.scheduling.CallScheduler
import org.json.JSONObject
import kotlin.random.Random

/**
 * Foreground service that owns a fake call from the first ring to hang-up:
 * ringtone/vibration, the 20 s missed-call timeout, the mumbling voice once
 * answered, earpiece/speaker routing and the single callback.
 */
class CallService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager

    private var call: ScheduledCall? = null
    private var caller: CallerInfo? = null

    private var ringtone: Ringtone? = null
    private var vibrating = false
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var proximityLock: PowerManager.WakeLock? = null
    private var screenOffReceiverRegistered = false

    private val missedTimeout = Runnable { onMissed() }

    /** Pressing the power button silences the ringer, like a real call. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (CallManager.state.value is CallState.Ringing) silenceRinger()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        CallManager.service = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val json = intent?.getStringExtra(EXTRA_CALL)
        if (intent?.action != ACTION_INCOMING || json == null) {
            if (this.call == null) stopSelf()
            return START_NOT_STICKY
        }
        val call = ScheduledCall.fromJson(JSONObject(json))
        if (this.call != null) {
            // Another fake call is already ringing or in progress: postpone this one.
            CallScheduler.schedule(this, call.copy(triggerAt = System.currentTimeMillis() + 60_000))
            // Every startForegroundService() must be answered by startForeground(), or Android
            // kills the app. Re-post the current call's notification.
            val current = caller ?: ContactsRepository.resolveCaller(this, call)
            val notification = when (val state = CallManager.state.value) {
                is CallState.Active -> CallNotifications.ongoing(this, current, System.currentTimeMillis() -
                    (SystemClock.elapsedRealtime() - state.connectedAt))
                else -> CallNotifications.incoming(this, current)
            }
            startForeground(CallNotifications.CALL_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            if (CallManager.state.value !is CallState.Ringing && CallManager.state.value !is CallState.Active) {
                stopForeground(STOP_FOREGROUND_REMOVE) // the current call is already ending
            }
            return START_NOT_STICKY
        }
        val caller = ContactsRepository.resolveCaller(this, call)
        this.call = call
        this.caller = caller

        // State first: the full-screen intent may start CallActivity as soon as the notification posts.
        CallManager.update(CallState.Ringing(caller))
        startForeground(
            CallNotifications.CALL_NOTIFICATION_ID,
            CallNotifications.incoming(this, caller),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )
        startRinging()
        return START_NOT_STICKY
    }

    // ---- Ringing -------------------------------------------------------------------------

    private fun startRinging() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "callme:ringing").apply {
            acquire(Config.RING_DURATION_MS + 5_000)
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        screenOffReceiverRegistered = true

        // SPEC §8.3: ring only in Normal mode; vibrate only in Vibrate / Silent / Do Not Disturb.
        val dnd = getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL
        val normal = audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL && !dnd

        if (normal) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }
        if (!normal || vibrateWhenRinging()) {
            // In silent/DND the ringtone usage would be muted by the system; the alarm usage is not.
            val usage = if (normal) VibrationAttributes.USAGE_RINGTONE else VibrationAttributes.USAGE_ALARM
            vibrator().vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0),
                VibrationAttributes.createForUsage(usage),
            )
            vibrating = true
        }
        handler.postDelayed(missedTimeout, Config.RING_DURATION_MS)
    }

    private fun vibrateWhenRinging(): Boolean =
        // Hidden setting; may be unreadable on some builds. Samsung's default is on.
        runCatching { Settings.System.getInt(contentResolver, "vibrate_when_ringing", 1) == 1 }
            .getOrDefault(true)

    fun silenceRinger() {
        ringtone?.stop()
        ringtone = null
        if (vibrating) {
            vibrator().cancel()
            vibrating = false
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(missedTimeout)
        silenceRinger()
        if (screenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiverRegistered = false
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun vibrator() = getSystemService(VibratorManager::class.java).defaultVibrator

    // ---- Call outcomes -------------------------------------------------------------------

    fun answer() {
        val caller = caller ?: return
        if (CallManager.state.value !is CallState.Ringing) return
        stopRinging()

        val connectedAt = SystemClock.elapsedRealtime()
        CallManager.update(CallState.Active(caller, connectedAt))
        getSystemService(NotificationManager::class.java).notify(
            CallNotifications.CALL_NOTIFICATION_ID,
            CallNotifications.ongoing(this, caller, System.currentTimeMillis()),
        )
        startVoice()

        setProximityScreenOff(true)
    }

    /** Blank the screen when the phone is held to the ear (not on speaker), like a real call. */
    private fun setProximityScreenOff(enabled: Boolean) {
        if (enabled) {
            if (proximityLock?.isHeld == true) return
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callme:proximity")
                    .apply { acquire(60 * 60 * 1000L) }
            }
        } else {
            proximityLock?.takeIf { it.isHeld }?.release()
            proximityLock = null
        }
    }

    fun decline() {
        if (CallManager.state.value !is CallState.Ringing) return
        stopRinging()
        scheduleCallbackIfNeeded()
        finish(null)
    }

    private fun onMissed() {
        if (CallManager.state.value !is CallState.Ringing) return
        stopRinging()
        caller?.let { CallNotifications.postMissed(this, it) }
        scheduleCallbackIfNeeded()
        finish(null)
    }

    fun hangUp() {
        val state = CallManager.state.value as? CallState.Active ?: return
        stopVoice()
        setProximityScreenOff(false)
        finish(CallState.Ended(state.caller, SystemClock.elapsedRealtime() - state.connectedAt))
    }

    /** SPEC §8.5: a declined or missed call calls back once, 90 s later. Callbacks don't chain. */
    private fun scheduleCallbackIfNeeded() {
        val call = call ?: return
        if (call.isCallback) return
        CallScheduler.schedule(
            this,
            call.copy(
                id = java.util.UUID.randomUUID().toString(),
                triggerAt = System.currentTimeMillis() + Config.CALLBACK_DELAY_MS,
                isCallback = true,
            ),
        )
    }

    /** Shows [endedState] ("Call ended") briefly if given, then tears everything down. */
    private fun finish(endedState: CallState.Ended?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (endedState != null) {
            CallManager.update(endedState)
            handler.postDelayed({ CallManager.update(CallState.Idle); stopSelf() }, 1_500)
        } else {
            CallManager.update(CallState.Idle)
            stopSelf()
        }
    }

    // ---- In-call audio -------------------------------------------------------------------

    private fun startVoice() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { audioManager.requestAudioFocus(it) }
        // Communication mode routes to the earpiece (or a connected headset), like a real call.
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                resources.openRawResourceFd(R.raw.mumble).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                isLooping = true
                prepare()
                // Start somewhere random so every call doesn't sound the same.
                seekTo(Random.nextInt(0, maxOf(1, duration - 2_000)))
                start()
            }
        }.getOrNull()
    }

    private fun stopVoice() {
        player?.runCatching { stop(); release() }
        player = null
        audioManager.clearCommunicationDevice()
        audioManager.mode = AudioManager.MODE_NORMAL
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    fun toggleSpeaker() {
        val state = CallManager.state.value as? CallState.Active ?: return
        val speaker = !state.speaker
        if (speaker) {
            audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { audioManager.setCommunicationDevice(it) }
        } else {
            audioManager.clearCommunicationDevice()
        }
        setProximityScreenOff(!speaker)
        CallManager.update(state.copy(speaker = speaker))
    }

    /** Cosmetic: there is no microphone involved, but the button behaves like the real one. */
    fun toggleMute() {
        val state = CallManager.state.value as? CallState.Active ?: return
        CallManager.update(state.copy(muted = !state.muted))
    }

    override fun onDestroy() {
        stopRinging()
        stopVoice()
        setProximityScreenOff(false)
        handler.removeCallbacksAndMessages(null)
        if (CallManager.service === this) CallManager.service = null
        if (CallManager.state.value !is CallState.Idle) CallManager.update(CallState.Idle)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_INCOMING = "com.pythdom.callme.action.INCOMING"
        private const val EXTRA_CALL = "call"

        fun startIncoming(context: Context, call: ScheduledCall) {
            context.startForegroundService(
                Intent(context, CallService::class.java)
                    .setAction(ACTION_INCOMING)
                    .putExtra(EXTRA_CALL, call.toJson().toString()),
            )
        }
    }
}
