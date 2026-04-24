package com.arflix.tv.util

/**
 * Simple PIN validation utility for profile locking (4-5 digits)
 */
object PinUtil {
    private const val MIN_LENGTH = 4
    private const val MAX_LENGTH = 5

    fun isValidPin(pin: String): Boolean {
        return pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }
    }

    fun formatPinInput(input: String): String {
        return input.filter { it.isDigit() }.take(MAX_LENGTH)
    }

    fun verifyPin(inputPin: String, storedPin: String?): Boolean {
        return storedPin != null && inputPin == storedPin
    }
}
