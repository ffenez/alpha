package app.alpha.ui.components

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

    /**
     * Настройки: λ — the decay constant, drawn in the same thin-line hand as
     * the tab icons. A gear says «механизм»; here the settings are about how
     * the instrument reads the world, and the letter is the field's own sign.
     *
     * Two strokes: the long one from the apex down to the lower right, and the
     * branch leaving it below the apex towards the lower left.
     */
    val Lambda: ImageVector by lazy {
        strokeIcon(
            "lambda",
            "M7.2 4.4 17 19.6",
            "M12.1 12 6.4 19.6",
        )
    }
}
