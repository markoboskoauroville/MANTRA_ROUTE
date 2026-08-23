package com.mantra.route

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The notification.
 *
 * design-language.md §1 is the rule this file is built around: nothing appears, nothing
 * disappears. Every output the phone knows about gets a row from the first frame, and a row
 * that cannot be used right now is drawn in slate rather than removed. The list must not
 * change height under a thumb that is already moving towards it.
 *
 * §3: colour is the state channel and the only one. Amber is the output in force. Sand is an
 * output that can be chosen. Slate is present but not available. Red appears nowhere here,
 * because a headset being disconnected is not a fault.
 */
object Notifier {

    const val CHANNEL_ID = "mantra_route_switcher"
    const val NOTIFICATION_ID = 1

    // modules/design-language.md §3, the measured palette.
    private const val AMBER = 0xFFF59E0B.toInt()
    private const val SAND = 0xFFF2DDB4.toInt()
    private const val SLATE = 0xFF5A6B7C.toInt()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio output switcher",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "The ongoing panel that lists every audio output"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun post(context: Context) {
        ensureChannel(context)

        val router = Router(context)
        val state = State(context)
        val caps = state.capabilities()

        val rows = router.rows(state.switchedOff)
        // NO fallback to state.lastSelectedKey. That fallback is what lit the row you tapped
        // whether or not anything moved. If the platform will not confirm a route, nothing is
        // lit, and the panel says so in the headline instead of implying it in colour.
        val callRouteKey = router.activeId()
            ?.let { id -> rows.firstOrNull { it.id == id }?.key }
        val routingWorks = caps.anyRouting

        val big = RemoteViews(context.packageName, R.layout.notif_root)
        big.removeAllViews(R.id.rows)

