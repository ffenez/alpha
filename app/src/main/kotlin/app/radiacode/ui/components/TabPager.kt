package app.radiacode.ui.components

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import app.radiacode.ui.theme.LocalAppColors
import kotlin.math.absoluteValue

/**
 * Вкладки листаются пальцем.
 *
 * ## Что здесь за движение
 *
 * Соседняя вкладка приезжает МЕДЛЕННЕЕ пальца и чуть уменьшенной, а уходящая
 * притемняется. Это не украшение: разная скорость слоёв — единственный
 * способ показать, что вкладки лежат рядом, а не подменяют друг друга, и
 * именно поэтому жест ощущается как перемещение, а не как перелистывание
 * картинок. Числа внутри при этом не анимируются ничем — движется КАДР, а не
 * данные (`ui/theme/Motion.kt`).
 *
 * ## Почему пейджер, а не жест поверх смены экрана
 *
 * Экран, который начинает двигаться только после того, как палец отпущен,
 * ощущается сломанным: половина жеста не даёт отклика. Пейджер ведёт
 * содержимое за пальцем и умеет бросок; отпущенный на полпути жест
 * возвращается сам.
 *
 * Соседние вкладки при этом НЕ висят в памяти постоянно: пейджер держит
 * только видимую страницу и ту, к которой ведёт палец.
 */
@Composable
fun TabPager(
    tabs: List<AppTab>,
    selected: AppTab,
    onSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (AppTab) -> Unit,
) {
    if (tabs.isEmpty()) return
    val colors = LocalAppColors.current
    val startPage = tabs.indexOf(selected).coerceAtLeast(0)
    val state = rememberPagerState(initialPage = startPage) { tabs.size }

    // Нажатие в нижнем меню и жест — одно и то же перемещение, поэтому
    // выбранная вкладка и страница держатся друг за друга в обе стороны.
    LaunchedEffect(selected, tabs) {
        val index = tabs.indexOf(selected)
        if (index >= 0 && index != state.currentPage) state.animateScrollToPage(index)
    }
    LaunchedEffect(state, tabs) {
        snapshotFlow { state.settledPage }.collect { page ->
            tabs.getOrNull(page)?.let { if (it != selected) onSelected(it) }
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier.fillMaxSize(),
        snapPosition = SnapPosition.Start,
        // Соседняя страница не собирается заранее: у Главной и Карты за
        // сборкой стоит чтение базы и карта, и держать их «на всякий случай»
        // значило бы платить за них всё время.
        beyondViewportPageCount = 0,
    ) { page ->
        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
        val distance = offset.absoluteValue.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Содержимое отстаёт от страницы на четверть хода —
                    // параллакс, из-за которого вкладки читаются как соседние.
                    translationX = size.width * offset * PARALLAX
                    val scale = 1f - distance * (1f - MIN_SCALE)
                    scaleX = scale
                    scaleY = scale
                }
                // Уходящая вкладка притемняется фоном, а не прозрачностью:
                // прозрачная страница просвечивала бы соседнюю насквозь.
                .drawWithContent {
                    drawContent()
                    if (distance > 0f) {
                        drawRect(colors.bg.copy(alpha = distance * DIM))
                    }
                },
        ) {
            content(tabs[page])
        }
    }
}

/** Насколько содержимое отстаёт от хода страницы. */
private const val PARALLAX = 0.25f

/** До какого масштаба ужимается страница, полностью ушедшая за край. */
private const val MIN_SCALE = 0.92f

/** Насколько притемняется уходящая страница. */
private const val DIM = 0.45f
