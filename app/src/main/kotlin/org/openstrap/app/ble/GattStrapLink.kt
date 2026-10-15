package org.openstrap.app.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.openstrap.protocol.StrapLink
import java.util.UUID

/** Strap GATT layout (docs/protocol/01 §1.1). */
object StrapUuids {
    private fun huami(id: Int): UUID = UUID.fromString("0000%04x-0000-3512-2118-0009af100700".format(id))
    private fun sig(id: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(id))

    val MAIN_SERVICE = sig(0xFEE0)
    val CHANNEL_CONTROL = huami(0x0016)
    val CHANNEL_DATA = huami(0x0017)
    val BATTERY = huami(0x0006)
    val USER_SETTINGS = huami(0x0008)
    val CURRENT_TIME = sig(0x2A2B)

    val DEVICE_INFO_SERVICE = sig(0x180A)
    val SOFTWARE_REVISION = sig(0x2A28)
    val SERIAL_NUMBER = sig(0x2A25)

    val HEART_RATE_SERVICE = sig(0x180D)
    val HR_MEASUREMENT = sig(0x2A37)
    val HR_CONTROL = sig(0x2A39)
}

/** Adapts [GattClient] to the protocol module's [StrapLink]. */
class GattStrapLink(private val gatt: GattClient) : StrapLink {
    override var payloadMtu: Int = 20

    override suspend fun writeControl(data: ByteArray) =
        gatt.write(StrapUuids.MAIN_SERVICE, StrapUuids.CHANNEL_CONTROL, data)

    override suspend fun writeData(data: ByteArray) =
        gatt.write(StrapUuids.MAIN_SERVICE, StrapUuids.CHANNEL_DATA, data)

    override val controlNotifications: Flow<ByteArray> =
        gatt.notifications.filter { it.characteristic == StrapUuids.CHANNEL_CONTROL }.map { it.value }

    override val dataNotifications: Flow<ByteArray> =
        gatt.notifications.filter { it.characteristic == StrapUuids.CHANNEL_DATA }.map { it.value }

    suspend fun enableChannelNotifications() {
        gatt.enableNotifications(StrapUuids.MAIN_SERVICE, StrapUuids.CHANNEL_CONTROL)
        gatt.enableNotifications(StrapUuids.MAIN_SERVICE, StrapUuids.CHANNEL_DATA)
    }
}
