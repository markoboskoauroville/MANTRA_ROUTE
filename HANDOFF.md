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
