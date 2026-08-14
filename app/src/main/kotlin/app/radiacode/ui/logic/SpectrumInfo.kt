package app.radiacode.ui.logic

import app.radiacode.ui.text.SpectrumRu
import app.radiacode.ui.text.SpectrumStrings

/**
 * Глубина ответа — правило всего приложения: сначала ЧТО это значит, потом
 * ПОЧЕМУ приложение так решило, и только потом КАК посчитано.
 *
 * Уровни не смешиваются в одном разделе: человек, открывший справку с вопросом
 * «что это за горб», не обязан читать про стандартную неопределённость
 * нетто-площади, чтобы добраться до ответа.
 */
enum class SpectrumInfoLevel {
    /** Что нарисовано и что это значит. */
    WHAT,

    /** Почему картинка выглядит так и почему вывод именно такой. */
    WHY,

    /** Как это посчитано и чем измерено — диагностика. */
    HOW,
}

data class SpectrumInfoSection(
    val level: SpectrumInfoLevel,
    val title: String,
    val lines: List<String>,
)

/**
 * Справка «Как читать спектр», разложенная по вопросам.
 *
 * Чистая сборка: состав зависит только от того, что реально есть на экране
 * (курсор в полноэкранном режиме, вход в него с вкладки, диагностика), и
 * проверяется тестом — порядок уровней здесь и есть обещание пользователю.
 */
object SpectrumInfo {

    fun sections(
        s: SpectrumStrings = SpectrumRu,
        /** «калибровка: E = … · 1024 канала» — уходит в технические данные. */
        calibrationLine: String? = null,
        /** «у верхней границы шкалы: N имп.» — тоже диагностика, а не вывод. */
        edgeLine: String? = null,
        /** Полноэкранный режим: у поля есть курсор, и о нём надо сказать. */
        cursor: Boolean = false,
        /** Включён режим «− фон»: что именно нарисовано, объясняется здесь. */
        subtracted: Boolean = false,
        /** Вкладка: тап по графику открывает полный экран. */
        fullscreenEntry: Boolean = false,
    ): List<SpectrumInfoSection> = buildList {
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.WHAT,
                title = s.infoWhatTitle,
                lines = buildList {
                    add(s.infoAxisX)
                    add(s.infoAxisY)
                    if (cursor) add(s.infoCursor)
                    if (fullscreenEntry) add(s.infoFullscreen)
                },
            ),
        )
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.WHAT,
                title = s.infoPeakTitle,
                lines = listOf(s.infoPeak),
            ),
        )
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.WHAT,
                title = s.infoCandidateTitle,
                // Оговорка идёт ОТДЕЛЬНОЙ строкой: слитая с описанием, она
                // читается как продолжение хорошей новости.
                lines = listOf(s.infoCandidate, s.infoCandidateCaution),
            ),
        )
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.WHY,
                title = s.infoPictureTitle,
                lines = buildList {
                    add(s.infoColumns)
                    add(s.infoScales)
                    add(s.infoSmoothing)
                    // Оговорка режима «− фон» переехала сюда из-под графика:
                    // разность спектров — способ ПОСМОТРЕТЬ, и объяснение её
                    // клампа принадлежит разделу «как построена картинка».
                    if (subtracted) add(s.differenceNote)
                },
            ),
        )
        // Что делает каждая кнопка под графиком: подписи стояли под ними
        // постоянно, а читаются один раз. Разница между «сохранить в историю»
        // и «сделать фоном» — не украшение: первая кладёт снимок в журнал,
        // вторая объявляет его эталоном, который потом вычитается.
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.WHY,
                title = s.infoActionsTitle,
                lines = listOf(s.saveSnapshotNote, s.setAsBackgroundNote),
            ),
        )
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.WHY,
                title = s.infoEdgeTitle,
                lines = listOf(s.edgeExplanation),
            ),
        )
        add(
            SpectrumInfoSection(
                level = SpectrumInfoLevel.HOW,
                title = s.infoSignificanceTitle,
                lines = listOf(s.infoSignificance),
            ),
        )
        val technical = listOfNotNull(calibrationLine, edgeLine)
        if (technical.isNotEmpty()) {
            add(
                SpectrumInfoSection(
                    level = SpectrumInfoLevel.HOW,
                    title = s.infoTechnicalTitle,
                    lines = technical,
                ),
            )
        }
    }
}
