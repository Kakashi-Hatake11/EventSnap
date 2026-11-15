package com.appybits.eventsnap.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.data.repository.EventAnalytics
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme

@Composable
fun EventAnalyticsScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventDetailsViewModel = hiltViewModel()
) {
    val analytics by viewModel.analytics.collectAsState()
    val event by viewModel.event.collectAsState()
    
    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    EventAnalyticsContent(analytics = analytics, eventName = event?.name ?: "", onBack = onBack)
}

@Composable
fun EventAnalyticsContent(
    analytics: EventAnalytics,
    eventName: String,
    onBack: () -> Unit
) {
    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = { EventSnapTopBar(title = "Analytics", subtitle = eventName, onBack = onBack) },
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatCard(number = analytics.photoCount.toString(), caption = "Photos", modifier = Modifier.weight(1f))
                        StatCard(number = analytics.guestCount.toString(), caption = "Guests joined", modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatCard(number = analytics.printRequestsCount.toString(), caption = "Print requests", modifier = Modifier.weight(1f))
                        StatCard(number = "₹ ${(analytics.printRequestsCount * 25)}", caption = "Print revenue", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EventAnalyticsScreenPreview() {
    EventAnalyticsContent(
        analytics = EventAnalytics(photoCount = 482, guestCount = 67, printRequestsCount = 34),
        eventName = "Anna's Wedding",
        onBack = {}
    )
}
