package org.openstrap.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.openstrap.protocol.auth.Auth
import org.openstrap.protocol.auth.AuthException
import org.openstrap.protocol.auth.AuthHandshake
import org.openstrap.protocol.crypto.ChannelCipher
import org.openstrap.protocol.services.ChannelVersion
import org.openstrap.protocol.services.ConnectCodec
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.services.ServiceList
import org.openstrap.protocol.transport.ChannelMessage
import org.openstrap.protocol.transport.Reassembler
import org.openstrap.protocol.transport.buildChunks

/**
 * The radio side the session needs. The Android app implements this on top of BluetoothGatt;
 * tests implement it with a simulated strap.
 */
interface StrapLink {
    /** ATT MTU − 3. 20 until the strap reports a larger one. */
    val payloadMtu: Int

    /** Write to FEE0/0016 (phone→device chunks, and the version query). */
    suspend fun writeControl(data: ByteArray)

    /** Write to FEE0/0017 (the phone's ACKs for incoming chunks). */
    suspend fun writeData(data: ByteArray)

    /** Notifications from 0016 (version reply, device ACKs). */
    val controlNotifications: Flow<ByteArray>

    /** Notifications from 0017 (device→phone chunks). */
    val dataNotifications: Flow<ByteArray>
}

class ProtocolException(message: String) : Exception(message)

/**
 * One authenticated channel connection (docs/protocol/01 §1.4 steps 6-10).
 * Call [start] once the link's notifications are enabled, then [handshake].
 */
class ChannelSession(
    private val link: StrapLink,
    private val log: (String) -> Unit = {},
) {
    private val sendLock = Mutex()
    private var nextSessionId = 0
    private var cipher: ChannelCipher? = null
    private var reader: Job? = null
    private var ponger: Job? = null

    var services: ServiceList = ServiceList(emptyMap())
        private set
    var version: ChannelVersion? = null
        private set
    val isAuthenticated get() = cipher != null

    private val _messages = MutableSharedFlow<ChannelMessage>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.SUSPEND)

    /** Every reassembled incoming message. */
    val messages: SharedFlow<ChannelMessage> = _messages

    fun messagesFor(endpoint: Int): Flow<ByteArray> = messages.filter { it.endpoint == endpoint }.mapNotNull { it.payload }

    private val reassembler = Reassembler(
        // Before the services list arrives we must accept replies on the services and auth endpoints.
        isKnownEndpoint = { ep -> ep == Endpoints.SERVICES || ep == Endpoints.AUTH || services.has(ep) },
        decrypt = { sid, data, len ->
            (cipher ?: throw ProtocolException("Encrypted message before auth")).decrypt(sid, data, len)
        },
        onMessage = { _messages.tryEmit(it) },
    )

    /** Keep-alive pings answered so far (ConnectModule 0x0015, 02 §11). */
    @Volatile var pings = 0
        private set

    fun start(scope: CoroutineScope) {
        reader = scope.launch {
            link.dataNotifications.collect { packet ->
                val ack = reassembler.accept(packet)
                if (ack != null) link.writeData(ack)
            }
        }
        // The strap pings with `03`; Zepp answers `04` at once, without asking for an ACK (jt7.java:428-445).
        ponger = scope.launch {
            messagesFor(Endpoints.CONNECT).filter { it.contentEquals(ConnectCodec.PING) }.collect {
                if (pings++ == 0) log("strap ping, answering pong")
                runCatching { send(Endpoints.CONNECT, ConnectCodec.PONG, wantReply = false) }.onFailure { e -> log("pong failed: $e") }
            }
        }
    }

    fun stop() {
        reader?.cancel()
        ponger?.cancel()
        cipher = null
    }

    /** Version query, services list, ECDH auth. Throws [AuthException] on a rejected key. */
    suspend fun handshake(authKey: ByteArray) {
        val v = withTimeout(5_000) {
            val versionReplies = link.controlNotifications.filter { it.isNotEmpty() && it.u8(0) == 0x02 }
            val reply = launchAndAwait(versionReplies) { link.writeControl(ChannelVersion.REQUEST) }
            ChannelVersion.parse(reply) ?: throw ProtocolException("Unexpected version reply ${reply.toHex()}")
        }
        version = v
        log("channel v${v.version}, encryptEnable=${v.encryptEnable}")
        if (v.version != 2 || v.encryptEnable == null) throw ProtocolException("Unsupported channel (v${v.version}, encryption ${v.encryptEnable})")

        services = ServiceList.parse(request(Endpoints.SERVICES, ServiceList.REQUEST, timeoutMs = 5_000) { it.u8(0) == 0x04 })
        log("services: " + services.endpoints.entries.joinToString { "%04x%s".format(it.key, if (it.value) "*" else "") })
        if (!services.has(Endpoints.AUTH)) throw ProtocolException("Strap does not offer the v2 auth endpoint")

        val hs = AuthHandshake(authKey)
        val step1 = authRequest(Auth.CMD_KEY_EXCHANGE, hs.step1Request)
        val step2 = authRequest(Auth.CMD_PROVE, hs.handleStep1Reply(step1))
        cipher = hs.handleStep2Reply(step2)
        log("authenticated")
    }

    private suspend fun authRequest(command: Int, payload: ByteArray): ByteArray {
        val reply = request(Endpoints.AUTH, payload, timeoutMs = 15_000) { Auth.stripReply(command, it) != null }
        return Auth.stripReply(command, reply)!!
    }

    /**
     * Sends [payload] to [endpoint], encrypting when the strap requires it for that endpoint. [wantReply] false
     * leaves ACK_REQ off the last chunk, for messages the strap doesn't acknowledge.
     */
    suspend fun send(endpoint: Int, payload: ByteArray, wantReply: Boolean = true) = sendLock.withLock {
        val sid = nextSessionId
        nextSessionId = (nextSessionId + 1) and 0xFF
        val c = cipher
        val encrypt = c != null && services.requiresEncryption(endpoint)
        val body = if (encrypt) c!!.encrypt(sid, payload) else payload
        for (chunk in buildChunks(endpoint, sid, body, payload.size, encrypt, link.payloadMtu, wantReply)) link.writeControl(chunk)
    }

    /** Sends and waits for the first reply on the same endpoint that satisfies [matches]. */
    suspend fun request(
        endpoint: Int,
        payload: ByteArray,
        timeoutMs: Long = 10_000,
        matches: (ByteArray) -> Boolean = { true },
    ): ByteArray = withTimeout(timeoutMs) {
        launchAndAwait(messagesFor(endpoint).filter(matches)) { send(endpoint, payload) }
    }

    /** Subscribes to [flow] before running [action], so a fast reply can't be missed. */
    private suspend fun <T> launchAndAwait(flow: Flow<T>, action: suspend () -> Unit): T =
        coroutineScope {
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { flow.first() }
            action()
            waiter.await()
        }
}
