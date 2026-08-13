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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.radiacode.ui.text.AppLanguage
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.stringsFor
import app.radiacode.ui.AppRoot
import app.radiacode.ui.theme.AppSkin
import app.radiacode.ui.theme.AppTheme
import app.radiacode.ui.theme.UiScale

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
            val fontPercent by graph.settings.fontScalePercent
                .collectAsState(initial = UiScale.DEFAULT_PERCENT)
            val elementPercent by graph.settings.elementScalePercent
                .collectAsState(initial = UiScale.DEFAULT_PERCENT)
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
                // Масштаб прикладывается ОДИН раз на всё приложение: экраны о
                // нём не знают, иначе про него пришлось бы помнить в каждой
                // новой карточке. Подробности развязки двух ползунков — в
                // KDoc [UiScale].
                val system = LocalDensity.current
                CompositionLocalProvider(
                    LocalStrings provides stringsFor(AppLanguage.resolve(language, systemTag)),
                    LocalDensity provides Density(
                        density = UiScale.density(system.density, elementPercent),
                        fontScale = UiScale.fontScale(
                            systemFontScale = system.fontScale,
                            fontPercent = fontPercent,
                            elementPercent = elementPercent,
                        ),
                    ),
                ) {
                    AppRoot(graph)
                }
            }
        }
    }
}
