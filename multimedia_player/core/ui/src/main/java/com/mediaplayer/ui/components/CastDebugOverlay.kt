package com.mediaplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CastDebugOverlay(
    rttMs: Long,
    latencyMs: Long,
    lastSeq: Long,
    tvPosSec: Double,
    shownPosSec: Double,
    driftMs: Long,
    recentEvents: List<String>,
    recentCommands: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF121418),
            border = BorderStroke(1.dp, Color(0xFFFF5722).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CAST DEBUG OVERLAY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFFFF5722)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2228), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text("Median RTT: $rttMs ms (Latency: $latencyMs ms)", style = MaterialTheme.typography.bodySmall, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("Last Applied Seq: $lastSeq", style = MaterialTheme.typography.bodySmall, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("TV Pos: ${"%.2f".format(tvPosSec)}s  |  Phone Pos: ${"%.2f".format(shownPosSec)}s", style = MaterialTheme.typography.bodySmall, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("Sync Drift: $driftMs ms ${if (driftMs < 150) "✓ (<150ms)" else "⚠ (>150ms)"}", style = MaterialTheme.typography.bodySmall, color = if (driftMs < 150) Color(0xFF4CAF50) else Color(0xFFFF9800), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Last 10 Sent Commands:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    items(recentCommands.takeLast(10)) { cmd ->
                        Text(cmd, style = MaterialTheme.typography.labelSmall, color = Color(0xFF81D4FA), fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Last 10 Received TV Events:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    items(recentEvents.takeLast(10)) { evt ->
                        Text(evt, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA5D6A7), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
