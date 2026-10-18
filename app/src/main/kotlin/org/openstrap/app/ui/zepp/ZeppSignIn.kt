package org.openstrap.app.ui.zepp

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing
import org.openstrap.app.zepp.ZeppCloud
import org.openstrap.app.zepp.ZeppCloudException

private sealed interface SignInStep {
    data object SignIn : SignInStep
    data object Fetching : SignInStep
    data class Found(val listing: DeviceListing) : SignInStep
    /** [thirdParty]: the user picked Google, Apple and the like, which can't finish inside a WebView. */
    data class Failed(val message: String, val thirdParty: Boolean = false) : SignInStep
}

/** Gadgetbridge's walkthrough for reading the auth key on a computer, for accounts that can't sign in here. */
const val COMPUTER_KEY_GUIDE_URL = "https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/#on-a-computer"

/** Sign-in providers that refuse to run in an embedded WebView (Google blocks it outright). */
private val THIRD_PARTY_HOSTS = mapOf(
    "accounts.google.com" to "Google",
    "appleid.apple.com" to "Apple",
    "www.facebook.com" to "Facebook",
    "m.facebook.com" to "Facebook",
    "account.xiaomi.com" to "Xiaomi",
    "access.line.me" to "LINE",
)

/**
 * Full-screen Zepp sign-in. The user signs in on watchface.zepp.com inside a WebView (OpenStrap never sees
 * the password); the app then reads the app token and user ID from that site's cookies, lists the account's
 * devices once, and shows the Helio Strap it found for the user to confirm.
 *
 * [onConfirm] gets the listing and the chosen strap (null when the user only keeps the sign-in). With
 * [requireKey], a strap without an auth key can't be confirmed with [confirmLabel], only kept as a sign-in.
 */
