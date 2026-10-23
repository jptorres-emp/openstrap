# Research 5: static glow with meaning, and card sizes

Scope: how strong products use light, gradient and dark/bright composition on dark surfaces to give each element a
character and carry meaning without motion. Then (extra scope) a standard set of card sizes.

Glow parameters referred to below are those in `GlowStyle`: core shape, core X/Y, core width/height, core lightness,
`lightFrom` (Top, Bottom, All), glow size, glow strength, tone band, bevel, depth, grain. New parameters are marked
**new**.

---

## Part 1: Light with meaning

### Light rises to the value
**Problem it solves in OpenStrap:** the Charge card shows a level (energy left) but the light says nothing about how much.
**Seen in:** [Tesla, Charge Stats meter filling green from the bottom](https://mobbin.com/screens/f332f73c-408f-46ec-9201-de21fdd6cfb2), [Google Health, small tiles whose fill shows progress to goal](https://mobbin.com/screens/e8c8fa3a-8dda-4022-a86a-4af0eb6bcf7c), [WHOOP, live heart rate with light filling up from below the curve](https://mobbin.com/screens/cac35c0a-44fe-4233-8f00-e3d173ee5831), [Revolut, Glow backgrounds that fade from a lit edge into black](https://mobbin.com/screens/d898938f-4a71-40c8-8d15-eef60b4d9e38)
**How it works:** the lit part of the surface is the amount. A full battery is mostly lit, an empty one mostly dark. No
needle or ring is needed, the eye reads "how full" from where the light stops. Google Health does it on tiny tiles, so
it survives small sizes.
**For OpenStrap:** make Ember a gauge. Charge 80 puts the light line high (the black top shrinks), Charge 20 leaves a
thin orange band at the bottom like a sunset. **New** parameter `lightLevel` (0..1) that sets core Y and core height
from the value (core Y about `-0.2 + (1 - level)`), with `lightFrom = Bottom`. Same idea fits Activity/Steps (goal
progress). Keep a floor of about 0.12 so an empty card still glows faintly.
**Avoid:** a hard horizontal edge. It turns into a progress bar. The line between dark and light must stay a blur.

### Light from the side the sun is on
**Problem it solves in OpenStrap:** the hero card looks the same at 07:00 and 22:00, so the adaptive day has no light of its own.
**Seen in:** [Tesla, My Home with sky light from the upper left and the rest in shadow](https://mobbin.com/screens/defd506d-bc3a-4963-8357-9067d6f39799), [Fresha, wallet header lit from the top right corner](https://mobbin.com/screens/38cc3463-5c59-4115-b754-c5e3a8161c79), [Oura, "Bedtime's approaching" night sky hero](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071)
**How it works:** one light source with a direction implies a time and a place. Corner light reads as early or late
sun, top light as midday, a low horizon glow as dusk. Oura swaps the whole hero image for a night scene when bedtime
nears.
**For OpenStrap:** drive the hero's light direction by the moment. Waking up: light from the lower left corner, warm.
Midday: from the top. Usual workout time: top right, brighter. Evening: low from the bottom right, dim. Night: barely
lit (next pattern). **New** `lightFrom` values `Left`, `Right`, `TopLeft`, `TopRight`, `BottomLeft`, `BottomRight`, or
better a single `lightAngle` in degrees, with the core pushed to the opposite side.
**Avoid:** following the real clock to the minute. Use the six moments only, so the hero changes a few times a day and
never feels like it drifts.

### Spotlight on the number
**Problem it solves in OpenStrap:** on Recovery and the workout live screen the big number competes with the glow instead of being lit by it.
**Seen in:** [Curater, a cone of light falling on one object, everything else black](https://mobbin.com/screens/79720071-7be0-4cb7-8413-9e387aa97788), [WHOOP, live heart rate inside a dark disk with light around it](https://mobbin.com/screens/cac35c0a-44fe-4233-8f00-e3d173ee5831), [Opal, widget with a single glowing gem in the middle](https://mobbin.com/screens/f6f2258d-c88e-4b70-b278-2950f8aad2bd)
**How it works:** the brightest point of the surface sits where the eye should go. WHOOP does the inverse that also
works: a calm dark disk behind the number, with the light pushed round it, so the number is framed.
**For OpenStrap:** for Recovery and the metric detail header, place the dark core exactly behind the score (core X/Y
set to the number's position, Radial, about 0.45 wide) and keep the rim light all round (`lightFrom = All`). The number
sits in a dark pool framed by colour. On the workout live screen use the WHOOP version: core behind the heart rate,
light below. **New** `coreAnchor` so the core can be placed on a laid-out element instead of a fraction.
**Avoid:** a bright hot spot under white text. Light goes round the number, never under it.

### Brighter rim when off your normal
**Problem it solves in OpenStrap:** a resting heart rate or HRV that is below your normal looks exactly like a normal one.
**Seen in:** [Oura, My Health cards tinted green when "Looking good" and amber when "Needs care"](https://mobbin.com/screens/f528871c-4be6-4bcf-ae97-c06f921449b1), [Opal, home card with a green glow rising from the lower left](https://mobbin.com/screens/ff0db889-bc41-4598-839a-f019972b8563)
**How it works:** Oura keeps the card shape and layout identical and changes only the warmth of a soft corner glow.
The status word is still printed, the light just agrees with it. Nothing flashes or turns red.
**For OpenStrap:** keep each card's hue (it is its identity) and use strength as the status channel. Within normal:
the usual `glowStrength` (about 0.6). Off normal: stronger rim (0.9) plus a thin `bevel` (0.4), so the card looks
slightly lit up. Fits Heart rate, HRV, SpO2, stress and the Recovery card's contributors. Pairs with the brief's "Below
your normal" wording.
**Avoid:** switching the hue to red or amber. It breaks the card's identity and reads as an alarm, which the voice rules forbid.

### Night cards almost unlit
**Problem it solves in OpenStrap:** in the evening and night moment the cards are as bright as at noon, which fights the "quiet, dim" tone.
**Seen in:** [Calm, sleep reflection screen in deep indigo with no highlights](https://mobbin.com/screens/fec18d50-a7b6-4299-a5c1-824842e8cc06), [Calm, Sleep History on a dark violet field](https://mobbin.com/screens/e7717f24-1d45-4a4e-aab0-70ddece37495), [Oura, bedtime hero with only faint starlight](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071), [Stardust, daily forecast on a near-black starfield](https://mobbin.com/screens/9fe5ad72-c2cc-4107-bbda-2e2b6dac31cd)
**How it works:** night screens drop contrast and brightness everywhere at once. The colour is still there but low,
and nothing is the brightest thing in the room.
**For OpenStrap:** a night multiplier for the whole Today screen in the evening and night moment: `glowStrength` x
0.35, core lightness 0.02, tone band off, light from the bottom only (a horizon). The Sleep card can keep this look
all day as its character: a dark sky with a low violet horizon. **New** screen level `GlowMood` (Day, Dim, Night)
applied on top of each card's style.
**Avoid:** going so dim that the cards merge with the black page. Keep the hairline edge at full strength at night so the shapes stay.

### Bigger glow for harder effort
**Problem it solves in OpenStrap:** a 20 minute walk and a hard interval session get the same workout summary card.
**Seen in:** [WHOOP, live heart rate where the lit area grows as the curve climbs](https://mobbin.com/screens/cac35c0a-44fe-4233-8f00-e3d173ee5831), [Sweatcoin, Daily Boost with a layered glowing blob behind the count](https://mobbin.com/screens/db00c4e6-5492-4997-aaa4-8bf50d1780e9), [Stardust, workout bars that glow brighter the higher they go](https://mobbin.com/screens/091568b4-005c-444e-b218-ecbfd6c2416c)
**How it works:** the size and reach of the light scale with intensity. A small effort leaves most of the surface
dark, a big one floods it.
**For OpenStrap:** on the workout summary map effort (strain or load) to `glowSize` (20 to 48 dp) and shrink the core
(core width 1.2 down to 0.7), so a hard session floods the card from below. On the workout live screen set the light
height from the current heart rate zone (zone 1 low, zone 5 almost to the top), using `lightLevel` from the first
pattern. The value updates with the reading, so it is signal, not idle motion.
**Avoid:** mapping too many values. One card, one driven parameter. If size means effort, colour and direction stay fixed.

### One recipe, many hues (the family feel)
**Problem it solves in OpenStrap:** once cards get different light directions and levels, the Today screen could look like six apps.
**Seen in:** [Spotify, playlist headers that take a colour from the cover and fade to the same black](https://mobbin.com/screens/e2697a6f-9951-4d66-adcd-2b3ee0e69de3), [Spotify, the same layout in blue](https://mobbin.com/screens/e64c32b5-563a-43b0-b073-a2452f5a2eae), [Revolut, Glow background set: many hues, one gradient recipe](https://mobbin.com/screens/d898938f-4a71-40c8-8d15-eef60b4d9e38), [Oura, My Health cards with identical shape and type, only the glow differs](https://mobbin.com/screens/f528871c-4be6-4bcf-ae97-c06f921449b1)
**How it works:** they vary one or two things and lock the rest. Spotify changes only the hue, the fade always ends in
the same black at the same height. Revolut's thumbnails all go from a lit edge into near black at the same rate.
**For OpenStrap:** lock these on every card: corner radius, grain amount, hairline edge, the black the core fades to
(lightness 0.02 to 0.09), text colour and text position (always top left), and the blur softness. Let only three
things vary per card: hue (what it is), light side and level (what it means), strength (status). Write this as a rule
in the spec so new cards cannot add a fourth variable.
**Avoid:** letting a card's light side change where its text sits. Text stays put, light moves.

---

## Card sizes

### A small fixed size set on a two column grid
**Problem it solves in OpenStrap:** Today mixes full width cards of many heights, so nothing lines up and every card claims equal weight.
**Seen in:** [Jomo, the same widget in large, medium and two small sizes](https://mobbin.com/screens/7ed174d3-40ef-4137-9b8a-830e40682ffc), [Waymo, a tall tile beside two stacked squares](https://mobbin.com/screens/e8d3839e-537f-40a8-981c-1b87645e441a), [Google Health, a large ring tile beside three short wide tiles](https://mobbin.com/screens/e8c8fa3a-8dda-4022-a86a-4af0eb6bcf7c), [Sweatcoin, square and wide home screen widgets](https://mobbin.com/screens/36fdc175-debf-440c-b42e-bc9cdbf6f4c7)
**How it works:** the best sets have four or five sizes built from one unit, on two columns: small square (1x1),
wide (2x1, about 2.1:1), large (2x2, square or 4:3), tall (1x2), and sometimes a short full width strip. Every size is
a multiple of the unit plus gaps, so any mix lines up.
**For OpenStrap:** adopt five sizes on a two column grid with a 12 dp gap. **Small** 1x1. **Wide** 2x1. **Large**
2x2 (the hero). **Tall** 1x2. **Strip** full width at half the small height, for status and actions. Proposed mapping:
- Large: the hero of the moment (Recovery when waking up, Charge at midday, Readiness at workout time, live workout, workout summary after, the day's total in the evening).
- Tall: Charge when it is not the hero (the light level reads best on a vertical battery).
- Wide: Sleep last night (number plus stage strip), Heart rate (number plus sparkline), Activity/Steps (number plus progress), Recovery when not the hero.
- Small: Resting HR, HRV, SpO2, Stress, Habit Age, Load.
- Strip: strap state (syncing, disconnected, charging), the "partial day" caveat, Start workout.
**Avoid:** more than five sizes or free sizes. The grid stops being a grid.

### Size follows role: hero, chart, glance
**Problem it solves in OpenStrap:** a single fact like SpO2 gets as much space as Recovery, so the morning answer takes longer than 3 seconds.
**Seen in:** [Bevel, a full width ring strip then full width charts](https://mobbin.com/screens/991baf8e-e320-4315-bba3-065e91bca20d), [Google Health, top grid of number and status tiles above the coach text](https://mobbin.com/screens/791c26f4-03f8-4280-b5bc-824ba6c1532d), [WHOOP, widgets where only the combined view gets the big size](https://mobbin.com/screens/eb710c39-dc18-4cb5-954d-d71b96c42519)
**How it works:** size is decided by job, not by importance of the metric in general. One answer to "how am I" gets the
large size. Anything with a shape over time (a chart) needs width. A single fact with a status word fits a square.
**For OpenStrap:** a simple rule for the spec. Sentence plus number plus why: Large. Number plus a trend or strip:
Wide. Number plus status word: Small. Something you act on or a state: Strip. The adaptive day promotes a card to Large
by moment, and the card it replaces drops to its normal size.
**Avoid:** giving a chart a square. A sparkline under 160 dp wide is noise.

### Content steps down with size
**Problem it solves in OpenStrap:** the same card component has to look right as a hero in the morning and as a smaller tile at midday.
**Seen in:** [American Airlines, medium widget with full trip details and small with time, status and gate only](https://mobbin.com/screens/34c8ab43-8b6e-4151-a01a-8b0276546663), [Jomo, Rules list in large shows four rows, medium two, small one with a timer](https://mobbin.com/screens/7ed174d3-40ef-4137-9b8a-830e40682ffc), [Perplexity, wide widget with four actions and small with two](https://mobbin.com/screens/9289eebd-4221-42df-9b55-82fef51780a4)
**How it works:** each step down removes a layer, in a fixed order. The headline value and the status always stay.
Detail, secondary numbers and actions go first. Type size of the headline barely changes.
**For OpenStrap:** define each card at three depths that match the voice rule. Large: sentence, number, small chart.
Wide: number, status word, sparkline or strip. Small: number and status word only. Example for Sleep: Large "7 h 40,
your best this week" plus stage chart, Wide "7 h 40" plus stage strip, Small "7 h 40" plus "Good".
**Avoid:** shrinking the type to fit the large content into a small tile.

### Pack mixed sizes in pairs, without gaps
**Problem it solves in OpenStrap:** with adaptive reordering, mixed sizes could leave holes in the grid at some moments.
**Seen in:** [Waymo, one tall tile next to two squares](https://mobbin.com/screens/e8d3839e-537f-40a8-981c-1b87645e441a), [pillowtalk, a bento of squares, a wide row and a tall circle tile](https://mobbin.com/screens/e4f6a8a2-4492-4cf2-b025-e3db69b1f705), [Google Health, two by two grid of short tiles](https://mobbin.com/screens/791c26f4-03f8-4280-b5bc-824ba6c1532d)
**How it works:** the layouts use a few row shapes only: one full width item, two smalls, or a tall beside two smalls
stacked. Anything else is not allowed, so there is never a hole.
**For OpenStrap:** build Today from row types, not free placement: `Full(Large | Wide | Strip)`, `Pair(Small, Small)`,
`TallPair(Tall, Small over Small)`. The adaptive ordering picks the order of rows, and a lone Small is promoted to Wide
(it gets its sparkline back) rather than leaving half a row empty.
**Avoid:** masonry layouts with uneven bottoms. They look broken on a phone width.

### Reorder and hide, but not resize
**Problem it solves in OpenStrap:** users want their own Today, but the adaptive day also reorders cards.
**Seen in:** [WHOOP, Customize Dashboard as a list with drag handles](https://mobbin.com/screens/44f95ec3-e60f-4cb3-987c-0085c2a6094c), [Google Health, Edit focus with plus and minus on tiles and a Small section](https://mobbin.com/screens/e8c8fa3a-8dda-4022-a86a-4af0eb6bcf7c), [Lloyds, a Customise button at the end of the home feed](https://mobbin.com/screens/c3d2d3aa-d2d0-4b5c-a3e9-3e6e9fa02a77)
**How it works:** editing happens in a separate mode, reached from the end of the feed. WHOOP uses a plain list with
handles, which is easier than dragging tiles. Google Health lets you choose what is in the focus area and the rest
falls into a pool of small tiles.
**For OpenStrap:** offer "Edit Today" at the bottom of the feed: a list where you hide cards and pin up to two to stay
near the top. The app keeps choosing sizes and the hero by moment. No resizing by the user.
**Avoid:** user ordering that fights the adaptive hero. Pinned cards sit under the hero, never above it.

### Glow composition scales with the tile
**Problem it solves in OpenStrap:** a small square cannot carry the rounded core, tone band, bevel and rim of a full width card, it turns into mud.
**Seen in:** [Opal, the same glowing gem widget at wide and small size](https://mobbin.com/screens/f6f2258d-c88e-4b70-b278-2950f8aad2bd), [Revolut, Glow thumbnails that still read as glow at tiny size](https://mobbin.com/screens/d898938f-4a71-40c8-8d15-eef60b4d9e38), [Sweatcoin, square and wide widgets with the same gradient](https://mobbin.com/screens/36fdc175-debf-440c-b42e-bc9cdbf6f4c7)
**How it works:** at small sizes they keep one gradient from one edge and drop every other layer. Opal keeps the focal
light and lets the surrounding glow scale with the tile. Revolut's thumbnails are only a lit edge into black.
**For OpenStrap:** set glow per size, with blur and glow size in proportion to the shorter side (not fixed dp).
- Large: full recipe. Core, tone band, light side by meaning, optional bevel and depth.
- Wide: light from the side (Left or Right), core on the other side behind the text, tone band thin, no depth.
- Tall: light from the bottom with `lightLevel` (the battery). No tone band.
- Small: one light source and a Radial core, no tone band, no bevel, no depth. Status shown by strength only.
- Strip: no core, a faint rim from the left edge only, so it reads as a quieter element than cards.
**Avoid:** scaling the full recipe down. Blur at 30 dp on a 160 dp tile eats the whole card.

---

## Anti-patterns seen

- [Revolut, Customise card on a flat bright yellow](https://mobbin.com/screens/f480b69b-4bae-4e4d-a9aa-0fdaca5e597d): a fully lit surface on black has no depth or focus. Light needs dark next to it.
- [Stardust, settings list where every toggle glows](https://mobbin.com/screens/08d5c249-e8e8-4c56-a21a-a482284f402f): when everything glows, nothing means anything.
- [Bevel, empty rings and charts in light grey](https://mobbin.com/screens/7d6cdcff-945b-47f6-a22f-0d9fca59c4a4): empty states with no light at all look broken, not calm. Keep a faint floor of light.
- [Tolan, a full screen two hue gradient behind text](https://mobbin.com/screens/ef79967c-af70-4576-8f8c-30d00e6f388a): two strong hues meeting in the middle carry no meaning and reduce text contrast.
- [Gentler Streak, many sections each with its own unrelated style](https://mobbin.com/screens/d52cd871-49fa-4ccb-b944-cb4a60ffed0d): no family rule, so the recap feels like a collage.
