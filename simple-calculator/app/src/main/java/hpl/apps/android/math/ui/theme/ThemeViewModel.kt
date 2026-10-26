package hpl.apps.android.math.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hpl.apps.android.math.CalculatorApplication
import hpl.apps.android.math.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * Holds the appearance preferences (theme mode and dynamic colour) so that the activity can
 * recreate the theme without forcing the user to restart the app.
 */
class ThemeViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = userPreferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { userPreferencesRepository.themeMode.first() }
    )

    val dynamicColor: StateFlow<Boolean> = userPreferencesRepository.dynamicColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { userPreferencesRepository.dynamicColor.first() }
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as CalculatorApplication)
                ThemeViewModel(application.userPreferencesRepository)
            }
        }
    }
}
