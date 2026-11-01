package com.mediaplayer.ui.common

import android.util.Log
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.mediaplayer.R

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    useDarkTheme: Boolean = false
) {
    val themeRes = if (useDarkTheme) R.style.Theme_CastButton_Dark else R.style.Theme_CastButton

    AndroidView(
        factory = { ctx ->
            val themedContext = ContextThemeWrapper(ctx, themeRes)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
                setOnClickListener {
                    Log.d("CastDebug", "Cast picker opened / tapped")
                }
            }
        },
        modifier = modifier.size(32.dp)
    )
}
