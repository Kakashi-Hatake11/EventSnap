package com.appybits.eventsnap.ui.guest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.repository.GuestRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GuestJoinState {
    object Idle : GuestJoinState()
    object Loading : GuestJoinState()
    data class Success(val eventId: String, val guestId: String) : GuestJoinState()
    data class Error(val message: String) : GuestJoinState()
}

@HiltViewModel
class GuestJoinViewModel @Inject constructor(
    private val repository: GuestRepository
) : ViewModel() {

    private val _state = MutableStateFlow<GuestJoinState>(GuestJoinState.Idle)
    val state: StateFlow<GuestJoinState> = _state

    fun joinByPin(pin: String) {
        if (pin.isBlank() || _state.value is GuestJoinState.Loading) return
        join { repository.joinEventByPin(pin) }
    }

    fun joinByQr(qrValue: String) {
        if (qrValue.isBlank() || _state.value is GuestJoinState.Loading) return
        join { repository.joinEventByQr(qrValue) }
    }

    private fun join(findEvent: suspend () -> com.appybits.eventsnap.data.model.Event) {
        viewModelScope.launch {
            _state.value = GuestJoinState.Loading
            try {
                val event = findEvent()
                val guestId = repository.createGuest(event.id)
                _state.value = GuestJoinState.Success(event.id, guestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The real cause shows up in Logcat under the tag "GuestJoin".
                Log.e("GuestJoin", "Join failed", e)
                _state.value = GuestJoinState.Error(friendlyMessage(e))
            }
        }
    }

    private fun friendlyMessage(e: Exception): String = when {
        e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Couldn't join this event right now. Please try again, or ask the studio for help."
        e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No internet connection. Check your network and try again."
        e is FirebaseNetworkException ->
            "No internet connection. Check your network and try again."
        else -> e.message ?: "Failed to join event."
    }

    fun resetState() {
        _state.value = GuestJoinState.Idle
    }
}