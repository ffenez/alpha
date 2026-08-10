package app.radiacode.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * Pixel-art icons: 12x12 cell grids compiled into ImageVectors where every
 * filled cell is a 1x1 rect. Drawn white and tinted at the call site.
 */
private fun pixelIcon(name: String, rows: List<String>): ImageVector {
    val height = rows.size.toFloat()
    val width = rows.maxOf { it.length }.toFloat()
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = width,
        viewportHeight = height,
    ).addPath(
        pathData = PathData {
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, cell ->
                    if (cell == '#') {
                        moveTo(x.toFloat(), y.toFloat())
                        horizontalLineToRelative(1f)
                        verticalLineToRelative(1f)
                        horizontalLineToRelative(-1f)
                        close()
                    }
                }
            }
        },
        fill = SolidColor(Color.White),
    ).build()
}

object PixelIcons {

    /** Дом: roof + walls + door. */
    val Home: ImageVector by lazy {
        pixelIcon(
            "pixel_home",
            listOf(
                ".....##.....",
                "....####....",
                "...##..##...",
                "..##....##..",
                ".##......##.",
                "##........##",
                "##........##",
                ".##......##.",
                ".##..##..##.",
                ".##..##..##.",
                ".##..##..##.",
                ".##########.",
            ),
        )
    }

    /** Поиск: magnifier with a chunky diagonal handle. */
    val Search: ImageVector by lazy {
        pixelIcon(
            "pixel_search",
            listOf(
                "..#####.....",
                ".##...##....",
                "##.....##...",
                "##.....##...",
                "##.....##...",
                "##.....##...",
                ".##...##....",
                "..#####.....",
                ".....###....",
                "......###...",
                ".......###..",
                "........##..",
            ),
        )
    }

    /** Спектр: columns with one tall peak on a baseline. */
    val Spectrum: ImageVector by lazy {
        pixelIcon(
            "pixel_spectrum",
            listOf(
                "............",
                ".....##.....",
                ".....##.....",
                "....####....",
                "....####....",
                ".##.####....",
                ".##.####.##.",
                ".##.####.##.",
                ".##.####.##.",
                "############",
                "............",
                "............",
            ),
        )
    }

    /** Карта: location pin. */
    val Map: ImageVector by lazy {
        pixelIcon(
            "pixel_map",
            listOf(
                "...######...",
                "..##....##..",
                ".##......##.",
                ".##......##.",
                ".##..##..##.",
                ".##..##..##.",
                ".##......##.",
                "..##....##..",
                "..##....##..",
                "...##..##...",
                "....####....",
                ".....##.....",
            ),
        )
    }

    /** Настройки: chunky gear, four teeth and a hollow center. */
    val Gear: ImageVector by lazy {
        pixelIcon(
            "pixel_gear",
            listOf(
                ".....##.....",
                "..#..##..#..",
                ".##########.",
                ".##########.",
                "..##....##..",
                "####....####",
                "####....####",
                "..##....##..",
                ".##########.",
                ".##########.",
                "..#..##..#..",
                ".....##.....",
            ),
        )
    }

    /** История: clock face, hands at ten past ten-ish. */
    val History: ImageVector by lazy {
        pixelIcon(
            "pixel_history",
            listOf(
                "...######...",
                "..##....##..",
                ".##......##.",
                "##....#...##",
                "##....#...##",
                "##....#...##",
                "##....###.##",
                "##........##",
                ".##......##.",
                "..##....##..",
                "...######...",
                "............",
            ),
        )
    }
}
