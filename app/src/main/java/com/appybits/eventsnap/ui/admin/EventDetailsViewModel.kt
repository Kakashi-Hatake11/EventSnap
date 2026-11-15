package com.appybits.eventsnap.ui.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.data.model.Photographer
import com.appybits.eventsnap.data.repository.AuthRepository
import com.appybits.eventsnap.data.repository.EventAnalytics
import com.appybits.eventsnap.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EventDetailsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    private val _photographers = MutableStateFlow<List<Photographer>>(emptyList())
    val photographers: StateFlow<List<Photographer>> = _photographers.asStateFlow()

    private val _analytics = MutableStateFlow(EventAnalytics())
    val analytics: StateFlow<EventAnalytics> = _analytics.asStateFlow()

    fun loadEvent(eventId: String) {
        if (eventId == "new") {
            _event.value = Event()
            return
        }
        viewModelScope.launch {
            val result = eventRepository.getEvent(eventId)
            if (result.isSuccess) {
                _event.value = result.getOrNull()
                loadPhotographers(eventId)
                loadAnalytics(eventId)
            }
        }
    }

    private fun loadPhotographers(eventId: String) {
        viewModelScope.launch {
            eventRepository.getPhotographers(eventId)
                .catch { }
                .collect { list ->
                    _photographers.value = list
                }
        }
    }

    private fun loadAnalytics(eventId: String) {
        viewModelScope.launch {
            eventRepository.getAnalytics(eventId)
                .catch { }
                .collect { analytics ->
                    _analytics.value = analytics
                }
        }
    }

    fun saveEvent(customId: String, name: String, clientName: String, venue: String, date: Long, coverImageUri: Uri?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentEvent = _event.value ?: return
        val adminId = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                if (currentEvent.id.isEmpty()) {
                    if (customId.isBlank()) { onError("Event ID cannot be empty."); return@launch }
                    val newEvent = currentEvent.copy(
                        name = name, clientName = clientName, venue = venue, date = date, adminId = adminId,
                        qrCodeValue = UUID.randomUUID().toString(),
                        pin = (1000..9999).random().toString()
                    )
                    // Create the event first: the Storage rules look it up to check ownership.
                    val created = eventRepository.createEventWithId(customId, newEvent)
                    if (created.isFailure) {
                        onError(created.exceptionOrNull()?.message ?: "Failed to create event")
                        return@launch
                    }
                    if (coverImageUri != null) {
                        eventRepository.uploadEventCoverImage(customId, coverImageUri).getOrNull()?.let { url ->
                            eventRepository.updateEvent(newEvent.copy(id = customId, coverImageUrl = url))
                        }
                    }
                    onSuccess()
                } else {
                    var coverUrl = currentEvent.coverImageUrl
                    if (coverImageUri != null) {
                        eventRepository.uploadEventCoverImage(currentEvent.id, coverImageUri).getOrNull()?.let { coverUrl = it }
                    }
                    val result = eventRepository.updateEvent(currentEvent.copy(name = name, clientName = clientName, venue = venue, date = date, coverImageUrl = coverUrl))
                    if (result.isSuccess) onSuccess() else onError(result.exceptionOrNull()?.message ?: "Failed to update event")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "An error occurred")
            }
        }
    }

    fun updateSettings(allowDownload: Boolean, allowPrint: Boolean, pricePerPrint: Double, isActive: Boolean) {
        val currentEvent = _event.value ?: return
        if (currentEvent.id.isEmpty()) return
        
        val updatedEvent = currentEvent.copy(
            allowDownload = allowDownload,
            allowPrint = allowPrint,
            pricePerPrint = pricePerPrint,
            status = if (isActive) "ACTIVE" else "INACTIVE"
        )
        _event.value = updatedEvent
        
        viewModelScope.launch {
            eventRepository.updateEvent(updatedEvent)
        }
    }

    fun addPhotographer(username: String, onSuccess: () -> Unit) {
        val eventId = _event.value?.id ?: return
        if (eventId.isEmpty()) return

        val pin = (100000..999999).random().toString()
        val photographer = Photographer(username = username, pin = pin)

        viewModelScope.launch {
            val result = eventRepository.addPhotographer(eventId, photographer)
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }

    fun deleteEvent(eventId: String? = null, onSuccess: () -> Unit = {}) {
        val idToDelete = eventId ?: _event.value?.id ?: return
        if (idToDelete.isEmpty() || (idToDelete == "new")) return

        viewModelScope.launch {
            val result = eventRepository.deleteEvent(idToDelete)
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }
}
