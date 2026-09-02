package com.sanat.trading

import android.content.Context

object PinManager {

    private const val PREFS_NAME = "sanat_trading_security"
    private const val PIN_KEY = "app_pin"

    private const val DEFAULT_PIN = "123456"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPin(context: Context): String {
        return preferences(context).getString(PIN_KEY, DEFAULT_PIN)
            ?: DEFAULT_PIN
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        return pin == getPin(context)
    }

    fun setPin(context: Context, newPin: String) {
        preferences(context)
            .edit()
            .putString(PIN_KEY, newPin)
            .apply()
    }

    fun isValidPin(pin: String): Boolean {
        return pin.length == 6 && pin.all { it.isDigit() }
    }
}
