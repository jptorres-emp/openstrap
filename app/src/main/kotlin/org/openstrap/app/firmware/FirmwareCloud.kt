package org.openstrap.app.firmware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openstrap.protocol.firmware.FirmwareType
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** What the strap reports about itself; sent so the server can pick the matching image. */
data class StrapIdentity(
    val firmwareVersion: String,
    val hardwareVersion: String?,
    val serial: String?,
    val deviceSource: Int = DEVICE_SOURCE_GLOBAL,
    val productId: Int = PRODUCT_ID,
    /**
     * Version/flag pairs from Settings group DEVICE_INFORMATION (codemap 07 §1.4). OpenStrap doesn't read that
     * group yet, so these hold the values Zepp sends when the strap reports nothing.
     */
    val resources: StrapResources = StrapResources(),
    /** From the cloud device list; Zepp sends `-1` when it has none (codemap 07 §1.4). */
    val productionSource: Int = -1,
    /** The strap has no GPS, so Zepp's value stays unset (docs/protocol/03 §1.1). */
    val gpsVersion: String? = null,
) {
    companion object {
        const val DEVICE_SOURCE_GLOBAL = 10289411
        const val DEVICE_SOURCE_CHINA = 10289410
        const val PRODUCT_ID = 157
    }
}

/**
 * Group 0x00 keys 2-6 as `hasNewVersion` sends them (codemap 07 §1.4). Defaults are Zepp's own: `-1`, except the
 * font pair which starts at `0`. Base resource is always `-1`/`-1` in Zepp because of a field mix-up, even when
 * the strap reports one.
 */
data class StrapResources(
    val firmwareFlag: Int = -1,
    val fontVersion: Int = 0,
    val fontFlag: Int = 0,
    val resourceVersion: Int = -1,
    val resourceFlag: Int = -1,
    val baseResourceVersion: Int = -1,
    val baseResourceFlag: Int = -1,
    val extResourceVersion: Int = -1,
    val extResourceFlag: Int = -1,
)

/** One downloadable part of an update (docs/protocol/03 §1.2). */
data class FirmwarePart(
    val prefix: String,
    val url: String,
    val md5: String,
    val version: String,
    val length: Long,
    val flag: Int,
) {
    val type: Int? get() = FirmwareType.BY_PREFIX[prefix]
    val label: String get() = when (prefix) {
        "firmware" -> "Firmware"
        "resource" -> "Resources"
        "baseResource" -> "Base resources"
        "extResource" -> "Extra resources"
        "font" -> "Font"
        "gps" -> "GPS"
        else -> prefix
    }
}

data class UpdateCheck(
    /** True when the server says there's nothing to install. */
    val upToDate: Boolean,
    val forced: Boolean,
    val changeLog: String?,
    val parts: List<FirmwarePart>,
) {
    /** Parts in the order they should be installed, one DFU session each (§6, single-file path). */
    val installOrder: List<FirmwarePart>
        get() = parts.filter { it.type != null }.sortedBy { FirmwareType.SINGLE_ORDER.indexOf(it.type) }
}

class FirmwareCloudException(message: String) : IOException(message)

/**
 * Talks to Zepp's firmware service with the user's own app token. Nothing is sent unless the user asks
 * for a check; the only data sent is what the server needs to choose an image for this strap.
 */
class FirmwareCloud(private val cacheDir: File) {

