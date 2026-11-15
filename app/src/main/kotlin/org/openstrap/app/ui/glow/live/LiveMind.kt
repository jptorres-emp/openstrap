package org.openstrap.app.ui.glow.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openstrap.app.data.WorkoutTarget
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.dotClock
import androidx.compose.foundation.layout.Row
import org.openstrap.app.ui.glow.LineDots
import org.openstrap.app.ui.glow.PlainLine
import org.openstrap.app.ui.glow.ProgressRing
import org.openstrap.app.ui.glow.activeSecondsState
import org.openstrap.app.ui.glow.workoutClock
import org.openstrap.app.ui.theme.MetricColors
import androidx.compose.foundation.gestures.detectTapGestures
import org.openstrap.app.presentation.WorkoutCopy
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/** The screen dims this long after the last touch. */
private const val DIM_AFTER_MS = 10_000L

/** Dimmed, the screen is covered by this much black, and the clock's digits by [CLOCK_VEIL], so the time still reads. */
private const val VEIL = 0.92f
private const val CLOCK_VEIL = 0.5f

/** The lighter spot reaches this far out, in the digits' width, and is at its lightest up to this share of it. */
private const val SPOT_WIDTH = 0.95f
private const val SPOT_LIT = 0.55f

/**
 * Mind and body (workouts spec 2.6, board LiveMind): dark by design. The time against the length, a soft heart line
 * that shows it settling, then Pause and the square to end. No zone colours, no kcal. The screen dims to near black
 * after ten seconds, all but the time, which stays at half, and lights again on a touch, which does nothing else.
 * One soft buzz at the end of the length; the workout keeps recording until you end it. With [split] the heart rate
 * is on the other half of a folded phone, so this half leaves it out, and [compact] (a short half) drops the ring
 * around the time.
 */
@Composable
internal fun LiveMindScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    controls: LiveControls,
    split: Boolean = false,
    compact: Boolean = false,
) {
    val connected = connection is ConnectionState.Connected
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    val active = activeSecondsState(r)
    val length = (WorkoutTarget.parse(r.workout.target) as? WorkoutTarget.Time)?.seconds
    var touched by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dim by remember { mutableStateOf(false) }
    LaunchedEffect(touched, r.paused) {
        dim = false
        if (r.paused) return@LaunchedEffect
        delay(DIM_AFTER_MS)
        dim = true
    }
    // Read only in the veil's draw, so dimming fades the screen without composing it again each frame.
    val veil = animateFloatAsState(if (dim) VEIL else 0f, tween(if (dim) 1500 else 200), label = "dim")
    // Where the clock's digits are, in the screen's root, for the lighter spot in the veil.
    val clock = remember { mutableStateOf(Rect.Zero) }
    val veilAt = remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Seen before anything under it, and never taken from it: a touch lights the screen and still presses.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touched = System.currentTimeMillis()
            }
        },
    ) {
        // Board HaloLiveMind: the time as the violet hero, the heart line in a solid card, a quiet hint under them.
        val color = liveColor(r)
        val tint = heroTint(color)
        LiveFrame(r, color, connected, contentPadding, controls, time = null, quiet = true, fill = true, tapHint = !compact) {
            LiveHero(color, liveAngle(r)) {
                HeroTitle(
                    stringResource(R.string.workout_live_mind_time_title),
                    length?.let { stringResource(R.string.workout_live_mind_length, it / 60) }, tint,
                )
                MindNumber({ active.value }, tint, onClock = { clock.value = it })
                HeroLine(
                    if (length != null) stringResource(R.string.workout_live_mind_buzz) else
                        stringResource(R.string.workout_live_mind_open), tint,
                )
            }
            if (split) return@LiveFrame
            val now = recent.lastOrNull()?.first ?: (System.currentTimeMillis() / 1000)
            val values = remember(recent) { bucketRecent(recent, now) }
            val first = values.firstOrNull { it != null }?.toInt()
            LiveCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.workout_live_heart_title), color = Color.White, style = GlowType.cardTitle,
                        modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.workout_live_mind_settling), color = GlowType.noteColor, style = GlowType.noteSmall)
                }
                // The heart rate as one thin line edge to edge, with a dot at now: no axis, no numbers.
                PlainLine(values, color, if (compact) 40.dp else 64.dp, smooth = false, dots = LineDots.Last, stroke = 2.dp)
                Text(
                    bpm?.let { b ->
                        first?.minus(b)?.takeIf { it > 0 }?.let { stringResource(R.string.workout_live_mind_heart_down, b, it) }
                            ?: stringResource(R.string.workout_live_mind_heart, b)
                    } ?: WorkoutCopy.noHeartRate(r.paused, connected),
                    color = LiveLabelInk, style = GlowType.secondary,
                )
            }
        }
        // Dimmed, the first touch only lights the screen: the veil takes it.
        Box(
            Modifier.fillMaxSize().onGloballyPositioned { veilAt.value = it.positionInRoot() }
                .drawBehind { if (veil.value > 0f) drawVeil(veil.value, clock.value.translate(-veilAt.value)) }
                .then(if (dim) Modifier.pointerInput(Unit) { detectTapGestures { touched = System.currentTimeMillis() } } else Modifier),
        )
    }
}

/** The hero's time in dots, its own scope so only it reads the second; [onClock] reports where it sits for the veil's spot. */
@Composable
private fun MindNumber(time: () -> Long, tint: Color, onClock: (Rect) -> Unit) {
    Box(Modifier.onGloballyPositioned { onClock(it.boundsInRoot()) }) {
        HeroNumber(dotClock(time()), null, tint)
    }
}

/**
 * The dimmed screen: [alpha] black everywhere, easing to [CLOCK_VEIL]'s share of it over the [clock]'s digits, a soft
 * oval with no edge to see. Before the clock is placed, the whole screen evenly.
 */
private fun DrawScope.drawVeil(alpha: Float, clock: Rect) {
    val black = Color.Black.copy(alpha = alpha)
    if (clock.isEmpty) {
        drawRect(black)
        return
    }
    val light = Color.Black.copy(alpha = alpha * CLOCK_VEIL / VEIL)
    // A circle stretched sideways to the digits' shape: the gradient is drawn round, under a scale.
    val radius = clock.width * SPOT_WIDTH
    val squash = (clock.height / radius).coerceAtMost(1f)
    scale(1f, squash, pivot = clock.center) {
        drawRect(
            Brush.radialGradient(SPOT_LIT to light, 1f to black, center = clock.center, radius = radius),
            topLeft = Offset(0f, clock.center.y - clock.center.y / squash),
            size = Size(size.width, size.height / squash),
        )
    }
}

