# Research 4: live modes, states and motion

Problem area: workouts in progress, live heart rate, the moment after a workout, every non-happy state (first run,
pairing, disconnected, charging, syncing, sync failed, empty, partial, errors) and the motion and haptics that go with
them. 17 Mobbin searches, iOS and web. Links only, no images saved.

### 1. Workout mode takes over, and colour says the state
**Problem it solves in OpenStrap:** the workout screen has to read at arm's length, and the user must see at a glance whether it is recording, paused, or not ready yet.
**Seen in:** [Nike Run Club, running in orange](https://mobbin.com/screens/6b506d61-b2e1-4690-9cf8-23aa94a80a7e), [Nike Run Club, paused with play and stop](https://mobbin.com/screens/7c5bf619-191e-4fbd-aa0c-1e467241b8da), [Strava, start screen with "GPS signal acquired" bar](https://mobbin.com/flows/e6356a85-1710-4b88-b329-e6d3df02d2b6), [Apple Fitness, workout tab with no heart rate sensor icon](https://mobbin.com/flows/cb3fa283-efa2-4079-b747-f62d2c82ce05)
**How it works:** Nike swaps the whole background when you pause: saturated orange while running, plain with a big play button when paused. One giant number leads and three small ones sit below. Strava does not let you start blind: a green bar confirms the sensor is locked before the Start button. Apple Fitness shows a struck-through heart in the corner when there is no heart rate source.
**For OpenStrap:** before Start, one line confirms the strap: "Strap connected. 72 bpm." Running is the full glow in the activity colour, paused drops to a dim outline of the same layout, so the state is obvious without reading. Number first, no prose.
**Avoid:** a paused state that only changes a small icon, so the user thinks it is still recording.

### 2. The live number sits on a zone arc, and only the heart beats
**Problem it solves in OpenStrap:** live heart rate (in a workout and on the heart rate screen) should show where you are, not only how fast.
**Seen in:** [Oura, live workout with zone arc and avg and max](https://mobbin.com/screens/fc0c9260-f9a1-494b-8034-a477e697fc8e), [Ultrahuman, live walk with "Zone 2, 106–118 bpm" on the arc](https://mobbin.com/screens/5fad3d2c-5723-4f47-98e1-b72abc2fdf20), [Withings, heart rate reading with pulse wave](https://mobbin.com/screens/c0b23842-412d-421d-ba1c-be00e35bdc6d)
**How it works:** a large bpm sits inside a segmented ring of zones. A dot or a lit segment marks the current zone, with the zone name and its bpm range on the arc itself. Average and max sit above and below in smaller, dimmer type. Ultrahuman adds one short status pill ("Using significant energy") under the timer.
**For OpenStrap:** the five zone colours become glowing arc segments, only the current one lit. The heart glyph pulses at the real beat (the brief's one allowed loop). Crossing into a new zone gives a single haptic tick and the lit segment slides over.
**Avoid:** animating the arc or the digits continuously, which turns signal into noise and fights the pulse.

### 3. Ending is a hold, then the calm-down leads
**Problem it solves in OpenStrap:** ending a workout by accident is costly, and the screen right after should show recovery, not a wall of stats.
**Seen in:** [Apple Fitness, controls sheet with End Workout and Lock Controls, then summary](https://mobbin.com/flows/5c95a0fb-e7a1-4134-8235-086a37556fca), [Me+, "Hold to quit" ring filling](https://mobbin.com/screens/38470ab6-45a6-4d0c-a5cd-d9f41cee5c5e), [Bevel, heart rate recovery "-69 bpm" with drop curve and scale](https://mobbin.com/screens/9dca9847-d793-4f27-af96-5f4f200e17f9), [Oura, workout summary with zone-coloured line and "Most time in Zone 5"](https://mobbin.com/screens/59337e64-fc72-4494-973e-51de337955f2)
**How it works:** Apple tucks End behind a pull-up sheet so the main screen stays clean. Hold buttons show a ring that fills, so the time is visible and an accidental tap does nothing. Bevel leads its post-workout block with one big drop number, the curve of the fall, and a plain Poor to Superior scale. Oura colours the heart rate line by zone and states the headline in one phrase.
**For OpenStrap:** End is a hold with a filling ring and a firm haptic at start and finish. The first screen after is "Calm down": a live 2 minute heart rate drop that draws as it happens, then the effort and Load below. Dim the glow (the brief's "toned down").
**Avoid:** guilt copy on the end action, like Opal's "Don't give up, there's a reason you started this" ([Opal](https://mobbin.com/screens/e187ab82-450c-4625-8592-e6df33f395a3)).

### 4. Live status lives outside the app too
**Problem it solves in OpenStrap:** during a workout (and a long sync) the phone is mostly locked or in another app.
**Seen in:** [Nike Run Club, lock screen live activity](https://mobbin.com/screens/60bc028c-c377-4962-b23a-4da37c9ee2a7), [Flighty, lock screen with progress curve and "1 hr, 18 min until gate arrival"](https://mobbin.com/screens/068a8ac5-0520-4d38-a542-32d3b4d96de5), [Flighty, compact pill with time left](https://mobbin.com/screens/a878818a-1f9e-4da1-93d6-5463fe9b0fcf), [Zomato, "Arriving in 24 mins" with three step dots](https://mobbin.com/screens/fe76846f-66e6-4ab7-a673-8936d0ff9885)
**How it works:** a black card with three numbers at most, one in the brand accent. Flighty puts a thin progress line with a glowing head across the card and one sentence of time left. The compact form is one icon and one number.
**For OpenStrap:** an Android ongoing notification on black: time, bpm in the zone colour, and a thin glowing zone bar. Same card for sync, with the step dots from pattern 6. Tapping it opens the workout (the existing deep link).
**Avoid:** cramming six metrics and controls into the notification so it reads worse than the app.

### 5. Sync shows where it is, with a timestamp
**Problem it solves in OpenStrap:** syncing and sync failed states, and the morning when last night's data is still coming off the strap.
**Seen in:** [WHOOP, "Data catching up" bar with "Synced to 01/01, 7:30 AM" and a progress ring](https://mobbin.com/screens/581ae395-67dd-477c-ab95-0fe26fb3c9f4), [Oura, "Updating…" in the title and "Waiting for your sleep data"](https://mobbin.com/screens/12be55fc-6496-4e57-9259-56b2456517ad), [Bolt Food, timestamped steps](https://mobbin.com/screens/9509b853-f44c-425e-81c0-c6e96efffe2c), [sweetgreen, four labelled step dots](https://mobbin.com/screens/76e2f253-96bc-4199-9439-8c74a519e0ec)
**How it works:** WHOOP keeps the dashboard visible and adds one slim bar: what is happening on the left, how far it has got (a date and time) on the right, with a small ring. Oura replaces the header title with "Updating…" so nothing on the page jumps. Delivery apps break a wait into named steps and tick each one off with its time.
**For OpenStrap:** one slim bar under the date: "Catching up. Synced to 03:10" with a ring, steps Connect, Read, Save. Cards keep their last values and animate to the new number when the step lands. Failure turns the same bar amber: "Sync stopped at 03:10. Try again", not a dialog.
**Avoid:** a blocking full-screen spinner, or values that reset to zero while syncing.

### 6. Strap status sits with the strap, with "last seen"
**Problem it solves in OpenStrap:** disconnected, charging and battery states, today shown by a small watch icon in the top bar.
**Seen in:** [ChatGPT Codex, "Offline, last seen 18 minutes ago" and Reconnect](https://mobbin.com/screens/635cf9e7-fad6-4e72-b13d-20c138e4af00), [Tesla, "61%" and "2h 15m remaining to charge limit" under the car name](https://mobbin.com/screens/c0a37ffb-23c3-44d7-a3e8-fd50735582e0), [Starlink, page title "Disconnected" with two fixes](https://mobbin.com/screens/a79e31e4-17b1-4ae9-a9c7-1bc3239b3f83), [Eight Sleep, "My Pod is offline" with numbered steps](https://mobbin.com/screens/bb976239-e92e-4ab6-bc78-d1e5a7e57607)
**How it works:** the device name is the header and the state is one line under it, with a relative time. Tesla gives charging a percent, a bolt and time to done, plus a glowing cable line. Starlink makes the state the page title and offers exactly two actions. Eight Sleep turns recovery into a short numbered list.
**For OpenStrap:** the strap icon becomes a chip with state and time: "Connected", "Last seen 18 min ago", "Charging, 64%, full in 40 min". Tapping opens a strap sheet with one fix per state. On Today, data older than a few hours gets the brief's single caveat marker, not a banner.
**Avoid:** a red full-width "Device disconnected" banner that shouts on every open even when the data is fine.

### 7. Pairing is one stage, the object stays still, the line changes
**Problem it solves in OpenStrap:** the first-run pairing flow and re-pairing after a reset.
**Seen in:** [WHOOP, "Check for pairing mode" with the LED circled, then a dotted line phone to strap](https://mobbin.com/flows/381a9375-b5c7-45fa-b2de-bf0872f17b6f), [Sonos, one sheet where only the status line and glyph change](https://mobbin.com/flows/8887a914-022d-483e-a280-e306103609a5), [Oura, "Select your ring" with "My Oura Ring isn't shown"](https://mobbin.com/flows/6d9deee0-6c57-47ca-8c7a-4348dd396f1e)
**How it works:** Sonos keeps the product render fixed and swaps only the sentence and a small glyph above it (dots, tick, wifi), so each step feels like progress on the same object. WHOOP shows the exact light to look for and a link for when it is not there. Oura always gives an escape for the device not appearing.
**For OpenStrap:** a single black stage with the Helio Strap render glowing in the accent. Steps: wake the strap, found it, bind (the signature hand-off), reading first data. A dotted line pulses between phone and strap while connecting and settles solid on success, with one firm haptic.
**Avoid:** a new full screen for every step, which resets the user's sense of progress and hides how many steps are left.

### 8. Empty and early states keep the real layout
**Problem it solves in OpenStrap:** first run with no data, no sleep last night, partial day, and the first weeks before baselines exist.
**Seen in:** [Oura, "No Readiness Score yet" with the contributor list still shown](https://mobbin.com/screens/f2b123f2-bf28-4fc4-83b7-b4d4a7f91ab1), [Oura, metric cards reading "Wear your ring to sleep"](https://mobbin.com/screens/9050cad7-1f11-49dd-9618-b0d2a00b57d1), [Oura, "Calibrating" tags and "two to four weeks"](https://mobbin.com/screens/8209e734-2c6c-45ed-951e-8f7600eed44b), [Linear, "No issues matching the filters" with "16 issues hidden by filters"](https://mobbin.com/screens/d3ab0127-662f-4764-9c84-511cd7345e27)
**How it works:** the card or page keeps its shape, header and contributors, and the number slot says what will fill it and when. Calibrating metrics get a small tag instead of a fake number. Linear tells you why the list is empty and how to undo it in one tap.
**For OpenStrap:** each glow card has a dim "waiting" form: same colour, low glow, a dash for the number and a time-aware line ("After your first night", "Learning your normal, day 3 of 7"). Partial day uses the single caveat marker. When the first real score arrives, mark it once, like [Oura's "Your first Readiness Score"](https://mobbin.com/screens/5a37255c-77fd-4e23-884b-5d9af68a0a38), then never again.
**Avoid:** removing the card when there is no data, which makes the layout jump day to day and hides what the app will do.

### 9. Errors appear where they happened, with a reason and one action
**Problem it solves in OpenStrap:** sync failed, a write to the strap failing, a bad input in settings.
**Seen in:** [Revolut, amount field turns red with "exceeds balance"](https://mobbin.com/screens/ca670269-cf7a-4a17-9810-daa3b948a948), [Revolut, "No ATM found in this area" and Retry inside the sheet](https://mobbin.com/screens/2a8c9c56-b4e5-4bd5-8e22-10d2f94f7892), [Monzo, explains what the warning does and does not mean](https://mobbin.com/screens/c73a3c93-eb1e-4c54-80c9-27e68531e9d0)
**How it works:** Revolut tints the failing field and puts a three-word reason under it, no modal. When a section has nothing, it says so in place with one text button. Monzo spells out what the error does not mean, so the user does not assume the worst.
**For OpenStrap:** a failed card keeps its last good value, dims, and shows one line and one action: "Couldn't read sleep from the strap. Try again." Reassure in plain words when data is safe ("Your data is still on the strap").
**Avoid:** a generic modal with a code and "Something went wrong" that blocks the screen behind it.

### 10. Motion points back to the source, and milestones stay quiet
**Problem it solves in OpenStrap:** card to detail transitions, values updating, and the rare moment worth marking (a personal best, first score, a long streak).
**Seen in:** [Gentler Streak, tapping a summary tile tints it and redraws the chart in its colour](https://mobbin.com/flows/cdfe56f0-afc7-4850-93d6-a0b9240cf80b), [Apple Health, steps card opening into its detail](https://mobbin.com/flows/dfd9da7c-b0a2-4deb-871c-e8c7df224db8), [Insight Timer, "Next milestone in 6 days"](https://mobbin.com/screens/3a74a615-658c-463c-b117-afac5989f66e), [How We Feel, streak rings on black](https://mobbin.com/screens/824dac5c-f611-4999-888c-8c3163475a2f)
**How it works:** Gentler Streak's selected tile takes on the chart's colour, so you always know which number the chart is showing. Apple Health's card header and value carry straight into the detail header. The better milestone screens are one number, one plain line and a forward-looking fact, on the app's normal background.
**For OpenStrap:** the card's glow expands into the detail background and the number stays put as the header (fast in, slow settle, no bounce). A personal best is a brief glow swell on the card plus one tick, with the line "Fastest 5 km since March". No confetti, no full-screen takeover.
**Avoid:** the full-screen mascot and "+20 gems" style ([Duolingo](https://mobbin.com/screens/84ec5a7b-37ce-4c38-808d-7c3c91c3c0aa)), which breaks the calm voice.

## Anti-patterns seen

- Cartoon full-screen offline pages that hide the data you already had: [GoPay](https://mobbin.com/screens/95483b32-a4d7-4025-b6e7-64220245eff5), [CHOPT, "You are offline"](https://mobbin.com/screens/ebc40138-872c-4dab-b77c-1abed601374e).
- Celebration modals stacked over the main number, with exclamation marks and a nag to come back: [Sweatcoin, "1 day streak!"](https://mobbin.com/screens/d50b4868-0442-4588-932d-1141e4218133), [Duolingo](https://mobbin.com/screens/84ec5a7b-37ce-4c38-808d-7c3c91c3c0aa).
- Guilt copy on a stop action: [Opal, "Leave Early?"](https://mobbin.com/screens/e187ab82-450c-4625-8592-e6df33f395a3).
- A loud red persistent "hasn't connected in a while" banner across the bottom of the home screen: [LARQ](https://mobbin.com/screens/6b859147-a151-4dd4-b92e-9e8e5fd0ae81).
- Empty dashboard filled with promo cards ("Get started", "Learn how to charge") above the actual scores, so the first-run home reads as an ad: [WHOOP](https://mobbin.com/screens/581ae395-67dd-477c-ab95-0fe26fb3c9f4).
- A placeholder that looks like a broken value, like "--'--"/km" in a finished summary: [Apple Fitness](https://mobbin.com/flows/5c95a0fb-e7a1-4134-8235-086a37556fca).
- A long marketing paragraph on a first-score moment ("Looks like you're ready for some sharp thinking today!"): [Oura, home while updating](https://mobbin.com/flows/6d9deee0-6c57-47ca-8c7a-4348dd396f1e).
