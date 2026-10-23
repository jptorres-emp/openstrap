# Research 2: navigation and finding things

Problem area: tab bar, deep hierarchies, summary to detail and back, moving between days, history lists, and where
settings and the strap live. Current app for reference: five labelled tabs (Today, Recovery, Sleep, Activity, You),
a TODAY pager with chevrons under the logo, a watch icon top right, and a You tab that mixes profile, goals, pairing
and data export in one long list.

### Detail opens as a sheet with a day strip and a metric switcher
**Problem it solves in OpenStrap:** tapping Charge or Sleep on the Today card pushes a full page, and to see another metric or yesterday you have to back out and start again.
**Seen in:** [Apple Weather, Conditions detail sheet](https://mobbin.com/flows/53773f08-043d-43ab-a8f3-2d04e5b6a847), [Apple Weather, switching metric inside the detail](https://mobbin.com/flows/cde5e30c-cde3-4e39-aa0e-86c0fac3002d), [Apple Weather, UV detail with week strip](https://mobbin.com/flows/58cb1ba6-8594-4d8d-b45e-7c6aebda6f85)
**How it works:** A tile on the home screen opens a tall sheet over it, so the home screen stays visible behind and a close button or swipe down returns you exactly where you were. The top of the sheet has a week strip to change day and a small dropdown to jump to a sibling metric (Conditions, UV, Wind) without closing. Below that the depth follows the brief: the number, the chart, then a plain paragraph and "About" text further down.
**For OpenStrap:** Every card on Today opens its detail as a black sheet that grows out of the card (the card's glow colour becomes the sheet's top edge), with a day strip and a metric switcher for Recovery, Charge, Sleep, Heart rate, Stress. Closing shrinks it back into the card. This is the "motion is signal" rule in practice.
**Avoid:** Stacking sheet on sheet. A second level (one workout inside Activity) should push inside the sheet, not open another sheet.

### Day pager that shows its neighbours
**Problem it solves in OpenStrap:** the TODAY label with two chevrons does not tell you that other days exist or which days have data, and tapping small chevrons is slow.
**Seen in:** [Oura, Readiness with yesterday peeking](https://mobbin.com/screens/aba0560d-220a-4b2c-9700-d603272c67f7), [WHOOP, Today stepper in the header](https://mobbin.com/screens/a11c3a93-6d32-491d-b86b-fb69ba758b4a), [Apple Fitness, week strip with dots on active days](https://mobbin.com/screens/eac5518f-afb2-4ddc-9fdf-9680fb1bc1d4), [Gentler Streak, chart bars as the day picker](https://mobbin.com/screens/b05a2fea-86b7-4004-ac8e-b906fb8b472e)
**How it works:** Oura shows "sterday" cut off at the left edge of the header, so the swipe is obvious without a hint. A small calendar icon next to "Today" opens a month view for longer jumps. Apple Fitness puts a dot under days that have data, and Gentler Streak lets you tap a bar in the chart to select that day.
**For OpenStrap:** Replace the chevrons with a swipeable date header where the previous day peeks in dimmed, plus a calendar icon. In detail sheets, use a seven day strip with a small coloured dot per day that has data (the metric's glow colour). Swiping the content left or right changes day, and the date header slides with it.
**Avoid:** Letting a horizontal day swipe fight with horizontal charts. Charts scrub on long press, day changes on swipe in the header only.

### Browse all, with pinned favourites on top
**Problem it solves in OpenStrap:** metrics like SpO2, HRV, resting heart rate, VO2 max and Sleep regularity have no single home, so you only find them if the right card happens to link there.
**Seen in:** [Apple Health, category index](https://mobbin.com/screens/cd8919aa-c470-48b8-8716-54343395eab2), [Apple Health, Edit List with Pinned section](https://mobbin.com/screens/8f5aa330-5bf0-44b3-9b91-8c76c80476ad), [Oura, Vitals grid by domain](https://mobbin.com/screens/cac516c9-f63d-4b42-b66d-342c942a5b0e), [Oura, Core metrics tiles](https://mobbin.com/screens/948c33d6-f91f-4b3f-92e4-0d4d449905d6)
**How it works:** Apple Health has one plain index of every category, and a Pinned list you reorder by dragging, which then leads the summary. Oura's Vitals tab is a two column grid of every metric, grouped under small headers (Readiness, Sleep, Core metrics, Activity, Stress). Each tile shows the latest value, its time context ("Last sleep") and a chevron. Metrics without enough data still appear with "About 5 days of data needed".
**For OpenStrap:** Add an "All metrics" index (reachable from search and from the bottom of Today), grouped Recovery, Sleep, Heart, Activity, Body. Each tile is a small dark card with the metric's glow colour as a thin accent and the latest value. Let people pin up to four, and pinned ones get a slot on Today.
**Avoid:** Turning the index into a second dashboard with big charts. It is a map, keep tiles small and uniform.

### Search that starts with recents and finds settings too
**Problem it solves in OpenStrap:** there is no way to type "alarm", "firmware" or "export" and land on the right screen, so people dig through You.
**Seen in:** [Things, Quick Find with recent lists before typing](https://mobbin.com/screens/11cbf594-7611-4e8a-9cc4-4026e19cfab7), [Linear, Search with Recently viewed](https://mobbin.com/screens/e9b4ccc3-e76f-4594-acc4-1a43828e8e39), [Apple Health, results shown as live data cards](https://mobbin.com/screens/e04fab4c-62e8-4bed-96f6-2c249c411e83), [bunq, "Search for a Setting"](https://mobbin.com/screens/b75e5826-dd63-45b3-ad6d-ee654adc4d6c)
**How it works:** Before you type, Things and Linear show what you opened recently, so search doubles as "go back to that thing". Apple Health returns results as mini cards with the current value, not bare links. bunq puts a settings search at the top of its settings sheet. Linear's search field sits at the bottom, by the thumb.
**For OpenStrap:** One search, opened from a round button next to the tab bar. Empty state shows recently opened screens. Results mix metrics (as mini cards with today's value), past workouts, and settings rows ("Wake alarm", "Firmware", "Export data"), each going straight to the place.
**Avoid:** Search that only matches exact titles. Map synonyms, so "battery", "update" and "backup" find Strap, Firmware and Export.

### The strap as a status chip that opens a device page
**Problem it solves in OpenStrap:** the watch icon on Today says nothing about the strap, and pairing, battery, firmware and alarms are buried in the You tab.
**Seen in:** [WHOOP, battery chip with strap icon in the header](https://mobbin.com/screens/c5067dc0-aae8-4bb3-a55b-5f26b78a7374), [WHOOP, Device Settings with live status](https://mobbin.com/screens/e89a256e-5d8b-4a62-bed9-60ebe1d6e68e), [Oura, My Devices with battery ring and last sync](https://mobbin.com/screens/c311f23b-7d47-4119-910d-f9ad6ecf03b5), [Tesla, product hero with charge and quick controls](https://mobbin.com/screens/c0a37ffb-23c3-44d7-a3e8-fd50735582e0)
**How it works:** WHOOP keeps "87%" and a strap glyph in the top right of Home, with a green dot for connected. Tapping it opens a device page led by a big product image, the connection state, last sync time and battery, then the few settings that matter. Tesla does the same for a car: name, charge, one line of state ("2h 15m remaining"), then quick action icons, then a list of sections.
**For OpenStrap:** Replace the watch icon with a chip: strap glyph, battery percent, and a dot for connected, syncing (animated only while syncing), or lost. Tapping it opens a "Strap" page with a glowing render of the Helio Strap, "Synced 2 min ago", battery, then rows for Alarms, Wear and sensors, Firmware, and Remove strap. Every strap state from the brief (charging, disconnected, sync failed) shows up on this chip first.
**Avoid:** Promo or upsell banners on the device page, as Oura does at the top of My Devices.

### Settings rows that tell you their current value
**Problem it solves in OpenStrap:** rows in You say what a screen is ("Export CSV") but not the state inside it, so you open each one to check.
**Seen in:** [Tesla, vehicle menu with live subtitles](https://mobbin.com/screens/09366a54-bd95-4774-8289-2377e6bffbf0), [Tesla, Security and Drivers with toggles and status](https://mobbin.com/screens/f8bf3894-4347-4f98-97ce-174ae1b45fc7), [Coinbase Wallet, rows listing what is inside](https://mobbin.com/screens/7b60c2bf-6b31-4d58-8711-a3483d7de071), [Fitbit, device rows with right aligned values](https://mobbin.com/screens/3385ad66-c7a1-402f-9f21-0e2554efa040)
**How it works:** Tesla's subtitles are live: "Charging will start at 11:00 PM on Monday", "Phone key connected". Coinbase lists the contents under each heading ("Passcode, backup, Face ID") so the row works as a mini index. Fitbit puts the value at the right edge ("Wrist: Non-Dominant", "Reminders to Move: On").
**For OpenStrap:** Every settings row gets a live second line: "Wake alarm: 06:40 weekdays", "Firmware: up to date", "Health Connect: last copy this morning", "Last export 3 Oct". Keep the dark rounded group cards the You tab already has.
**Avoid:** Long explanations as subtitles (Tesla's Parental Controls row runs to three lines). State, not description.

### Profile and settings behind the avatar, tabs for the day
**Problem it solves in OpenStrap:** the You tab spends a full tab slot on things people visit once a month (profile, goals, export) while daily things compete for the other four.
**Seen in:** [Monzo, avatar opens a sheet with profile and Settings](https://mobbin.com/screens/f783c73d-c6ab-4b2c-8335-8e6738917c55), [Revolut, profile hub sheet with grouped cards](https://mobbin.com/screens/8b0c7aa9-1dd5-45e5-92c4-35dc1ffb5559), [Revolut, Account sub page grouped by kind](https://mobbin.com/screens/bfe291d7-34a8-4d75-864e-3495d2452296)
**How it works:** Monzo and Revolut put a round avatar top left. It opens a modal sheet: who you are, then grouped cards (account, security, documents, help), each a short list. Close it and you are back on the tab you left. The tab bar is kept for daily jobs.
**For OpenStrap:** Consider an avatar in the top left of Today that opens "You" as a sheet (profile, goals, your data, about), and the strap chip in the top right. That frees a tab for something used daily, or lets the bar drop to four. If You stays a tab, at least split it into Profile, Strap and Your data cards with live subtitles.
**Avoid:** Hiding pairing behind the avatar. First run and "no strap paired" must still lead with a big pair action on Today.

### Compact pill tab bar with one detached action
**Problem it solves in OpenStrap:** the current five labelled tabs fill the bottom and overlap content (the Today card text runs under the bar), and there is no fast way to start a workout or search.
**Seen in:** [Linear, icon pill plus round search button](https://mobbin.com/screens/97e4122c-fb36-414e-8f11-da31e1a8aebc), [Apple TV, labelled pill plus round search](https://mobbin.com/screens/80567033-0a03-4450-b95a-a65f0fb51fdd), [Oura, three tab pill plus round action](https://mobbin.com/screens/cac516c9-f63d-4b42-b66d-342c942a5b0e), [Orbit, three tab pill on a dark glow background](https://mobbin.com/screens/ccee144c-65ce-4813-8876-2b4f67017a63)
**How it works:** A floating rounded pill holds three to four tabs, with the selected one in a lighter inner capsule. A separate circle beside it holds the one global action (search in Linear and Apple TV, the assistant in Oura). Oura cut down to three tabs (Today, Vitals, My Health), each labelled. Linear's last tab shows the current context and swaps on long press.
**For OpenStrap:** A dark pill with a faint edge glow, labelled tabs, and the active tab's capsule tinted with that section's colour (green for Recovery, blue for Sleep). A detached circle to its right becomes the moment action from the adaptive day: search most of the day, "Start workout" at the usual training time, live heart rate during a workout. Add bottom padding so the last card never sits under the bar.
**Avoid:** Icon-only tabs. Oura's older six icon bar (leaf, moon, flame, sprout, heart, waves) is unreadable without labels.

### Summary header that collapses but stays on screen
**Problem it solves in OpenStrap:** once you scroll Today past the Recovery card, you lose the main number and the date.
**Seen in:** [WHOOP, rings collapse to a small sticky row](https://mobbin.com/screens/c6049cfc-e244-4f46-88e7-f2f4d620e7bb), [WHOOP, full rings at the top](https://mobbin.com/screens/a11c3a93-6d32-491d-b86b-fb69ba758b4a)
**How it works:** WHOOP's three large rings shrink into a slim sticky row of three small rings with labels when you scroll. Tapping one jumps to its detail. The date stepper stays above.
**For OpenStrap:** When the hero card scrolls off, pin a slim strip with Readiness, Charge and Sleep as small coloured dots with numbers, under the date header. Tapping one opens its detail sheet.
**Avoid:** Animating the collapse with bounce or scale. It should track the scroll exactly.

### History as a filtered list and a month calendar
**Problem it solves in OpenStrap:** past workouts, nights and readings are only reachable one day at a time.
**Seen in:** [Spotify, Your Library with filter chips and sort](https://mobbin.com/screens/c8300705-3b32-4dc2-af76-1e1097c553e3), [Spotify, sort sheet](https://mobbin.com/screens/e407265a-af25-4f72-86c3-24908d5cfecd), [Strava, month calendar with activity icons in day cells](https://mobbin.com/screens/02f8ff4a-0d9d-492b-8be8-08fb9e9fe486), [Monzo, month calendar with items on their day](https://mobbin.com/screens/acb0b2d4-d563-44f7-be73-dc0029ba63ba)
**How it works:** Spotify puts type filters as chips under the title and a one line sort control ("Recents") above the list, both changing the list in place. Strava and Monzo show a month grid where each day cell holds an icon for what happened, so a busy week is visible at a glance and any day is one tap away.
**For OpenStrap:** In Activity, a "History" view with chips (All, Runs, Walks, Strength, Auto detected) and a sort row. A toggle swaps list for month grid, where each day cell carries a small glowing dot in the workout's colour. Tapping a day opens that day's Today view.
**Avoid:** Endless filters. Three to five chips that match how people think about their training is enough.

## Anti-patterns seen

- WHOOP's "More" tab with a hamburger icon is a junk drawer that hides real features. [WHOOP Home](https://mobbin.com/screens/c5067dc0-aae8-4bb3-a55b-5f26b78a7374)
- WHOOP stacks dismissable promo cards above My Day, so the useful part of Home starts below the fold. [WHOOP Home with promos](https://mobbin.com/screens/10431550-32a3-40f6-b8e5-bdebed0e2ad6)
- Oura's older tab bar is six unlabelled icons, and you cannot tell Readiness from Cycle from Stress. [Oura Daytime Stress](https://mobbin.com/screens/c43bf8b9-bbb5-4da7-a5e3-7b0cde55f0b3)
- Apple Watch search returns a wall of near identical "Change settings" rows with no hint of where each leads. [Apple Watch search](https://mobbin.com/screens/d3b00045-adda-4503-a084-7ecbe3971334)
- Tesla lists Dashcam Viewer both in the main menu and inside Security and Drivers, so the same thing lives in two places. [Tesla Security and Drivers](https://mobbin.com/screens/f8bf3894-4347-4f98-97ce-174ae1b45fc7)
- Oura's My Devices opens with a shop banner above the ring you own. [Oura My Devices](https://mobbin.com/screens/c311f23b-7d47-4119-910d-f9ad6ecf03b5)
- Moonly's floating tab bar uses tiny low contrast labels over busy content, so the active tab is hard to read. [Moonly Topics](https://mobbin.com/screens/d856bd90-3fb0-4b6e-a84f-d3d0485e5a4d)
