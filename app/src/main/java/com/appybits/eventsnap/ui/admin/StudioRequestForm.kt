package com.appybits.eventsnap.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appybits.eventsnap.data.repository.StudioRequest
import com.appybits.eventsnap.ui.components.InputField
import com.appybits.eventsnap.ui.components.PrimaryButton
import java.util.Locale

private const val INDIA = "India"

/** Collects the details your team needs to contact a new studio. */
@Composable
fun StudioRequestForm(
    isLoading: Boolean,
    onSubmit: (StudioRequest) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var studioName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(deviceCountry()) }
    var state by rememberSaveable { mutableStateOf("") }

    val normalizedPhone = normalizePhone(phone, country)

    val nameError = if (name.isNotBlank() && !isValidName(name)) "Enter your full name" else null
    val phoneError = if (phone.isNotBlank() && !isValidPhone(normalizedPhone)) {
        if (country.equals(INDIA, ignoreCase = true)) "Enter a 10-digit mobile number"
        else "Include your country code, e.g. +1 415 555 0123"
    } else null
    val emailError = when {
        email.isBlank() -> null
        !isValidEmail(email) -> "Enter a valid email address"
        isDisposableEmail(email) -> "Temporary email addresses aren't allowed"
        else -> null
    }

    val canSubmit = !isLoading &&
            isValidName(name) &&
            isValidPhone(normalizedPhone) &&
            isValidEmail(email) && !isDisposableEmail(email) &&
            country.isNotBlank() && state.isNotBlank()

    Column {
        InputField(value = name, onValueChange = { name = it }, label = "Full name")
        FieldError(nameError)
        Spacer(modifier = Modifier.height(9.dp))

        InputField(
            value = studioName,
            onValueChange = { studioName = it },
            label = "Studio name (optional)"
        )
        Spacer(modifier = Modifier.height(9.dp))

        InputField(value = phone, onValueChange = { phone = it }, label = "Phone number")
        FieldError(phoneError)
        Spacer(modifier = Modifier.height(9.dp))

        InputField(value = email, onValueChange = { email = it }, label = "Email")
        FieldError(emailError)
        Spacer(modifier = Modifier.height(9.dp))

        DropdownField(
            label = "Country",
            value = country,
            options = COUNTRIES,
            onSelected = {
                if (it != country) state = ""
                country = it
            }
        )
        Spacer(modifier = Modifier.height(9.dp))

        if (country.equals(INDIA, ignoreCase = true)) {
            DropdownField(
                label = "State",
                value = state,
                options = INDIA_STATES,
                onSelected = { state = it }
            )
        } else {
            InputField(
                value = state,
                onValueChange = { state = it },
                label = "State / province / region"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Request account",
            onClick = {
                onSubmit(
                    StudioRequest(
                        name = name,
                        studioName = studioName,
                        phone = phone,
                        email = email,
                        country = country,
                        state = state
                    )
                )
            },
            enabled = canSubmit,
            isLoading = isLoading
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun deviceCountry(): String =
    Locale.getDefault().getDisplayCountry(Locale.ENGLISH).takeIf { it in COUNTRIES } ?: ""

// Built from the device's own ISO list, so it works offline and needs no extra library.
private val COUNTRIES: List<String> by lazy {
    Locale.getISOCountries()
        .map { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH) }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
}

private val INDIA_STATES = listOf(
    "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
    "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
    "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka",
    "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
    "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim",
    "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
)