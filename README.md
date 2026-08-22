# Mantra Route

**Every audio output on the phone, as a row in one notification. Tap a row, the sound goes there.**

Nothing Phone (2a) · Android 16 · Shizuku · Mantra Productions

---

## What it is

An ongoing notification that lists every output the phone currently knows about — speaker,
earpiece, wired, USB, Bluetooth, LE audio, hearing aid, HDMI, dock — each one a line you can
press. No disconnecting, no re-pairing, no going through three screens to get back to the
speaker. Plus stereo, mono, and a balance dial that applies to everything at once.

## The thing to understand before installing it

**Android does not let an ordinary app move another app's media audio.** The permission that
does that, `MODIFY_AUDIO_ROUTING`, is `signature|privileged`: it cannot be granted by you, by
`adb`, or by Shizuku. Anything claiming otherwise is either a system app or is quietly doing
something less than it says.

What *is* reachable from the shell user that Shizuku provides differs by Android version and by
vendor, and there is no way to know which rungs exist on a particular phone by reasoning about
it from somewhere else. **So the app asks.** The Probe screen runs one real call per capability
against the real system and prints what came back:

| Probe | What it would buy |
|---|---|
| Shizuku shell | everything below |
| `master_mono` | mono downmix, system-wide |
| `master_balance` | panning the whole system left or right |
| Swap L/R | exchanging the channels — expected absent, and measured rather than assumed |
| `MODIFY_AUDIO_ROUTING` | direct routing of any app's audio. Expected refused |
| `MEDIA_ROUTING_CONTROL` app-op | the Android 15 route that might open the door |
| `cmd audio` / `cmd media_router` / `cmd bluetooth_manager` | whether these services exist here |

Every probe restores whatever it changed.

## The ladder

When a row is pressed the app climbs down until something takes:

1. **Audio policy strategy** — only if `MODIFY_AUDIO_ROUTING` probed green. Moves everything.
2. **Communication routing** — public API, no privilege. Calls and voice apps follow; music may
   not. The app says so instead of pretending.
3. **The platform's own output picker** — one tap, and it never lies about what it did.

## Swap L/R, honestly

`master_balance` is a pan, not a swap: it moves the whole mix towards one ear. Nothing in secure
settings exchanges the two channels, and a real swap would mean rewriting another app's PCM,
which no non-system app can do. The control is on screen, in slate, refusing — rather than
shipping as a switch that appears to work.

## Install

Sideload the APK from Releases. Then: install Shizuku, start it, open Mantra Route, allow
Shizuku, press **Probe this phone**, then **Show the switcher**.

Shizuku must be restarted after every reboot. That is Shizuku's constraint, not this app's.

## Documents

- [`HANDOFF.md`](HANDOFF.md) — current to the last build, including what has *not* been tested
- [`NEXT_DEFAULTS.md`](NEXT_DEFAULTS.md) — why each decision was made

*Mantra Productions.*
