package com.pythdom.callme.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.pythdom.callme.call.CallState
import com.pythdom.callme.data.CallerInfo
import org.junit.Rule
import org.junit.Test

/**
 * Renders the call screens on the JVM (no device needed).
 * Record:  ./gradlew recordPaparazziDebug   Verify: ./gradlew verifyPaparazziDebug
 */
class CallScreenSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, showSystemUi = false)

    // Decoded lazily: native graphics are only available once Paparazzi has started.
    private val withPhoto by lazy {
        val photo = javaClass.classLoader!!.getResourceAsStream("face.jpg").use { BitmapFactory.decodeStream(it) }
        CallerInfo("Sophie Martin", "+32 470 12 34 56", "Mobile", photo)
    }
    private val noPhoto = CallerInfo("Thomas Dubois", "+32 475 98 76 54", "Mobile", null)

    @Test fun incomingWithPhoto() = paparazzi.snapshot { CallScreen(CallState.Ringing(withPhoto)) }

    @Test fun incomingWithoutPhoto() = paparazzi.snapshot { CallScreen(CallState.Ringing(noPhoto)) }

    @Test fun inCall() = paparazzi.snapshot {
        CallScreen(CallState.Active(withPhoto, SystemClock.elapsedRealtime() - 83_000, speaker = true))
    }

    @Test fun callEnded() = paparazzi.snapshot { CallScreen(CallState.Ended(withPhoto, 83_000)) }
}
