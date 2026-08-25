# HANDOFF — Mantra Route

Current to **v2**, 22.8.2026. Still has not been on a phone.

---

## What v2 added

1. **The rung that does what was asked.** `ProxyRouter` gets a `MediaRouter2` aimed at another
   package and transfers its session, so music follows rather than only calls. Gated on the
   `MEDIA_ROUTING_CONTROL` app-op probing green; never called otherwise.
2. **`RouteListener`.** MediaSessionManager will not name the playing app without either a
   signature permission or an enabled notification listener. The listener is enabled from the
   shell. It reads nothing and overrides nothing — it is a name for `allow_listener` to allow.
3. **Stable keys.** v1 remembered the chosen output by device id. **That was a real bug**: ids
   are reissued on every reconnection, so the choice was forgotten the first time the
   headphones came out. Now keyed on type plus label, and the watcher re-selects on reconnect.
4. **§7, which v1 did not honour.** Tick to include, one button to reset, and the set stores
   what is switched **off** so an output kind added later is live by default.
5. **Signed releases.** A 4096-bit key generated into the sandbox vault and pushed straight to
   GitHub Actions secrets. It was never printed and is not in this repository.
6. **The receiver came off the main thread.** The proxy rung waits up to two seconds for routes
   and doing that inline froze the shade, which reads as the notification being broken.

---

## The four tests

**TEST 1 — the mechanism, alone.** RUN, GREEN. **23 tests, 0 failures** (12 + 7 + 4), compiled
with kotlinc 1.9.24 and run under JUnit outside any Android context.

Made to fail on purpose, both rounds:

| sabotage | went red | stayed green |
|---|---|---|
| dedup Bluetooth by name instead of address | 1 of 12 | 11 |
| `balanceFor(SWAPPED)` returns `0.0f` | 2 of 4 | 2 |
| key on the id — the bug v1 actually shipped | 5 of 7 together with ↓ | 12 in OutputsTest |
| treat the off-set as what to *show* | ↑ | " |

**Icon geometry.** RUN, GREEN. 0.5694 against the 0.55–0.60 band, inside the 42 box. Arithmetic
on the path, **not a raster of the rendered vector**.

**TEST 2 — the real thing, once.** NOT RUN, and cannot be from here. The Probe screen is Test 2,
packaged so it runs where the truth is. The compile against the real Android SDK in CI closes
the structural class of failure and nothing else.

**TEST 3 — the ugly cases.** PARTIAL, written and unproven: a deadline on every shell call and
on the route wait, read-back after every write instead of trusting an exit code, `UNTESTED` as
the default verdict, all-targets-refused falling through instead of reporting success, an
unplugged output, an empty list, an unknown future type, restoring `null` versus a real value.

**TEST 4 — the upgrade.** NOW POSSIBLE, NOT YET RUN. v1 was debug-signed by the runner, v2 is
release-signed by a stable key, so **v2 will not install over v1** — uninstall v1 first, once.
From v2 onward it works, and CI prints the certificate digest each build so a key change is
visible rather than arriving as "app not installed".

---

## What has NOT been tested, plainly

- **Everything that needs a phone.** No line of this has run on Android.
- **The whole proxy chain.** `getInstance(Context, String)` by reflection, the two-second route
  wait, and type-matching `MediaRoute2Info` against `AudioDeviceInfo` — the constants agree on
  paper and have never been compared on a device.
- **`Shizuku.newProcess`** is reflection against unpublished API. If 13.x moved it, every probe
  returns FAULT with the exception in the detail column.
- **Whether any probe passes.** `MODIFY_AUDIO_ROUTING` should be refused. `MEDIA_ROUTING_CONTROL`
  is the one that decides whether this app is what was asked for or a good list with
  communication routing, mono and balance attached.
- **The notification's custom RemoteViews.** Code inspection only. Nothing OS is not stock.
- **Swap L/R** is expected to sit in slate refusing, and says why.
- **The release-pruning step** in CI. Two releases now exist, so it has still never deleted
  anything.
