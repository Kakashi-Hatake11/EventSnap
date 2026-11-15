package com.appybits.eventsnap.ui.guest

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.repository.GuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GuestSelfieState {
    object Idle : GuestSelfieState()
    object Uploading : GuestSelfieState()
    object Success : GuestSelfieState()
    data class Error(val message: String) : GuestSelfieState()
}

@HiltViewModel
class GuestSelfieViewModel @Inject constructor(
    private val repository: GuestRepository
) : ViewModel() {

    private val _state = MutableStateFlow<GuestSelfieState>(GuestSelfieState.Idle)
    val state: StateFlow<GuestSelfieState> = _state
    
    fun uploadSelfie(eventId: String, guestId: String, photoUri: Uri) {
        viewModelScope.launch {
            _state.value = GuestSelfieState.Uploading
            try {
                repository.uploadSelfie(eventId, guestId, photoUri)
                _state.value = GuestSelfieState.Success
            } catch (e: Exception) {
                _state.value = GuestSelfieState.Error(e.message ?: "Upload failed")
            }
        }
    }
    
    fun resetState() {
        _state.value = GuestSelfieState.Idle
    }
}
