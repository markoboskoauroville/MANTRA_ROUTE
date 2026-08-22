# NEXT_DEFAULTS — why each decision was made

## Shizuku rather than the companion-watch role
Chosen by Marko, 22.8.2026, over the Android 15 `COMPANION_DEVICE_WATCH` route. The watch role
grants `MEDIA_ROUTING_CONTROL` without any helper app, which is tidier, but it requires the app
to associate itself with a device it is pretending to be a watch for, and the grant can be
revoked when the app is not in the foreground. Shizuku is coarser and needs restarting after a
reboot; it also reaches `master_mono` and `master_balance`, which the watch role does not.
**The watch role is not dead** — if the app-op probe comes back ABSENT it is the fallback.

## The app probes instead of assuming
Because there was no phone in the room when it was written. Reasoning about what the shell user
may do on a particular vendor build produces a confident answer that is wrong about a third of
the time, and a confident report on an untested feature spends trust that has to be earned back
later. Nine probes, one real call each, restoring what they change.

## Swap L/R ships as a control that refuses
It would have been easier to leave it out or to wire it to `master_balance` and let it look like
it worked. Both were rejected: leaving it out loses the answer to "can this be done", and wiring
it to balance is a control that lies. It sits in slate and says why.

## Bluetooth dedup keys on address, not name
Two identical pairs of the same earbuds report the same `productName`. Keying on the name
collapses them into one row and there is no way to reach the second. The cost is that when
`BLUETOOTH_CONNECT` has not been granted the address is empty and they *do* collapse — which is
why the permission is asked for at launch rather than lazily.

## Built-in outputs ignore the name the platform gives them
`productName` for the earpiece and for the speaker are both "Nothing Phone (2a)". Two rows, same
words. Only removable outputs are allowed to name themselves.

## Rows are recoloured, never added or removed
`design-language.md` §1. The notification's height must not change while a thumb is already
moving towards a row.

## The blend labels are sized to content
`design-language.md` §10, and this was caught during the build rather than after: three labels
at a third of the line each clips "Swap L/R" to "Swap L". Sized to content, spaced evenly.

## No gradle wrapper jar is committed
A binary in the tree that nobody reads. CI provisions Gradle instead. The cost is that a local
build needs Gradle 8.9 installed; that is written down here rather than discovered.

## minSdk 31
`setCommunicationDevice` arrived at 31 and it is the only rung of the ladder that works without
privilege. Below 31 the app would be a list that cannot switch anything.
