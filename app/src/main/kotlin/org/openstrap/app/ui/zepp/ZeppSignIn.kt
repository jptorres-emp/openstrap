@file:Suppress("MagicNumber") // the sheet colours and sizes are the boards'

package org.openstrap.app.ui.zepp

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.glow.HaloButtonSurface
import org.openstrap.app.ui.glow.PageFootnote
import org.openstrap.app.ui.glow.PageHeaderHeight
import org.openstrap.app.ui.glow.PageInfoRow
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.RoundIconButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.catching
import org.openstrap.app.device.StrapManager
import org.openstrap.app.ui.device.ErrorCopy
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PagePanel
import org.openstrap.app.ui.glow.PageProgress
import org.openstrap.app.ui.glow.PageTextButton
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.zepp.BoundDevice
import org.openstrap.app.zepp.DeviceListing
import org.openstrap.app.zepp.ZeppCloud

private sealed interface SignInStep {
    data object SignIn : SignInStep
    data object Fetching : SignInStep
    data class Found(val listing: DeviceListing) : SignInStep
    /** Zepp's device list failed, and [message] says why. */
    data class Failed(val message: String) : SignInStep
    /** The user picked [provider], Google, Apple and the like, whose sign-in can't finish inside a WebView. */
    data class ThirdParty(val provider: String) : SignInStep
}