- **No quick settings tile.** Considered and not built; the notification is the surface.

---

## Where things are

| File | What it holds |
|---|---|
| `RouteModel.kt` | all the pure logic, keys and the off-set. No Android imports |
| `Shell.kt` | the Shizuku channel, deadline on every call |
| `Probe.kt` | ten probes, one real call each, restoring what they change |
| `ProxyRouter.kt` | MediaRouter2 aimed at another package, and who is playing |
| `Router.kt` | the ladder: strategy → proxy → communication → the platform's picker |
| `Notifier.kt` | the notification. Rows are recoloured, never added and removed |
| `RouteReceiver.kt` | taps off the main thread, and re-select on reconnect |
| `State.kt` | verdicts, the off-set, the last key. Defaults to UNTESTED, never WORKS |

## First thing to do next

Uninstall v1, install v2, press **Probe this phone**, send back that screen. Everything after
this depends on what it says.

---

## v2 — what happened at the end of the build, recorded because it matters

**The first v2 build was red and no v2 existed.** `:app:packageRelease` failed with
`Get Key failed: Given final block not properly padded` — the classic signature of a
`KEY_PASSWORD` that does not match the key inside the keystore. Test 1 and the icon check were
both green in that run; only packaging failed.

**Three signing secrets were overwritten by a later session that did not check first.**
`KEYSTORE_B64`, `KEYSTORE_PASSWORD` and `KEY_ALIAS` were replaced with a newly generated
keystore before it was noticed that secrets already existed. GitHub secrets are write-only, so
the previous values are not recoverable from the repository.

**The damage was nil, and this is why:** the earlier keystore had never successfully signed
anything. The only build that used it failed at packaging, and no v2 release existed. So no
installed APK was signed with the lost key, and no upgrade path was broken. `KEY_PASSWORD` was
then aligned to the new keystore, which is what turned the build green.

**The keystore of record is now:**

```
SHA-256  88:3F:8C:D3:57:11:65:15:E9:D4:50:AA:7E:A6:1D:5D:4C:72:39:C3:B3:E4:B5:D6:EE:33:BB:7B:CC:A8:59:F0
alias    mantra
RSA 4096, valid 30 years
```

The v2 APK on the release page carries exactly this fingerprint — checked, not assumed.

**If this keystore is lost, the app can never be updated again.** It exists in two places: the
four GitHub Actions secrets, which cannot be read back, and the copy handed over in the chat
where v2 was finished. One of those two is not a backup. Put it somewhere it will survive.

### Test 4, and the one time it does not hold

v1 was **debug-signed** by the CI runner. v2 is **release-signed**. Android refuses an install
where the signature has changed, and reports it as *app not installed* rather than as anything
mentioning signatures.

**So v1 must be uninstalled once, by hand, before v2 goes on.** Everything from v2 forward
upgrades in place, and from v3 onward Test 4 becomes a real test with something to run it
against. This is a one-time cost, paid deliberately, to stop the runner minting a fresh debug
key on every build.

---

## v3 — TEST 2 finally ran, and it failed

**The phone said:** `java.lang.IllegalArgumentException: process hasn't exited`, on every single
shell probe. Shizuku connected, permission granted, notification drawn correctly with Phone
speaker and Earpiece — and nine probes all faulting identically.

**Nine identical failures is one bug, not nine.** The shared thing is `Shell.run`.

**Cause.** `ShizukuRemoteProcess` is not a local process. Its `waitFor(long, TimeUnit)` and
`exitValue()` are binder calls into the Shizuku server. Binder does not carry exception classes;
it maps a throwable onto a small fixed set of codes. The server raises
`IllegalThreadStateException`, which is a **subclass of IllegalArgumentException**, so it travels
as the parent and arrives as a plain `IllegalArgumentException`. The JDK's own timed `waitFor`
catches `IllegalThreadStateException` specifically — and therefore does not catch it.

