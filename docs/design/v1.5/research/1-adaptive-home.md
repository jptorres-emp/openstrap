# Research 1: the home screen that adapts to the moment

Scope: how strong apps decide what leads the screen right now, and how they reorder or retone the rest. 16 Mobbin
searches across travel, finance, weather, smart home, cars, productivity and health.

Starting point: the current Today screen leads with a Recovery dial (75, "Ready"), then a Sleep card with a hypnogram.
The one sentence that says what it means ("Recovered enough for your usual training. Sleep balance is holding you
back.") sits at the very bottom, under the tab bar. The meaning is the last thing you see.

---

### One hero that swaps by state
**Problem it solves in OpenStrap:** Today always leads with the Recovery dial, even at 21:00 or mid-run, when it is not the answer to the question the user has.
**Seen in:** [Oura, Today in Rest Mode](https://mobbin.com/screens/03958157-6ab8-4046-9a49-ebf639baa348), [Oura, Today at bedtime](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071), [Uber, pickup in 1 min](https://mobbin.com/screens/02e52b85-dd21-40b0-b75c-548b35ae2125), [Uber, dropoff at 7:44 PM](https://mobbin.com/screens/d5a6ea48-a20c-4603-8816-3d19bff9aad1)
**How it works:** The screen has one fixed hero slot and the app picks what fills it. Oura shows the Readiness gauge with "Feeling good?" in the morning, "Rest Mode" when that is on, and drops the gauge entirely for "Bedtime's approaching" at night. Uber keeps the same sheet but its headline changes from "Connecting you to a driver" to "Pickup in 3 min" to "Dropoff at 7:44 PM". The layout never moves, only the content of the top slot.
**For OpenStrap:** Keep a single hero card at the top of Today and map it to the adaptive day table: Last night plus Recovery on waking, Charge at midday, Readiness with a start button at usual workout time, live numbers during a workout, the effort after it, the day's total at night. The hero's glow colour becomes the moment's colour (teal morning, amber midday, red effort, deep blue night).
**Avoid:** Swapping the whole page layout per moment, so the user cannot find the Sleep card they saw an hour ago.

### Sentence above the number
**Problem it solves in OpenStrap:** The morning "how am I" answer is a dial and the word "Ready", and the actual meaning is buried at the bottom of the screen.
**Seen in:** [Gentler Streak, Day to Rest and Recover](https://mobbin.com/screens/f9bf6004-fa31-4dbe-818a-6acbd51f0cae), [Oura, Readiness 80 with Feeling good?](https://mobbin.com/screens/bfe532d9-62fe-4246-9775-9c8934195bcb), [Apple Weather, summary line above the hourly strip](https://mobbin.com/screens/facfb5a0-be47-4083-8b61-fbe53602f7d5), [Joi, Good evening, nothing left today](https://mobbin.com/screens/362e5a69-9886-49b8-be3e-a260377a8681)
**How it works:** A large plain-language headline states the verdict ("Day to Rest and Recover", "You have nothing left today"), with the number either beside it or below it in smaller type. Apple Weather puts one forecast sentence at the top of the hourly module ("Cloudy conditions will continue for the rest of the day") so the chart under it only confirms. Joi greys out the filler words and keeps the key word bright, so a glance reads only the important part.
**For OpenStrap:** Put the sentence at the top of the hero in large type ("Ready for your usual run. Sleep was short."), the score under it, and the "why" one tap away. Borrow Joi's trick in glow form: the key words full white, the rest dimmed, so a 2 second glance reads "Ready" and "short".
**Avoid:** Gentler Streak and Oura follow the headline with a paragraph of generic advice ("Exercise is a wonderful way...") that breaks the 3 second rule and the no-advice voice.

### Live state headline with a countdown
**Problem it solves in OpenStrap:** During and right after a workout the app has no "you are here" signal, and the auto-detect notification has nothing on Today to land on.
**Seen in:** [Flighty, My Flights with Taxiing and 1h to departure](https://mobbin.com/screens/939d5aae-4c27-4a32-b91c-767ee82a1845), [Flighty, Landing in 40m](https://mobbin.com/screens/d2a15492-9519-4965-826f-649edbfc3521), [Flighty, live activity until gate arrival](https://mobbin.com/screens/81ec2024-c231-4e53-8f23-60d3197b488c), [Lightyear, market is closed, opens in 9h 23m](https://mobbin.com/screens/a4f39152-01fd-4b78-986d-d381ef12fd57)
**How it works:** Each item carries a state word plus a relative time ("Taxiing", "Departs on time", "Landing in 40m", "Gate departure in 2h 58m"). The state is the biggest coloured text, the absolute times are secondary. Flighty colours the state text (green early, red late) and puts the one important exception in a tinted band ("Arrives at gate 1h 8m late"). Lightyear does the same for a quiet state: "The market is closed. It opens in 9h 23m."
**For OpenStrap:** Use state plus relative time as the hero headline in active moments: "Running, 32 min" (number first, no prose), then "Since your run, 40 min" with heart rate settling, then "Bedtime in 1 h 10". For the strap itself: "Syncing", "Strap charging, 80%", "Last synced 2 h ago".
**Avoid:** Using absolute timestamps as the headline. "Started 07:42" makes the user do maths that "32 min" does not.

### Ordered list of modules, reordered by relevance
**Problem it solves in OpenStrap:** Today's cards sit in a fixed order, so the Sleep card with a full hypnogram fills the screen at 18:00 when Charge or the workout matter more.
**Seen in:** [CARROT Weather, rain and UV tiles under the forecast](https://mobbin.com/screens/6865b73f-ba1c-49ff-9b11-7e1d86a7d8e5), [Copilot, dashboard with To Review, Upcoming, Net](https://mobbin.com/screens/39e851c1-7c5d-4f1e-bf81-1c3be05e5566), [Bevel, home with Stress and Energy below the daily overview](https://mobbin.com/screens/485cc150-d5ee-4d3c-b515-22a5524c10e1), [Apple Fitness, Summary cards](https://mobbin.com/screens/2745a5a7-c443-46cf-80bb-a3e7b72ff8f7)
**How it works:** Under the hero the page is a stack of self-contained modules with a title and one value. CARROT promotes condition tiles ("0.2 in of rain in next 12 hrs", "UV index of 8 right now") in warning colours when they matter. Copilot orders by "needs you now" (To Review), then "coming soon" (Upcoming), then "how it's going" (Net this month). Each module has a "View all" link to its full page, so the home stays short.
**For OpenStrap:** Give each card a relevance score per moment and sort, but keep a stable set: Recovery, Sleep, Charge, Activity, Heart. Past-the-moment cards collapse to one line ("Sleep 8:01, score 83") instead of disappearing. The full hypnogram moves to the Sleep tab.
**Avoid:** Reordering on every open. Change order only at moment boundaries, or the user loses muscle memory.

### Status chips row for the always-there facts
**Problem it solves in OpenStrap:** Strap state (connected, charging, syncing, battery) is a small watch icon in the corner, and there is no quick read of the secondary numbers.
**Seen in:** [Apple Home, Lights 1 On and Speakers chips](https://mobbin.com/screens/97cedc62-854b-4cb8-a043-bfc7aae9833d), [Bevel, Active until changed and weather chips](https://mobbin.com/screens/c33feab6-41f6-475a-b370-caa18c10e204), [WHOOP, low battery banner above the dials](https://mobbin.com/screens/4e7d8b71-ac46-472f-8e31-b619f5e533ae), [Tesla, battery and time to charge limit under the car name](https://mobbin.com/screens/c0a37ffb-23c3-44d7-a3e8-fd50735582e0)
**How it works:** A row of small pill chips under the title, each a category plus its live state ("Lights, 1 On"). Chips only appear when they have something to say. Tesla puts one line of state under the name ("61%, 2h 15m remaining to charge limit"). WHOOP escalates to a full width dismissible banner only when the battery is low.
**For OpenStrap:** One chip row under the date: strap state ("Synced 4 min ago", "Charging 62%", "Disconnected"), plus a mode chip if set. Escalate to a banner only for states that block data (strap off wrist for hours, sync failed).
**Avoid:** WHOOP stacks a battery banner, the dials, an insight card and an ad, so the top of the screen holds four competing messages.

### One next action, shaped by time
**Problem it solves in OpenStrap:** At usual workout time there is no "start workout" prompt on Today, and after a workout there is no clear next step.
**Seen in:** [Airbnb, Check in after 7:00 PM timeline step](https://mobbin.com/screens/1e9e8e06-9b27-4ac5-8ea4-b6da24875fdb), [Peloton, Live and Upcoming with Join class](https://mobbin.com/screens/0cd621cb-59a9-46b3-ad46-86d7a5886474), [Runna, Today with Record workout button](https://mobbin.com/screens/be9b0bcc-6c28-4abf-bde0-6b046df1207d), [Gentler Streak, Today's Recommendations chips](https://mobbin.com/screens/c991401a-cc03-4eb2-9143-e775e8b15865)
**How it works:** A single action tied to the time sits directly under the state ("Check in after 7:00 PM", "Join class, 27:05 elapsed", "Record workout"). Airbnb puts the steps on a small dated timeline so "next" is obvious. Gentler Streak offers two small chips (Rest, Active Recovery) rather than one forced choice.
**For OpenStrap:** In the usual-workout window, the hero gains one button, "Start workout", and a line like "Ready for effort. You usually train around now." Outside that window the button lives in a quieter spot. After a workout, the next action is passive ("Heart rate back to resting in about 20 min"), not a task.
**Avoid:** Peloton and Equinox make the "next" item a content upsell. A next action should be about the user's body, never a feature promo.

### Evening dims and goes quiet
**Problem it solves in OpenStrap:** The night version of Today is as bright and loud as the morning one.
**Seen in:** [Oura, Bedtime's approaching on a night sky](https://mobbin.com/screens/f861b807-74b7-4bcb-a194-853f10408071), [Oura, Bedtime detail with bedtime chart](https://mobbin.com/screens/8e5e0f94-7f9d-44a9-a0d3-39dc724a90e7), [Joi, Good evening on black](https://mobbin.com/screens/362e5a69-9886-49b8-be3e-a260377a8681), [CARROT, background turning to night colour](https://mobbin.com/screens/046e634e-141f-427d-89ad-9e0b448377fc)
**How it works:** The whole background and type shift darker and lower contrast at night, the scores shrink to a strip of small rings at the top, and a calm headline takes the hero. Detail moves behind "Learn more". CARROT recolours the hourly strip from blue to violet as it crosses into night, so time of day is visible without a label.
**For OpenStrap:** At night lower the glow intensity of every card, collapse the scores into a small row, and lead with "Today, 9,400 steps, Load 62. Bedtime in 1 h 10." The softest motion curve applies here.
**Avoid:** Oura's evening copy runs to a paragraph of wellness advice. Keep it to the day's facts and one time cue.

### Pending and empty states that say what will happen
**Problem it solves in OpenStrap:** First run, strap disconnected, no sleep recorded, and partial day all need a hero that is honest about missing data.
**Seen in:** [WHOOP, Sleep Pending with dashed dials](https://mobbin.com/screens/c8932f72-07aa-4495-9b48-ca4fead689bb), [WHOOP, Sleep Detected with End Sleep](https://mobbin.com/screens/240d5f29-4dcb-47a0-a972-52c2887f4ed5), [Uber, Confirming your ride skeleton](https://mobbin.com/screens/bb781701-0b2e-44e1-bc67-50736c041a8e), [Airbnb, Pending badge on a trip](https://mobbin.com/screens/79f36e2f-ac49-4169-b473-abb7b0dd13bd)
**How it works:** The layout stays the same but values become "--" in dashed rings, and the hero sentence explains what is happening and when it will resolve ("Your sleep is being analysed... We'll notify you"). WHOOP even offers an action when it detects a state change ("Sleep detected. End sleep."). Airbnb uses a small "Pending" badge rather than a different screen.
**For OpenStrap:** Same hero, same cards, values dashed, and the sentence carries the state: "Syncing last night. About a minute." or "No sleep recorded last night. Was the strap on?" One small marker for partial day, explained on tap.
**Avoid:** A blank grey skeleton with no words, as in Uber's confirming state. It gives no time cue and looks broken.

### Show that the screen changed without a jump
**Problem it solves in OpenStrap:** If cards reorder by moment, a user opening the app at 12:00 after seeing the 08:00 layout could feel lost.
**Seen in:** [Bevel, Sync Complete pill at the top](https://mobbin.com/screens/485cc150-d5ee-4d3c-b515-22a5524c10e1), [Monzo, notifications summary card on top of home](https://mobbin.com/screens/07263bcf-0be2-4cfc-a105-c0e047a96e90), [WHOOP, Elevated HRV insight with a count badge](https://mobbin.com/screens/4876d637-ff31-4e3e-a1b6-e489842fff06), [Flighty, Lock Screen live activity](https://mobbin.com/screens/9cb2c4db-85aa-49d6-a211-de4e89537e57)
**How it works:** A short-lived pill ("Sync Complete") confirms new data arrived, then fades. Monzo groups several changes into one "You have 4 notifications" card instead of moving the page. WHOOP numbers its insight cards (1 of 3) so the user knows there is more. Flighty mirrors the same state outside the app, so opening it is never a surprise.
**For OpenStrap:** On open, keep the last layout for a beat, show a small "Updated, 3 new" pill, then let changed values count to their new numbers (motion is signal). Name the moment in the header ("Midday", "Evening") so a reorder has a visible reason.
**Avoid:** Animating every card into a new position on launch. Movement should mark change, not decorate it.

---

## Anti-patterns seen

- **Headline that cheers or jokes.** CARROT's snarky lines ([example](https://mobbin.com/screens/33fd6f9c-2dda-4c3f-b595-3926c2f20cbf)) and Bevel's "Someone's been busy!" ([example](https://mobbin.com/screens/b3d64dfc-fba8-45cb-b3a8-68abc557ae4b)) take the sentence slot and answer nothing.
- **Three equal dials.** WHOOP and Bevel ([WHOOP](https://mobbin.com/screens/b28890cc-6d12-4acc-bc1f-c37510182815), [Bevel](https://mobbin.com/screens/2d3e8d5f-f3f1-4370-8b34-f914c8eb31ed)) give Sleep, Recovery and Strain the same weight, so nothing leads and the eye has to compare.
- **Truncated insight paragraphs.** Bevel's insight card ends in "..." mid sentence ([example](https://mobbin.com/screens/d99b3093-5eca-4cc4-aadd-36a6de950dd1)). If the sentence does not fit, it is too long.
- **Promo in the adaptive slot.** WHOOP's "What your doctor likely didn't test" sits on every state ([example](https://mobbin.com/screens/c8932f72-07aa-4495-9b48-ca4fead689bb)), and Uber fills the trip sheet with ads ([example](https://mobbin.com/screens/103ece7a-9a40-4fb0-90ab-e046c6d76ba5)).
- **Alarm words for normal lows.** Oura's contributors list uses "Pay attention" in orange ([example](https://mobbin.com/screens/aba0560d-220a-4b2c-9700-d603272c67f7)), which the OpenStrap voice already rules out.
- **Empty zeros as the hero.** Apple Fitness leads with "0/300 CAL" and a flat chart early in the day ([example](https://mobbin.com/screens/5b8d5290-6c52-436f-bac3-9d0a531db829)), a number that says nothing yet.
