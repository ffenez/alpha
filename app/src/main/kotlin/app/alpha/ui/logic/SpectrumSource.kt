package app.alpha.ui.logic

import app.alpha.device.DeviceModel

/** Что именно нарисовано на экране Спектра. */
enum class SpectrumSource {
    /** Снимок из Истории: прибор к этой кривой не имеет отношения. */
    SNAPSHOT,

    /** «Продолжить накопление»: сохранённый снимок плюс живой поток. */
    MERGED_CONTINUATION,

    /** Живое накопление прибора. */
    LIVE,

    /** Продолжение выбрано, но потока нет — виден только сохранённый снимок. */
    CONTINUATION_ONLY,

    /** Показывать нечего. */
    NONE,
}

/** Почему действие, относящееся к прибору, недоступно. */
enum class DeviceActionBlock {
    NONE,

    /** Открыт снимок: у него нет прибора, даже если рядом подключён другой. */
    VIEWING_SNAPSHOT,

    /** Прибор не подключён. */
    NOT_CONNECTED,
}

object SpectrumSources {

    /**
     * Источник кривой. Порядок приоритетов — это порядок ответов на вопрос
     * «чей это спектр»: открытый снимок принадлежит прошлому и не смешивается
     * с потоком ни при каких условиях, дальше идёт сумма продолжения, затем
     * живое накопление, и только потом — сохранённый снимок продолжения,
     * выбранный без связи с прибором.
     */
    fun choose(
        viewingSnapshot: Boolean,
        hasSnapshot: Boolean,
        hasMerged: Boolean,
        hasLive: Boolean,
        hasContinuation: Boolean,
    ): SpectrumSource = when {
        viewingSnapshot && hasSnapshot -> SpectrumSource.SNAPSHOT
        viewingSnapshot -> SpectrumSource.NONE
        hasMerged -> SpectrumSource.MERGED_CONTINUATION
        hasLive -> SpectrumSource.LIVE
        hasContinuation -> SpectrumSource.CONTINUATION_ONLY
        else -> SpectrumSource.NONE
    }

    /**
     * Доступность действий, которые что-то делают С ПРИБОРОМ: сброс
     * накопления, запись фона, продолжение накопления.
     *
     * Открытый снимок перевешивает подключение: команда ушла бы на прибор,
     * который к этому спектру отношения не имеет, и результат человек списал
     * бы на снимок.
     */
    fun deviceActionBlock(viewingSnapshot: Boolean, connected: Boolean): DeviceActionBlock = when {
        viewingSnapshot -> DeviceActionBlock.VIEWING_SNAPSHOT
        !connected -> DeviceActionBlock.NOT_CONNECTED
        else -> DeviceActionBlock.NONE
    }

    /**
     * Модель прибора, по которой считаются разрешение пиков и допуск на
     * совпадение линии.
     *
     * У снимка модель НЕ ХРАНИТСЯ (в `spectra` нет серийника), а подключённый
     * сейчас прибор ничего не говорит о том, чем снимали месяц назад. Поэтому
     * снимок всегда разбирается как НЕОПОЗНАННЫЙ прибор — самое широкое
     * опубликованное разрешение серии ([DeviceModel.DEFAULT_RESOLUTION_662]):
     * ошибиться в сторону широкого окна безопаснее, узкое ищет структуру там,
     * где её нет. И это говорится словами, а не молча подставляется.
     */
    fun analysisModel(connectedModel: DeviceModel?, viewingSnapshot: Boolean): DeviceModel =
        if (viewingSnapshot) DeviceModel.UNKNOWN else connectedModel ?: DeviceModel.UNKNOWN

    /** Опознан ли прибор, чьи параметры пошли в анализ. */
    fun modelIdentified(connectedModel: DeviceModel?, viewingSnapshot: Boolean): Boolean =
        !viewingSnapshot && connectedModel != null && connectedModel != DeviceModel.UNKNOWN
}
