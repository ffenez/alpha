package app.radiacode.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    /**
     * Вкладки, у которых горизонтальный жест занят своим содержимым.
     *
     * Карту двигают пальцем во все стороны, и пейджер отбирал у неё каждое
     * движение вбок: карта дёргалась и уезжала на соседнюю вкладку. Там, где
     * жест уже что-то значит, листания вкладок нет — переключают их нижним
     * меню, как и раньше.
     */
    swipeDisabledOn: Set<AppTab> = emptySet(),
    content: @Composable (AppTab) -> Unit,
) {
    if (tabs.isEmpty()) return
    val startPage = tabs.indexOf(selected).coerceAtLeast(0)
    val state = rememberPagerState(initialPage = startPage) { tabs.size }

    // Нажатие в нижнем меню и жест — одно и то же перемещение, поэтому
    // выбранная вкладка и страница держатся друг за друга в обе стороны.
    //
    // Обе величины читаются ЧЕРЕЗ `rememberUpdatedState`: подписка живёт
    // дольше одной сборки, и захваченные ею `selected` с `onSelected`
    // остались бы теми, какими были в момент запуска, — из-за чего нижнее
    // меню после свайпа показывало прежнюю вкладку.
    val current = rememberUpdatedState(selected)
    val select = rememberUpdatedState(onSelected)
    // Пока идёт НАША анимация, страница проезжает через промежуточные —
    // и каждая из них сообщала бы о себе как о выбранной.
    //
    // Полевой дефект: нажатие «Карта» с Главной открывало Спектр. Анимация
    // 0 → 3 проходила через 2, промежуточная страница объявлялась выбранной,
    // это перенацеливало анимацию на неё же — и она там и останавливалась.
    var animating by remember { mutableStateOf(false) }
    LaunchedEffect(selected, tabs) {
        val index = tabs.indexOf(selected)
        if (index < 0 || index == state.currentPage) return@LaunchedEffect
        animating = true
        try {
            state.animateScrollToPage(index)
        } finally {
            animating = false
        }
    }
    LaunchedEffect(state, tabs) {
        // `settledPage`, а не `currentPage`: она меняется, когда страница
        // ОСТАНОВИЛАСЬ, и промежуточные кадры жеста ничего не переключают.
        snapshotFlow { state.settledPage }.collect { page ->
            if (animating) return@collect
            tabs.getOrNull(page)?.let { if (it != current.value) select.value(it) }
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = selected !in swipeDisabledOn,
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

