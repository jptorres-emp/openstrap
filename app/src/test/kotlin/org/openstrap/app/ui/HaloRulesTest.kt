package org.openstrap.app.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Halo rules that can be read from source (docs/design/halo/plan.md sections 1 and 5, detail-rules.md,
 * workout-rules.md, rewards-settings-rules.md, type.md). Each test names the board or rule it guards, scans the code
 * under ui/ with comments and strings blanked, and fails with file and line.
 */
class HaloRulesTest {
    private val ui = UiSources()
    private val sources get() = ui.sources

    /**
     * Plan section 1 (squircle corners everywhere) and HaloSystem board: a pill is [androidx.compose.foundation.shape.CircleShape],
     * not a percent corner of its own.
     */
    @Test fun `pills are CircleShape, never a RoundedCornerShape of 50 percent`() {
        val found = ui.find(Regex("""\bRoundedCornerShape\(\s*(percent\s*=\s*)?50\b"""))
        if (found.isNotEmpty()) fail("A pill is CircleShape (plan section 1, board HaloSystem):\n" + found.joinToString("\n"))
    }

    /**
     * Plan section 1 and wave 0 (squircle shapes and radius tokens): a corner is never a dp literal outside GlowLayout and ui/theme/Scale.kt.
     * Card-sized corners are DesignTokenTest's. Small ones come from Corner (Inner, Chip, Pip and Pill).
     */
    @Test fun `no corner literal outside GlowLayout and the theme corners`() {
        val found = sources.keys.filter { it != "glow/GlowLayout.kt" && it != "theme/Scale.kt" }.flatMap { path ->
            ui.calls(Regex("RoundedCornerShape"), listOf(path)).filter { (_, _, args) -> Regex("""\d+(\.\d+)?\.dp""").containsMatchIn(args) }
                .map { "${it.first}:${it.second}  RoundedCornerShape(${it.third.trim()})" }
        }
        if (found.isNotEmpty()) fail("Corners come from GlowLayout's tokens or CircleShape (plan section 1):\n" + found.joinToString("\n"))
    }

    /**
     * Plan section 1 (drop: glass, any other blur; keep: the blurred tab bar). The only backdrop blur is the floating
     * tab bar's, in components/Insights.kt. The light of a hero card is drawn, not blurred behind, and is not matched.
     */
    @Test fun `no glass and no backdrop blur outside the tab bar`() {
        val found = ui.find(Regex("""\.glass\(|\bfun Modifier\.glass\b|\bhazeBlur\b|\bhazeEffect\b|\bhazeChild\b|\.blur\(|\bRenderEffect\b|\bBlurEffect\b"""), setOf("components/Insights.kt"))
        if (found.isNotEmpty()) fail("Halo has glass and blur on the tab bar only (plan section 1):\n" + found.joinToString("\n"))
        assertTrue("the tab bar keeps its blur", ui.code("components/Insights.kt").contains("hazeBlur("))
    }

    /**
     * Workout rules section 5 and settings rules B3 (switches): on is a white track with a black knob, off is #2C2C2C
     * with a grey knob, never a sport's colour. So the one switch is GlowSwitch, it takes no accent, and Material's
     * Switch is not used.
     */
    @Test fun `switches have the white track`() {
        val found = ui.find(Regex("""(?<![A-Za-z])Switch\(|\bSwitchDefaults\b|\bSwitchColors\b"""))
        if (found.isNotEmpty()) fail("A switch is GlowSwitch (workout rules section 5):\n" + found.joinToString("\n"))
        val accent = ui.calls(Regex("GlowSwitch")).filter { Regex("""\baccent\s*=""").containsMatchIn(it.third) }
        if (accent.isNotEmpty()) fail("A switch track is never a sport colour (workout rules section 5):\n" + accent.joinToString("\n") { "${it.first}:${it.second}" })
        val body = ui.code("glow/GlowParts.kt").substringAfter("fun GlowSwitch(").substringBefore("\n}\n")
        assertTrue("GlowSwitch's track is white when on, Greys.Control when off", "if (checked) Color.White else Greys.Control" in body)
        assertTrue("GlowSwitch's knob is black when on", "if (checked) Color.Black" in body)
    }

