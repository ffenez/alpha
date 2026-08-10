package app.radiacode.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin-line 24dp stroke icons (1.8 stroke, round caps/joins), path data from
 * the design mockup. Drawn white and tinted at the call site.
 */
private fun strokeIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (d in paths) {
        builder.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

object AppIcons {

    /** Главная: roof + walls. */
    val Home: ImageVector by lazy {
        strokeIcon(
            "home",
            "M3 11 12 4l9 7",
            "M5 10v9h14v-9",
        )
    }

    /** Поиск: magnifier. */
    val Search: ImageVector by lazy {
        strokeIcon(
            "search",
            "M11 5a6 6 0 1 1 0 12 6 6 0 1 1 0-12",
            "M20 20 15.8 15.8",
        )
    }

    /** Спектр: histogram columns. */
    val Spectrum: ImageVector by lazy {
        strokeIcon(
            "spectrum",
            "M4 19V9M9 19V5M14 19v-8M19 19v-4",
        )
    }

    /** Карта: location pin. */
    val Map: ImageVector by lazy {
        strokeIcon(
            "map",
            "M12 21s-6-5.1-6-10a6 6 0 0 1 12 0c0 4.9-6 10-6 10Z",
            "M12 8.8a2.2 2.2 0 1 1 0 4.4 2.2 2.2 0 1 1 0-4.4",
        )
    }

    /** История: clock face. */
    val History: ImageVector by lazy {
        strokeIcon(
            "history",
            "M12 4a8 8 0 1 1 0 16 8 8 0 1 1 0-16",
            "M12 8v4l2.5 2.5",
        )
    }

    /** Настройки: gear — hub + eight radial teeth. */
    val Gear: ImageVector by lazy {
        strokeIcon(
            "gear",
            "M12 8.9a3.1 3.1 0 1 1 0 6.2 3.1 3.1 0 1 1 0-6.2",
            "M12 3.8v2.4M12 17.8v2.4M3.8 12h2.4M17.8 12h2.4" +
                "M6.2 6.2l1.7 1.7M16.1 16.1l1.7 1.7M6.2 17.8l1.7-1.7M16.1 7.9l1.7-1.7",
        )
    }
}
