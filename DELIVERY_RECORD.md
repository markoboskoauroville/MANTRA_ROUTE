# DELIVERY RECORD — Mantra Route v22 — 25.8.2026

Second full run of `modules/delivery-gate.md`, on the version of the module that carries §15a.

    ARTEFACT     22-mantra-route-v22-release.apk
                 9,868,072 bytes, 898 entries, dex 8,447,972
                 sha256 1aca572a79b6b9ebcf89568132098b903a794c235fb86413f85d6671b50706d7
                 built by Actions from commit 7569eb93cc4f3cd26a70d06193d70055f79aae03
    VERSION      new 22, previous 21 still downloadable

    GATES        G1 provenance   PASS  clean tree, versionCode 22 == tag == filename,
                                       4/4 actions pinned by SHA, v21 kept
                 G2 secrets      PASS  898 APK entries 0 hits; 27 commits of patch text 0 hits;
                                       0 keystores ever committed
                 G3 analysis     PASS  44 lint issues, 0 errors, 43 warnings unread
                 G4 dead code    PASS  after fixes: 6 drawables 0 unreferenced,
                                       22 model functions 0 uncalled. BEFORE fixes: 11 and 1
                 G5 dead loops   PASS  4 loops 0 unbounded; 3 postDelayed chains, each bounded
                                       or cancelled on detach
                 G6 stress       PARTIAL  400,000 fuzz iterations 0 faults; cycle driven through
                                       the real index round-trip across 11 stream ranges.
                                       NO DEVICE, so no monkey run
                 G7 budgets      PASS  apk 9,868,072, DOWN 14,384 from v20 across three versions
                 G8 upgrade      PASS BY CONSTRUCTION  nothing is persisted at all — no
                                       SharedPreferences, no files — so no stored value can be
                                       misread by a later build. Signature identical v20→v22,
                                       compared as certificate bytes inside the APK
                 G9 record       this document

    DEFECTS FOUND AND FIXED
                 1  A COARSE STREAM GOT STUCK ON ONE LEVEL, every press appearing to work.
                    Two causes: the cycle ran on percentages a coarse stream cannot represent,
                    and snap's tolerance was 50/max, which grows past the 25-point gap between
                    presets. Found by G6, not by any static check
                 2  press() called its lambda BARE. Any refusal from the clipboard, an absent
                    Settings screen or an unavailable audio effect took the app down and froze
                    the button on its old label. Five call sites, one fix
                 3  Tile faces re-rendered a 147 KB bitmap on every press. Twenty faces exist;
                    they are now cached
                 4  11 unreferenced drawables and the Needs model, dead since v18 and v16

    MY OWN CHECKS THAT WERE WRONG
                 The dead-function sweep reported 19 of 22 functions unused. The lookbehind
                 excluded qualified calls, so every `Volume.byId(` was invisible. Corrected, it
                 found 1. §15a says this is the most likely thing to be wrong and it was
                 A guard added for "the presets collapse to one step" was UNREACHABLE, proven
                 exhaustively over ranges 1..4096. Removed. Unreachable code that looks
                 defensive is a branch nobody can test and everybody trusts
                 A cycle test asserted snap(38,2) == 38 when 50 is correct under the new cap

    NOT TESTED   No device, so: G6 monkey, G8 installed upgrade, every G7 figure except size
                 The VU meter, Normalize and Boost have NEVER RUN. Visualizer and
                 LoudnessEnhancer on session 0 may be refused outright by this build
                 The 43 lint warnings were counted, not read
                 Toast from a TileService under Android 12+ background limits
                 Rollback: installing v21 over v22 was not attempted

    ROLLOUT      Sideload, one device. Halt criterion: if a tile stops responding to presses,
                 revert to v21 and send the report.
