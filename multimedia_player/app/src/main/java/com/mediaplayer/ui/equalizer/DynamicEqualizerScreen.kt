package com.mediaplayer.ui.equalizer

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediaplayer.data.model.EqualizerRepository
import com.mediaplayer.util.AutoEqParser
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.EqualizerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import java.util.Locale

enum class AudioEngineMode {
    MANUAL, // Uses Headphone Target and 10-Band EQ
    DOLBY,  // Exclusive Dolby processing
    DIRAC   // Exclusive Dirac processing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicEqualizerScreen(
    onBack: () -> Unit,
    vm: PlayerViewModel
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val eqVm: EqualizerViewModel = hiltViewModel(activity)
    
    val state by vm.state.collectAsStateWithLifecycle()
    
    // Routing state machine
    var engineMode by remember { mutableStateOf(AudioEngineMode.MANUAL) }
    var isEnabled by remember { mutableStateOf(state.eqEnabled) }
    
    val customTargets by eqVm.customTargets.collectAsState()
    val selectedTarget by eqVm.selectedTargetName.collectAsState()
    val bandLevels by eqVm.bandLevels.collectAsState()

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val result = AutoEqParser.parseAutoEqJson(context, it)
            if (result != null) {
                // 1. Add new target to the list if not already present
                eqVm.addCustomTarget(result.name, result.gains)
                
                // 2. Pass the gains to update the UI Spline curve
                eqVm.updateAllBands(result.gains) 

                // 3. Trigger the actual Native Engine to change the sound
                vm.dspEngine.applyManualEqualizer(result.gains)
                
                engineMode = AudioEngineMode.MANUAL
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isRendering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF00FFCC),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.5f)
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp)
            ) {
                // Section 1: 10-Band Spline EQ
                val isManualActive = isEnabled && engineMode == AudioEngineMode.MANUAL
                
                Box(modifier = Modifier.alpha(if (isManualActive) 1f else 0.4f)) {
                    DynamicSplineEqualizer(
                        bandLevels = bandLevels,
                        onBandChange = { index, value ->
                            if (isManualActive) {
                                eqVm.updateBand(index, value)
                                vm.setEqPresetName("Custom")
                                // Trigger sound update on manual change
                                vm.dspEngine.applyManualEqualizer(eqVm.bandLevels.value)
                            }
                        },
                        enabled = isManualActive
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section 2: Headphone Target
                HeadphoneTargetSection(
                    targets = customTargets.keys.toList(),
                    selectedTarget = selectedTarget,
                    onTargetSelect = { target ->
                        eqVm.selectTarget(target)
                        vm.setEqPresetName(target)
                        engineMode = AudioEngineMode.MANUAL
                        // Trigger sound update
                        customTargets[target]?.let { gains ->
                            vm.dspEngine.applyManualEqualizer(gains)
                        }
                    },
                    onImportClick = {
                        jsonPickerLauncher.launch("application/json")
                    },
                    enabled = isEnabled && (engineMode == AudioEngineMode.MANUAL)
                )

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(24.dp))

                // Section 4: Dolby Atmos
                AudioEngineSection(
                    title = "Dolby Atmos",
                    options = EqualizerRepository.dolbyModes,
                    selectedOption = if (engineMode == AudioEngineMode.DOLBY) state.selectedDolby else null,
                    onOptionSelect = {
                        if (isEnabled) {
                            engineMode = if (engineMode == AudioEngineMode.DOLBY && state.selectedDolby == it) AudioEngineMode.MANUAL else AudioEngineMode.DOLBY
                            vm.setDolbyMode(it)
                        }
                    },
                    enabled = isEnabled
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Section 5: Dirac Audio
                AudioEngineSection(
                    title = "Dirac Audio",
                    options = EqualizerRepository.diracModes,
                    selectedOption = if (engineMode == AudioEngineMode.DIRAC) state.selectedDirac else null,
                    onOptionSelect = {
                        if (isEnabled) {
                            engineMode = if (engineMode == AudioEngineMode.DIRAC && state.selectedDirac == it) AudioEngineMode.MANUAL else AudioEngineMode.DIRAC
                            vm.setDiracMode(it)
                        }
                    },
                    enabled = isEnabled
                )
            }

            if (state.isRendering) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00FFCC))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Rendering Spatial Audio...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeadphoneTargetSection(
    targets: List<String>,
    selectedTarget: String?,
    onTargetSelect: (String) -> Unit,
    onImportClick: () -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).alpha(if (enabled) 1f else 0.5f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Headphone Target",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hardware Correction: Equalizes specific in-ear monitors & headphones to match target response curves.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onImportClick,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import Poweramp / AutoEQ JSON Target",
                    tint = Color(0xFF00FFCC)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            targets.forEach { target ->
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedTarget == target) Color(0xFF00FFCC) else Color(0xFF1E1E1E))
                        .clickable(enabled = enabled) { onTargetSelect(target) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = target,
                        color = if (selectedTarget == target) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicSplineEqualizer(
    bandLevels: FloatArray,
    onBandChange: (Int, Float) -> Unit,
    enabled: Boolean
) {
    val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    
    val animatedBands = bandLevels.map { 
        animateFloatAsState(targetValue = it, animationSpec = tween(durationMillis = 500), label = "BandAnimation")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(280.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f
                val stepX = width / (bandLevels.size - 1)
                val gainRange = 30f // -15 to +15
                val pxPerDb = height / gainRange

                val points = animatedBands.mapIndexed { index, state ->
                    Offset(index * stepX, centerY - (state.value * pxPerDb))
                }

                val path = Path()
                if (points.isNotEmpty()) {
                    path.moveTo(points[0].x, points[0].y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = points[if (i == 0) 0 else i - 1]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points[if (i + 2 >= points.size) points.size - 1 else i + 2]

                        val cp1x = p1.x + (p2.x - p0.x) / 6f
                        val cp1y = p1.y + (p2.y - p0.y) / 6f
                        val cp2x = p2.x - (p3.x - p1.x) / 6f
                        val cp2y = p2.y - (p3.y - p1.y) / 6f

                        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                if (enabled) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF00FFCC).copy(alpha = 0.3f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )

                    drawPath(
                        path = path,
                        color = Color(0xFF00FFCC),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                } else {
                    val grayPath = Path()
                    grayPath.moveTo(0f, centerY)
                    grayPath.lineTo(width, centerY)
                    drawPath(
                        path = grayPath,
                        color = Color.DarkGray,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                bandLevels.forEachIndexed { index, level ->
                    VerticalEqSlider(
                        value = level,
                        onValueChange = { onBandChange(index, it) },
                        frequency = frequencies[index],
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    frequency: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = (if (value >= 0) "+" else "") + String.format(Locale.US, "%.1f", value),
            color = if (enabled) Color(0xFF00FFCC) else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .width(44.dp)
                .onGloballyPositioned { coords ->
                    trackHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaDb = -(dragAmount / trackHeightPx) * 24f
                            val newGain = (value + deltaDb).coerceIn(-12f, 12f)
                            onValueChange(newGain)
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(16.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )

            val normalizedFraction = ((value + 12f) / 24f).coerceIn(0f, 1f)
            val thumbYOffsetPx = (1f - normalizedFraction) * trackHeightPx

            Surface(
                modifier = Modifier
                    .offset { IntOffset(0, thumbYOffsetPx.toInt() - 12.dp.roundToPx()) }
                    .size(24.dp)
                    .shadow(8.dp, CircleShape),
                shape = CircleShape,
                color = if (enabled) Color(0xFF00FFCC) else Color.DarkGray,
                border = BorderStroke(2.dp, Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                }
            }
        }

        Text(
            text = frequency,
            color = if (enabled) Color.White.copy(alpha = 0.8f) else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}



@Composable
fun PresetChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) Color(0xFF00FFCC) else Color(0xFF1E1E1E),
        label = "ChipBg"
    )
    val contentColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun AudioEngineSection(
    title: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelect: (String) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).alpha(if (enabled) 1f else 0.5f)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = selectedOption == option
                val backgroundColor by animateColorAsState(
                    if (isSelected) Color(0xFF00FFCC) else Color(0xFF1E1E1E),
                    label = "EngineBg"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable(enabled = enabled) { onOptionSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun DynamicSplineEqualizerPreview() {
    DynamicSplineEqualizer(
        bandLevels = floatArrayOf(5f, 2f, 0f, -3f, -5f, -2f, 3f, 8f, 4f, 1f),
        onBandChange = { _, _ -> },
        enabled = true
    )
}


