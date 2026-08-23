# DELIVERY RECORD — Mantra Route v9 — 23.8.2026

First artefact in this account run through `modules/delivery-gate.md`. The module asks the first
project to come back and say what was wrong; §2 of this record does that.

    ARTEFACT     9-mantra-route-v9-release.apk
                 9,980,486 bytes
                 sha256 dc0edb6bea5d068a40eb5cf7b8f29b21e7e4723a894e31e01e485c6f506f33de
                 built by Actions run 32667344609, from commit 0c3ca3a1473d653a8436b7bd514c9937323be8e0
    VERSION      new: 9   previous: 8, still available at
                 https://github.com/markoboskoauroville/MANTRA_ROUTE/releases/tag/v8

    GATES        G1 provenance   PASS   clean tree (0 modified, 0 untracked), HEAD == origin/main,
                                        CI build, versionCode 9 == tag v9 == filename, v8 still
                                        downloadable, 2 releases kept, 4/4 actions now pinned by SHA
                 G2 secrets      PASS   911 APK entries scanned 0 hits; 10 commits of full patch
                                        text 0 hits; 688 CI log lines 0 hits; 0 keystores ever
                                        committed. Method corrected — see §2
                 G3 analysis     PASS   35 lint issues examined, 0 errors, 34 warnings unread
                 G4 dead code    PASS   12 drawables 0 unreferenced; 9 layouts 0 unreferenced;
                                        5 enums, 19 entries, 0 unwired
                 G5 dead loops   PASS   12 loops examined, 0 unbounded; 20 external calls, all
                                        through Shell.run with an 8000ms deadline; 105 functions
                                        examined for recursion, 3 flagged, 3 confirmed false
                 G6 stress       NOT RUN — no device in this environment. Blocking gate unmet
                 G7 budgets      BASELINE ONLY   apk 9,980,486 (v8 9,980,482, +4 bytes).
                                        No start time, frame time, memory or battery figures exist
                 G8 upgrade      NOT RUN — signature matches v8 (certificate bytes compared inside
                                        the APK, not a fingerprint from a log), so upgrade is
                                        possible, but installing v8 then v9 was never performed
                 G9 record       this document

    NOT TESTED   everything requiring a phone: G6 entirely, G8 entirely, every G7 figure except
                 artefact size
                 the Media row of the patch bay — never had a second media-capable output
                 the MediaRouter2 proxy rung — compiled, never executed, no target has existed
                 the 34 lint warnings — counted, not read
                 rollback: installing v8 over v9 was not attempted
                 Croatian/English string drift — no gate covers it, module §15 says so too

    KNOWN        the signing keystore was handed over in chat as a file. It is a live credential
                 sitting in a conversation log and should be moved
                 selectOutput still holds the communication device until released by hand (v7)
                 G7 has no previous numbers for anything but size, so it is a note, not a gate

    ROLLOUT      sideload only, one device. No staged rollout exists for this app.
                 Halt criterion: if the call path regresses again, stop and revert to v8.
