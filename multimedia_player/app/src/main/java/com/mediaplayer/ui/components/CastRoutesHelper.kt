package com.mediaplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.mediaplayer.data.model.OutputRoute

@Composable
fun rememberCastRoutes(): List<OutputRoute> {
    val context = LocalContext.current
    var routes by remember { mutableStateOf<List<OutputRoute>>(emptyList()) }

    DisposableEffect(context) {
        val mediaRouter = MediaRouter.getInstance(context)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build()

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }

            private fun updateRoutes(router: MediaRouter) {
                routes = router.routes.filter { !it.isDefault && !it.isBluetooth }.map {
                    OutputRoute(id = it.id, name = it.name, description = "Chromecast")
                }
            }
        }

        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        routes = mediaRouter.routes.filter { !it.isDefault && !it.isBluetooth }.map {
            OutputRoute(id = it.id, name = it.name, description = "Chromecast")
        }

        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    return routes
}
