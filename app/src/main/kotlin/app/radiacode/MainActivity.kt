package app.radiacode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.radiacode.data.ThemeSetting
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import app.radiacode.ui.text.AppLanguage
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.stringsFor
import app.radiacode.ui.AppRoot
import app.radiacode.ui.theme.AppSkin
import app.radiacode.ui.theme.AppTheme

/** Single activity; all screens are Compose under [AppRoot]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val graph = AppGraph.get(this)
            val theme by graph.settings.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)
            val language by graph.settings.language.collectAsState(initial = AppLanguage.SYSTEM)
            val skin by graph.settings.skin.collectAsState(initial = AppSkin.TERMINAL)
            // Язык телефона читается один раз здесь: ниже по дереву его берут
            // из каталога, а не из системы, поэтому переключатель в настройках
            // работает мгновенно и без пересоздания активности.
            val systemTag = LocalConfiguration.current.locales[0]?.language.orEmpty()
            AppTheme(
                dark = when (theme) {
                    ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                    ThemeSetting.DARK -> true
                    ThemeSetting.LIGHT -> false
                },
                skin = skin,
            ) {
                CompositionLocalProvider(
                    LocalStrings provides stringsFor(AppLanguage.resolve(language, systemTag)),
                ) {
                    AppRoot(graph)
                }
            }
        }
    }
}
