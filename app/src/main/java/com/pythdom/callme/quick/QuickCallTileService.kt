package com.pythdom.callme.quick

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pythdom.callme.R

/**
 * Quick-settings tile: tap = "call me in N min, by X" using the tile preset.
 * Long-press opens the preset settings (QuickCallConfigActivity handles QS_TILE_PREFERENCES).
 */
class QuickCallTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val preset = QuickCallStore.tile(this)
        if (preset == null) {
            val intent = Intent(this, QuickCallConfigActivity::class.java)
                .putExtra(QuickCallConfigActivity.EXTRA_TILE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            QuickCallTrigger.fire(this, preset)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val preset = QuickCallStore.tile(this)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_call_notification)
        tile.label = getString(R.string.tile_label)
        tile.subtitle = preset?.label ?: "Tap to set up"
        tile.state = if (preset == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    companion object {
        /** Asks the system to refresh the tile after its preset changed. */
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, QuickCallTileService::class.java))
        }
    }
}
