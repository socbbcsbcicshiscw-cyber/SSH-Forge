package hpl.apps.android.math.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import hpl.apps.android.math.utils.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode


class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val PRECISION = intPreferencesKey("precision")
        private val HISTORY = stringPreferencesKey("history")
        private val DEGREE_MODE = booleanPreferencesKey("degree_mode")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val CONVERTER_DIMENSION = stringPreferencesKey("converter_dimension")
        private val CONVERTER_UNIT_1 = stringPreferencesKey("converter_unit_1")
        private val CONVERTER_UNIT_2 = stringPreferencesKey("converter_unit_2")

        private const val DEFAULT_PRECISION = 20
        const val MAX_PRECISION = 100
        const val MIN_PRECISION = 0

        const val MAX_HISTORY_ENTRIES = 100
        private const val HISTORY_SEPARATOR = "\n"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        val roundingMode = RoundingMode.HALF_UP

        private const val STEP = 100
        private const val MIN_INTERNAL_PRECISION = MAX_PRECISION+STEP
        const val MAX_INTERNAL_PRECISION = MIN_INTERNAL_PRECISION+9*STEP
        private const val MAX_INTERNAL_PRECISION_MINUS_STEP = MAX_INTERNAL_PRECISION-STEP

        private val MAX = BigDecimal.valueOf(1L, -STEP)
        private val MIN = BigDecimal.valueOf(-1L, -STEP)

        fun isInRange(value: BigDecimal): Boolean = value > MIN && value < MAX
        val MAX_FACTORIAL_INPUT: BigDecimal = BigDecimal.valueOf(69L, 0)

        fun getInternalPrecision(): Int = MIN_INTERNAL_PRECISION
        fun increaseInternalPrecision(currentInternalPrecision: Int): Int{
            return if(currentInternalPrecision <= MAX_INTERNAL_PRECISION_MINUS_STEP){
                currentInternalPrecision+STEP
            }else{MAX_INTERNAL_PRECISION}
        }

    }

    private val preferences: Flow<Preferences> = dataStore.data
        .catch {
            if (it is IOException) {
                log(it.message)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }

    val precision: Flow<Int> = preferences
        .map { it[PRECISION] ?: DEFAULT_PRECISION }

    val history: Flow<List<String>> = preferences
        .map { preferences ->
            preferences[HISTORY]
                ?.split(HISTORY_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    val degreeMode: Flow<Boolean> = preferences
        .map { it[DEGREE_MODE] ?: false }

    val themeMode: Flow<String> = preferences
        .map { it[THEME_MODE] ?: THEME_SYSTEM }

    val dynamicColor: Flow<Boolean> = preferences
        .map { it[DYNAMIC_COLOR] ?: true }

    /** Name of the dimension (enum name) selected in the converter, if any. */
    val converterDimension: Flow<String?> = preferences
        .map { it[CONVERTER_DIMENSION] }

    val converterUnit1: Flow<String?> = preferences
        .map { it[CONVERTER_UNIT_1] }

    val converterUnit2: Flow<String?> = preferences
        .map { it[CONVERTER_UNIT_2] }

    suspend fun savePrecisionPreference(precision: Int) {
        dataStore.edit { preferences ->
            preferences[PRECISION] = precision
        }
    }

    suspend fun saveHistory(entries: List<String>) {
        dataStore.edit { preferences ->
            preferences[HISTORY] = entries
                .takeLast(MAX_HISTORY_ENTRIES)
                .joinToString(HISTORY_SEPARATOR)
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { preferences ->
            preferences.remove(HISTORY)
        }
    }

    suspend fun saveDegreeMode(degreeMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[DEGREE_MODE] = degreeMode
        }
    }

    suspend fun saveThemeMode(themeMode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode
        }
    }

    suspend fun saveDynamicColor(dynamicColor: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = dynamicColor
        }
    }

    suspend fun saveConverterSelection(dimension: String, unit1: String, unit2: String) {
        dataStore.edit { preferences ->
            preferences[CONVERTER_DIMENSION] = dimension
            preferences[CONVERTER_UNIT_1] = unit1
            preferences[CONVERTER_UNIT_2] = unit2
        }
    }
}
