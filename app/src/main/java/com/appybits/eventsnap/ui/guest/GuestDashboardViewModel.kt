package com.appybits.eventsnap.ui.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.data.model.Photo
import com.appybits.eventsnap.data.repository.GuestRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuestDashboardState(
    val isLoading: Boolean = false,
    val event: Event? = null,
    val commonPhotos: List<Photo> = emptyList(),
    val myPhotos: List<Photo> = emptyList(),
    /** A problem such as no connection. The guest stays signed in and can retry. */
    val error: String? = null,
    /** The studio closed the event. Only this sends the guest back to the join screen. */
    val eventEnded: Boolean = false
)

@HiltViewModel
class GuestDashboardViewModel @Inject constructor(
    private val repository: GuestRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow(GuestDashboardState())
    val state: StateFlow<GuestDashboardState> = _state

    private var statusListener: ListenerRegistration? = null

    fun loadData(eventId: String, guestId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val event = repository.getEvent(eventId)
                if (event.status != "ACTIVE") {
                    _state.value = _state.value.copy(isLoading = false, eventEnded = true)
                    return@launch
                }

                val commonPhotos = repository.getCommonPhotos(eventId)
                val myPhotos = repository.getMyPhotos(eventId, guestId)

                _state.value = _state.value.copy(
                    isLoading = false,
                    event = event,
                    commonPhotos = commonPhotos,
                    myPhotos = myPhotos
                )
                listenForEventEnd(eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load your photos. Check your connection."
                )
            }
        }
    }

    /** Re-fetches photos without the full-screen spinner (new photos arrive during the event). */
    fun refreshPhotos(eventId: String, guestId: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    commonPhotos = repository.getCommonPhotos(eventId),
                    myPhotos = repository.getMyPhotos(eventId, guestId)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Couldn't refresh photos.")
            }
        }
    }

    // Realtime listener for event status. Replaces any earlier one so they never pile up.
    private fun listenForEventEnd(eventId: String) {
        statusListener?.remove()
        statusListener = firestore.collection("events").document(eventId)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("status")
                if (status != null && status != "ACTIVE") {
                    _state.value = _state.value.copy(eventEnded = true)
                }
            }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        statusListener?.remove()
        super.onCleared()
    }
}