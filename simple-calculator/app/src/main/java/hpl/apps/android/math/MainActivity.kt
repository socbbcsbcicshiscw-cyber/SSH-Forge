package hpl.apps.android.math

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import hpl.apps.android.math.ui.theme.CalculatorTheme
import hpl.apps.android.math.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)
            val themeMode by themeViewModel.themeMode.collectAsState()
            val dynamicColor by themeViewModel.dynamicColor.collectAsState()

            CalculatorTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                CalculatorApp()
            }
        }
    }
}