Confirmed by reading the constant pool of `ShizukuRemoteProcess.class` from
`dev.rikka.shizuku:api:13.1.5`: it overrides `waitFor(JLjava/util/concurrent/TimeUnit;)Z` and
delegates to a remote `waitForTimeout(JLjava/lang/String;)Z`. Not inferred from the message.

**Fix.** Stop asking. The command now carries its own exit code out through stdout
(`echo __MR_EXIT__$?`), and completion is EOF on the stream rather than a question put to the
server. No binder call in the path, so there is nothing to flatten.

**What this means for the v2 verdicts: they were never measured.** Mono, balance and the
MEDIA_ROUTING_CONTROL app-op read "refused" because the shell died, not because the phone said
no. Every one of them is UNTESTED again until v3 probes.

**Also changed.** `destroyForcibly()` → `destroy()`, because the former routes through
`exitValue()`. Caught throwables now report their class name as well as their message — the only
reason v2 was diagnosable at all. And a **Copy the report** button, because the probe screen
scrolls past the bottom of a screenshot and the detail column is the part that matters.

### Tests

TEST 1: 31 green (12 + 4 + 7 + 8 new). Both new sabotages caught the right cases and only those:
reading the marker from the front instead of the end broke the token-in-output case; treating a
missing marker as exit zero broke the "no marker is not the same as exit zero" case.

TEST 2: the fix itself is **not verified**. It is a correct account of a failure that was
observed and a mechanism that was read out of the library, but no line of the new `Shell.run`
has run on a phone. If v3 probes still fault, the next thing to read is the exception class,
which will now be printed in full.

---

## v4 — the shell fix worked, and it exposed a worse bug

v3 on the phone: **Shizuku shell WORKS, uid 2000.** Seven probes returned real verdicts for the
first time. Nothing Phone (2a), Android 36.

### The probe changed the phone and could not change it back

The report read `master_mono accepted 1, restored to 1` and `master_balance accepted 0.5,
restored to 0.5`. Those "originals" are the probe's own test values. What happened: on the first
run the keys were unset, so the restore ran `settings delete secure`; that did not land, the test
value stuck, and the next run read the stuck value as the user's own setting and wrote it back.
**Two probe runs and the phone is permanently mono, panned 50% right.**

Cause: the restore was fire-and-forget. It performed the restore and reported "restored to X"
without ever reading the key again. Every other write in this codebase is verified by read-back;
the one write that undoes damage to someone's device was not.

Fixed: restore, read back, compare, and return **FAULT** naming the key and both values if it
did not land. A probe that cannot undo itself is a fault regardless of what it learned.

### The notification was showing a claim, not the phone

`Notifier` painted the Stereo/Mono row from `state.blend` — its own stored value, defaulting to
Stereo. So it showed Stereo in amber while `master_mono` was actually 1. It now calls
`router.currentBlend()`, which reads the setting. design-language §14, and the failure it
describes exactly.

### Two false greens

`cmd media_router` scored WORKS on the strength of "No shell command implementation." — the probe
only asked whether the service was listed. `cmd audio` scored WORKS on empty help. Both now
require real help text; a listed service with no commands is ABSENT.

### MEDIA_ROUTING_CONTROL: "default"

Not an error — the op exists on Android 36 and `appops set` silently did nothing. An app-op
attaches to a permission the package **declares**, and the manifest never declared
MEDIA_ROUTING_CONTROL. Now declared, and the probe tries `pm grant` as well as `appops set`,
reporting all three answers separately instead of merging them.

**This is a hypothesis, not a finding.** It is the most likely reason a set returns "default",
but it has not been tested. If v4 still reports "default", the manifest was not the obstacle.

### Tests

TEST 1: 38 green (12 + 4 + 7 + 8 + 7 new). Sabotages: restoring an unset key by writing the
literal "null" broke the delete case; accepting any listed service broke the media_router case.
Each turned exactly one test red.

TEST 4: v3 → v4 is the first upgrade over a same-signed install. Still unproven until it runs.

---

## v5 — "only colors are changing"

Reported from the phone, 23.8.2026, and correct. Three separate faults, all of them the app
claiming more than it knew.

