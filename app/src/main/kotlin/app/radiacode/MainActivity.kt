package app.radiacode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.radiacode.data.ThemeSetting
import app.radiacode.ui.AppRoot
import app.radiacode.ui.theme.AppTheme

/** Single activity; all screens are Compose under [AppRoot]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val graph = AppGraph.get(this)
            val theme by graph.settings.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)
            AppTheme(
                dark = when (theme) {
                    ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                    ThemeSetting.DARK -> true
                    ThemeSetting.LIGHT -> false
                },
            ) {
                AppRoot(graph)
            }
        }
    }
}