/** Turns the down caret into the intro's fold-up arrow (the icon set has no outline up caret). */
private const val UP_DEGREES = 180f

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
// TODO (deferred): a cloud first bind, so a new or reset strap can be set up with only OpenStrap and a Zepp
// account, without the Zepp app: AuthInfo 00, ECDH 02, binds.json digest, signature 03, then normal auth. Steps
// and byte layouts are in zepp-alt analysis/codemap/08-cloud-and-account.md §2 (first-bind checklist). Untried;
// whether the server signs for OpenStrap's appid is unknown.
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
    var showIntro by remember { mutableStateOf(true) }
    val app = LocalContext.current.applicationContext
    val diagnostics: (String) -> Unit = { line ->
        ((app as? OpenStrapApp)?.container?.strap as? StrapManager)?.log(line) ?: Log.w("unstrap", line)
    }

    // Watch the cookies while the user signs in; the site is a single-page app, so page loads alone don't tell us.
    LaunchedEffect(attempt) {
        while (step == SignInStep.SignIn) {
            val session = ZeppCloud.sessionFromCookies(currentCookies())
            if (session != null) {
                step = SignInStep.Fetching
                step = catching { SignInStep.Found(ZeppCloud.listDevices(session, preferredApiBase)) }
                    .getOrElse {
                        // The raw reply (an HTTP status, say) goes to Diagnostics, and the page says it in plain words.
                        diagnostics("zepp: device list failed: $it")
                        SignInStep.Failed(ErrorCopy.zepp(it))
                    }
                break
            }
            delay(1000)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Black to the screen's edges, the page itself in one centred column like every other page (spec 2.4).
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = GlowLayout.PageMaxWidth).fillMaxSize()) {
                // The page's top (HaloZeppSignIn board): the close button at the left, the title centred, Reset at the right,
                // in the header's 96 / 1fr / 96 grid.
                val close = stringResource(R.string.you_zepp_close)
                Row(
                    Modifier.fillMaxWidth().height(PageHeaderHeight).padding(horizontal = GlowLayout.Gutter).padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                        RoundIconButton(AppIcons.X, close, onDismiss, size = 44.dp, iconSize = 18.dp)
                    }
                    Text(
                        stringResource(R.string.you_zepp_sign_in_title), color = Color.White, style = GlowType.pageTitle, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                        if (step is SignInStep.SignIn) PageTextButton(stringResource(R.string.you_zepp_reset),
                            { clearZeppCookies(); attempt++ })
                    }
                }
                when (val s = step) {
                    SignInStep.SignIn -> {
                        // The intro can be hidden, in case Zepp's page needs the room on a short screen. Zepp's own page sits in a
                        // panel, then the note on what unstrap reads, then the way to a computer (HaloZeppSignIn board).
                        if (showIntro) {
                            Row(Modifier.padding(start = GlowLayout.Gutter, end = GlowLayout.Gutter, top = 16.dp, bottom = 4.dp)) {
                                Text(
                                    stringResource(R.string.you_zepp_sign_in_text), Modifier.weight(1f).padding(horizontal = 4.dp),
                                    color = Color(0xFFC9CED6), style = GlowType.body.copy(fontSize = 14.sp),
                                )
                                Spacer(Modifier.width(8.dp))
                                val hide = stringResource(R.string.you_action_hide)
                                Box(
                                    Modifier.size(32.dp).clip(GlowLayout.IconShape).background(HaloButtonSurface)
                                        .clickable(onClickLabel = hide, onClick = { showIntro = false }),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AppIcons.CaretDown, hide, Modifier.size(18.dp).rotate(UP_DEGREES), tint = Color.White) }
                            }
                        }
                        key(attempt) {
                            ZeppWebView(
                                Modifier.fillMaxWidth().weight(1f).padding(horizontal = GlowLayout.Gutter, vertical = 12.dp)
                                    .clip(GlowLayout.CardShape),
                            ) { provider -> step = SignInStep.ThirdParty(provider) }
                        }
                        Column(
                            Modifier.padding(start = GlowLayout.Gutter, end = GlowLayout.Gutter, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PageFootnote(stringResource(R.string.you_zepp_sign_in_note))
                            ComputerGuideButton()
                        }
                    }
                    SignInStep.Fetching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PageProgress(modifier = Modifier.width(160.dp))
                            Text(stringResource(R.string.you_zepp_fetching), color = Color.White, style = GlowType.body)
                        }
                    }
                    is SignInStep.Failed, is SignInStep.ThirdParty -> Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(GlowLayout.Gutter),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val thirdParty = s is SignInStep.ThirdParty
                        PagePanel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(AppIcons.WarningCircle, null, Modifier.size(20.dp), tint = MetricColors.warn)
                                Text(
                                    stringResource(if (thirdParty) R.string.you_zepp_use_email else R.string.you_zepp_no_devices),
                                    color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                                )
                            }
                            Text(
                                when (s) {
                                    is SignInStep.ThirdParty -> stringResource(R.string.you_zepp_third_party, s.provider)
                                    else -> (s as SignInStep.Failed).message
                                },
                                color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.body,
                            )
                        }
                        PageButton(
                            stringResource(if (thirdParty) R.string.you_zepp_sign_in_email else R.string.you_zepp_sign_in_again),
                            { clearZeppCookies(); step = SignInStep.SignIn; attempt++ }, Modifier.fillMaxWidth(), primary = true,
                        )
                        ComputerGuideButton()
                        PageButton(close, onDismiss, Modifier.fillMaxWidth())
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
    // The board draws this as a sheet over the pairing page: the dialog is its own window, so it is drawn here, at the
    // foot, on the sheet's #111215 under the same dim (HaloPairFound board).
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = GlowLayout.PageMaxWidth).fillMaxWidth().clip(GlowLayout.SheetShape).background(Color(0xFF111215))
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp,
                    bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp,
                height = 5.dp).clip(CircleShape).background(Color(0xFF3A3D45)))
            Text(
                stringResource(if (strap != null) R.string.you_zepp_found else R.string.you_zepp_not_found),
                color = Color.White, style = GlowType.sheetTitle,
            )
            if (strap == null) {
                Text(
                    if (listing.devices.isEmpty()) {
                        stringResource(R.string.you_zepp_account_empty)
                    } else {
                        pluralStringResource(R.plurals.you_zepp_account_other_devices, listing.devices.size, listing.devices.size)
                    },
                    color = Color(0xFFC9CED6), style = GlowType.body,
                )
            } else {
                PageRows {
                    PageInfoRow(stringResource(R.string.you_pair_address), strap.macAddress, divider = false)
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.you_pair_key), color = Color.White, style = GlowType.body,
                            modifier = Modifier.weight(1f))
                        Text(
                            when {
                                strap.authKey == null -> stringResource(R.string.you_zepp_key_missing)
                                showKey -> strap.authKey
                                else -> "•".repeat(8) + strap.authKey.takeLast(4)
                            },
                            color = GlowType.noteColor, style = GlowType.body.copy(fontFamily = FontFamily.Monospace),
                        )
                        if (strap.authKey != null) {
                            PageTextButton(
                                stringResource(if (showKey) R.string.you_action_hide else R.string.you_action_show), { showKey = !showKey },
                            )
                        }
                    }
                    strap.serial?.let { PageInfoRow(stringResource(R.string.you_zepp_serial), it) }
                    strap.firmwareVersion?.let { PageInfoRow(stringResource(R.string.you_firmware_title), it) }
                    PageInfoRow(
                        stringResource(R.string.you_zepp_region),
                        stringResource(if (strap.china) R.string.you_zepp_region_china else R.string.you_zepp_region_global),
                    )
                    PageInfoRow(stringResource(R.string.you_zepp_user), listing.session.userId)
                }
                if (strap.authKey == null) PageNote(stringResource(R.string.you_zepp_no_key), color = MetricColors.warn)
            }
            if (canConfirm && requireKey) PageNote(stringResource(R.string.you_zepp_keep_on_charger))
            PageNote(stringResource(R.string.you_zepp_token_stays))
            if (!canConfirm && requireKey) ComputerGuideButton()
            // Another account (dark) and the confirming tap (white), half and half; a lone sign-in is kept the same way.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageButton(stringResource(R.string.you_zepp_other_account), onOtherAccount, Modifier.weight(1f))
                PageButton(
                    if (canConfirm) confirmLabel else stringResource(R.string.you_zepp_keep_sign_in), { onConfirm(strap) },
                    Modifier.weight(1f), primary = true,
                )
            }
        }
    }
}

/** Opens Gadgetbridge's computer walkthrough in the browser. */
@Composable
fun ComputerGuideButton(modifier: Modifier = Modifier.fillMaxWidth()) {
    val uri = LocalUriHandler.current
    // A nav row on its own panel (HaloPair and HaloZeppSignIn boards).
    PageRows(modifier) {
        PageRow(
            stringResource(R.string.you_zepp_guide), stringResource(R.string.you_zepp_guide_note),
                onClick = { uri.openUri(COMPUTER_KEY_GUIDE_URL) },
            below = true, divider = false, icon = AppIcons.Info,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ZeppWebView(modifier: Modifier, onThirdParty: (provider: String) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // AndroidView leaves a view at WRAP_CONTENT, and a WRAP_CONTENT WebView gives the page a 0px viewport
                // height, so Zepp's login page (sized with 100dvh) collapsed and pushed its form above the top.
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
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
