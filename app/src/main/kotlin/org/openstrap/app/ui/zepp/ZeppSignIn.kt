package org.openstrap.app.ui.zepp

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing
import org.openstrap.app.zepp.ZeppCloud

private sealed interface SignInStep {
    data object SignIn : SignInStep
    data object Fetching : SignInStep
    data class Found(val listing: DeviceListing) : SignInStep
    data class Failed(val message: String) : SignInStep
}

/**
 * Full-screen Zepp sign-in. The user signs in on watchface.zepp.com inside a WebView (OpenStrap never sees
 * the password); the app then reads the app token and user ID from that site's cookies, lists the account's
 * devices once, and shows the Helio Strap it found for the user to confirm.
 *
 * [onConfirm] gets the listing and the chosen strap (null when the user only keeps the sign-in).
 */
@Composable
fun ZeppSignInDialog(
    preferredApiBase: String?,
    confirmLabel: String,
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
                    .getOrElse { SignInStep.Failed(it.message ?: "Couldn't read the device list") }
                break
            }
            delay(1000)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text("Sign in with Zepp", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (step is SignInStep.SignIn) TextButton(onClick = { clearZeppCookies(); attempt++ }) { Text("Reset") }
                }
                when (val s = step) {
                    SignInStep.SignIn -> {
                        Text(
                            "Sign in to your Zepp account on Zepp's own page. OpenStrap never sees your password; it reads only " +
                                "the app token and user ID, then asks Zepp once for your devices. Health data is never uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        key(attempt) { ZeppWebView(Modifier.fillMaxWidth().weight(1f)) }
                    }
                    SignInStep.Fetching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Signed in. Looking up your devices…")
                        }
                    }
                    is SignInStep.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.ErrorOutline, null, tint = MetricColors.bad)
                            Text("Couldn't get your devices", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(s.message)
                        Button(onClick = { clearZeppCookies(); step = SignInStep.SignIn; attempt++ }, Modifier.fillMaxWidth()) { Text("Sign in again") }
                        OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Close") }
                    }
                    is SignInStep.Found -> FoundStrap(
                        s.listing, confirmLabel,
                        onConfirm = { strap -> clearZeppCookies(); onConfirm(s.listing, strap) },
                        onOtherAccount = { clearZeppCookies(); step = SignInStep.SignIn; attempt++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun FoundStrap(listing: DeviceListing, confirmLabel: String, onConfirm: (BoundDevice?) -> Unit, onOtherAccount: () -> Unit) {
    val strap = listing.helioStrap
    var showKey by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            PanelHeader(if (strap != null) "Found your Helio Strap" else "No Helio Strap on this account", Icons.Default.Watch, MaterialTheme.colorScheme.primary)
            if (strap == null) {
                Text(
                    "The account has ${listing.devices.size} other device(s) but no Amazfit Helio Strap. Bind the strap with the " +
                        "Zepp app once, then sign in again. You can still keep this sign-in for firmware checks.",
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
                        "Zepp didn't return an auth key for this strap. Bind it with the Zepp app again, or paste the key by hand.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Line("Zepp user", listing.session.userId)
        }
        Text(
            "Saving keeps the app token and user ID on this phone only, for firmware checks. The auth key goes into pairing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onConfirm(strap) }, Modifier.fillMaxWidth()) { Text(if (strap != null) confirmLabel else "Keep sign-in only") }
        OutlinedButton(onClick = onOtherAccount, Modifier.fillMaxWidth()) { Text("Use another account") }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ZeppWebView(modifier: Modifier) {
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
                    // Keep every https page inside the WebView so the sign-in stays here.
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) =
                        request.url.scheme != "https"
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