@Composable
fun ZeppSignInDialog(
    preferredApiBase: String?,
    confirmLabel: String,
    requireKey: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (DeviceListing, BoundDevice?) -> Unit,
) {
    var step by remember { mutableStateOf<SignInStep>(SignInStep.SignIn) }
    var attempt by remember { mutableIntStateOf(0) }

    // Watch the cookies while the user signs in; the site is a single-page app, so page loads alone don't tell us.
    LaunchedEffect(attempt) {
        while (step == SignInStep.SignIn) {
            val session = ZeppCloud.sessionFromCookies(currentCookies())
            if (session != null) {
                step = SignInStep.Fetching
                step = runCatching { SignInStep.Found(ZeppCloud.listDevices(session, preferredApiBase)) }
                    .getOrElse { SignInStep.Failed(deviceListError(it)) }
                break
            }
            delay(1000)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(PhosphorIcons.X, "Close") }
                    Text("Sign in with Zepp", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (step is SignInStep.SignIn) TextButton(onClick = { clearZeppCookies(); attempt++ }) { Text("Reset") }
                }
                when (val s = step) {
                    SignInStep.SignIn -> {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Use the email and password of the Zepp account your strap is linked to. Google, Apple, " +
                                    "Facebook and Xiaomi sign-in don't work here.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "This is Zepp's own page, so OpenStrap never sees your password. It reads only the app token and " +
                                    "user ID, then asks Zepp once for your devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        key(attempt) {
                            ZeppWebView(Modifier.fillMaxWidth().weight(1f)) { provider ->
                                step = SignInStep.Failed(
                                    "$provider sign-in doesn't work inside OpenStrap, because $provider blocks sign-in in embedded pages.\n\n" +
                                        "Open the Zepp app and add an email and password to your account in its account settings. " +
                                        "Then sign in here with that email. Or get the auth key on a computer.",
                                    thirdParty = true,
                                )
                            }
                        }
                    }
                    SignInStep.Fetching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Signed in. Looking up your devices…")
                        }
                    }
                    is SignInStep.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(PhosphorIcons.WarningCircle, null, tint = MetricColors.bad)
                            Text(if (s.thirdParty) "Use email sign-in" else "Couldn't get your devices", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(s.message)
                        Button(onClick = { clearZeppCookies(); step = SignInStep.SignIn; attempt++ }, Modifier.fillMaxWidth()) {
                            Text(if (s.thirdParty) "Sign in with email" else "Sign in again")
                        }
                        ComputerGuideButton()
                        OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Close") }
                    }
                    is SignInStep.Found -> FoundStrap(
                        s.listing, confirmLabel, requireKey,
                        onConfirm = { strap -> clearZeppCookies(); onConfirm(s.listing, strap) },
                        onOtherAccount = { clearZeppCookies(); step = SignInStep.SignIn; attempt++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun FoundStrap(
    listing: DeviceListing,
    confirmLabel: String,
    requireKey: Boolean,
    onConfirm: (BoundDevice?) -> Unit,
    onOtherAccount: () -> Unit,
) {
    val strap = listing.helioStrap
    val canConfirm = strap != null && (strap.authKey != null || !requireKey)
    var showKey by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            PanelHeader(if (strap != null) "Found your Helio Strap" else "No Helio Strap on this account", PhosphorIcons.Watch, MaterialTheme.colorScheme.primary)
            if (strap == null) {
                Text(
                    if (listing.devices.isEmpty()) {
                        "This account has no devices. Link the strap in the Zepp app first. If you did, it's on another " +
                            "account: sign in with the email added to the account you use in the Zepp app."
                    } else {
                        "This account has ${listing.devices.size} device(s), but none is an Amazfit Helio Strap. Link the strap " +
                            "in the Zepp app, then sign in again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Line("Bluetooth address", strap.macAddress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auth key", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            strap.authKey == null -> "Not on the account"
                            showKey -> strap.authKey
                            else -> "•".repeat(8) + strap.authKey.takeLast(4)
                        },
                        style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                    )
                    if (strap.authKey != null) TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                }
                strap.serial?.let { Line("Serial", it) }
                strap.firmwareVersion?.let { Line("Firmware", it) }
                Line("Region", if (strap.china) "China" else "Global")
                if (strap.authKey == null) {
                    Text(
                        "Zepp didn't return an auth key for this strap. Open the Zepp app and check the strap is still linked " +
                            "(unlink and link it again if needed), then sign in here again.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Line("Zepp user", listing.session.userId)
        }
        if (canConfirm && requireKey) {
            Text(
                "Keep the strap on its charger near the phone. Don't unlink it in the Zepp app: that wipes this key from the strap.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "The app token and user ID stay on this phone only, for firmware checks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onConfirm(strap) }, Modifier.fillMaxWidth()) { Text(if (canConfirm) confirmLabel else "Keep sign-in only") }
        OutlinedButton(onClick = onOtherAccount, Modifier.fillMaxWidth()) { Text("Use another account") }
        if (!canConfirm && requireKey) ComputerGuideButton()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Opens Gadgetbridge's computer walkthrough in the browser. */
@Composable
fun ComputerGuideButton(modifier: Modifier = Modifier.fillMaxWidth()) {
    val uri = LocalUriHandler.current
    OptionButton("Can't sign in with email?", "Get the key on a computer", modifier) { uri.openUri(COMPUTER_KEY_GUIDE_URL) }
}

/** A secondary choice: an outlined, rounded row with a caption, a label and a chevron, so it reads as tappable. */
@Composable
fun OptionButton(caption: String, label: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Icon(PhosphorIcons.CaretRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun deviceListError(e: Throwable): String = when {
    e is ZeppCloudException && e.rejected ->
        "Zepp didn't accept the sign-in. It may have expired, or the account is in another region. Sign in again."
    e is ZeppCloudException -> "${e.message}. Zepp may be having trouble; try again in a few minutes."
    else -> "Couldn't reach Zepp's servers. Check your internet connection and try again."
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ZeppWebView(modifier: Modifier, onThirdParty: (provider: String) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    // Keep every https page inside the WebView so the sign-in stays here, except third-party sign-in,
                    // which can't finish in a WebView: stop it and explain instead of showing the provider's error.
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        THIRD_PARTY_HOSTS[request.url.host]?.let { onThirdParty(it); return true }
                        return request.url.scheme != "https"
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        THIRD_PARTY_HOSTS[url?.let(android.net.Uri::parse)?.host]?.let { view.stopLoading(); onThirdParty(it) }
                    }
                }
                loadUrl(ZeppCloud.SIGN_IN_URL)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}

private fun currentCookies(): String {
    val cm = CookieManager.getInstance()
    return ZeppCloud.COOKIE_URLS.mapNotNull { cm.getCookie(it) }.joinToString("; ")
}

/** Drops the WebView's Zepp sign-in once we have what we need (the token is kept in settings instead). */
private fun clearZeppCookies() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
}
