package com.appybits.eventsnap.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.data.repository.AuthRepository
import com.appybits.eventsnap.data.repository.EventRepository
import com.appybits.eventsnap.data.repository.StudioPlan
import com.appybits.eventsnap.data.repository.StudioPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
    private val planRepository: StudioPlanRepository
) : ViewModel() {

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _plan = MutableStateFlow<StudioPlan?>(null)
    val plan: StateFlow<StudioPlan?> = _plan.asStateFlow()

    /** False until the first answer about the plan arrives (a missing plan is also an answer). */
    private val _planLoaded = MutableStateFlow(false)
    val planLoaded: StateFlow<Boolean> = _planLoaded.asStateFlow()

    init {
        loadEvents()
        loadPlan()
    }

    private fun loadEvents() {
        val adminId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            eventRepository.getEvents(adminId)
                .catch { e ->
                    // Handle error
                }
                .collect { eventList ->
                    _events.value = eventList
                }
        }
    }

    private fun loadPlan() {
        viewModelScope.launch {
            planRepository.observePlan()
                .catch { _planLoaded.value = true }
                .collect { plan ->
                    _plan.value = plan
                    _planLoaded.value = true
                }
        }
    }

    fun logout() {
        authRepository.logout()
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = eventRepository.deleteEvent(eventId)
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }
}