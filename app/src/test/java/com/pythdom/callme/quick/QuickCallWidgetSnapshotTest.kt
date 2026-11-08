package com.pythdom.callme.quick

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.pythdom.callme.R
import org.junit.Rule
import org.junit.Test

class QuickCallWidgetSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT),
        theme = "Theme.Callme.Widget",
    )

    @Test fun configuredWidget() {
        val widget = paparazzi.inflate<View>(R.layout.widget_quick_call)
        widget.findViewById<TextView>(R.id.widget_name).text = "Sophie Martin"
        widget.findViewById<TextView>(R.id.widget_delay).text = "Call me in 5 min"
        val frame = FrameLayout(paparazzi.context).apply {
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF3D5A80.toInt()) // stand-in for a wallpaper
            addView(widget, FrameLayout.LayoutParams(560, 180))
        }
        paparazzi.snapshot(frame)
    }
}