    suspend fun check(apiBase: String, appToken: String, userId: String, strap: StrapIdentity): UpdateCheck =
        withContext(Dispatchers.IO) {
            val base = apiBase.trim().let { if (it.endsWith("/")) it else "$it/" }
            require(base.startsWith("https://")) { "The server address must start with https://" }
            val locale = Locale.getDefault()
            val params = queryParams(strap, userId, locale, TimeZone.getDefault().id, android.os.Build.VERSION.SDK_INT)
            val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

            val conn = URL("${base}devices/ALL/hasNewVersion?$query").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("apptoken", appToken.trim())
                conn.setRequestProperty("appname", "com.huami.midong")
                conn.setRequestProperty("appplatform", "android_phone")
                conn.setRequestProperty("channel", "play")
                conn.setRequestProperty("cv", "${APP_VERSION_CODE}_$APP_VERSION_NAME")
                conn.setRequestProperty("v", "2.0")
                conn.setRequestProperty("lang", "${locale.language}_${locale.country}")
                conn.setRequestProperty("country", locale.country)
                conn.setRequestProperty("timezone", TimeZone.getDefault().id)
                conn.setRequestProperty("hm-privacy-diagnostics", "false")
                conn.setRequestProperty("hm-privacy-ceip", "false")
                conn.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                when (code) {
                    in 200..299 -> parse(body)
                    401, 403 -> throw FirmwareCloudException("The server rejected the app token (HTTP $code). It may have expired.")
                    else -> throw FirmwareCloudException("Update server returned HTTP $code${body.take(200).let { if (it.isBlank()) "" else ": $it" }}")
                }
            } finally {
                conn.disconnect()
            }
        }

    /** Downloads [part] (or reuses a cached copy) and returns its bytes once the MD5 matches. */
    suspend fun download(part: FirmwarePart, onProgress: (Long, Long) -> Unit): ByteArray = withContext(Dispatchers.IO) {
        require(part.url.startsWith("https://")) { "Refusing a non-HTTPS download" }
        val dir = File(cacheDir, "firmware").apply { mkdirs() }
        val file = File(dir, part.md5.lowercase())
        if (file.exists() && md5(file.readBytes()).equals(part.md5, ignoreCase = true)) return@withContext file.readBytes()

        repeat(2) { attempt ->
            val conn = URL(part.url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (conn.responseCode !in 200..299) throw FirmwareCloudException("Download failed (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: part.length
                val tmp = File(dir, "${part.md5}.part")
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                val bytes = tmp.readBytes()
                if (md5(bytes).equals(part.md5, ignoreCase = true)) {
                    tmp.renameTo(file)
                    return@withContext bytes
                }
                tmp.delete()
                if (attempt == 1) throw FirmwareCloudException("Downloaded file failed its MD5 check")
            } finally {
                conn.disconnect()
            }
        }
        error("unreachable")
    }

    companion object {
        // Identify as the Zepp app version this work was based on; the server keys answers on it.
        private const val APP_ID = "428135909242707968"
        private const val APP_VERSION_NAME = "10.9.0-play"
        private const val APP_VERSION_CODE = "151983"

        private val PREFIXES = listOf("firmware", "resource", "baseResource", "extResource", "font", "gps")

        /** The `hasNewVersion` query, in Zepp's order (docs/protocol/03 §1.1, codemap 08 §3). */
        fun queryParams(strap: StrapIdentity, userId: String, locale: Locale, timezone: String, sdkInt: Int): LinkedHashMap<String, String> {
            val r = strap.resources
            val params = linkedMapOf(
                "v" to "2.0",
                "appid" to APP_ID,
                "lang" to "${locale.language}_${locale.country}",
                "country" to locale.country,
                "timezone" to timezone,
                "channel" to "play",
                "device" to "android_$sdkInt",
                "device_type" to "android_phone",
                "cv" to "${APP_VERSION_CODE}_$APP_VERSION_NAME",
                "userid" to userId,
                "userId" to userId,
                "appVersion" to "${APP_VERSION_NAME}_$APP_VERSION_CODE",
                "deviceType" to "ALL",
                "deviceSource" to strap.deviceSource.toString(),
                "firmwareVersion" to strap.firmwareVersion.removePrefix("V").removePrefix("v"),
                "support8Bytes" to "true",
                "firmwareFlag" to r.firmwareFlag.toString(),
                "fontVersion" to r.fontVersion.toString(),
                "fontFlag" to r.fontFlag.toString(),
                "resourceVersion" to r.resourceVersion.toString(),
                "resourceFlag" to r.resourceFlag.toString(),
                "baseResourceVersion" to r.baseResourceVersion.toString(),
                "baseResourceFlag" to r.baseResourceFlag.toString(),
                "extResourceVersion" to r.extResourceVersion.toString(),
                "extResourceFlag" to r.extResourceFlag.toString(),
                "vendorId" to "-1",
                "vendorSource" to "-1",
            )
            // Zepp leaves an unknown GPS version unset, and its query builder drops unset values.
            strap.gpsVersion?.let { params["gpsVersion"] = it }
            strap.serial?.takeIf { it.isNotBlank() }?.let { params["sn"] = it }
            strap.hardwareVersion?.takeIf { it.isNotBlank() }?.let { params["hardwareVersion"] = it.removePrefix("V").removePrefix("v") }
            params["productionSource"] = strap.productionSource.toString()
            params["productId"] = strap.productId.toString()
            return params
        }

        fun md5(bytes: ByteArray): String =
            MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Parses the §1.2 response. Parts with only some fields set make the whole answer invalid. */
        fun parse(body: String): UpdateCheck {
            val json = runCatching { JSONObject(body) }.getOrElse { throw FirmwareCloudException("Unexpected reply from the update server") }
            val parts = PREFIXES.mapNotNull { p ->
                val url = json.optString("${p}Url")
                val md5 = json.optString("${p}Md5")
                val version = json.optString("${p}Version")
                val length = json.optLong("${p}Length", 0)
                val any = url.isNotEmpty() || md5.isNotEmpty() || version.isNotEmpty() || length > 0
                val all = url.isNotEmpty() && md5.isNotEmpty() && version.isNotEmpty() && length > 0
                if (any && !all) throw FirmwareCloudException("The update server sent an incomplete $p entry")
                if (all) FirmwarePart(p, url, md5, version, length, json.optInt("${p}Flag", -1)) else null
            }
            val ignore = json.optBoolean("ignore", false)
            return UpdateCheck(
                upToDate = ignore || parts.isEmpty(),
                forced = json.optInt("upgradeType", 0) == 2,
                changeLog = json.optString("changeLog").takeIf { it.isNotBlank() },
                parts = if (ignore) emptyList() else parts,
            )
        }
    }
}
