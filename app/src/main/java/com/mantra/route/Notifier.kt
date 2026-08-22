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

        val rows = router.rows()
        val active = router.activeId() ?: state.lastSelectedId

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
            view.setTextViewText(R.id.label, row.label)
            view.setImageViewResource(R.id.glyph, glyphRes(row.glyph))

            val lit = row.id == active
            view.setTextColor(R.id.label, if (lit) AMBER else SAND)
            view.setInt(R.id.glyph, "setColorFilter", if (lit) AMBER else SAND)

            view.setOnClickPendingIntent(R.id.row_root, selectIntent(context, row.id))
            big.addView(R.id.rows, view)
        }

        // The blend row. Exactly one is in force, so these behave as a radio: choosing one
        // visibly takes the mark off the last. §6.
        val blend = state.blend
        paintBlend(big, R.id.blend_stereo, blend == Blend.STEREO, true)
        paintBlend(big, R.id.blend_mono, blend == Blend.MONO, caps.works(Probe.MONO))
        paintBlend(big, R.id.blend_swap, blend == Blend.SWAPPED, caps.works(Probe.SWAP))

        big.setOnClickPendingIntent(R.id.blend_stereo, blendIntent(context, Blend.STEREO))
        big.setOnClickPendingIntent(R.id.blend_mono, blendIntent(context, Blend.MONO))
        big.setOnClickPendingIntent(R.id.blend_swap, blendIntent(context, Blend.SWAPPED))

        val headline = when {
            !Shell.isRunning() -> "Shizuku is not running"
            !Shell.hasPermission() -> "Shizuku has not been allowed"
            caps.anyRouting -> "Routing"
            else -> "Limited routing"
        }
        big.setTextViewText(R.id.title, headline)
        big.setTextColor(R.id.title, if (caps.anyRouting) SAND else SLATE)

        val collapsed = RemoteViews(context.packageName, R.layout.notif_collapsed)
        collapsed.setTextViewText(R.id.title, headline)
        collapsed.setTextViewText(
            R.id.subtitle,
            rows.firstOrNull { it.id == active }?.label ?: "${rows.size} outputs",
        )

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
