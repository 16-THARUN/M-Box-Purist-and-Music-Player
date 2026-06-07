import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun VinylAlbumCover(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    // Replace this with a String/Url if you are using Coil/Glide for network images
    albumArtPainter: androidx.compose.ui.graphics.painter.Painter
) {
    // This state keeps track of the exact rotation angle.
    var rotation by remember { mutableFloatStateOf(0f) }

    // This loop ensures the record spins smoothly and freezes in place when paused.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            withFrameNanos {
                // Adjust the '0.5f' to make the record spin faster or slower
                rotation = (rotation + 0.5f) % 360f
            }
        }
    }

    Box(
        modifier = modifier
            .size(120.dp) // Total size of the vinyl record
            .graphicsLayer {
                rotationZ = rotation
            }
            .clip(CircleShape)
            // The black edge of the vinyl record
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 1. The Album Art (The label on the vinyl)
        Image(
            painter = albumArtPainter,
            contentDescription = "Album Art",
            modifier = Modifier
                .fillMaxSize(0.65f) // The art covers 65% of the vinyl
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // 2. The Center Hole
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                // Using the app's background color makes it look like a real hole
                .background(MaterialTheme.colorScheme.background)
        )
    }
}