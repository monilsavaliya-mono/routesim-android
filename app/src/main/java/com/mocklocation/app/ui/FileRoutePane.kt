package com.mocklocation.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocklocation.app.fileroute.FileRouteSummary
import com.mocklocation.app.ui.theme.Cockpit

/**
 * The FILE console tab (section: UI CHANGES). Empty state offers importing a file or
 * loading one of the bundled samples; once a file route is loaded, shows the metadata the
 * spec calls for — name, type, point count, distance, duration, start/end clock time —
 * exactly what the rest of the console already reads off `Route`/`SimulationState`, so
 * pressing DRIVE afterwards needs nothing file-route-specific at all.
 */
@Composable
fun FileRoutePane(
    summary: FileRouteSummary?,
    onImport: () -> Unit,
    onLoadSample: (assetPath: String, displayName: String) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("File route", trailing = summary?.type?.uppercase())
        Spacer(Modifier.height(10.dp))

        if (summary == null) {
            Text(
                text = "Replay a route from a JSON or CSV file of timestamped coordinates " +
                    "— a taxi trip, a train timetable, a walk, anything with time + " +
                    "latitude + longitude. The file's own timestamps drive playback.",
                color = Cockpit.InkFaint,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))

            ImportButton(text = "CHOOSE FILE (JSON / CSV)", onClick = onImport)

            Spacer(Modifier.height(14.dp))
            SectionHeader("Try a sample")
            Spacer(Modifier.height(8.dp))
            SampleRow("Train · Delhi → Jaipur", "sample_routes/sample_train.json", onLoadSample)
            Spacer(Modifier.height(6.dp))
            SampleRow("Taxi · city traffic", "sample_routes/sample_taxi.json", onLoadSample)
            Spacer(Modifier.height(6.dp))
            SampleRow("Walk · multi-stop", "sample_routes/sample_walk.json", onLoadSample)
            Spacer(Modifier.height(6.dp))
            SampleRow("CSV · minimal format", "sample_routes/sample_route.csv", onLoadSample)
        } else {
            Text(
                text = summary.name,
                color = Cockpit.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                ReadoutCell(
                    label = "Points",
                    value = "%,d".format(summary.pointCount),
                    modifier = Modifier.weight(1f),
                    valueSize = 15,
                )
                ReadoutCell(
                    label = "Distance",
                    value = formatDistance(summary.distanceMeters),
                    modifier = Modifier.weight(1f),
                    valueSize = 15,
                )
                ReadoutCell(
                    label = "Duration",
                    value = formatDuration(summary.durationSeconds),
                    modifier = Modifier.weight(1f),
                    valueSize = 15,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                ReadoutCell(
                    label = "Start",
                    value = formatClockTime(summary.startEpochMillis),
                    modifier = Modifier.weight(1f),
                    valueSize = 15,
                )
                ReadoutCell(
                    label = "End",
                    value = formatClockTime(summary.endEpochMillis),
                    modifier = Modifier.weight(1f),
                    valueSize = 15,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallOutlineButton("EXPORT JSON", onExportJson, Modifier.weight(1f))
                SmallOutlineButton("EXPORT CSV", onExportCsv, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            ImportButton(text = "CHOOSE A DIFFERENT FILE", onClick = onImport)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Press DRIVE below to start playback — pause, seek and stop work exactly as with any other route.",
                color = Cockpit.InkFaint,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun ImportButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Cockpit.Live.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Cockpit.Live.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = Cockpit.Live,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun SmallOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Cockpit.Hairline),
        modifier = modifier,
    ) {
        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Cockpit.InkMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SampleRow(
    title: String,
    assetPath: String,
    onLoadSample: (assetPath: String, displayName: String) -> Unit,
) {
    Surface(
        onClick = { onLoadSample(assetPath, title) },
        shape = RoundedCornerShape(11.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Cockpit.Hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("▶", color = Cockpit.InkMuted, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = Cockpit.Ink,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
