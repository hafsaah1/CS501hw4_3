package com.example.a501hw4_3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random


data class TempReading(
    val temperature: Float,
    val timestamp: String,
    val id: String = UUID.randomUUID().toString()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TempDashboardTheme {
                TempDashboardApp()
            }
        }
    }
}

// ViewModel manages all the temperature data and simulation logic
// survives rotation so data doesn't get lost
class TempViewModel : ViewModel() {
    // StateFlow holds the list of temperature readings
    // keeps last 20 readings max
    private val _readings = MutableStateFlow<List<TempReading>>(emptyList())
    val readings: StateFlow<List<TempReading>> = _readings.asStateFlow()

    // tracks if simulation is currently running or paused
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // computed stats from the readings
    // recalculates whenever readings change
    private val _currentTemp = MutableStateFlow(0f)
    val currentTemp: StateFlow<Float> = _currentTemp.asStateFlow()

    private val _avgTemp = MutableStateFlow(0f)
    val avgTemp: StateFlow<Float> = _avgTemp.asStateFlow()

    private val _minTemp = MutableStateFlow(0f)
    val minTemp: StateFlow<Float> = _minTemp.asStateFlow()

    private val _maxTemp = MutableStateFlow(0f)
    val maxTemp: StateFlow<Float> = _maxTemp.asStateFlow()

    // start the temperature simulation
    fun startSimulation() {
        _isRunning.value = true
        viewModelScope.launch {
            // keeps looping while running
            while (_isRunning.value) {
                // generate random temp between 65 and 85
                val temp = Random.nextFloat() * (85f - 65f) + 65f

                // format timestamp as HH:mm:ss
                val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val timestamp = formatter.format(Date())

                // create new reading
                val newReading = TempReading(temp, timestamp)

                // add to list and keep only last 20
                val updatedList = (_readings.value + newReading).takeLast(20)
                _readings.value = updatedList

                // update stats
                updateStats()

                // wait 2 seconds before next reading
                // delay is suspend function - doesn't block main thread
                delay(2000L)
            }
        }
    }

    // pause the simulation
    fun stopSimulation() {
        _isRunning.value = false
    }

    // toggle between running and paused
    fun toggleSimulation() {
        if (_isRunning.value) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    // recalculate all the stats from current readings
    private fun updateStats() {
        val temps = _readings.value.map { it.temperature }
        if (temps.isNotEmpty()) {
            _currentTemp.value = temps.last() // most recent reading
            _avgTemp.value = temps.average().toFloat() // mean of all readings
            _minTemp.value = temps.minOrNull() ?: 0f // lowest temp
            _maxTemp.value = temps.maxOrNull() ?: 0f // highest temp
        }
    }
}

@Composable
fun TempDashboardApp(viewModel: TempViewModel = remember { TempViewModel() }) {
    // collect all StateFlows so UI updates automatically
    // unidirectional data flow - state flows down from ViewModel
    val readings by viewModel.readings.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentTemp by viewModel.currentTemp.collectAsState()
    val avgTemp by viewModel.avgTemp.collectAsState()
    val minTemp by viewModel.minTemp.collectAsState()
    val maxTemp by viewModel.maxTemp.collectAsState()

    Scaffold(
        topBar = { TopBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // control button - start/pause simulation
            ControlButton(
                isRunning = isRunning,
                onToggle = { viewModel.toggleSimulation() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // stats cards showing current, avg, min, max
            StatsSection(
                current = currentTemp,
                avg = avgTemp,
                min = minTemp,
                max = maxTemp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // simple line chart showing temperature trend
            if (readings.isNotEmpty()) {
                TempChart(readings = readings)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // scrollable list of all readings
            Text(
                text = "Temperature Readings (${readings.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // lazy column for efficient scrolling
            // only renders visible items
            if (readings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No readings yet. Press Start to begin.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // show newest first
                    items(readings.reversed(), key = { it.id }) { reading ->
                        ReadingCard(reading = reading)
                    }
                }
            }
        }
    }
}

// top bar with app title
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    TopAppBar(
        title = { Text("Temperature Dashboard") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// button to start/pause the simulation
// changes color and text based on state
@Composable
fun ControlButton(
    isRunning: Boolean,
    onToggle: () -> Unit
) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.error // red when running
            else
                MaterialTheme.colorScheme.primary // blue when paused
        )
    ) {
        // just text, no icons - keeps it simple
        Text(
            text = if (isRunning) "⏸ Pause Simulation" else "▶ Start Simulation",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// displays the 4 main stats in a grid
// current, average, min, max
@Composable
fun StatsSection(
    current: Float,
    avg: Float,
    min: Float,
    max: Float
) {
    Column {
        // first row - current and average
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Current",
                value = current,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Average",
                value = avg,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // second row - min and max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Min",
                value = min,
                color = Color(0xFF2196F3), // blue
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Max",
                value = max,
                color = Color(0xFFF44336), // red
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// individual stat card
// shows label and temp value
@Composable
fun StatCard(
    title: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (value > 0f) "${value.toInt()}°F" else "--",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// simple line chart using Canvas
// draws temperature readings as a connected line
@Composable
fun TempChart(readings: List<TempReading>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Canvas lets us draw custom graphics
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (readings.isEmpty()) return@Canvas

                // get temp values
                val temps = readings.map { it.temperature }
                val minTemp = temps.minOrNull() ?: 65f
                val maxTemp = temps.maxOrNull() ?: 85f
                val range = maxTemp - minTemp

                // avoid division by zero
                if (range == 0f) return@Canvas

                // calculate spacing between points
                val width = size.width
                val height = size.height
                val spacing = width / (readings.size - 1).coerceAtLeast(1)

                // create path connecting all points
                val path = Path()
                readings.forEachIndexed { index, reading ->
                    // map temperature to y coordinate
                    // higher temp = lower y (canvas y increases downward)
                    val x = index * spacing
                    val normalizedTemp = (reading.temperature - minTemp) / range
                    val y = height - (normalizedTemp * height)

                    if (index == 0) {
                        path.moveTo(x, y) // start point
                    } else {
                        path.lineTo(x, y) // connect to previous point
                    }
                }

                // draw the line
                drawPath(
                    path = path,
                    color = Color(0xFF2196F3),
                    style = Stroke(width = 3f)
                )

                // draw dots at each point
                readings.forEachIndexed { index, reading ->
                    val x = index * spacing
                    val normalizedTemp = (reading.temperature - minTemp) / range
                    val y = height - (normalizedTemp * height)

                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = 4f,
                        center = Offset(x, y)
                    )
                }
            }

            // chart label
            Text(
                text = "Temperature Trend",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

// card showing individual reading
// displays temp and timestamp
@Composable
fun ReadingCard(reading: TempReading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // temperature value - big and bold
            Text(
                text = "${reading.temperature.toInt()}°F",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = getColorForTemp(reading.temperature)
            )

            // timestamp - when this reading was taken
            Text(
                text = reading.timestamp,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// color code temperatures
// cooler = blue, warmer = red
fun getColorForTemp(temp: Float): Color {
    return when {
        temp < 70 -> Color(0xFF2196F3) // cool - blue
        temp < 75 -> Color(0xFF4CAF50) // mild - green
        temp < 80 -> Color(0xFFFF9800) // warm - orange
        else -> Color(0xFFF44336) // hot - red
    }
}

@Composable
fun TempDashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}