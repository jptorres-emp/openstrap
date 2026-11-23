@file:Suppress("MagicNumber") // the sheet colours and sizes are the boards'

package org.openstrap.app.ui.zepp

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.NoteVariant
import org.openstrap.app.ui.glow.HaloProgress
import org.openstrap.app.ui.glow.CardFill
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
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
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.PageSpec
import org.openstrap.app.ui.glow.InfoRow
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.glow.HaloIconButton
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
import org.openstrap.app.ui.theme.Ink
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
                    Modifier.fillMaxWidth().height(PageSpec.HeaderHeight).padding(horizontal = GlowLayout.Gutter).padding(top = Space.S16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                        HaloIconButton(AppIcons.X, close, onDismiss)
                    }
                    Text(
                        stringResource(R.string.you_zepp_sign_in_title), color = Color.White, style = GlowType.pageTitle, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                        if (step is SignInStep.SignIn) HaloButton(stringResource(R.string.you_zepp_reset),
                            { clearZeppCookies(); attempt++ }, variant = HaloButtonVariant.Text)
                    }
                }
                when (val s = step) {
                    SignInStep.SignIn -> {
                        // The intro can be hidden, in case Zepp's page needs the room on a short screen. Zepp's own page sits in a
                        // panel, then the note on what unstrap reads, then the way to a computer (HaloZeppSignIn board).
                        if (showIntro) {
                            Row(Modifier.padding(start = GlowLayout.Gutter, end = GlowLayout.Gutter, top = Space.S16, bottom = Space.S4)) {
                                Text(
                                    stringResource(R.string.you_zepp_sign_in_text), Modifier.weight(1f).padding(horizontal = Space.S4),
                                    color = Ink.Strong, style = GlowType.body,
                                )
                                Spacer(Modifier.width(Space.S8))
                                val hide = stringResource(R.string.you_action_hide)
                                Box(
                                    Modifier.size(IconSize.TileS).clip(Corner.Pill).background(Greys.Control)
                                        .clickable(onClickLabel = hide, onClick = { showIntro = false }),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AppIcons.CaretDown, hide, Modifier.size(18.dp).rotate(UP_DEGREES), tint = Color.White) }
                            }
                        }
                        key(attempt) {
                            ZeppWebView(
                                Modifier.fillMaxWidth().weight(1f).padding(horizontal = GlowLayout.Gutter, vertical = Space.S12)
                                    .clip(GlowLayout.CardShape),
                            ) { provider -> step = SignInStep.ThirdParty(provider) }
                        }
                        Column(
                            Modifier.padding(start = GlowLayout.Gutter, end = GlowLayout.Gutter, bottom = Space.S16),
                            verticalArrangement = Arrangement.spacedBy(Space.S12),
                        ) {
                            HaloNote(stringResource(R.string.you_zepp_sign_in_note), variant = NoteVariant.Footnote)
                            ComputerGuideButton()
                        }
                    }
                    SignInStep.Fetching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S16)) {
                            HaloProgress(null, Modifier.width(160.dp))
                            Text(stringResource(R.string.you_zepp_fetching), color = Color.White, style = GlowType.body)
                        }
                    }
                    is SignInStep.Failed, is SignInStep.ThirdParty -> Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(GlowLayout.Gutter),
                        verticalArrangement = Arrangement.spacedBy(Space.S12),
                    ) {
                        val thirdParty = s is SignInStep.ThirdParty
                        HaloCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                                Icon(AppIcons.WarningCircle, null, Modifier.size(IconSize.GlyphM), tint = MetricColors.warn)
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
                                color = Ink.Secondary, style = GlowType.body,
                            )
                        }
                        HaloButton(
                            stringResource(if (thirdParty) R.string.you_zepp_sign_in_email else R.string.you_zepp_sign_in_again),
                            { clearZeppCookies(); step = SignInStep.SignIn; attempt++ }, Modifier.fillMaxWidth(),
                            variant = HaloButtonVariant.Primary,
                        )
                        ComputerGuideButton()
                        HaloButton(close, onDismiss, Modifier.fillMaxWidth())
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
    Box(Modifier.fillMaxSize().background(Ink.ScrimDeep), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = GlowLayout.PageMaxWidth).fillMaxWidth().clip(Corner.Sheet).background(Greys.Raised)
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = Space.S20, end = Space.S20, top = Space.S12,
                    bottom = Space.S32),
            verticalArrangement = Arrangement.spacedBy(Space.S16),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp,
                height = 5.dp).clip(CircleShape).background(Greys.Line))
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
                    color = Ink.Strong, style = GlowType.body,
                )
            } else {
                HaloCard(fill = CardFill.Rows) {
                    InfoRow(stringResource(R.string.you_pair_address), strap.macAddress, divider = false)
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = Space.S16, end = Space.S8),
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
                            HaloButton(
                                stringResource(if (showKey) R.string.you_action_hide else R.string.you_action_show), { showKey = !showKey },
                                variant = HaloButtonVariant.Text,
                                )
                        }
                    }
                    strap.serial?.let { InfoRow(stringResource(R.string.you_zepp_serial), it) }
                    strap.firmwareVersion?.let { InfoRow(stringResource(R.string.you_firmware_title), it) }
                    InfoRow(
                        stringResource(R.string.you_zepp_region),
                        stringResource(if (strap.china) R.string.you_zepp_region_china else R.string.you_zepp_region_global),
                    )
                    InfoRow(stringResource(R.string.you_zepp_user), listing.session.userId)
                }
                if (strap.authKey == null) HaloNote(stringResource(R.string.you_zepp_no_key), color = MetricColors.warn)
            }
            if (canConfirm && requireKey) HaloNote(stringResource(R.string.you_zepp_keep_on_charger))
            HaloNote(stringResource(R.string.you_zepp_token_stays))
            if (!canConfirm && requireKey) ComputerGuideButton()
            // Another account (dark) and the confirming tap (white), half and half; a lone sign-in is kept the same way.
            ButtonPair(
                stringResource(R.string.you_zepp_other_account),
                onOtherAccount,
                if (canConfirm) confirmLabel else stringResource(R.string.you_zepp_keep_sign_in),
                { onConfirm(strap) },
                modifier = Modifier.padding(top = Space.S4),
            )
        }
    }
}

/** Opens Gadgetbridge's computer walkthrough in the browser. */
@Composable
fun ComputerGuideButton(modifier: Modifier = Modifier.fillMaxWidth()) {
    val uri = LocalUriHandler.current
    // A nav row on its own panel (HaloPair and HaloZeppSignIn boards).
    HaloCard(modifier, fill = CardFill.Rows) {
        NavRow(
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
