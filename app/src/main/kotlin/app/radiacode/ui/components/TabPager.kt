package app.radiacode.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/**
 * Вкладки листаются пальцем.
 *
 * ## Почему пейджер, а не жест поверх смены экрана
 *
 * Экран, который начинает двигаться только после того, как палец отпущен,
 * ощущается сломанным: половина жеста не даёт отклика. Пейджер ведёт
 * содержимое за пальцем и умеет бросок; отпущенный на полпути жест
 * возвращается сам.
 *
 * ## Почему без параллакса и притемнения
 *
 * Они здесь были — и лагали. Причина не в самих слоях: положение страницы
 * читалось В СБОРКЕ, а значит каждый кадр жеста пересобирал вкладку целиком —
 * со всеми её карточками, графиками и чтением состояния. Красивый эффект,
 * который стоит пересборки Главной шестьдесят раз в секунду, дороже того, что
 * он сообщает; страница едет за пальцем и без него.
 *
 * Соседние вкладки не висят в памяти постоянно: пейджер держит только видимую
 * страницу и ту, к которой ведёт палец.
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
        // Соседняя вкладка собирается ЗАРАНЕЕ, а не под пальцем.
        //
        // Без этого первый кадр жеста строил целый экран — с чтением базы у
        // Главной и картой у Карты, — и рывок приходился ровно на начало
        // движения, когда он заметнее всего. Цена — одна лишняя собранная
        // вкладка в памяти; жест важнее.
        beyondViewportPageCount = 1,
    ) { page ->
        content(tabs[page])
    }
}