### 1. The cache made v4 look like v3

v4 opened showing v3's stored verdicts — including `restored to 1` and
`cmd media_router: WORKS`, two strings v4 exists to stop producing. SharedPreferences survive an
upgrade and nothing invalidated them. Verdicts are now stamped with `BuildConfig.VERSION_CODE`
and a stamp from a different build reads as UNTESTED.

**Any probe output sent before pressing Probe on the new build is evidence about the old one.**

### 2. The amber row was showing the tap, not the sound

`Notifier` lit the row matching `communicationDevice` — the CALL path — and when that was empty
fell back to `state.lastSelectedKey`, the app's own memory of what was pressed. So tapping
Earpiece lit Earpiece whether or not anything moved, which is precisely what was observed.

Now: amber requires a routing capability to exist at all, and this phone has none. Nothing is
lit. The call route is reported in words on the row (`calls here`, `calls only`), not in colour,
because amber already means "in force" and giving it a second meaning destroys the legend.

### 3. The earpiece was offered as a music destination

It cannot be one. Android routes a call there and nothing else. It now says `calls only`.

### What this phone can actually do, stated plainly

- Mono downmix — **yes**, system-wide
- Left/right balance — **yes**, system-wide
- Swap L/R — **no**, and no build setting exists
- Move calls and voice apps between outputs — **yes**
- **Move music — no.** MODIFY_AUDIO_ROUTING is signature|privileged. MEDIA_ROUTING_CONTROL
  reads `default` after both `appops set` and `pm grant`, so the v4 manifest hypothesis was
  **wrong**: declaring the permission was not the obstacle.

The system output switcher is therefore the only thing on this device that moves music, and it
is now a permanent row in the panel rather than a consolation prize shown after a failure.

### Tests

TEST 1: 43 green. Sabotages: letting the app claim media moved regardless, and treating the
earpiece as a music destination — each turned exactly one test red.

---

## v6 — the app-op WORKS, and the UI was wrong

### MEDIA_ROUTING_CONTROL: allow

Reported from the phone on v5. **The v4 manifest hypothesis was correct.** It was declared wrong
in conversation only because v4's cached verdicts were being read as v5's — the same stale-cache
trap the version stamp now closes. `caps.anyRouting` is true, and Rung 2 (the MediaRouter2 proxy)
is live for the first time.

### Why there is still no switching, and it is not the code

`OUTPUTS` lists **Phone speaker** and **Earpiece**. Nothing else. Bluetooth is off in every
screenshot and nothing is plugged in. An earpiece cannot carry media, so the phone currently has
exactly ONE media destination.

**There is nothing to switch to.** No routing implementation, however correct, can demonstrate
anything against a single destination. Rung 2 additionally needs a live media session to move;
with nothing playing, `MediaTargets.playing()` is empty and the rung is skipped.

The proxy rung is therefore still **UNTESTED**, not failed. It has never had a target.

### The window drew under the system bars

Android 15 made edge-to-edge mandatory for `targetSdk` 35 and up. The window runs under the
status bar and navigation bar, and `activity_main.xml` never consumed the insets — so the title
sat behind the clock and the footer behind the nav buttons. Now padded from
`systemBars() or displayCutout()`, with `clipToPadding=false` so content still scrolls under.

### The collapsed notification spent its whole height on a word

Replaced with one row of chips: every output, Stereo, Mono, Switcher — all tappable without
expanding. Labels shortened by rule (whole first word before truncation, §10).

**Always-expanded is not possible.** Android has no API for it; the platform owns expansion state
and remembers what the user last did. The collapsed row is made complete instead of pretending.

### Tests

TEST 1: 48 green.

---

## v7 — the app broke the speakerphone

Reported from the phone, 23.8.2026: during a call there is no sound on speakerphone even when
the speakerphone button is pressed, while music through the speaker is fine.

