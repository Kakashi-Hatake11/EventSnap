package com.appybits.eventsnap.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.appybits.eventsnap.ui.components.InputField
import com.appybits.eventsnap.ui.theme.ErrorColor

/**
 * Asks for the current (or temporary) password plus a new one.
 *
 * First login:  ChangePasswordDialog(message = "...", onSubmit = { cur, new -> vm.completeFirstLogin(...) })
 * From settings: ChangePasswordDialog(onSubmit = { cur, new -> vm.changePassword(cur, new) }, onDismiss = { ... })
 */
@Composable
fun ChangePasswordDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    currentLabel: String = "Current password"
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val newError = if (newPassword.isNotEmpty()) passwordRuleError(newPassword) else null
    val sameError =
        if (newPassword.isNotEmpty() && newPassword == current) "Choose a different password" else null
    val confirmError =
        if (confirm.isNotEmpty() && confirm != newPassword) "Passwords don't match" else null

    val canSubmit = !isLoading && current.isNotEmpty() && newPassword.isNotEmpty() &&
            newError == null && sameError == null && confirm == newPassword

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Set a new password") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                InputField(
                    value = current,
                    onValueChange = { current = it },
                    label = currentLabel,
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(9.dp))
                InputField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New password",
                    isPassword = true
                )
                FieldError(newError ?: sameError)
                Spacer(modifier = Modifier.height(9.dp))
                InputField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm new password",
                    isPassword = true
                )
                FieldError(confirmError)

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = ErrorColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(current, newPassword) },
                enabled = canSubmit
            ) {
                Text(if (isLoading) "Updating…" else "Update password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}