package com.everyonesniche.titantools.rotate

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The "Rotation" Quick Settings tile, there while the Rotation tool is on: a tap flips portrait and landscape. */
class RotationTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        show(null)
    }

    override fun onClick() {
        super.onClick()
        Rotation.flip(this) { message -> show(message) }
    }

    private fun show(message: String?) {
        val tile = qsTile ?: return
        val label = if (message == "Portrait" || message == "Landscape") message else Rotation.label(this)
        tile.state = if (label == "Landscape") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = label
        tile.updateTile()
    }
}
