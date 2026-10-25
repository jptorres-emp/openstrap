package org.openstrap.app.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.device.ConnectionState

/**
 * The strap status button's tap on a stale sync (widgets spec 4, 5): syncs in place, no activity. Connected, it's the same
 * sync as pulling Today down. Away, it reconnects as the app does in the background (a Disconnect the user tapped
 * holds), and connecting syncs on its own. The line reads Syncing while it runs; [org.openstrap.app.device.WidgetUpdater]
 * refreshes the widgets as it starts and ends.
 */
class SyncNowAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            val strap = (context.applicationContext as OpenStrapApp).container.strap
            when {
                strap.connection.value is ConnectionState.Connected -> strap.syncNow()
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED -> strap.backgroundConnect()
            }
        } catch (e: Exception) {
            Log.w("OpenStrap", "widgets: sync from the status line failed: $e")
        }
    }
}