**Cause, and it is this app.** `Router.selectOutput` calls `audio.setCommunicationDevice(device)`.
`clearCommunicationDevice()` appeared **nowhere in the codebase**. That request persists. Every
tap on Earpiece pinned the call route to the earpiece, and the phone's own speakerphone control
could not override a held request. Media was untouched, which is why it read as broken hardware
rather than as software holding a resource.

**The rule this broke:** an app that takes a system-wide resource must have a way to give it
back, and that way must be reachable by the person — not only by the code path that took it.
There was a `set` with no `clear`, and nothing in the four tests catches that, because each
individual selection worked exactly as designed.

**Immediate remedy without a new build:** force-stop the app, or reboot. The pin is runtime
state and does not survive either.

**v7 adds:**
- `Router.releaseCallAudio()` — clears the held device and verifies it actually let go
- A button at the top of the screen, *Fix call audio — give calls back to the system*, which
  depends on nothing: not Shizuku, not a probe having been run
- A `Release` chip in the collapsed notification row and a full row in the panel
- A live `CALL AUDIO` readout: what is held, what is available for calls, the audio mode — and
  the same block in the copied report

**Not yet done, and it should be:** `selectOutput` still has no automatic release. Selecting an
output holds it until the person releases it by hand. The honest fix is either to release on a
second tap of the same row, or never to hold it at all outside a call. That decision needs the
call-audio readout from a real device first.

TEST 1: 48 green. None of them cover this — the fault was a missing counterpart to a call that
worked, which is a shape unit tests over pure logic cannot see.

---

## v8 — the patch bay

Asked for on 23.8.2026 in the language of an X32: sources down the side, destinations across the
top, press a crosspoint to connect. It is a better model than the button list it replaces, and
not only because it is familiar.

**A crosspoint grid has somewhere to put the fact that a connection is impossible.** A button
does not — a button either works or disappoints, and for eight versions this app was a list of
buttons that mostly disappointed. A blocked crosspoint is information.

### Two rows, because Android has two paths

`Media` and `Calls` are independent in the platform, and conflating them is what made this app
confusing from v1. Calls have never needed a privilege — `setCommunicationDevice` is a normal
API and has worked the whole time. Media needs one this phone refuses. Same list of
destinations, completely different answer, and the grid shows both at once.

### What the grid says on this phone today

|  | Phone speaker | Earpiece |
|---|---|---|
| **Media** | · not possible | · not possible |
| **Calls** | ○ free | ● patched |

The Media row's blocked cells are not a failure to implement something. `Media → Earpiece` is
blocked permanently — Android does not route music there, and offering it was this app's
longest-standing lie. `Media → Speaker` is blocked because no routing privilege was measured.

### Three marks, not three colours

`●` patched, `○` free, `·` not possible. Colour reinforces but does not carry — a test asserts
the three marks are distinct, so a colour-blind reading still works. §3 says colour is the state
channel; it does not say colour should be the only one.

A blocked cell is still tappable and explains itself rather than doing nothing silently.

### Tests

TEST 1: 54 green. Sabotages: allowing media to patch to the earpiece, and giving CONNECTED and
CONNECTABLE the same mark — each turned exactly one test red.

### Still open

The Media row cannot be exercised until a second media-capable output exists on the device. The
grid will show `○` in the Media row the moment headphones are connected AND a routing capability
is measured; if it shows `·` with headphones connected, the proxy rung is genuinely unavailable
and the capture-and-replay fork is the remaining option.

---

## v10 — volume, and the press that said nothing

### The volume hole, which was a design fault not an oversight

Reported: switching works, the sound is inaudibly quiet. **Android has no per-device volume.**
There is no earpiece volume and no speaker volume. Volume is per STREAM, and the call path and
the media path are different streams with separately remembered levels.

So routing a call to the earpiece hands you `STREAM_VOICE_CALL` at whatever level it was last
left at — possibly months ago. Nothing in this app ever displayed that number, let alone let it
be changed. **The routing was visible and the gain was not**, and a correctly-routed inaudible
call reads as a routing failure.

