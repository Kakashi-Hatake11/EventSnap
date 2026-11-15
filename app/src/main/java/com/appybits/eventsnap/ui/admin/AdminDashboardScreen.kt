package com.appybits.eventsnap.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.data.repository.StudioPlan
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onCreateEvent: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val planLoaded by viewModel.planLoaded.collectAsState()
    AdminDashboardContent(
        events = events,
        onLogout = {
            viewModel.logout()
            onLogout()
        },
        onCreateEvent = onCreateEvent,
        onEventClick = onEventClick,
        plan = plan,
        planLoaded = planLoaded
    )
}

@Composable
fun AdminDashboardContent(
    events: List<Event>,
    onLogout: () -> Unit,
    onCreateEvent: () -> Unit,
    onEventClick: (String) -> Unit,
    plan: StudioPlan? = null,
    planLoaded: Boolean = true
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    EventSnapTheme {
        EventSnapBackground {
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Logout") },
                    text = { Text("Are you sure you want to logout?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showLogoutDialog = false
                            onLogout()
                        }) { Text("Yes", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) { Text("No") }
                    }
                )
            }

            Scaffold(
                topBar = {
                    EventSnapTopBar(
                        title = "Your events",
                        subtitle = "${events.filter { it.status == "ACTIVE" }.size} active",
                        actions = {
                            TextButton(onClick = { showLogoutDialog = true }) {
                                Text("Logout", color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                        // No prices or purchase links here on purpose: the app only reports usage.
                        val blockedReason = when {
                            plan != null -> plan.createBlockedReason
                            planLoaded -> "Your plan isn't set up yet. Contact support."
                            else -> null
                        }
                        if (blockedReason != null) {
                            Text(
                                text = blockedReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        PrimaryButton(
                            text = "Create new event",
                            onClick = onCreateEvent,
                            enabled = plan?.canCreateEvent == true
                        )
                    }
                },
                containerColor = Color.Transparent
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (plan != null) {
                        PlanUsageCard(
                            plan = plan,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
                        )
                    }

                    if (events.isEmpty()) {
                        EmptyState(
                            title = "No events found.",
                            subtitle = "Create your first event to get started."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(events, key = { it.id }) { event ->
                                val dateString = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(event.date))
                                ListTile(
                                    title = event.name,
                                    subtitle = "$dateString · Event ID: ${event.id.take(4)}...",
                                    statusText = event.status,
                                    onClick = { onEventClick(event.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanUsageCard(plan: StudioPlan, modifier: Modifier = Modifier) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val statusText = when {
        !plan.active -> "Inactive"
        plan.isExpired -> "Expired"
        else -> "Active"
    }
    val statusIsProblem = !plan.active || plan.isExpired

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(plan.planName, style = MaterialTheme.typography.titleMedium)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsProblem) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            if (plan.expiresAtMillis > 0) {
                Text(
                    "Valid until ${dateFormat.format(Date(plan.expiresAtMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (plan.unlimitedEvents) {
                UsageRow(label = "Events", value = "${plan.eventsUsed} created", fraction = null)
            } else {
                UsageRow(
                    label = "Events",
                    value = "${plan.eventsUsed} of ${plan.eventsAllowed}",
                    fraction = plan.eventsFraction
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            UsageRow(
                label = "Storage",
                value = "${formatGb(plan.storageUsedBytes)} of ${formatGb(plan.storageLimitBytes)}",
                fraction = plan.storageFraction
            )

            if (plan.storageFraction >= 0.8f) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Storage is almost full. Contact support to add more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String, fraction: Float?) {
    val warning = Color(0xFFE6A23C)
    val barColor = when {
        fraction == null -> MaterialTheme.colorScheme.primary
        fraction >= 0.95f -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> warning
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (fraction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

private fun formatGb(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)

@Preview(showBackground = true)
@Composable
fun AdminDashboardScreenPreview() {
    AdminDashboardContent(
        events = listOf(
            Event(id = "1", name = "Anna's Wedding", date = System.currentTimeMillis(), status = "ACTIVE"),
            Event(id = "2", name = "Corporate Gala", date = System.currentTimeMillis() + 86400000L, status = "ARCHIVED")
        ),
        onLogout = {},
        onCreateEvent = {},
        onEventClick = {},
        plan = StudioPlan(
            planName = "Event pack",
            eventsAllowed = 3,
            eventsUsed = 2,
            storageLimitBytes = 50L * 1_073_741_824,
            storageUsedBytes = 42L * 1_073_741_824,
            expiresAtMillis = System.currentTimeMillis() + 90L * 86_400_000,
            active = true
        )
    )
}