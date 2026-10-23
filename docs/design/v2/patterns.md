# Design v2: pattern board

Step 2 of the [brief](brief.md). It pairs what the [inventory](inventory.md) found wrong with patterns from strong
apps on Mobbin (68 searches, iOS and web, in and outside health). The full notes with every link are in
[research/](research/). Inventory problems are cited as **F1** to **F20** (findability, inventory section 6).

Each pattern has a proposal. The decisions that need you are collected at the end.

## A. Today follows your day

Today now has one time rule (before noon the Sleep card moves up) and leads with a dial whose meaning sits at the
bottom of the screen. Nothing changes for evening, workout time or after a workout.

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| A1 | **One hero slot, content swaps by moment** | [Oura bedtime](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071), [Uber trip states](https://mobbin.com/screens/02e52b85-dd21-40b0-b75c-548b35ae2125) | The top card never moves. What fills it follows the adaptive day: Last night and Recovery on waking, Charge at midday, readiness plus Start at your usual workout time, live numbers while recording, the effort after, the day's total at night. The glow colour is the moment's colour. |
| A2 | **Sentence above the number, key words bright** | [Gentler Streak](https://mobbin.com/screens/f9bf6004-fa31-4dbe-818a-6acbd51f0cae), [Apple Weather summary](https://mobbin.com/screens/facfb5a0-be47-4083-8b61-fbe53602f7d5), [Joi](https://mobbin.com/screens/362e5a69-9886-49b8-be3e-a260377a8681) | Hero starts with one sentence in large type, score under it. Key words full white, the rest dimmed, so a 2 second glance reads "Ready" and "short sleep". |
| A3 | **Modules reorder at moment boundaries only** | [Copilot dashboard](https://mobbin.com/screens/39e851c1-7c5d-4f1e-bf81-1c3be05e5566), [CARROT tiles](https://mobbin.com/screens/6865b73f-ba1c-49ff-9b11-7e1d86a7d8e5) | A stable set of cards sorted by relevance for the moment. Order changes when the moment changes, never on every open. |
| A4 | **Past moments collapse, never vanish** | [Oura bedtime strip](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071) | After midday, Last night becomes one line ("Slept 7 h 40, score 83"). The full hypnogram lives on Sleep. Fixes the vanishing cards behind F16. |
| A5 | **State plus relative time** | [Flighty](https://mobbin.com/screens/939d5aae-4c27-4a32-b91c-767ee82a1845), [Lightyear](https://mobbin.com/screens/a4f39152-01fd-4b78-986d-d381ef12fd57) | Active moments headline as state and duration: "Running, 32 min", "Since your run, 40 min", "Bedtime in 1 h 10". Never "Started 07:42". |
| A6 | **One next action, about your body** | [Runna](https://mobbin.com/screens/be9b0bcc-6c28-4abf-bde0-6b046df1207d), [Airbnb timeline](https://mobbin.com/screens/1e9e8e06-9b27-4ac5-8ea4-b6da24875fdb) | At usual workout time the hero gains Start workout and "You usually train around now." After, the next step is passive: "Back to resting in about 20 min." |
| A7 | **Evening dims and goes quiet** | [Oura night](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071), [CARROT night colour](https://mobbin.com/screens/046e634e-141f-427d-89ad-9e0b448377fc) | After the evening boundary every card drops glow intensity, scores shrink to a strip, the hero reads the day's total and one time cue. |
| A8 | **Say why the screen changed** | [Bevel sync pill](https://mobbin.com/screens/485cc150-d5ee-4d3c-b515-22a5524c10e1), [Monzo summary card](https://mobbin.com/screens/07263bcf-0be2-4cfc-a105-c0e047a96e90) | The header names the moment ("Midday", "Evening") so a reorder has a visible reason. New data shows a short "Updated" pill, then values move to their new numbers. |

## B. Finding things

The inventory found 20 findability problems. The worst: a Today card either opens a detail or jumps to another tab
(F1), any tab tap wipes every tab's history (F2), and settings sit three taps deep (F5).

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| B1 | **A card opens its own detail, in place** | [Apple Weather detail sheet](https://mobbin.com/flows/53773f08-043d-43ab-a8f3-2d04e5b6a847), [Apple Health card to detail](https://mobbin.com/flows/dfd9da7c-b0a2-4deb-871c-e8c7df224db8) | One rule: tapping any card opens its detail grown out of that card, and closing returns you to the card. No tap ever switches tab. Fixes F1, F11 (every own score gets a detail), F17, F18. |
| B2 | **Switch day or metric inside the detail** | [Apple Weather metric switcher](https://mobbin.com/flows/cde5e30c-cde3-4e39-aa0e-86c0fac3002d) | A day strip and a sibling metric switcher at the top of the detail, so seeing yesterday's HRV doesn't mean backing out twice. |
| B3 | **Tabs keep their place** | (inventory F2) | Tapping the current tab scrolls to top, a second tap pops to root. Other tabs keep their stacks. Back returns to the tab you came from. |
| B4 | **Compact pill tab bar with one action circle** | [Linear](https://mobbin.com/screens/97e4122c-fb36-414e-8f11-da31e1a8aebc), [Oura](https://mobbin.com/screens/cac516c9-f63d-4b42-b66d-342c942a5b0e), [Orbit on dark glow](https://mobbin.com/screens/ccee144c-65ce-4813-8876-2b4f67017a63) | Labelled pill, active tab tinted with its section colour. A detached circle holds the moment's action: Start workout at usual time, live heart rate while recording, otherwise the day picker or search (see decision 3). Content gets bottom padding so nothing sits under the bar. |
| B5 | **You behind an avatar, tabs for the day** | [Monzo](https://mobbin.com/screens/f783c73d-c6ab-4b2c-8335-8e6738917c55), [Revolut](https://mobbin.com/screens/8b0c7aa9-1dd5-45e5-92c4-35dc1ffb5559) | Profile, goals, your data and about open from an avatar as a sheet. The bar drops to four tabs. Fixes F6 (sleep need and sleep goal sit together) and F15. See decision 2. |
| B6 | **The strap is a chip with state, opening one Strap page** | [WHOOP chip](https://mobbin.com/screens/c5067dc0-aae8-4bb3-a55b-5f26b78a7374), [Tesla](https://mobbin.com/screens/c0a37ffb-23c3-44d7-a3e8-fd50735582e0) | Top right chip: battery, dot, and words when it matters ("Charging 64%", "Last seen 18 min ago", "Not paired"). It opens a Strap page with alarms, phone alerts, auto-detect, firmware and the Zepp account one tap away. Fixes F5, F12, and the shared icon for "not paired" and "disconnected". |
| B7 | **Settings rows show their current value** | [Tesla live subtitles](https://mobbin.com/screens/09366a54-bd95-4774-8289-2377e6bffbf0), [Fitbit values](https://mobbin.com/screens/3385ad66-c7a1-402f-9f21-0e2554efa040) | "Wake alarm, 06:40 weekdays", "Firmware, up to date", "Health Connect, last copy this morning". State, not description. And the strap and You pages move to glow styling (F20). |
| B8 | **Day header that shows its neighbours** | [Oura peek](https://mobbin.com/screens/aba0560d-220a-4b2c-9700-d603272c67f7), [Apple Fitness dots](https://mobbin.com/screens/eac5518f-afb2-4ddc-9fdf-9680fb1bc1d4) | Swipe the date header, yesterday peeks in dimmed. The calendar marks days with data. Chevrons go. |
| B9 | **Summary strip stays when you scroll** | [WHOOP collapsing rings](https://mobbin.com/screens/c6049cfc-e244-4f46-88e7-f2f4d620e7bb) | When the hero scrolls off, a slim strip keeps Recovery, Charge and Sleep visible, each tappable. Tracks scroll exactly, no bounce. |
| B10 | **History as a filtered list and a month grid** | [Spotify Library chips](https://mobbin.com/screens/c8300705-3b32-4dc2-af76-1e1097c553e3), [Strava calendar](https://mobbin.com/screens/02f8ff4a-0d9d-492b-8be8-08fb9e9fe486) | All workouts is always reachable, with chips (All, Runs, Walks, Strength, Auto-detected) and a month grid toggle. Fixes F10. |
| B11 | **One home per thing, one name per thing** | [Tesla duplicate as anti-pattern](https://mobbin.com/screens/f8bf3894-4347-4f98-97ce-174ae1b45fc7) | Body metrics appear once with one rule (fixes F3). Regularity once (F8). "Recovery" means only the score, the rest get distinct names like "Time to recover" and "Heart rate drop" (F4). Load gets one name family (F19). "Breathing rate" vs "Breathing disturbances" (F9). Live HR switch in one place (F13). |

## C. Explaining numbers

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| C1 | **Value with a status word, then chart, then one sentence** | [Apple Weather UV](https://mobbin.com/screens/932444de-875b-45e6-b56c-c1e2df20610f), [Apple Health highlight](https://mobbin.com/screens/92cf868c-3d1c-434b-aba1-ea52a196e03a) | Every detail opens with "75 Ready" where the word glows, then the chart, then one time-aware sentence. |
| C2 | **Your normal as a glow band** | [Strava range band](https://mobbin.com/screens/fff0b506-9862-44e7-80a9-e1e447d404d9), [Oura baseline](https://mobbin.com/screens/e56ac286-cc80-477d-bed3-effd19f3f7ad) | Dim band behind every chart. Points inside stay dim, points outside glow, so unusual days jump out. Before there's enough data: "Learning your normal, 4 of 14 nights", never a fake band. |
| C3 | **One status vocabulary, relative to you** | [Oura contributors](https://mobbin.com/screens/aba0560d-220a-4b2c-9700-d603272c67f7) (minus its "Pay attention") | "Above your normal", "Usual", "Below your normal" everywhere. Glow only on off-normal. Retires "Pay attention", "Primed", "Signals off baseline". |
| C4 | **Range switch rewrites the headline** | [Apple Health D W M Y](https://mobbin.com/screens/c6701e04-13cd-40ee-825f-e98a516ff86d), [WHOOP 6M sentence](https://mobbin.com/screens/ac9866dd-4010-4028-b0c3-bf92ebcbb40a) | Number, sentence and chart always describe the same span. |
| C5 | **Scrub the chart, the headline follows** | [Apple Health scrub](https://mobbin.com/screens/51dd8941-0326-4b0d-8f48-b5fbbcdd3539) | The big number becomes the day under your finger, no tooltip. Haptics as in F6m. Long press to scrub, so it doesn't fight the day swipe. |
| C6 | **Info sheet in three layers** | [Oura Resilience](https://mobbin.com/screens/b67baac0-cc03-4ae0-bdf4-3c8cc7e81347), [MyDyson scale](https://mobbin.com/screens/829efb48-202a-4e55-a9fb-198d00ff9ad8) | Bold one-line meaning, the scale as coloured bars, then folded rows: How it's worked out, What moves it, Why it might be off. All method text moves here off the cards (24 strings). |
| C7 | **One caveat marker per screen** | [Oura dashed gaps](https://mobbin.com/screens/6aee12bc-6f70-4812-a83e-455d5af2d77a), [Opendoor](https://mobbin.com/screens/af22bb55-e74b-49f6-b09c-72d333040966) | A small outlined pill ("Partial day", "Early estimate") that explains itself on tap. Hollow points and dashed lines for low confidence. |
| C8 | **Compare pace, not totals** | [Apple Health on track](https://mobbin.com/screens/92cf868c-3d1c-434b-aba1-ea52a196e03a), [Copilot pace line](https://mobbin.com/screens/39e851c1-7c5d-4f1e-bf81-1c3be05e5566) | Steps, Load and Charge compare "so far today" with your usual by this time, drawn as a faint ghost line. "About usual for midday." |
| C9 | **Week told as a short story** | [Spotify Wrapped](https://mobbin.com/screens/9cc6cdc9-6c8f-49aa-99f7-1531de8b5017), [Oura weekly](https://mobbin.com/screens/3cba06a0-6e7b-4691-acd0-3f6a735783d8) | The existing Monday "Your week" card becomes 4 or 5 black slides, one number and one sentence each. Stays findable all week (F16). See decision 5. |

## D. Workouts, live and after

The live workout screen is already the strongest part of the app. Changes are small.

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| D1 | **Colour says recording or paused** | [Nike Run Club](https://mobbin.com/screens/6b506d61-b2e1-4690-9cf8-23aa94a80a7e), [Strava ready bar](https://mobbin.com/flows/e6356a85-1710-4b88-b329-e6d3df02d2b6) | Recording is full glow, paused drops to an outline of the same layout. Ready screen confirms "Strap connected. 72 bpm." |
| D2 | **Live number on a zone arc** | [Ultrahuman](https://mobbin.com/screens/5fad3d2c-5723-4f47-98e1-b72abc2fdf20), [Oura live](https://mobbin.com/screens/fc0c9260-f9a1-494b-8034-a477e697fc8e) | Zone segments glow, only the current one lit, zone name and range on the arc. A tick when the zone changes. |
| D3 | **Hold to end, then calm down** | [Apple Fitness](https://mobbin.com/flows/5c95a0fb-e7a1-4134-8235-086a37556fca), [Bevel recovery drop](https://mobbin.com/screens/9dca9847-d793-4f27-af96-5f4f200e17f9) | Hold to end stays. The first screen after leads with the live heart rate drop drawing as it happens, glow toned down, effort and Load below. |
| D4 | **Live state outside the app** | [Flighty live activity](https://mobbin.com/screens/068a8ac5-0520-4d38-a542-32d3b4d96de5) | The ongoing notification gets the same black card: time, bpm in zone colour, a thin zone bar. Three numbers at most. |

| D5 | **Two live views, heart rate never hidden** (approved) | [Strava record](https://mobbin.com/flows/e6356a85-1710-4b88-b329-e6d3df02d2b6), research [4](research/4-live-states-motion.md) | GPS workouts drop from three tabs (Heart, Map, Splits) to two views: Heart, and Map with a bpm chip in the zone colour on top. Swipe with two small dots instead of segment buttons. The last view is remembered. Splits leave the live screen: after each km a line shows under the stats for about 10 seconds ("Km 3 in 5:42, avg 152") with one tick, and the full table lives in the summary. |

## E. Every state

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| E1 | **Sync problems stay quiet until they matter** (revised 2026-10-23) | | No bar by default. The strap chip turns amber and says "Not synced", the app retries in the background, and stale cards carry an "Up to 09:40" marker. Only when it matters (still failing after a few hours, or in the morning when last night is stuck on the strap) does the hero slot take it over with one "Sync now" button. Supersedes the slim bar below. |
| E1 (old) | **Sync as one slim bar with a timestamp** | [WHOOP catching up](https://mobbin.com/screens/581ae395-67dd-477c-ab95-0fe26fb3c9f4) | "Catching up. Synced to 03:10." Cards keep last values and move to new ones. Failure turns the same bar amber in place: "Sync stopped at 03:10. Try again." Today, a failed sync shows only on the strap page. |
| E2 | **Strap state with "last seen"** | [Codex offline](https://mobbin.com/screens/635cf9e7-fad6-4e72-b13d-20c138e4af00), [Starlink](https://mobbin.com/screens/a79e31e4-17b1-4ae9-a9c7-1bc3239b3f83) | On the chip and the Strap page, one fix per state. Charging says that wear data pauses. No shouting banners. |
| E3 | **Pairing is one stage** | [Sonos](https://mobbin.com/flows/8887a914-022d-483e-a280-e306103609a5), [WHOOP pairing](https://mobbin.com/flows/381a9375-b5c7-45fa-b2de-bf0872f17b6f) | The strap stays still and glowing, only the line under it changes. A dotted line pulses while connecting, turns solid with one firm haptic. With no strap paired, Today's hero leads with Pair. |
| E4 | **Waiting cards keep the real layout** | [Oura no score yet](https://mobbin.com/screens/f2b123f2-bf28-4fc4-83b7-b4d4a7f91ab1), [Oura calibrating](https://mobbin.com/screens/8209e734-2c6c-45ed-951e-8f7600eed44b) | Same card, low glow, a dash for the number, and when it fills: "After your first night". Cards no longer disappear. The first real score is marked once. |
| E5 | **Errors where they happened** | [Revolut inline](https://mobbin.com/screens/2a8c9c56-b4e5-4bd5-8e22-10d2f94f7892), [Monzo reassurance](https://mobbin.com/screens/c73a3c93-eb1e-4c54-80c9-27e68531e9d0) | Keep the last good value, dim it, one line and one action. Say when data is safe: "Your data is still on the strap." Fixes Recovery's endless "Calculating…" when its computation fails. |

## F. Motion and haptics

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| F1m | **Card grows into its detail** | [Gentler Streak tile tint](https://mobbin.com/flows/cdfe56f0-afc7-4850-93d6-a0b9240cf80b), [Apple Health](https://mobbin.com/flows/dfd9da7c-b0a2-4deb-871c-e8c7df224db8) | The card's glow expands into the detail background and its number becomes the header. Fast in, slow settle, no bounce. Replaces the generic slide. |
| F2m | **Values move to the new number, not from zero** | inventory 4.1 | Sleep arc no longer sweeps from 0 on every appear. Only changed values animate, after a sync. |
| F3m | **Only live things loop** | inventory 4.2 | 14 cards breathe, drift or beat with no live signal behind them, including a heart beating at a reading hours old. See decision 4. |
| F4m | **Quiet milestones** | [Insight Timer](https://mobbin.com/screens/3a74a615-658c-463c-b117-afac5989f66e) vs [Duolingo](https://mobbin.com/screens/84ec5a7b-37ce-4c38-808d-7c3c91c3c0aa) | A personal best is a short glow swell and one tick. "Fastest 5 km since March." No modal, no confetti. |
| F5m | **Haptic map** | brief | Add: workout start (firm), zone change and set done (tick), pairing success (firm), chart scrub (F6m). Change: sync failure gets its own feel instead of the success tick. Remove: the pull ratchet ticks (ordinary gesture). |

| F6m | **Chart scrub you can feel** | [Apple Health scrub](https://mobbin.com/screens/51dd8941-0326-4b0d-8f48-b5fbbcdd3539), [Finimize](https://mobbin.com/screens/984065ba-bbe8-417c-a706-11c888996e17) | Every scrubbable chart (metric detail, hypnogram, Charge curve, workout heart rate) gives a light tick each time the finger lands on a new point (a day, an hour, a sleep stage). A stronger tick when crossing in or out of your normal band, and at the highest and lowest point. Ticks are rate-limited so a fast drag stays smooth, and none on release. |

## G. Glow with meaning, and card sizes

With the idle motion gone, the glow itself carries character and meaning. Today almost every card uses one recipe
(light from the top, rounded core low in the middle). Only Charge (Ember: black top, orange rising from below), Sleep
and the hero differ, which is why Charge stands out. Full notes in [research/5](research/5-glow-and-sizes.md).

### G1. Light with meaning

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| G1a | **Light rises to the value** | [Tesla charge meter](https://mobbin.com/screens/f332f73c-408f-46ec-9201-de21fdd6cfb2), [Google Health fill tiles](https://mobbin.com/screens/e8c8fa3a-8dda-4022-a86a-4af0eb6bcf7c) | Ember becomes a gauge. Charge 80 lights most of the card, Charge 20 leaves a thin orange horizon like a sunset. Same for steps toward the goal. The edge between dark and light stays a soft blur, never a progress bar. New parameter `lightLevel`. |
| G1b | **Light from the side the sun is on** | [Tesla My Home](https://mobbin.com/screens/defd506d-bc3a-4963-8357-9067d6f39799), [Oura night hero](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071) | The hero's light follows the moment: lower left and warm on waking, top at midday, top right and brighter at workout time, low right and dim in the evening, a faint horizon at night. Changes only at moment boundaries. New parameter `lightAngle`. |
| G1c | **The number sits in a dark pool** | [WHOOP live HR](https://mobbin.com/screens/cac35c0a-44fe-4233-8f00-e3d173ee5831), [Curater spotlight](https://mobbin.com/screens/79720071-7be0-4cb7-8413-9e387aa97788) | On Recovery, metric detail headers and the live workout, the dark core sits right behind the big number with light all round, so the colour frames it. Light goes round the number, never under it. |
| G1d | **Stronger light when off your normal** | [Oura My Health](https://mobbin.com/screens/f528871c-4be6-4bcf-ae97-c06f921449b1) | The hue stays (it's the card's identity). Strength is the status: usual cards glow softly, off-normal cards get a stronger rim and a thin bevel. Never switch to red. |
| G1e | **Night cards almost unlit** | [Calm](https://mobbin.com/screens/fec18d50-a7b6-4299-a5c1-824842e8cc06), [Oura bedtime](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071) | A screen mood (Day, Dim, Night) on top of every card. Night: about a third of the glow, light from below only, hairline edges kept so shapes stay. Sleep keeps this look all day. |
| G1f | **More effort, more light** | [WHOOP](https://mobbin.com/screens/cac35c0a-44fe-4233-8f00-e3d173ee5831), [Stardust bars](https://mobbin.com/screens/091568b4-005c-444e-b218-ecbfd6c2416c) | A hard session floods the summary card from below, an easy walk leaves it mostly dark. During a workout the light height follows the heart rate zone, which is live signal, not idle motion. |
| G1g | **One recipe, many hues** | [Spotify headers](https://mobbin.com/screens/e2697a6f-9951-4d66-adcd-2b3ee0e69de3), [Revolut glow set](https://mobbin.com/screens/d898938f-4a71-40c8-8d15-eef60b4d9e38) | The family rule. Locked on every card: radius, grain, hairline edge, the black the core fades to, blur softness, text colour and position. Free per card: hue (what it is), light side and level (what it means), strength (status). Nothing else. |

**G1j. Dots are for numbers (decided 2026-10-23).** Only the dot-matrix digits are made of dots, so they stay the
signature. Charts use lines, arcs, bars and bands: hourly steps are rounded pill bars, and hours still to come are
thin flat stubs. The fine grain inside glow cards stays, since it doesn't read as dots.

**G1i. Three card tiers (decided 2026-10-23).** Glow card for the main card of a screen (plus at most one more).
Edge light card for mid-importance cards with a shape of their own (last night, heart rate, activity, load, sleep
regularity, Habit Age). Grouped list for small facts: body signals, time to recover, energy and the like collapse
into one panel per screen, each row a coloured dot, name, value and status, with colour on the text only when off
your normal.

**G1h. The glow budget (decided 2026-10-23).** The top card plus at most one more glows on a screen. Everything
else is the quiet layer: an **edge light card**, near black with a soft glow coming in from one edge in the metric
colour, and only a hairline border (no bright line). It replaces the dotted `DotPanel`. Off your normal, the glow
grows and the hairline takes the metric colour. Neutral panels (lists, settings) keep a plain hairline with a faint white edge.
Where the glow sits carries meaning: bottom by default, top when above your normal, a stronger bottom glow when
below; progress lights the bottom edge
only as far as the value; live cards light the right edge (now), cards about earlier light the left; sleep cards
light the top, like a night sky. Static, never animated.

### G2. Card sizes

| # | Pattern | Seen in | Proposal for OpenStrap |
|---|---|---|---|
| G2a | **Five sizes on a two column grid** | [Jomo widget sizes](https://mobbin.com/screens/7ed174d3-40ef-4137-9b8a-830e40682ffc), [Waymo tall plus squares](https://mobbin.com/screens/e8d3839e-537f-40a8-981c-1b87645e441a) | Small 1x1, Wide 2x1, Large 2x2, Tall 1x2, Strip (full width, half height). One unit, 12 dp gap, so any mix lines up. |
| G2b | **Size follows the job** | [Bevel](https://mobbin.com/screens/991baf8e-e320-4315-bba3-065e91bca20d), [Google Health](https://mobbin.com/screens/791c26f4-03f8-4280-b5bc-824ba6c1532d) | Sentence, number and why: Large. Number with a trend or strip: Wide. Number and status word: Small. A state or an action: Strip. A chart never gets a square. |
| G2c | **Content steps down with size** | [American Airlines widgets](https://mobbin.com/screens/34c8ab43-8b6e-4151-a01a-8b0276546663) | Each card is defined at three depths that match the voice rule. Sleep: Large "7 h 40, your best this week" with stages, Wide "7 h 40" with a stage strip, Small "7 h 40, Good". Type never shrinks to fit. |
| G2d | **Rows, not free placement** | [Waymo](https://mobbin.com/screens/e8d3839e-537f-40a8-981c-1b87645e441a), [pillowtalk bento](https://mobbin.com/screens/e4f6a8a2-4492-4cf2-b025-e3db69b1f705) | Today is built from three row shapes: one full width card, two Smalls, or a Tall beside two stacked Smalls. A lone Small grows to Wide. No holes, no uneven bottoms. |
| G2e | **Glow scales with the tile** | [Opal gem widget](https://mobbin.com/screens/f6f2258d-c88e-4b70-b278-2950f8aad2bd), [Revolut thumbnails](https://mobbin.com/screens/d898938f-4a71-40c8-8d15-eef60b4d9e38) | Large: the full recipe. Wide: light from one side, core behind the text. Tall: light from below with a level (the battery). Small: one light source, no band, no bevel. Strip: a faint rim from the left only. Blur scales with the shorter side. |
| G2f | **Hide and pin, not resize** | [WHOOP customise](https://mobbin.com/screens/44f95ec3-e60f-4cb3-987c-0085c2a6094c) | "Edit Today" at the end of the feed: hide cards, pin up to two under the hero. The app keeps choosing sizes and the hero. It replaces today's "What changed shows" chooser. |

Proposed sizes for today's cards:

| Size | Cards |
|---|---|
| Large | The hero of the moment: Recovery on waking, Charge at midday, readiness and Start at workout time, live workout, the effort after, the day's total in the evening |
| Tall | Charge when it isn't the hero (light level as a battery) |
| Wide | Last night, Heart rate, Activity, Recovery when it isn't the hero, Habit Age |
| Small | Resting heart rate, HRV, Blood oxygen, Stress, Skin temperature, Breathing, Load |
| Strip | Strap state, sync bar, the caveat marker, Start workout outside the workout moment |

## Copy, by the numbers

1,209 user-facing strings. 223 break a rule:

- 64 interpunct, 26 semicolon, 37 em dash (35 of them as the empty value).
- 45 health acronyms, 13 technical terms, 24 method or formula names on cards.
- 34 alarming words.
- cheering or "you should": "Primed", "Great night", "A good day to push hard.", "Favour rest, light movement and an early night."

The empty value becomes a quiet dash glyph drawn by the UI, not a character in the text. The full list is in the
[inventory](inventory.md#5-copy-audit). Rewrites happen screen by screen in step 6.

## Anti-patterns to keep out

- Three equal dials, so nothing leads ([WHOOP](https://mobbin.com/screens/b28890cc-6d12-4acc-bc1f-c37510182815)).
- Promo or advice paragraphs in the hero ([WHOOP](https://mobbin.com/screens/c8932f72-07aa-4495-9b48-ca4fead689bb), [Oura](https://mobbin.com/flows/6d9deee0-6c57-47ca-8c7a-4348dd396f1e)).
- Jokey or cheering copy ([CARROT](https://mobbin.com/screens/33fd6f9c-2dda-4c3f-b595-3926c2f20cbf), [Bevel](https://mobbin.com/screens/b3d64dfc-fba8-45cb-b3a8-68abc557ae4b)).
- An empty state that looks broken ([Fitbit "--%"](https://mobbin.com/screens/a300c0c9-fdc4-4cf7-a38d-f0a99cd38eb2)).
- Bare percentage changes ([Copilot "999%"](https://mobbin.com/screens/60052b55-ee17-47bc-b982-5016542b250c)).
- A junk drawer "More" tab ([WHOOP](https://mobbin.com/screens/c5067dc0-aae8-4bb3-a55b-5f26b78a7374)).
- Full-screen offline pages that hide data you already had ([GoPay](https://mobbin.com/screens/95483b32-a4d7-4025-b6e7-64220245eff5)).

## Decisions (all six approved 2026-10-23)

1. **Detail as a sheet or a full page.** A sheet grown from the card (Apple Weather) keeps Today visible behind and
   makes "close returns you" obvious. A full page grown from the card gives charts more room. Recommendation: full
   page with the grow-from-card motion, since detail pages hold charts, history and the info sheet.
2. **Four tabs plus an avatar.** Move You behind an avatar on Today, keep Today, Recovery, Sleep, Activity.
   Recommendation: yes. You is visited monthly and the strap chip covers the daily strap needs.
3. **Search and an All metrics index.** Both fix findability but are new surfaces, and the brief says no new
   features. Recommendation: an All metrics index (it's navigation, a map of details that already exist) and no
   search for now.
4. **Idle glow motion.** The breathing and drifting cards are part of the look people liked, but they break "only live
   things loop". Recommendation: every card still, and the character moves into the static glow (section G). Live
   heart rate keeps its real beat, and the live workout light follows the zone.
5. **Week story.** Turn the Monday card into slides, or keep it a card that stays all week. Recommendation: a card
   that stays all week, slides later.
6. **Learning your usual workout time.** "When they work out or feel like working out" needs a small rule from
   workout history (for example, the hour you started most workouts on that weekday over the last 4 weeks). It
   computes nothing new about your body, so it fits the no-algorithm-changes rule. Recommendation: yes, with a
   fallback of no workout moment until there are 3 workouts.

After these, step 3 critiques each screen against this board, then step 4 is mockups.