Four sliders now: Call, Music, Ring, Alarm — each showing its real index out of its real range,
not a percentage. A stream at or below 25% is drawn in red and marked `<-- LOW` in the report,
because the whole point is to name it as a level rather than leave it looking like a dead route.

`setStreamVolume` returns void and is routinely clamped on the call stream outside a call, so
every set is read back, and falls through to `media volume --stream N --set M` over Shizuku when
the direct call does not take.

### The press that said nothing

Reported: *"I press and there is no interaction... the copy button is interacting, so I know."*

The copy button responded by accident — it happened to have something to say. Nothing else did.
Now one rule, `press()`, applied to every control on the screen, on three channels at once
because any single one can be missed:

- the label becomes the RESULT and holds 2.2s, then returns to saying what the next press does
- the button turns amber for that time
- a haptic tick, which is the only channel that works when you are not looking at the screen

`Feedback.resultLabel` never returns empty — a control that goes blank on press is the reported
bug, and a test asserts it.

### Also

A **Centre the balance** button, which writes 0.0 and verifies. The v9 report showed balance
sitting at 0.31 with no way to zero it except dragging.

### Tests

TEST 1: 64 green. Sabotages: truncating instead of rounding in `indexFor` (the slider stops one
step short of maximum), and letting an empty result blank the button — each turned exactly one
test red.

### Still true

Call audio is still held until released by hand. The v9 report showed `held by this app:
Earpiece`, meaning the speakerphone was still captive at the time it was taken.

---

## v11 — mostly removals

Reported 24.8.2026, and the instruction was the right one: *"things which don't work, if it's
not possible to fix, we remove them. They're just taking space."*

### The app was blocking the speakerphone

*"I need to press the speaker icon during the call... the app is blocking it, it takes over."*
Exactly right, and worse than v7 understood. v7 added a release BUTTON. The real fault was that
the app was taking the call route at all.

`setCommunicationDevice` pins the call route system-wide and **outranks the dialer's own
speakerphone button**. Media routing was the goal; the call path was never asked for. Taking it
was collateral damage, and every tap on Earpiece re-took it.

**Call routing is gone.** No `setCommunicationDevice` anywhere. Anything held by v7–v10 is
released at startup, before the screen draws, because an upgrader arrives with the earpiece
still pinned and no reason to know it.

### Removed, each because its answer could never change

    Swap L/R            ABSENT on every run. No secure setting exchanges channels
    Calls row           the app has no business on the call path
    MODIFY_AUDIO_ROUTING probe   signature|privileged, refused on every retail Android
    cmd audio probe     service listed, no shell commands, on every run
    cmd media_router    same
    System switcher row reported as doing nothing on this phone

Ten probes became six. A probe whose answer cannot change is not a measurement, it is a
paragraph you re-read every time.

### "A142" explained, and fixed

`releaseCallAudio` printed `after.productName`, which for the earpiece is the phone's model
code. Accurate value, meaningless sentence. It now prints the name, so the message reads
*"could not let go of Earpiece."*

### Names

"Phone speaker" → **Speaker**. The phone is the whole object in your hand; the speaker and the
earpiece are parts of it. It also stopped the chip being truncated to "Phone", which named the
wrong thing entirely.

### Icons

Redrawn as Material Icons paths, matching the 24dp house style already used in TTT_MINI.
Speaker is the loudspeaker glyph, Earpiece is the headphones glyph.

### Quick Settings tile

The panel at the top of the screen is **Quick Settings**; a button an app puts there is a
**Quick Settings tile**. `MonoTileService` adds one, carrying the mono downmix — the control
that measurably works and is worth one gesture. Not a launcher shortcut: the app is already in
the launcher, and a tile that only opens an app has spent the most valuable strip of screen on
nothing. It must be placed by hand from the pencil in Quick Settings.

### Layout

Four buttons became a 2×2 grid of equal boxes. §10 forbids sizing a WORD by a fraction of the
line; these are boxes sharing width equally, each wrapping its own text, with a minHeight so all
four stay the same size whatever the labels say.

### Tests

