package app.alpha.ui.screens

import app.alpha.ui.components.EntityMenuItem
import app.alpha.ui.text.ExportStrings
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.Strings

/**
 * Действия над записью — один источник на список и на экран.
 *
 * ## Зачем один источник
 *
 * У маршрута в журнале было своё меню, у спектра — свой диалог, у сессии —
 * кнопка рядом с заголовком. Одно и то же действие называлось по-разному и
 * стояло в разном порядке, а часть действий существовала только в одном из
 * мест. Здесь набор и порядок определяются один раз, а «⋮» строки журнала и
 * «⋮» экрана записи просто показывают его.
 *
 * ## Порядок постоянен
 *
 * Сначала то, что уносит запись наружу, потом то, что с ней делают, и в самом
 * конце — удаление: разрушающее действие не должно стоять там, куда палец
 * приходит по привычке.
 *
 * Действие, которого у записи сейчас нет, не исчезает из меню, а гаснет:
 * пропавший пункт заставляет искать, куда он делся, а погасший объясняет
 * состояние записи (сравнивать не с чем, пока снимок один).
 */
internal object EntityMenus {

    /** Снимок спектра: посмотреть его — это tap, а здесь всё остальное. */
    fun spectrum(
        strings: Strings,
        export: ExportStrings,
        history: HistoryStrings,
        canCompare: Boolean,
        onExport: () -> Unit,
        onCompare: () -> Unit,
        onContinue: () -> Unit,
        onRename: () -> Unit,
        onProfile: () -> Unit,
        onDelete: () -> Unit,
    ): List<EntityMenuItem> = listOf(
        EntityMenuItem(export.export, onClick = onExport),
        EntityMenuItem(strings.compareWithAnother, enabled = canCompare, onClick = onCompare),
        EntityMenuItem(strings.continueAccumulation, onClick = onContinue),
        EntityMenuItem(history.routeRename, onClick = onRename),
        // Профиль снимка правится задним числом: прибор снимал там, где стоял,
        // а человек может знать это лучше автоматики.
        EntityMenuItem(strings.profile, onClick = onProfile),
        EntityMenuItem(strings.delete, onClick = onDelete),
    )

    /** Сессия: имени у неё нет, зато есть профиль — им её и правят. */
    fun session(
        strings: Strings,
        export: ExportStrings,
        onExport: () -> Unit,
        onProfile: () -> Unit,
        onDelete: () -> Unit,
        /**
         * Идущую запись удалять нечем: измерения приходят в неё прямо сейчас.
         * Пункт гаснет, а не исчезает — иначе непонятно, куда он делся.
         */
        canDelete: Boolean = true,
    ): List<EntityMenuItem> = listOf(
        EntityMenuItem(export.export, onClick = onExport),
        EntityMenuItem(strings.profile, onClick = onProfile),
        EntityMenuItem(strings.delete, enabled = canDelete, onClick = onDelete),
    )

    fun route(
        strings: Strings,
        export: ExportStrings,
        history: HistoryStrings,
        canCompare: Boolean,
        onExport: () -> Unit,
        onCompare: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit,
    ): List<EntityMenuItem> = listOf(
        EntityMenuItem(export.export, onClick = onExport),
        EntityMenuItem(history.routeCompare, enabled = canCompare, onClick = onCompare),
        EntityMenuItem(history.routeRename, onClick = onRename),
        EntityMenuItem(strings.delete, onClick = onDelete),
    )

    /**
     * Исследование продукта.
     *
     * Переименование меняет только НАЗВАНИЕ: геометрия, время и измеренные
     * числа после старта не правятся — иначе результат перестал бы относиться
     * к тому, что измеряли.
     */
    fun study(
        strings: Strings,
        export: ExportStrings,
        history: HistoryStrings,
        onExport: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit,
    ): List<EntityMenuItem> = listOf(
        EntityMenuItem(export.export, onClick = onExport),
        EntityMenuItem(history.routeRename, onClick = onRename),
        EntityMenuItem(strings.delete, onClick = onDelete),
    )

    fun experiment(
        strings: Strings,
        export: ExportStrings,
        onExport: () -> Unit,
        onDelete: () -> Unit,
    ): List<EntityMenuItem> = listOf(
        EntityMenuItem(export.export, onClick = onExport),
        EntityMenuItem(strings.delete, onClick = onDelete),
    )
}
