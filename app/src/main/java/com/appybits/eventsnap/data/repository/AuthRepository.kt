package com.appybits.eventsnap.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Studio usernames map to this email domain inside Firebase Auth (same convention as before). */
const val STUDIO_EMAIL_DOMAIN = "admin.eventsnap.com"

private const val STUDIO_ACCOUNTS = "studioAccounts"
private const val DEFAULT_WRONG_CREDENTIALS = "Incorrect username or password."

enum class LoginOutcome { SUCCESS, PASSWORD_CHANGE_REQUIRED }

/** Credentials were right, but the account isn't an active studio account. */
class StudioAccessException(message: String) : Exception(message)

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUser get() = auth.currentUser

    // Process-wide scope so background session work isn't cancelled when a screen closes.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Guests and photographers use Firestore as anonymous Firebase users, and the security rules
     * require a signed-in user. Call this whenever there might be no session (app start, after a
     * studio signs out). Does nothing if someone is already signed in.
     */
    suspend fun ensureSession() {
        if (auth.currentUser != null) return
        try {
            auth.signInAnonymously().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline or anonymous sign-in disabled; callers will see the real error on use.
        }
    }

    /**
     * Quick local check used at app start. This is only a convenience for choosing a screen;
     * real protection is enforced by Firestore/Storage rules via the "studio" custom claim.
     */
    val hasStudioSession: Boolean
        get() {
            val user = auth.currentUser ?: return false
            return !user.isAnonymous &&
                    (user.email?.endsWith("@$STUDIO_EMAIL_DOMAIN") == true ||
                            !user.phoneNumber.isNullOrEmpty())
        }

    /**
     * Signs in with the username your team issued.
     * The account must carry the server-set "studio" claim and an active plan, otherwise
     * the user is signed back out. If the account still has its temporary password, the
     * session is also closed and PASSWORD_CHANGE_REQUIRED is returned.
     */
    suspend fun login(username: String, password: String): Result<LoginOutcome> =
        safeCall<LoginOutcome> {
            auth.signInWithEmailAndPassword(usernameToEmail(username), password).await()
            try {
                val user = auth.currentUser ?: error("Sign in failed. Please try again.")
                val outcome = requireStudioAccess(user)
                if (outcome == LoginOutcome.PASSWORD_CHANGE_REQUIRED) {
                    auth.signOut()
                    ensureSession() // keep guest/photographer access working
                }
                outcome
            } catch (e: Exception) {
                auth.signOut() // never leave a half-checked session behind
                ensureSession()
                throw e
            }
        }

    /**
     * Changes the password.
     *  - First login (temporary password): pass [username]; this signs in, changes the
     *    password and leaves the user signed in.
     *  - Later, from settings: leave [username] null; the current session is re-authenticated.
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        username: String? = null
    ): Result<Unit> = safeCall<Unit>(wrongCredentials = "Current password is incorrect.") {
        val signedInHere = username != null
        val user: FirebaseUser = if (username != null) {
            auth.signInWithEmailAndPassword(usernameToEmail(username), currentPassword)
                .await().user ?: error("Sign in failed. Please try again.")
        } else {
            val existing = auth.currentUser ?: error("Please sign in again.")
            val email = existing.email ?: error("Please sign in again.")
            existing.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            existing
        }
        try {
            if (signedInHere) requireStudioAccess(user)
            user.updatePassword(newPassword).await()
            clearMustChangePassword(user.uid)
        } catch (e: Exception) {
            if (signedInHere) {
                auth.signOut()
                ensureSession()
            }
            throw e
        }
        Unit
    }

    fun logout() {
        auth.signOut()
        // Back to an anonymous session so the guest/photographer screens keep working.
        appScope.launch { ensureSession() }
    }

    suspend fun updateFcmToken(token: String) {
        val user = auth.currentUser ?: return
        try {
            firestore.collection("users")
                .document(user.uid)
                .set(
                    mapOf(
                        "fcmToken" to token,
                        "role" to if (user.isAnonymous) "guest" else "admin/photographer"
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignore for now
        }
    }

    // ------------------------------------------------------------------ internals

    private fun usernameToEmail(username: String) =
        "${username.trim().lowercase()}@$STUDIO_EMAIL_DOMAIN"

    private suspend fun requireStudioAccess(user: FirebaseUser): LoginOutcome {
        // Force-refresh so claims set by your team are picked up immediately.
        val token = user.getIdToken(true).await()
        if (token.claims["studio"] != true) {
            throw StudioAccessException(
                "This account isn't set up for studio access. Contact EventSnap support."
            )
        }

        val snap = firestore.collection(STUDIO_ACCOUNTS).document(user.uid).get().await()
        val expired = snap.getTimestamp("expiresAt")?.toDate()?.before(Date()) == true
        if (!snap.exists() || snap.getBoolean("active") != true || expired) {
            throw StudioAccessException(
                "Your studio plan isn't active. Contact EventSnap support."
            )
        }

        return if (snap.getBoolean("mustChangePassword") == true) {
            LoginOutcome.PASSWORD_CHANGE_REQUIRED
        } else {
            LoginOutcome.SUCCESS
        }
    }

    /** Best effort. If it fails, the person is simply asked to change the password again. */
    private suspend fun clearMustChangePassword(uid: String) {
        try {
            withTimeoutOrNull(10_000) {
                firestore.collection(STUDIO_ACCOUNTS).document(uid)
                    .update("mustChangePassword", false).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun <T> safeCall(
        wrongCredentials: String = DEFAULT_WRONG_CREDENTIALS,
        block: suspend () -> T
    ): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: StudioAccessException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception(e.toFriendlyMessage(wrongCredentials), e))
        }

    private fun Exception.toFriendlyMessage(wrongCredentials: String): String = when (this) {
        // WeakPassword is a subclass of InvalidCredentials, so it must come first.
        is FirebaseAuthWeakPasswordException ->
            "Password is too weak. Use at least 8 characters with letters and numbers."
        is FirebaseAuthInvalidUserException ->
            if (errorCode == "ERROR_USER_DISABLED") "This account has been disabled. Contact EventSnap support."
            else wrongCredentials // same text as a wrong password: no username probing
        is FirebaseAuthInvalidCredentialsException -> wrongCredentials
        is FirebaseTooManyRequestsException ->
            "Too many attempts. Wait a few minutes and try again."
        is FirebaseNetworkException ->
            "No internet connection. Check your network and try again."
        is FirebaseAuthException -> "Authentication failed. Please try again."
        else -> message ?: "Something went wrong. Please try again."
    }
}