57 green, down from 64 — six tests deleted with the features they covered, and six updated for
the rename. **A test kept alive for a deleted feature is dead code that passes.**

---

## v13 — icons, matched to TTT_MINI properly

v11 redrew the icons as legacy Material Icons: `viewportWidth="24"`, hand-transcribed paths.
Close, but not the house style. **TTT_MINI uses Material *Symbols*** — `viewportWidth="960"`,
one filled path, `fillColor="@android:color/white"` with `android:tint`.

The difference is checkable: TTT_MINI's check mark is `M382,720L154,492…` where Google's published
symbol is `M382-240 154-468…`. Same path, y shifted by +960. So the conversion is exactly
`y -> y + 960`, and every icon here is now the official published path put through that shift
rather than an approximation drawn by hand.

Eleven icons regenerated from `google/material-design-icons` (Apache 2.0): speaker is
`volume_up` — the loudspeaker with waves, which reads as "sound leaves here" where the
speaker-cabinet glyph reads as furniture — earpiece is `headphones`, wired is `headset_mic`,
plus usb, bluetooth, bluetooth_searching, hearing_aid, hdmi, dock, cast.

The converter is kept at `tools/icon_from_material.py` so the next one does not get hand-drawn.

### A collision worth recording

This session opened the tree, edited it, and only then read `git log` — finding v11 and v12
already committed by another session. One unintended edit to `Probe.kt` was made and reverted
before any commit. **The v2 incident had exactly this shape and the lesson did not stick: read
the log before touching the tree, every time, because the tree is shared and the chat is not.**

---

## v14 — four volume tiles, and mono off the shelf

Asked for on 24.8.2026: one Quick Settings tile per volume section, each toggling 50% / 100%.
`MonoTileService` is deleted. Mono was **my** judgement of what deserved that slot; a level
toggle is what was wanted, and it is the better use of the strip — the mono setting changes
twice a year, a volume changes hourly.

Four tiles: **Call, Music, Ring, Alarm**. Each shows its live level in the subtitle
(`53%  (8/15)`), so it answers without being pressed, and lights ACTIVE when loud.

They must be placed by hand: pull the shade down twice, tap the pencil, drag them up from the
inactive tiles at the bottom.

### The toggle rule, and why it is not the obvious one

The obvious rule is `if (index == max) half else full`. It is wrong: a stream sitting at 87%
would be raised to 100% rather than halved, and where you were is silently discarded. Instead
anything from **75% up** counts as loud and goes to half; everything else goes to full. Two
presses always return you to where you started.

### A test hole the sabotage step found

The first version of this suite **passed with the naive toggle in place.** Every case tested was
either exactly at maximum or well below the line, so the two rules agreed on all of them. The
sabotage exercise — break it on purpose, confirm the tests go red — went green, which is the
signal that the tests are decorative.

Added: 13/15 and 14/15, loud but not maximum, plus 11/15 just below the line. The sabotage now
fails on that test specifically. **This is the second time the "make it fail on purpose" step
has caught something the tests would not have**, and it is the cheapest step in the whole
process.

### Tiles bind by stream id, not list position

They were written as `Volume.STREAMS[0]`, `[1]`, `[2]`, `[3]`. Reordering that list would have
silently swapped the Call and Music tiles with nothing failing anywhere. Now `Volume.byId(0)`,
and an unknown id throws rather than returning a neighbour.

### Icons

Four more from the official Material Symbols: `call`, `music_note`, `notifications`, `alarm`.

---

## v15 — five tiles that can be read, and the blank-icon bug

### The blank tile was a malformed vector, and nothing anywhere said so

One Quick Settings tile rendered as an empty circle. Cause: the hand-rolled tokeniser in
`tools/icon_from_material.py` **glued implicit repeated coordinate groups together with no
separator** — `L100,200` followed by an implicit `300,400` became `L100,200300,400`. Paths
without implicit repeats survived, which is why most icons looked right; `alarm` has them and
became unparseable.