    /**
     * Settings rules B3 (action row: both in #FF5A5F for destructive) and the workout rules (Delete is a dark pill with
     * red text, board HaloDeleteSleepSheet). A button or action row with the trash icon says `HaloButtonVariant.Destructive`, and
     * the red is DestructiveInk, written in PageParts only (a sport's or the heart's own red lives in its theme file).
     */
    @Test fun `destructive buttons use the Destructive variant`() {
        val bare = ui.calls(Regex("HaloButton|ActionRow")).filter { (_, _, args) ->
            "AppIcons.Trash" in args && !Regex("""\bHaloButtonVariant\.Destructive\b|\bdestructive\s*=\s*true\b""").containsMatchIn(args)
        }
        if (bare.isNotEmpty()) fail("A delete button is dark with red text, destructive = true (board HaloDeleteSleepSheet):\n" + bare.joinToString("\n") { "${it.first}:${it.second}  ${it.third.trim().take(80)}" })
        val red = ui.find(Regex("""0xFFFF5A5F|0xFFFF7A7E"""), setOf("glow/PageParts.kt", "theme/Theme.kt", "glow/Medals.kt", "workout/WorkoutStyle.kt"))
        if (red.isNotEmpty()) fail("A destructive red is DestructiveInk from PageParts (settings rules B3):\n" + red.joinToString("\n"))
    }

    /**
     * Type scale, "The one big number" (type.md) and workout rules section 3: the one dotted number is DotSpec dots at
     * the board's Doto size, through HaloBigNumber, never the Doto font itself and never a DotMatrixText of a screen's own.
     */
    @Test fun `the one dotted big number goes through HaloBigNumber or DotSpec`() {
        val doto = ui.find(Regex("""\bDoto\w*"""))
        if (doto.isNotEmpty()) fail("The big number is DotSpec dots, not the Doto font (type.md):\n" + doto.joinToString("\n"))
        val homes = setOf("glow/GlowType.kt", "glow/GlowParts.kt")
        val over = ui.calls(Regex("DotMatrixText")).filter { it.first !in homes }
        if (over.isNotEmpty()) fail("A screen's big number is HaloBigNumber (type.md):\n" + over.joinToString("\n") { "${it.first}:${it.second}" })
    }

    /**
     * Rewards and settings rules B4 (picker sheets: grabber, title, content, note, then Cancel and Save) and plan
     * section 1. Sheets are HaloSheet: no Material sheet or dialog, GlassSheet is only HaloSheet's, and a sheet that
     * saves also cancels (Save needs its Cancel beside it, half and half).
     */
    @Test fun `sheets are HaloSheet and a sheet with Save has Cancel`() {
        val material = ui.find(Regex("""\b(ModalBottomSheet|BottomSheetScaffold|AlertDialog|ModalDrawerSheet)\b"""))
        if (material.isNotEmpty()) fail("A sheet is HaloSheet (rewards and settings rules B4):\n" + material.joinToString("\n"))
        val glass = ui.find(Regex("""\bGlassSheet\("""), setOf("components/Glass.kt")).filter { !it.startsWith("glow/HaloSheet.kt:") }
        if (glass.isNotEmpty()) fail("GlassSheet is HaloSheet's alone (rewards and settings rules B4):\n" + glass.joinToString("\n"))
        val found = mutableListOf<String>()
        sources.forEach { (path, lines) ->
            val code = lines.joinToString("\n")
            Regex("""\bHaloSheet\(""").findAll(code).forEach { m ->
                val args = balanced(code, m.range.last)
                val open = code.indexOf('{', m.range.last + args.length + 1)
                if (open < 0) return@forEach
                val body = braced(code, open)
                // The footer is in the call's arguments, the rest in its body.
                val saves = "glow_save" in body || "ButtonPair(" in body || "ButtonPair(" in args
                val cancels = "glow_cancel" in body || "ButtonPair(" in body || "ButtonPair(" in args
                if (saves && !cancels) found += "$path:${code.substring(0, m.range.first).count { it == '\n' } + 1}  HaloSheet has Save and no Cancel"
            }
        }
        if (found.isNotEmpty()) fail("A picker sheet ends in Cancel and Save (rewards and settings rules B4):\n" + found.joinToString("\n"))
    }

    /**
     * Detail rules 4 and 6 ("No dots under the days", "No dotted charts"), plan section 1 (dots only on the HaloCharts
     * list: counts and progress to a goal on cards, none on details). The detail pictures are solid lines, bars and bands.
     */
    @Test fun `no dotted chart inside the detail pictures`() {
        val files = listOf("glow/DetailPicture.kt", "glow/DetailSleepPictures.kt", "glow/LoadUsual.kt", "glow/ActivityDetail.kt", "glow/SleepDetail.kt", "glow/ScoreDetail.kt")
        files.forEach { assertTrue("missing $it", it in sources) }
        val hits = files.flatMap { path ->
            sources.getValue(path).mapIndexedNotNull { i, line ->
                if (Regex("""\b(TrendDots|GlowDotBars|DotPanel|DotHeader|DotDivider|DotMatrixText|dotLayout|drawDots)\b""").containsMatchIn(line)) "$path:${i + 1}  ${line.trim()}" else null
            }
        }
        if (hits.isNotEmpty()) fail("A detail's picture is solid, only its header number is dots (detail rules, plan section 1):\n" + hits.joinToString("\n"))
    }
}
