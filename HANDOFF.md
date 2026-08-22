# HANDOFF — Mantra Route

Current to **v1**, 22.8.2026. First build. Nothing here has been on a phone.

---

## The four tests

**TEST 1 — the mechanism, alone.** RUN, GREEN. 16 tests, 0 failures.
`app/src/test/java/com/mantra/route/OutputsTest.kt`, compiled with kotlinc 1.9.24 and run
under JUnit 4.13.2 outside any Android context, because `RouteModel.kt` imports no Android type.

Both halves were then **made to fail on purpose**, per the meta-rule:

| sabotage | went red | and nothing else did |
|---|---|---|
| dedup Bluetooth by product name instead of address | `two different headsets sharing a product name stay two rows` | 11 others stayed green |
| let `balanceFor(SWAPPED)` return `0.0f` | `swap has no balance value` and `swap is refused everywhere` | 2 others stayed green |

**Icon geometry.** RUN, GREEN. `tools/icon_check.py` → extent x 33.50…74.50, y 35.00…73.00,
widest 41 against a 72 mask = **0.5694**, inside the 0.55–0.60 band and inside the 42 box.
This is arithmetic on the path, **not a raster of the rendered vector**.

**TEST 2 — the real thing, once.** NOT RUN. There is no phone here. This is the whole reason
the Probe screen exists: it is Test 2, packaged so it runs where the truth is.

**TEST 3 — the ugly cases.** PARTIAL. Written into the code and unproven at runtime:
timeout on every shell call, read-back after every write rather than trusting an exit code,
`UNTESTED` as the default verdict, a selected output that has been unplugged, an empty output
list, an unknown future type code, restoring `null` versus a previous value in probes.

**TEST 4 — the upgrade.** NOT APPLICABLE, and it needs saying why rather than being left blank:
there is no previous version to install over. It becomes mandatory at v2, and the trigger will
be the SharedPreferences schema in `State.kt`.

---

## What has NOT been tested, plainly

- **Everything that needs a phone.** No line of this has run on Android.
- **The Shizuku channel.** `Shizuku.newProcess` is reached by reflection because it is not in
  the published API. If Shizuku 13.x has moved it, every probe returns FAULT with the
  `NoSuchMethodException` in the detail column — which is at least legible, but it is a guess
  until it runs.
- **Whether any probe passes.** The honest position on `MODIFY_AUDIO_ROUTING` is that it will
  be refused. `MEDIA_ROUTING_CONTROL` is the one worth watching. If both refuse, the app is a
  good list plus communication routing plus mono and balance — useful, but less than asked for.
- **The notification's custom RemoteViews.** Layout is code inspection only. Custom views in
  notifications are re-themed by the launcher and Nothing OS is not stock.
- **`MediaRouter2` proxy routing.** Not implemented at all in v1. If the app-op probes green,
  that is the next thing to build and it is the thing that would make this app do what was
  actually asked for.
- **The release-pruning step** in CI (§4 of the versioning module). There has only ever been
  one release, so the loop that deletes the third-newest has never had anything to delete.
- **Debug signing.** CI builds a debug APK, and the runner's debug keystore is regenerated per
  run. Two builds may therefore refuse to install over each other. This directly threatens
  Test 4 and is the first thing to fix — a keystore in GitHub secrets.

---

## Where things are

| File | What it holds |
|---|---|
| `RouteModel.kt` | all the pure logic. No Android imports, on purpose |
| `Shell.kt` | the Shizuku channel, with a deadline on every call |
| `Probe.kt` | one real call per capability, and it restores what it changed |
| `Router.kt` | the ladder: strategy → communication → the platform's picker |
| `Notifier.kt` | the notification. Rows are recoloured, never added and removed |
| `RouteReceiver.kt` | taps, and the watcher that redraws when a device connects |
| `State.kt` | cached verdicts. Defaults to UNTESTED, never to WORKS |

## First thing to do next

Install it, press Probe, and send back the Probe screen. **Every decision after this one
depends on what that screen says**, and nothing more should be built until it has been read.
