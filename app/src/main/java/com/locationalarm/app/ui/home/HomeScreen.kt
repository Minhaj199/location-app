package com.locationalarm.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationalarm.app.ui.theme.DeepNavy
import com.locationalarm.app.ui.theme.LocationAlarmTheme
import com.locationalarm.app.ui.theme.SecondaryText
import com.locationalarm.app.ui.theme.WarmAmber

/** Temporary phase-one screen; alarms are introduced with persistence in a later phase. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(text = "Location Alarm", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Arrive with confidence.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Card(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = WarmAmber.copy(alpha = 0.16f)),
                    border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.40f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {}
                Spacer(Modifier.height(20.dp))
                Text(text = "No location alarms yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Create an alarm for a place and we'll let you know when you arrive.",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepNavy,
                    contentColor = Color.White,
                ),
            ) {
                Text("Create location alarm")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LocationAlarmTheme { HomeScreen() }
}
