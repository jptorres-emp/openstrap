# Research 3: metric cards, detail pages and explaining numbers in layers

Thirteen Mobbin searches across weather, finance, trading, health, fitness and music. Links only, no images saved.

### Verdict headline above the chart
**Problem it solves in OpenStrap:** The Recovery and Charge detail pages open on a number and a chart, so the morning question "how am I?" needs reading and decoding.
**Seen in:** [Apple Weather, UV Index detail](https://mobbin.com/screens/932444de-875b-45e6-b56c-c1e2df20610f), [Apple Health, Steps with highlight](https://mobbin.com/screens/92cf868c-3d1c-434b-aba1-ea52a196e03a), [WHOOP, strength trend view](https://mobbin.com/screens/70b15e71-84ab-420f-a547-c9681310ea5b), [Oura, Resilience](https://mobbin.com/screens/801a877d-fa06-447e-889b-3ffd29779c1a)
**How it works:** The value and a status word sit together on one line ("5 Moderate"), then the chart, then one plain sentence saying what it means right now ("Sun protection recommended. Levels of Moderate or higher are reached from 10AM to 5PM."). Apple Health puts the sentence in a "Highlights" card under the chart ("Your steps are on track for this point in the day."). WHOOP writes the comparison as a sentence with both numbers in it.
**For OpenStrap:** Every detail page opens with value plus status word in the card colour ("75 Ready"), then the chart, then one sentence that is time-aware ("Recovered enough for your usual training. Sleep balance is holding you back."). The status word is the thing that glows, not the digits.
**Avoid:** Don't stack two sentences of advice above the fold. WHOOP's "Keep Sleep Debt under control to keep Sleep Need down" slips into "you should".

### Your normal as a shaded band
**Problem it solves in OpenStrap:** HRV, resting heart rate and Load have no visible "normal", so a user can't tell if 48 ms is good.
**Seen in:** [Strava, Relative Effort with suggested range](https://mobbin.com/screens/fff0b506-9862-44e7-80a9-e1e447d404d9), [Oura, session HRV vs nighttime baseline](https://mobbin.com/screens/e56ac286-cc80-477d-bed3-effd19f3f7ad), [WHOOP, steps with AVG. line](https://mobbin.com/screens/010184b9-da45-49df-a98f-e32722b7d3a6), [Copilot, monthly spending vs budget line](https://mobbin.com/screens/6ed04b22-e36a-482e-aa77-5df208859186)
**How it works:** Strava draws a dashed, tinted band labelled "Suggested Range (From 3-Week Average)" and plots this week and last week as two lines over it. Oura uses a single labelled dashed "Baseline" line and states it in words ("Your nighttime baseline is 50 ms"). Copilot draws a threshold line with a value tag on the right edge and colours each bar by which side of it it falls.
**For OpenStrap:** A soft glow band in the metric colour at low opacity behind the line, labelled "Your normal" on the right edge, with the range in the info sheet. Points outside the band get the full glow, points inside stay dim, so the eye goes straight to the unusual days.
**Avoid:** Don't show a band before there are enough nights to compute it. Show "Learning your normal, 4 of 14 nights" instead of a fake band.

### Status words relative to you, not to a scale
**Problem it solves in OpenStrap:** Labels like "Good energy" or "Ready" are absolute, and the brief bans alarming lowest ratings.
**Seen in:** [Oura, Readiness contributors](https://mobbin.com/screens/aba0560d-220a-4b2c-9700-d603272c67f7), [Oura, Symptom Radar biometrics "Elevated"](https://mobbin.com/screens/4e3a4957-3dbd-4cba-ba15-41bac9594a9f), [Apple Weather, daily comparison](https://mobbin.com/screens/f615bca2-cf3f-42a9-b491-e397492ed00f), [Noom, vascular elasticity scale](https://mobbin.com/screens/c2f6a0f1-162b-4534-8aa1-ed7ad9f60a76)
**How it works:** Oura's contributor rows are a name, a thin progress bar, and one right-aligned word ("Optimal", "Fair", "Pay attention") that is coloured only when it needs attention. The Symptom Radar uses a neutral pill ("Elevated") next to the raw number. Apple Weather compares today with yesterday in a sentence plus two range bars, one per day.
**For OpenStrap:** Use a small fixed vocabulary for every metric: "Above your normal", "Usual", "Below your normal", with the word in a pill and the glow only on the off-normal states. Contributors on the Recovery page follow Oura's row shape with our words.
**Avoid:** Oura's "Pay attention" in orange on a row that just means "a bit low". It is exactly the alarming tone the brief rules out.

### Range switch that rewrites the headline
**Problem it solves in OpenStrap:** The current cards show a fixed 7-day strip, and switching to week, month or year has no defined home.
**Seen in:** [Apple Health, Steps D W M 6M Y](https://mobbin.com/screens/c6701e04-13cd-40ee-825f-e98a516ff86d), [WHOOP, HRV 6M trend](https://mobbin.com/screens/ac9866dd-4010-4028-b0c3-bf92ebcbb40a), [Copilot, cash flow with floating 4W 3M 1Y MTD YTD](https://mobbin.com/screens/60052b55-ee17-47bc-b982-5016542b250c), [Gentler Streak, This Week vs last week](https://mobbin.com/screens/14694404-10b6-403b-a01e-6b0afbbd4346)
**How it works:** A segmented control sits directly above the chart. Changing it changes the headline label too ("AVERAGE 1,259 steps, Aug 5 to 11" becomes the monthly average). WHOOP rewrites the sentence for the period ("During this 6-month period, your average HRV has been 68."). Copilot floats the range pill at the bottom of the screen so the thumb reaches it.
**For OpenStrap:** Day, Week, Month, Year as a pill row under the headline. The headline switches from "today's value" to "average for this week" and the sentence is rewritten per range. The value animates to the new number, consistent with "motion is signal".
**Avoid:** Don't keep the headline at today's value when the chart shows a year. The number and the chart must always describe the same span.

### Scrub the chart, the headline follows
**Problem it solves in OpenStrap:** The Recovery dots and Charge curve on the cards are static. There is no way to read a past day without leaving the chart.
**Seen in:** [Apple Health, scrubbed bar replaces headline](https://mobbin.com/screens/51dd8941-0326-4b0d-8f48-b5fbbcdd3539), [WHOOP Age, trend with dashed scrub line](https://mobbin.com/screens/de0259f7-43f3-4fbb-83d4-82fb63202b69), [Finimize, stock chart with scrub dot](https://mobbin.com/screens/984065ba-bbe8-417c-a706-11c888996e17), [Oura, Resilience chart with level tag](https://mobbin.com/screens/801a877d-fa06-447e-889b-3ffd29779c1a)
**How it works:** Press and drag puts a thin vertical rule on the chart. Apple Health turns the header block into "TOTAL 1,535 steps Aug 6" while dragging, and a hairline connects that label down to the bar. Oura floats the level word ("Strong") on top of the rule. Release snaps back to today.
**For OpenStrap:** The big dot-matrix number becomes the scrubbed value, the date replaces "Today", and the status word updates. Add the brief's haptic tick only when the scrub crosses in or out of the "your normal" band.
**Avoid:** Don't use a floating tooltip box over the chart (Perplexity style) on a phone. The finger hides it. Reuse the headline instead.

### Info sheet in three layers
**Problem it solves in OpenStrap:** HRV, Load, Charge and Habit Age need a "how it's worked out" one tap away, without walls of text.
**Seen in:** [Oura, Resilience info sheet](https://mobbin.com/screens/b67baac0-cc03-4ae0-bdf4-3c8cc7e81347), [Oura, Resilience levels list](https://mobbin.com/screens/639c5f10-12ed-4826-a254-8573095d2208), [MyDyson, PM10 explained with scale](https://mobbin.com/screens/829efb48-202a-4e55-a9fb-198d00ff9ad8), [Apple Weather, About Feels Like](https://mobbin.com/screens/f615bca2-cf3f-42a9-b491-e397492ed00f)
**How it works:** Oura's sheet opens with one bold sentence ("Resilience is your ability to withstand and recover..."), one supporting paragraph, then collapsed rows: "How is Resilience calculated?", "Resilience graphs", contributors, and the five levels each expandable. MyDyson pairs a one-line definition with a coloured scale so the bands are seen, not described. Apple Weather keeps "About Feels Like" to four lines at the bottom of the detail page.
**For OpenStrap:** An (i) on each detail page opens a dark bottom sheet. Line one is the bold meaning. Then the scale as coloured glow bars with our status words. Then collapsed rows: "How it's worked out", "What moves it", "Why it might be off". The formula lives only in the first collapsed row.
**Avoid:** Apple Health's "About Sleep Score" is five paragraphs of grey text on one page. Nobody reads it.

### One caveat marker, explained on tap
**Problem it solves in OpenStrap:** Partial days, short wear and early estimates need flagging without a disclaimer under every number.
**Seen in:** [Oura, HRV with dashed gap line](https://mobbin.com/screens/6aee12bc-6f70-4812-a83e-455d5af2d77a), [Opendoor, estimate with widening band](https://mobbin.com/screens/af22bb55-e74b-49f6-b09c-72d333040966), [Turo, not enough data message](https://mobbin.com/screens/d7bda7b3-df1a-427a-a493-6d48b2df2a93), [Strava, can't build a graph yet](https://mobbin.com/screens/ea1f3f23-5630-48f4-a25a-95502571c509)
**How it works:** Oura joins points across a missing day with a dashed segment and leaves the day's dot hollow, so the gap is visible but quiet. Opendoor states "This isn't a cash offer" in one line, links "Learn about estimated value", and draws uncertainty as a band that widens with time. Turo and Strava replace the chart with one sentence that says why and what will fix it.
**For OpenStrap:** One small outlined pill per screen ("Partial day", "Early estimate") next to the headline. Tapping it opens a two-line sheet. In charts, low-confidence points are hollow and lines through gaps are dashed. Empty states say what's missing and when it will appear ("Wear the strap tonight to see HRV tomorrow morning").
**Avoid:** Fitbit's empty chart reads "--% in range" over a full grid, which looks broken rather than empty.

### Comparison phrased as pace, not total
**Problem it solves in OpenStrap:** Midday steps or Load look low against yesterday's full day, which makes partial days feel like failures.
**Seen in:** [Apple Health, on track for this point in the day](https://mobbin.com/screens/92cf868c-3d1c-434b-aba1-ea52a196e03a), [Strava, up from 0 at this point last month](https://mobbin.com/screens/8d71697d-b15c-4ebe-8f31-cc348fd9f115), [Copilot, spent vs last month with pace line](https://mobbin.com/screens/39e851c1-7c5d-4f1e-bf81-1c3be05e5566), [Gentler Streak, this week vs last week overlay](https://mobbin.com/screens/0d99c07a-0d34-4562-aefa-00086517c851)
**How it works:** The comparison is to the same moment in the past period, not the whole period. Copilot draws last month as a thin dashed line under this month and tags the gap ("$848 over"). Apple Health shows "Today" and "Average" side by side, both measured to now.
**For OpenStrap:** Steps, Load and Charge drain compare "so far today" to "your usual by 14:00", drawn as a faint ghost line of your typical day under today's glowing line. The sentence reads "About usual for midday."
**Avoid:** Don't use bare percentages like WHOOP's "44% vs. prior month" or Copilot's "999%". They are noisy and say nothing about whether it matters.

### Story recap for week and month
**Problem it solves in OpenStrap:** The You tab and weekly summaries have no moment where a period is told as a story.
**Seen in:** [Spotify Wrapped, minutes listened](https://mobbin.com/screens/9cc6cdc9-6c8f-49aa-99f7-1531de8b5017), [Spotify Wrapped, listening age](https://mobbin.com/screens/1d44c2fa-99ed-4f63-acfa-0fbcf889d558), [Oura, weekly report HRV slide](https://mobbin.com/screens/3cba06a0-6e7b-4691-acd0-3f6a735783d8)
**How it works:** One slide, one huge number, one sentence that translates it ("You listened for 12,775 minutes. That's 8 days."). Oura's weekly report does the same with a calm line chart and one sentence of meaning under it, paged with dots.
**For OpenStrap:** A Monday "Your week" sequence: four to five black slides, each one dot-matrix number in a single glow colour and one translating sentence ("Habit Age 34. Two years younger than in September."). Skip the cheering.
**Avoid:** Wrapped's jokey sign-offs ("Nice.") break the calm, sharp friend voice.

## Anti-patterns seen

- [Apple Health, "1 day 14 hr" average sleep](https://mobbin.com/screens/6af952f2-8cea-4a40-a3ef-beaac81bf421): a bad value rendered at full size with no sanity check. Clamp or flag implausible numbers.
- [Oura, "Pay attention" on mild contributors](https://mobbin.com/screens/aba0560d-220a-4b2c-9700-d603272c67f7): alarm colour for ordinary dips.
- [Apple Health, About Sleep Score](https://mobbin.com/screens/38b2d05a-63fc-4983-b179-e8ec1eec2890): explanation as a wall of text.
- [Public, chart with MACD, RSI and Bollinger bands](https://mobbin.com/screens/09260a5d-3662-443a-934b-f658b421aeaf): every overlay at once, no hierarchy.
- [Fitbit, empty glucose chart with "--%"](https://mobbin.com/screens/a300c0c9-fdc4-4cf7-a38d-f0a99cd38eb2): empty state that looks like an error.
- [WHOOP, steps with four menstrual phase colours over bars](https://mobbin.com/screens/010184b9-da45-49df-a98f-e32722b7d3a6): an overlay legend that competes with the actual metric.
- [Copilot, "999%" change pill](https://mobbin.com/screens/60052b55-ee17-47bc-b982-5016542b250c): a percentage on a tiny base is meaningless.
- [Oura, Readiness chart with three bar colours and a label for each](https://mobbin.com/screens/6a2156ea-5470-4ab1-993d-591d6d0966eb): mixing two metrics in one header chart makes neither readable.