        if (rows.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.notif_row)
            empty.setTextViewText(R.id.label, "No outputs reported")
            empty.setTextColor(R.id.label, SLATE)
            empty.setImageViewResource(R.id.glyph, R.drawable.ic_out_speaker)
            big.addView(R.id.rows, empty)
        }

        rows.forEach { row ->
            val view = RemoteViews(context.packageName, R.layout.notif_row)
            val holdsCall = row.key == callRouteKey
            val carries = Claim.carriesMedia(row.typeCode)
            view.setTextViewText(
                R.id.label,
                row.label + Claim.annotation(carries, holdsCall),
            )
            view.setImageViewResource(R.id.glyph, glyphRes(row.glyph))

            // Amber means "media is here", and that claim is only available when a routing
            // capability exists. The call route is reported in words on the row instead.
            val lit = routingWorks && holdsCall
            view.setTextColor(R.id.label, if (lit) AMBER else SAND)
            view.setInt(R.id.glyph, "setColorFilter", if (lit) AMBER else SAND)

            view.setOnClickPendingIntent(R.id.row_root, selectIntent(context, row.id))
            big.addView(R.id.rows, view)
        }

        // The release valve. Permanent, and above the picker, because a person whose
        // speakerphone has stopped working needs to find this without knowing what it is called.
        val release = RemoteViews(context.packageName, R.layout.notif_row)
        release.setTextViewText(R.id.label, "Give calls back to the system")
        release.setImageViewResource(R.id.glyph, R.drawable.ic_out_earpiece)
        release.setTextColor(R.id.label, SAND)
        release.setInt(R.id.glyph, "setColorFilter", SAND)
        release.setOnClickPendingIntent(R.id.row_root, releaseIntent(context))
        big.addView(R.id.rows, release)

        // The escape hatch, promoted to a permanent row rather than a consolation prize shown
        // only after a failure. On a phone that refuses MODIFY_AUDIO_ROUTING this is the ONLY
        // control here that moves music, so it belongs in the list, not hidden behind an error.
        val picker = RemoteViews(context.packageName, R.layout.notif_row)
        picker.setTextViewText(R.id.label, "System output switcher…")
        picker.setImageViewResource(R.id.glyph, R.drawable.ic_out_cast)
        picker.setTextColor(R.id.label, SAND)
        picker.setInt(R.id.glyph, "setColorFilter", SAND)
        picker.setOnClickPendingIntent(R.id.row_root, pickerIntent(context))
        big.addView(R.id.rows, picker)

        // The blend row. Exactly one is in force, so these behave as a radio: choosing one
        // visibly takes the mark off the last. §6.
        // §14: the object is the truth, the state is a claim about it. Reading state.blend
        // here is what let the panel show Stereo in amber while master_mono was actually 1.
        // Ask the system.
        val blend = router.currentBlend()
        paintBlend(big, R.id.blend_stereo, blend == Blend.STEREO, true)
        paintBlend(big, R.id.blend_mono, blend == Blend.MONO, caps.works(Probe.MONO))
        paintBlend(big, R.id.blend_swap, blend == Blend.SWAPPED, caps.works(Probe.SWAP))

        big.setOnClickPendingIntent(R.id.blend_stereo, blendIntent(context, Blend.STEREO))
        big.setOnClickPendingIntent(R.id.blend_mono, blendIntent(context, Blend.MONO))
        big.setOnClickPendingIntent(R.id.blend_swap, blendIntent(context, Blend.SWAPPED))

        val headline = Claim.headline(Shell.isRunning(), Shell.hasPermission(), routingWorks)
        big.setTextViewText(R.id.title, headline)
        big.setTextColor(R.id.title, if (caps.anyRouting) SAND else SLATE)

        // The collapsed view: every action in one row, no title.
        //
        // There is NO Android API to force a notification to open expanded — the platform owns
        // that and remembers what the user last did. So rather than pretend, the collapsed row
        // is made complete enough that expanding is optional.
        val collapsed = RemoteViews(context.packageName, R.layout.notif_collapsed)
        collapsed.removeAllViews(R.id.chips)

        rows.forEach { row ->
            val chip = RemoteViews(context.packageName, R.layout.notif_chip)
            val carries = Claim.carriesMedia(row.typeCode)
            chip.setTextViewText(R.id.chip, Chip.short(row.label))
            chip.setTextColor(
                R.id.chip,
                when {
                    routingWorks && row.key == callRouteKey -> AMBER
                    carries -> SAND
                    else -> SLATE
                },
            )
            chip.setOnClickPendingIntent(R.id.chip, selectIntent(context, row.id))
            collapsed.addView(R.id.chips, chip)
        }

        val stereoChip = RemoteViews(context.packageName, R.layout.notif_chip)
        stereoChip.setTextViewText(R.id.chip, "Stereo")
        stereoChip.setTextColor(R.id.chip, if (blend == Blend.STEREO) AMBER else SAND)
        stereoChip.setOnClickPendingIntent(R.id.chip, blendIntent(context, Blend.STEREO))
        collapsed.addView(R.id.chips, stereoChip)

        val monoChip = RemoteViews(context.packageName, R.layout.notif_chip)
        monoChip.setTextViewText(R.id.chip, "Mono")
        monoChip.setTextColor(
            R.id.chip,
            when {
                !caps.works(Probe.MONO) -> SLATE
                blend == Blend.MONO -> AMBER
                else -> SAND
            },
        )
        monoChip.setOnClickPendingIntent(R.id.chip, blendIntent(context, Blend.MONO))
        collapsed.addView(R.id.chips, monoChip)

        val releaseChip = RemoteViews(context.packageName, R.layout.notif_chip)
        releaseChip.setTextViewText(R.id.chip, "Release")
        releaseChip.setTextColor(R.id.chip, SAND)
        releaseChip.setOnClickPendingIntent(R.id.chip, releaseIntent(context))
        collapsed.addView(R.id.chips, releaseChip)

        val pickerChip = RemoteViews(context.packageName, R.layout.notif_chip)
        pickerChip.setTextViewText(R.id.chip, "Switcher")
        pickerChip.setTextColor(R.id.chip, SAND)
        pickerChip.setOnClickPendingIntent(R.id.chip, pickerIntent(context))
        collapsed.addView(R.id.chips, pickerChip)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(big)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(openAppIntent(context))
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /**
     * §5: a control says what the next press does. These three say what they will become, not
     * what they are — so the lit one is the one currently in force and pressing another is the
     * change. Unavailable stays on screen in slate and refuses the press.
     */
    private fun paintBlend(views: RemoteViews, id: Int, lit: Boolean, available: Boolean) {
        views.setTextColor(id, if (!available) SLATE else if (lit) AMBER else SAND)
    }

    private fun glyphRes(glyph: Glyph): Int = when (glyph) {
        Glyph.SPEAKER -> R.drawable.ic_out_speaker
        Glyph.EARPIECE -> R.drawable.ic_out_earpiece
        Glyph.WIRED -> R.drawable.ic_out_wired
        Glyph.USB -> R.drawable.ic_out_usb
        Glyph.BLUETOOTH -> R.drawable.ic_out_bluetooth
        Glyph.BLE -> R.drawable.ic_out_ble
        Glyph.HEARING_AID -> R.drawable.ic_out_hearing
        Glyph.HDMI -> R.drawable.ic_out_hdmi
        Glyph.DOCK -> R.drawable.ic_out_dock
    }

    private fun releaseIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            4000,
            Intent(context, RouteReceiver::class.java).setAction(RouteReceiver.ACTION_RELEASE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pickerIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            3000,
            Intent(context, RouteReceiver::class.java).setAction(RouteReceiver.ACTION_PICKER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun selectIntent(context: Context, deviceId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1000 + deviceId,
            Intent(context, RouteReceiver::class.java)
                .setAction(RouteReceiver.ACTION_SELECT)
                .putExtra(RouteReceiver.EXTRA_DEVICE_ID, deviceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun blendIntent(context: Context, blend: Blend): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            2000 + blend.ordinal,
            Intent(context, RouteReceiver::class.java)
                .setAction(RouteReceiver.ACTION_BLEND)
                .putExtra(RouteReceiver.EXTRA_BLEND, blend.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
