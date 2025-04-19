package com.example.tle

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.tle.ui.theme.TLETheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orekit.data.DataContext
import org.orekit.data.ZipJarCrawler
import org.orekit.frames.FramesFactory
import org.orekit.models.earth.ReferenceEllipsoid
import org.orekit.propagation.analytical.tle.TLE
import org.orekit.propagation.analytical.tle.TLEPropagator
import org.orekit.time.AbsoluteDate
import org.orekit.time.TimeScalesFactory
import org.orekit.utils.IERSConventions
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.util.Calendar


data class TLEEntry(
    val name: String,
    val line1: String,
    val line2: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TLETheme {
                Scaffold(modifier = Modifier) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        ContentView()
                    }
                }
            }
        }
    }
}

@Composable
fun ContentView() {
    val tleList = getTLEList()
    val itemList = tleList.map { it.name }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var year by rememberSaveable { mutableStateOf(2024) }
    var month by rememberSaveable { mutableStateOf(4) }
    var day by rememberSaveable { mutableStateOf(12) }
    var hour by rememberSaveable { mutableStateOf(14) }
    var minute by rememberSaveable { mutableStateOf(30) }

    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DropdownList(
                itemList = itemList,
                selectedIndex = selectedIndex,
                modifier = Modifier.widthIn(max = 100.dp),
                onItemClick = { selectedIndex = it }
            )
            Spacer(modifier = Modifier.width(16.dp))

            Text(
                textAlign = TextAlign.End,
                text = "Pick Date and Time",
                modifier = Modifier
                    .clickable {
                        datePicker(context) { selectedCalendar ->
                            year = selectedCalendar.get(Calendar.YEAR)
                            month = selectedCalendar.get(Calendar.MONTH) + 1 // 0-based
                            day = selectedCalendar.get(Calendar.DAY_OF_MONTH)
                            hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
                            minute = selectedCalendar.get(Calendar.MINUTE)
                        }
                    }
                    .padding(8.dp)
                    .background(color = MaterialTheme.colorScheme.background)
            )
        }

        Text(
            text = "You have chosen ${itemList[selectedIndex]}\nDate and Time: $year/$month/$day/$hour:$minute",
            modifier = Modifier.padding(1.dp)
        )

        getTLEByName(itemList[selectedIndex], tleList)?.let {
            GetCoordinates(it, year, month, day, hour, minute)
        }
    }
}

@Composable
fun getTLEList(): List<TLEEntry> {
    val context = LocalContext.current
    val json = context.assets.open("tleData.json").bufferedReader().use { it.readText() }
    val type = object : TypeToken<List<TLEEntry>>() {}.type
    return Gson().fromJson(json, type)
}

@Composable
fun getTLEByName(name: String, tleList: List<TLEEntry>): TLEEntry? {
    return tleList.find { it.name.equals(name, ignoreCase = true) }
}

@Composable
fun GetCoordinates(
    tleData: TLEEntry,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier
) {
    var coordinates by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(tleData, year, month, day, hour, minute) {
        withContext(Dispatchers.IO) {
            try {
                val zipFile = File(context.filesDir, "orekit-data-main.zip")
                if (!zipFile.exists()) {
                    context.assets.open("orekit-data-main.zip").use { input ->
                        zipFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val manager = ZipJarCrawler(zipFile)
                DataContext.getDefault().dataProvidersManager.clearProviders()
                DataContext.getDefault().dataProvidersManager.addProvider(manager)

                val tle = TLE(tleData.line1, tleData.line2)
                val propagator = TLEPropagator.selectExtrapolator(tle)
                val date = AbsoluteDate(year, month, day, hour, minute, 0.0, TimeScalesFactory.getUTC())

                val itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true)
                val pvInItrf = propagator.getPVCoordinates(date, itrf)
                val earth = ReferenceEllipsoid.getWgs84(itrf)
                val point = earth.transform(pvInItrf.position, itrf, date)

                val latDeg = Math.toDegrees(point.latitude)
                val lonDeg = Math.toDegrees(point.longitude)
                val altMeters = point.altitude

                coordinates = "Name: ${tleData.name}\nLatitude: $latDeg\nLongitude: $lonDeg\nAltitude: $altMeters"
            } catch (e: Exception) {
                coordinates = "Error loading data: ${e.message}"
            }
        }
    }

    Text(
        text = coordinates ?: "Loading TLE data...",
        modifier = modifier.padding(top = 8.dp)
    )
}

@Composable
fun DropdownList(
    itemList: List<String>,
    modifier: Modifier,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = modifier.clickable { showDropdown = !showDropdown },
            contentAlignment = Alignment.Center
        ) {
            Text(text = itemList[selectedIndex], modifier = Modifier.padding(3.dp))
        }

        if (showDropdown) {
            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(excludeFromSystemGesture = true),
                onDismissRequest = { showDropdown = false }
            ) {
                Column(
                    modifier = modifier
                        .heightIn(max = 300.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .border(width = 1.dp, color = Color.Gray),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemList.forEachIndexed { index, item ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .fillMaxWidth()
                                .width(150.dp)
                                .clickable {
                                    onItemClick(index)
                                    showDropdown = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = item)
                        }
                    }
                }
            }
        }
    }
}

fun datePicker(context: Context, onDateSelected: (Calendar) -> Unit) {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
        calendar.set(Calendar.YEAR, selectedYear)
        calendar.set(Calendar.MONTH, selectedMonth)
        calendar.set(Calendar.DAY_OF_MONTH, selectedDay)
        timePicker(context, calendar, onDateSelected)
    }, year, month, day).show()
}

fun timePicker(context: Context, calendar: Calendar, onDateSelected: (Calendar) -> Unit) {
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    TimePickerDialog(context, { _, selectedHour, selectedMinute ->
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)
        onDateSelected(calendar)
    }, hour, minute, true).show()
}
