package com.youme.memoria.settings


import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "general_settings")

object SettingsPrefs {
    val IndicatorStatus = booleanPreferencesKey("indicator_status")

    suspend fun setIndicatorStatus(context: Context, status: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IndicatorStatus] = status
        }
    }

    fun getIndicatorStatus(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[IndicatorStatus] ?: false
        }
    }
}