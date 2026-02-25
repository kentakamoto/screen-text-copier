package com.kentakamoto.screentextcopier.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class CopyMode { CLIPBOARD, SHARE }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "screen_text_copier_prefs"
)

object AppPreferences {

    private val KEY_COPY_MODE = stringPreferencesKey("copy_mode")
    private val KEY_BUTTON_OPACITY = floatPreferencesKey("button_opacity")
    private val KEY_BUTTON_SIZE_DP = intPreferencesKey("button_size_dp")
    private val KEY_BUTTON_POS_X = intPreferencesKey("button_pos_x")
    private val KEY_BUTTON_POS_Y = intPreferencesKey("button_pos_y")

    const val DEFAULT_OPACITY = 0.8f
    const val DEFAULT_SIZE_DP = 56

    // --- CopyMode ---
    fun copyModeFlow(context: Context): Flow<CopyMode> =
        context.dataStore.data.map { prefs ->
            val name = prefs[KEY_COPY_MODE]
            CopyMode.entries.find { it.name == name } ?: CopyMode.CLIPBOARD
        }

    suspend fun getCopyModeOnce(context: Context): CopyMode =
        copyModeFlow(context).first()

    suspend fun saveCopyMode(context: Context, mode: CopyMode) {
        context.dataStore.edit { prefs -> prefs[KEY_COPY_MODE] = mode.name }
    }

    // --- Button Opacity ---
    fun buttonOpacityFlow(context: Context): Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_BUTTON_OPACITY] ?: DEFAULT_OPACITY
        }

    suspend fun getButtonOpacityOnce(context: Context): Float =
        buttonOpacityFlow(context).first()

    suspend fun saveButtonOpacity(context: Context, opacity: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUTTON_OPACITY] = opacity.coerceIn(0.2f, 1.0f)
        }
    }

    // --- Button Size ---
    fun buttonSizeFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_BUTTON_SIZE_DP] ?: DEFAULT_SIZE_DP
        }

    suspend fun getButtonSizeOnce(context: Context): Int =
        buttonSizeFlow(context).first()

    suspend fun saveButtonSize(context: Context, sizeDp: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUTTON_SIZE_DP] = sizeDp.coerceIn(40, 80)
        }
    }

    // --- Button Position ---
    suspend fun saveButtonPosition(context: Context, x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUTTON_POS_X] = x
            prefs[KEY_BUTTON_POS_Y] = y
        }
    }

    suspend fun getButtonPositionOnce(context: Context): Pair<Int, Int> {
        val prefs = context.dataStore.data.first()
        return Pair(prefs[KEY_BUTTON_POS_X] ?: 0, prefs[KEY_BUTTON_POS_Y] ?: 300)
    }
}