**A malformed vector compiles, packages, installs and draws nothing.** No lint error, no crash,
no warning. The only way to catch it is to look at the pixels.

So the converter no longer parses paths by hand — `svgpathtools` understands the grammar and the
shift is a real transform — and **every icon is now rendered to PNG and checked for ink before
it is kept**. 16 generated, 0 blank, and the check exits non-zero if any is.

Rendering them also showed a second fault the eye would have caught and the build never would:
Ring was using the bell glyph, which belongs to Notification. Both now match the system volume
panel exactly.

### Four channels for one fact

The screenshot showed the panel drawing tiles as bare circles — no label, no subtitle. A
two-state toggle that cannot be read is a coin flip. The rule quoted with the request is the
reason it matters: engage more than one sense and comprehension goes up. A tile speaking through
glyph shape alone is one channel, and it was the channel that failed.

    SHAPE     the glyph, matched to the system volume panel
    NUMBER    the percentage drawn INTO the icon bitmap, because that square is all there is
    WORDS     label "Media 100%" and subtitle "tap for 50%", for the expanded panel and for
              a screen reader
    TOUCH     a haptic tick on press

### Five streams, named as the platform names them

Call, **Media** (not Music — the system panel says Media), Ring, **Notification** (new), Alarm.

### A collision, handled better than last time

Another session was working in this tree with uncommitted changes: `TileIcon.kt`, `TileText`,
the Media rename and the Notification stream. **That work is better than what this session would
have done alone** — drawing the number into the bitmap is the right answer to a panel that gives
you no label, and I would not have found it. It was backed up before anything was touched, and
then completed rather than replaced: the tile classes now use it, a fifth tile was added, and
the icons it depended on were repaired.

TEST 1: 71 green. The Media rename correctly broke a v14 test that asserted "Music" — the test
doing its job, and fixed rather than deleted.

---

## v16 — notifications removed, and Shizuku is no longer needed for the tiles

### The tiles never needed Shizuku, and the app was implying otherwise

Asked on 24.8.2026: why does this app need Shizuku when other volume apps do not?

**It does not, and it never did.** `AudioManager.setStreamVolume` is guarded by **no permission
at all**. That is the whole answer to "why can other apps do it": they are not doing anything
privileged, and neither are these tiles. `Router.setVolume` has always tried the direct call
first and only fallen through to the shell if it was refused.

What actually needed Shizuku was three different things wearing one banner:

| | needs |
|---|---|
| Volume tiles — Call, Media, Alarm | **nothing** |
| Volume tiles — Ring, Notification | nothing, unless Do Not Disturb is on |
| `master_mono`, `master_balance` | Shizuku — they are `Settings.Secure` |
| routing app-op | Shizuku — and it never produced a working route anyway |

The app conflated them by leading with "Shizuku connected" and gating a probe-driven UI behind
it. `Needs` names the distinction and a test asserts the property that matters: **no combination
of stream, DND state and permission ever returns SHIZUKU for volume.**

### One real gap, and it was hidden by a swallowed exception

`setStreamVolume` throws `SecurityException` in exactly one case — Do Not Disturb is on and the
app has no notification-policy access, which affects Ring and Notification only. The old code
did `runCatching { … }` and discarded it, then reported *"Shizuku is not available to force it"*,
sending anyone reading it to fix entirely the wrong thing.

Now the exception is named, `ACCESS_NOTIFICATION_POLICY` is declared, and a **Do Not Disturb
access** button opens the right Settings screen. Granted on the phone. No computer, no Wi-Fi.

### Removed

`Notifier.kt`, `RouteReceiver.kt`, `ProxyRouter.kt`, four `notif_*` layouts, the
`RouteListener` notification-listener service, and the `POST_NOTIFICATIONS` and
`RECEIVE_BOOT_COMPLETED` permissions.

The listener is worth calling out: it read **every notification on the phone**, and it existed
only to name which app was playing so the proxy router could move it. The proxy router never
once moved anything. That is a large permission held for a feature that never worked, and it is
gone.

TEST 1: 75 green.
