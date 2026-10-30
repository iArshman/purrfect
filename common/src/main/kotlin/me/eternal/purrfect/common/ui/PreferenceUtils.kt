package me.eternal.purrfect.common.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit

@Composable
fun rememberMutableBooleanPreferenceState(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    val state = remember(key) {
        mutableStateOf(sharedPreferences.getBoolean(key, defaultValue))
    }

    return remember(key) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    sharedPreferences.edit { putBoolean(key, newValue) }
                }

            override fun component1(): Boolean = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberMutableStringPreferenceState(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: String
): MutableState<String> {
    val state = remember(key) {
        mutableStateOf(sharedPreferences.getString(key, defaultValue) ?: defaultValue)
    }

    return remember(key) {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    sharedPreferences.edit { putString(key, newValue) }
                }

            override fun component1(): String = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}
