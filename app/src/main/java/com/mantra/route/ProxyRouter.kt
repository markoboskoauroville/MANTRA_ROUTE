package com.mantra.route

import android.content.ComponentName
import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exists only so the system has a component to allow.
 *
 * MediaSessionManager will not name the app that is playing unless the caller either holds
 * MEDIA_CONTENT_CONTROL — signature|privileged, so not available — or is an enabled
 * notification listener. A listener can be enabled from the shell, which Shizuku provides.
 * Nothing is read from any notification here and nothing is overridden; the class is a name.
 */
class RouteListener : NotificationListenerService()

/**
 * Who is actually playing.
 *
 * Routing another app's audio means naming that app first. There is no way to ask "what is
 * making noise" that does not go through the media sessions, and no way to read those without
 * the listener above.
 */
object MediaTargets {

    fun component(context: Context) = ComponentName(context, RouteListener::class.java)

    fun listenerEnabled(context: Context): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners",
        ).orEmpty()
        return enabled.contains(context.packageName)
    }

    /**
     * Packages with a live media session, most recently active first.
     *
     * Returns empty rather than throwing when the listener has not been allowed — the caller
     * has a ladder to climb down and a thrown SecurityException would skip the rest of it.
     */
    fun playing(context: Context): List<String> = try {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(component(context))
            .mapNotNull { it.packageName }
            .distinct()
    } catch (t: Throwable) {
        emptyList()
    }
}

/**
 * MediaRouter2, aimed at somebody else's app.
 *
 * `MediaRouter2.getInstance(Context)` controls this app's own routing, which is useless here —
 * this app plays nothing. The two-argument form returns a proxy that controls the routing of
 * the package named, and that one is hidden and gated on MEDIA_ROUTING_CONTROL. So it is
 * reached by reflection, and everything else in this file is ordinary public API.
 *
 * If Probe reports that app-op as ABSENT or REFUSED this is never called.
 */
object ProxyRouter {

    /** Null means it worked. A string means it did not, and says why. */
    fun transfer(context: Context, targetPackage: String, row: Row): String? {
        val router = proxyFor(context, targetPackage)
            ?: return "no proxy router for $targetPackage"

        val executor = context.mainExecutor
        val seen = CountDownLatch(1)

        // Routes are not populated until a callback is registered. Asking for getRoutes()
        // straight after getInstance() reliably returns an empty list, which reads exactly
        // like "this phone has one output".
        val callback = object : MediaRouter2.RouteCallback() {
            override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
                if (routes.isNotEmpty()) seen.countDown()
            }
        }

        val preference = RouteDiscoveryPreference.Builder(emptyList(), true).build()

        return try {
            router.registerRouteCallback(executor, callback, preference)
            seen.await(2, TimeUnit.SECONDS)   // TEST 3: never wait on another process forever

            val routes = router.routes
            if (routes.isEmpty()) return "the proxy router reported no routes"

            val match = pick(routes, row)
                ?: return "no route matched ${row.label}"

            router.transferTo(match)
            null
        } catch (t: Throwable) {
            (t.cause ?: t).toString()
        } finally {
            runCatching { router.unregisterRouteCallback(callback) }
        }
    }

    private fun proxyFor(context: Context, targetPackage: String): MediaRouter2? = try {
        MediaRouter2::class.java
            .getMethod("getInstance", Context::class.java, String::class.java)
            .invoke(null, context, targetPackage) as? MediaRouter2
    } catch (t: Throwable) {
        null
    }

    /**
     * Match on type first, name second.
     *
     * MediaRoute2Info's type constants are the same numbers as AudioDeviceInfo's, which is
     * lucky and is why this is not string matching. Name matching is the fallback because the
     * built-in outputs are given names by this app rather than by the platform, and those two
     * sets of names do not have to agree.
     */
    private fun pick(routes: List<MediaRoute2Info>, row: Row): MediaRoute2Info? {
        routes.firstOrNull { typeOf(it) == row.typeCode }?.let { return it }
        return routes.firstOrNull { it.name.toString().equals(row.label, ignoreCase = true) }
            ?: routes.firstOrNull { it.name.toString().contains(row.label, ignoreCase = true) }
    }

    private fun typeOf(route: MediaRoute2Info): Int? = try {
        MediaRoute2Info::class.java.getMethod("getType").invoke(route) as? Int
    } catch (t: Throwable) {
        null
    }
}
