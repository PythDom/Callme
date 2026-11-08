package com.pythdom.callme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.pythdom.callme.quick.QuickCallPreset
import org.junit.Rule
import org.junit.Test

class QuickPresetsRowSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    private fun preset(name: String, delay: Int) =
        QuickCallPreset(1, "k", name, "+32470123456", "Mobile", null, delay)

    @Test fun presetsRow() = paparazzi.snapshot {
        MaterialTheme {
            Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                QuickPresetsRow(
                    listOf(preset("Sophie Martin", 5), preset("Thomas Dubois", 2), preset("Maman", 15), null),
                    enabled = true,
                    onChanged = {},
                )
            }
        }
    }
}
