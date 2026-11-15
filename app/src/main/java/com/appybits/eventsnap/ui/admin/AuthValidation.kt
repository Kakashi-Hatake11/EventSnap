package com.appybits.eventsnap.ui.admin

const val MIN_PASSWORD_LENGTH = 8

// Requires a real-looking domain with a 2+ letter TLD (rejects "a@b", "x@y..com", etc.).
private val EMAIL_REGEX = Regex(
    "^[A-Za-z0-9](?:[A-Za-z0-9._%+\\-]{0,62}[A-Za-z0-9])?" +
            "@(?:[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}$"
)

// E.164: "+" then 8-15 digits, first digit non-zero.
private val PHONE_REGEX = Regex("^\\+[1-9][0-9]{7,14}$")

// Speed bump for the request form only; your team still vets every request by phone.
private val DISPOSABLE_DOMAINS = setOf(
    "mailinator.com", "guerrillamail.com", "guerrillamail.net", "sharklasers.com",
    "10minutemail.com", "tempmail.com", "temp-mail.org", "yopmail.com",
    "trashmail.com", "getnada.com", "dispostable.com", "maildrop.cc",
    "throwawaymail.com", "fakeinbox.com", "mailnesia.com", "mintemail.com"
)

fun isValidEmail(email: String): Boolean {
    val e = email.trim()
    return e.length <= 254 && !e.contains("..") && EMAIL_REGEX.matches(e)
}

fun isDisposableEmail(email: String): Boolean {
    val domain = email.trim().substringAfterLast('@', "").lowercase()
    return domain in DISPOSABLE_DOMAINS
}

fun isValidName(name: String): Boolean = name.trim().length in 2..100

/**
 * Cleans up a typed phone number into "+<digits>" form.
 * Indian 10-digit numbers get +91 added automatically; other countries must include "+code".
 */
fun normalizePhone(raw: String, country: String): String {
    val cleaned = raw.trim().replace(Regex("[\\s\\-().]"), "")
    return when {
        cleaned.startsWith("+") -> cleaned
        cleaned.startsWith("00") -> "+" + cleaned.drop(2)
        country.equals("India", ignoreCase = true) && cleaned.length == 10 -> "+91$cleaned"
        else -> cleaned
    }
}

fun isValidPhone(normalized: String): Boolean = PHONE_REGEX.matches(normalized)

/** Light check for the sign-in form. Returns an error message or null. */
fun usernameFormatError(username: String): String? = when {
    username.contains('@') -> "Enter your username, not an email address"
    username.any { it.isWhitespace() } -> "Usernames can't contain spaces"
    username.trim().length > 64 -> "That username is too long"
    else -> null
}

/** Returns an error message, or null if the password is acceptable for a NEW password. */
fun passwordRuleError(password: String): String? = when {
    password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
    password.none { it.isLetter() } || password.none { it.isDigit() } ->
        "Include at least one letter and one number"
    else -> null
}