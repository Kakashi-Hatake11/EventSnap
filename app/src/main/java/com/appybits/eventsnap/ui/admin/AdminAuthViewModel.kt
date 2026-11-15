package com.appybits.eventsnap.ui.admin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.repository.AuthRepository
import com.appybits.eventsnap.data.repository.LoginOutcome
import com.appybits.eventsnap.data.repository.StudioRequest
import com.appybits.eventsnap.data.repository.StudioRequestRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AdminAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studioRequestRepository: StudioRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Idle)
    val uiState: StateFlow<AuthState> = _uiState.asStateFlow()

    private var verificationId: String? = null

    /** True only for a real (non-guest) studio session. */
    fun isUserLoggedIn(): Boolean = authRepository.hasStudioSession

    /** Makes sure someone (an anonymous guest, if nobody else) is signed in before screens query Firestore. */
    suspend fun ensureSession() = authRepository.ensureSession()

    /** Saves this device's push token to the signed-in user's record. */
    fun saveFcmToken(token: String) {
        viewModelScope.launch { authRepository.updateFcmToken(token) }
    }

    // ---------------------------------------------------------------- Username sign in

    fun login(username: String, pass: String) {
        if (_uiState.value is AuthState.Loading) return
        if (username.isBlank()) return fail("Enter your username.")
        usernameFormatError(username)?.let { return fail(it) }
        if (pass.isEmpty()) return fail("Enter your password.")

        _uiState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.login(username.trim(), pass).fold(
                onSuccess = { outcome ->
                    _uiState.value = when (outcome) {
                        LoginOutcome.SUCCESS -> AuthState.Success
                        LoginOutcome.PASSWORD_CHANGE_REQUIRED -> AuthState.PasswordChangeRequired
                    }
                },
                onFailure = { fail(it.message) }
            )
        }
    }

    // ---------------------------------------------------------------- Password change

    /** First login: replaces the temporary password your team issued, then signs in. */
    fun completeFirstLogin(username: String, tempPassword: String, newPassword: String) {
        if (_uiState.value is AuthState.Loading) return
        validateNewPassword(tempPassword, newPassword)?.let { return fail(it) }

        _uiState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.changePassword(tempPassword, newPassword, username.trim()).fold(
                onSuccess = { _uiState.value = AuthState.Success },
                onFailure = { fail(it.message) }
            )
        }
    }

    /** From a settings screen while already signed in. */
    fun changePassword(currentPassword: String, newPassword: String) {
        if (_uiState.value is AuthState.Loading) return
        validateNewPassword(currentPassword, newPassword)?.let { return fail(it) }

        _uiState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.changePassword(currentPassword, newPassword).fold(
                onSuccess = { _uiState.value = AuthState.PasswordChanged },
                onFailure = { fail(it.message) }
            )
        }
    }

    private fun validateNewPassword(current: String, new: String): String? = when {
        current.isEmpty() -> "Enter your current password."
        passwordRuleError(new) != null -> passwordRuleError(new) + "."
        new == current -> "Your new password must be different from the current one."
        else -> null
    }

    // ---------------------------------------------------------------- Request an account

    fun submitRequest(request: StudioRequest) {
        if (_uiState.value is AuthState.Loading) return

        val phone = normalizePhone(request.phone, request.country)
        when {
            !isValidName(request.name) -> return fail("Enter your full name.")
            !isValidPhone(phone) ->
                return fail("Enter a valid phone number with country code.")
            !isValidEmail(request.email) -> return fail("Enter a valid email address.")
            isDisposableEmail(request.email) ->
                return fail("Temporary email addresses aren't allowed.")
            request.country.isBlank() || request.state.isBlank() ->
                return fail("Select your country and state.")
        }

        val clean = request.copy(
            name = request.name.trim(),
            studioName = request.studioName.trim(),
            phone = phone,
            email = request.email.trim().lowercase(),
            country = request.country.trim(),
            state = request.state.trim()
        )

        _uiState.value = AuthState.Loading
        viewModelScope.launch {
            studioRequestRepository.submit(clean).fold(
                onSuccess = { _uiState.value = AuthState.RequestSubmitted },
                onFailure = { fail(it.message) }
            )
        }
    }

    // ---------------------------------------------------------------- Phone OTP (unchanged)

    fun sendOtp(phoneNumber: String, activity: Activity) {
        _uiState.value = AuthState.Loading
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    try {
                        FirebaseAuth.getInstance().signInWithCredential(credential).await()
                        _uiState.value = AuthState.OtpVerified
                    } catch (e: Exception) {
                        _uiState.value = AuthState.Error(e.message ?: "Verification failed")
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = AuthState.Error(e.message ?: "Verification failed")
            }

            override fun onCodeSent(verId: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = verId
                _uiState.value = AuthState.OtpSent
            }
        }

        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(code: String) {
        val vId = verificationId ?: return fail("Request a new code and try again.")
        _uiState.value = AuthState.Loading
        val credential = PhoneAuthProvider.getCredential(vId, code)
        viewModelScope.launch {
            try {
                FirebaseAuth.getInstance().signInWithCredential(credential).await()
                _uiState.value = AuthState.OtpVerified
            } catch (e: Exception) {
                _uiState.value = AuthState.Error(e.message ?: "Invalid OTP")
            }
        }
    }

    // ---------------------------------------------------------------- Helpers

    fun resetState() {
        _uiState.value = AuthState.Idle
    }

    private fun fail(message: String?) {
        _uiState.value = AuthState.Error(message ?: "Something went wrong. Please try again.")
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object PasswordChangeRequired : AuthState()
    object PasswordChanged : AuthState()
    object RequestSubmitted : AuthState()
    object OtpSent : AuthState()
    object OtpVerified : AuthState()
    data class Error(val message: String) : AuthState()
}