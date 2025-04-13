package com.example.tle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.tle.ui.theme.TLETheme

import org.orekit.bodies.GeodeticPoint
import org.orekit.data.DataContext
import org.orekit.data.ZipJarCrawler
import org.orekit.frames.Frame
import org.orekit.frames.FramesFactory
import org.orekit.models.earth.ReferenceEllipsoid
import org.orekit.propagation.analytical.tle.TLE
import org.orekit.propagation.analytical.tle.TLEPropagator
import org.orekit.time.AbsoluteDate
import org.orekit.time.TimeScalesFactory
import org.orekit.utils.IERSConventions
import java.io.File

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class TLEEntry (
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
                    Column() {
                        ContentView(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Composable
fun ContentView(modifier: Modifier = Modifier) {
    val tleList = getTLEList()
    val names: List<String> = tleList.map { it.name }
    val itemList = names
    var selectedIndex by rememberSaveable { mutableStateOf(0) }

    var buttonModifier = modifier

    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.Center
    ) {
        DropdownList(itemList = itemList, selectedIndex = selectedIndex, modifier = buttonModifier, onItemClick = {selectedIndex = it})

        Text(
            text = "You have chosen ${itemList[selectedIndex]}",
            modifier = Modifier.padding(1.dp)
        )
        getTLEByName(
            name = itemList[selectedIndex],
            tleList = tleList
        )?.let {
            getCoordinates(
                tleData = it,
                modifier = Modifier
            )
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
fun getCoordinates(tleData: TLEEntry, modifier: Modifier = Modifier){
    val context = LocalContext.current
    val zipFile = File(context.filesDir, "orekit-data-main.zip")
    if (!zipFile.exists()) {
        context.assets.open("orekit-data-main.zip").use { input ->
            zipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    val manager = ZipJarCrawler(zipFile)
    DataContext.getDefault().dataProvidersManager.addProvider(manager)
    val name = tleData.name
    val line1 = tleData.line1
    val line2 = tleData.line2
    val tle = TLE(
        line1, line2
    )

    val propagator = TLEPropagator.selectExtrapolator(tle)
    val date = AbsoluteDate(2024, 4, 9, 12, 0, 0.0, TimeScalesFactory.getUTC())

    val itrf: Frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true)
    val pvInItrf = propagator.getPVCoordinates(date, itrf)

    val earth = ReferenceEllipsoid.getWgs84(itrf)

    val point: GeodeticPoint = earth.transform(pvInItrf.position, itrf, date)

    val latDeg = Math.toDegrees(point.latitude)
    val lonDeg = Math.toDegrees(point.longitude)
    val altMeters = point.altitude

    Text(
        text = "Name:      $name\nLatitude:  $latDeg\nLongitude: $lonDeg\nAltitude:  $altMeters",
        modifier = modifier
    )
    println("Name:      $name")
    println("Latitude:  $latDeg")
    println("Longitude: $lonDeg")
    println("Altitude:  $altMeters")
}

@Composable
fun DropdownList(itemList: List<String>, selectedIndex: Int, modifier: Modifier, onItemClick: (Int) -> Unit) {

    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = modifier
                .clickable { showDropdown = false }
            .clickable { showDropdown = !showDropdown },
            contentAlignment = Alignment.Center
        ) {
            Text(text = itemList[selectedIndex], modifier = Modifier.padding(3.dp))
        }

        Box() {
            if (showDropdown) {
                Popup(
                    alignment = Alignment.Center,
                    properties = PopupProperties(
                        excludeFromSystemGesture = true,
                    ),
                    onDismissRequest = { showDropdown = false }
                ) {

                    Column(
                        modifier = modifier
                            .heightIn(max = 90.dp)
                            .verticalScroll(state = scrollState)
                            .border(width = 1.dp, color = Color.Gray),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {

                        itemList.onEachIndexed { index, item ->
                            Box(
                                modifier = Modifier
                                    .background(color = MaterialTheme.colorScheme.background)
                                    .fillMaxWidth()
                                    .width(150.dp)
                                    .clickable {
                                        onItemClick(index)
                                        showDropdown = !showDropdown
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
}



