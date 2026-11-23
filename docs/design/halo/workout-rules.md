# Workout screen rules (user approved 2026-11-15)

Applies to every Ready, Live, Summary and Share board. Boards are 390 wide. Keep the hero(), hsl(), rgba() helpers and the
`radius` prop (default 36, tiles r*0.75 = 27) from the existing boards.

## Tokens
- Sport colours: Distance #C6F55A, Indoor cardio #52ACFC, Strength #FF8410, HIIT #FF5A5F, Mind and body #B67AE8, Games #6E8FB8.
- Rest / recovery teal #7CF2C9. Paused grey #AEB6C2.
- Zone colours (heart rate things only): #52ACFC #6CA11E #EAC40C #FF8410 #BC1823.
- Light angle per session, same on Ready, Live and Summary: Run, Rowing, HIIT, Padel 45. Strength 225 (morning). Yoga 160 with k 0.8.
- Solid card: `linear-gradient(180deg, #1A1B20, #101115)`, radius r, padding 20.
- Plain panel: `#0C0D10`, radius rt, no light.
- Dark button: `#17181C`. Page `#000`.
- Gutter 16, gap between blocks 12, card inset 20.

## 1. Frame
Top bar: `display:grid; grid-template-columns: 96px minmax(0,1fr) 96px; height: 48px; align-items:center`.
- Left: a 44 px squircle button (radius 16, superellipse(1.6), #17181C).
  - Ready: X (Close). Live: chevron down (Minimise). Summary: chevron left (Back). Share: X.
- Middle, centred:
  - Ready: the sport pill (40 high, #17181C, 26 px tinted icon tile, name 15/600, caret).
  - Live: the sport name 15/600 in its sport colour with an 8 px dot before it, and under it the total time 13 px #9AA0AA tabular ("24:12").
  - Summary: the sport name 17/600 white, and under it the date 12 px #9AA0AA ("This evening, 17:42").
- Right, right-aligned, 12 px #9AA0AA, two lines:
  - Ready: "♥ 72 bpm" (the heart in #FF5A5F) / "Strap 71%".
  - Live: "Strap 71%" / "GPS ±5 m" (second line only for GPS sports).
  - Summary: a Trim pill (36 high, scissors icon, 13 px).

Bottom bar: absolute, left 16, right 16, bottom 24, with the 120 px fade to black behind it.
- Ready: one full-width 64 px white button "Start run" (play icon).
- Live: `grid-template-columns: 72px 1fr 72px`.
  - Left is the sport's action: a 60 px squircle (radius 22) #17181C with an icon and a 12 px label under it. Lap (run, rowing), Skip or +10 s (HIIT), Half time (padel), or empty (strength, yoga, paused).
  - Middle: Pause, an 88 px circle.
  - Right: Stop, a 60 px squircle #17181C holding an 18 px white rounded square inside a 44 px ring (track rgba(255,255,255,0.18), 3 px), with the label "Hold to stop".
- Summary: a 64 px squircle Share icon button (#17181C), then Done, a white pill filling the rest.
- Share: Save (dark) and Share (white), half each.

**One white thing per screen.** It is the next thing you're expected to tap. On Live, Pause is white unless the screen has its own next step (Set done, Start set 4, Resume, Save). Then that step is white, and Pause is #17181C with a white icon.

## 2. Body order, always
1. **Hero:** one lit card in the sport colour (teal for any rest, grey for paused), using hero(colour, angle). It holds the screen's one big number.
2. **The sport's own card:** solid card. On Ready it holds the choices. On Live it holds the picture of the session (splits, heart line, sets done, peaks, effort). On Summary there may be one or two (route then splits, sets then rest).
3. **Figures row:** plain, no card. Three figures (two for mind and body), value over label.
   - Value: Funnel Display Light 28, line 1.
   - Label: 12 px #9AA0AA.
   - Each column after the first has a 1 px #24262C left rule and 16 px padding-left.
   - On Ready the row is last time's figures, with a small "Last time, Fri" caption above it.
4. **Extras:**
   - Ready: switch rows in one plain panel.
   - Summary: chips, then the heart rate drop, then Load (plain panel), then Delete.
   - Live has none, unless it is the strength or HIIT next-step button.

Live never scrolls (it fits 844). A Live screen that has nothing left to put in a block leaves that block out.

## 3. One big number per screen (Doto 700, 64 to 80 px, unit after it in 15 px)

| | Ready | Live | Summary |
|---|---|---|---|
| Run | 5.0 km goal | 5:48 /km pace now | 5.20 km |
| Rowing | Zone 2 to 3 goal (Doto "2–3", unit "zones") | 142 bpm | 134 avg bpm |
| Strength | 5 sets (unit "sets, 90 s rest") | 0:42 set clock, or 1:12 rest left | 8,225 kg lifted |
| HIIT | Tabata 4:00 (Doto "4:00", unit "Tabata") | 14 seconds left | 8 rounds done |
| Yoga | 30 min | 18:30 | 30 min |
| Padel | 2 × 30 (unit "min halves") | 164 bpm | 64 min played |

Note for Rowing's Ready, since "–" isn't allowed: write "2 to 3" with "zones" after it.

Only this number uses Doto. Every other number is Funnel Display Light (figures 28, inline 15 to 20).

The hero holds: a title row (16/600, left) with one small note on the right, then the big number, then one 13 px line in a tint of the sport colour (what the strap will do, or what's next).

## 4. Colour
- The sport colour lights the hero on Ready, Live and Summary (and the share card).
- Zone colours are only for heart rate things: the heart strip or heart figure dot, zone bars, the route, peak columns, the zone arc.
- Teal #7CF2C9 is any rest: strength rest, HIIT rest.
- The heart rate drop card is solid. It is lit teal (level style) only when the drop beats your usual.
- Paused hero is grey #AEB6C2.

## 5. Shared pieces
- **Progress** (sets, rounds, reps): a row of solid segments, height 6, gap 4, radius 3. Done ones are in the hero's colour shade (white at 0.55 on a lit hero), the current one is white, and the rest are rgba(255,255,255,0.18). Never dots.
- **Figure labels**, only from this list: Time, Distance, Avg pace, Avg bpm, Peak bpm, Active kcal, Load, Sets, Rounds, kg lifted, Hard efforts, m climbed.
- **Switches:** on is a white track with a black knob, off is #2A2C33 with a grey knob. Never the sport colour (user, 2026-11-15).
- **Heart rate inline:** "♥ 156 bpm" with the heart in its zone colour, and "Zone 3" in 13 px #9AA0AA.
- No dotted charts anywhere on workout screens. No glass. Squircles (`corner-shape: superellipse(1.6)`). No ·, ; or em dash in visible text.

## 6. Summary order
Top bar, then:
1. hero
2. the sport's card(s)
3. figures row
4. chips
5. heart rate drop (solid, or teal when it beats usual). Mind and body has none.
6. Load: a plain panel with Load, "usual 80 to 120" and time to recover
7. Delete workout (dark pill, red text and icon)

Bottom: Share and Done.
