package app.alpha.ui.feedback

import android.content.Context
import app.alpha.baseline.BaselineState
import app.alpha.data.AppSettings
import app.alpha.service.ServiceStatus
import app.alpha.ui.logic.AdaptiveBackground
import app.alpha.ui.logic.BackgroundRecord
import app.alpha.ui.logic.ClickRate
import app.alpha.ui.logic.NavigateSession
import app.alpha.ui.logic.SearchFeedbackChannels
import app.alpha.ui.logic.SearchReference
import app.alpha.ui.logic.SearchReferences
import app.alpha.ui.logic.SearchTone
import app.alpha.ui.logic.SearchVibro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Отклик поиска — щелчки, тон и вибрация — на ЛЮБОМ экране приложения.
 *
 * ## Почему не в экране
 *
 * Отклик отвечает на вопрос «теплее или холоднее», и этот вопрос не
 * заканчивается, когда человек посмотрел на карту или открыл спектр: прибор
 * по-прежнему в руке, и звук ведёт его. Пока управление жило в композиции
 * экрана Поиска, уход с него означал тишину — прибор молчал ровно тогда, когда
 * на него не смотрят, то есть когда отклик и нужен.
 *
 * Точку отсчёта хаб берёт из [NavigateSession] — того же места, где её видит
 * экран, поэтому «холодно-горячо» на слух и стрелка на шкале говорят об одном
 * и том же.
 *
 * ## Что хаб НЕ делает
 *
 * Не работает в фоне. Отклик привязан к видимости приложения ([start]/[stop]
 * зовёт корень интерфейса): непрерывные щелчки при заблокированном экране —
 * это разряженная батарея и звук из кармана, о котором никто не просил.
 * Тревога — другое дело, она живёт в службе и звучит независимо.
 */
class FeedbackHub(
    context: Context,
    private val settings: AppSettings,
    private val status: ServiceStatus,
    private val navigateSession: NavigateSession,
    private val scope: CoroutineScope,
) {

    private val clicker = GeigerClicker(context)
    private val appContext = context.applicationContext

    private var running = false
    private var clicks: Job? = null
    private var tone: Job? = null
    private var pulse: Job? = null

    /**
     * Подкраска щелчков по средней энергии («тон по энергии»).
     *
     * Ставится экраном: она требует опроса спектра раз в пять секунд, а этот
     * опрос идёт, только пока спектр на экране. Вне экрана подкраска молчит —
     * но сами щелчки продолжают идти.
     */
    fun setToneBand(band: app.alpha.ui.logic.EnergyTone.Band?) {
        clicker.setToneBand(band)
    }

    /** Диагностика для экрана: почему сейчас тихо (аудио, громкость). */
    val audioUnavailable: Boolean get() = clicker.audioUnavailable
    val volumeZero: Boolean get() = clicker.volumeZero

    /** Отклик включается вместе с интерфейсом и глохнет вместе с ним. */
    fun start() {
        if (running) return
        running = true
        clicks = scope.launch { runClicks() }
        tone = scope.launch { runTone() }
        pulse = scope.launch { runPulse() }
    }

    fun stop() {
        running = false
        clicks?.cancel()
        tone?.cancel()
        pulse?.cancel()
        clicks = null
        tone = null
        pulse = null
        clicker.stop()
    }

    /**
     * Щелчки: один звук на зарегистрированный импульс, темп — от самой
     * скорости счёта. Канал абсолютный: он описывает импульсы, а не отношение,
     * и потому работает без всякой точки отсчёта.
     */
    private suspend fun runClicks() {
        combine(
            settings.searchFeedbackChannels,
            status.lastSample,
        ) { channels, sample -> channels to sample }
            .collect { (channels, sample) ->
                if (!channels.usesSound) {
                    clicker.stop()
                    return@collect
                }
                clicker.start()
                val fresh = sample != null &&
                    System.currentTimeMillis() - sample.receivedAtMillis <= STALE_MILLIS
                // Замолчавший поток — тишина, а не последняя известная частота:
                // щёлкающий прибор без данных врёт о том, что он измеряет.
                clicker.setRate(
                    if (fresh && channels.clicks) {
                        ClickRate.clicksPerSecond(sample!!.countRate)
                    } else {
                        0f
                    },
                )
            }
    }

    /** Тон: высота несёт отношение к тому, с чем сравнивают. */
    private suspend fun runTone() {
        settings.searchFeedbackChannels.distinctUntilChanged().collect { channels ->
            if (!channels.tone) {
                clicker.setSearchTone(enabled = false, targetHz = null)
                return@collect
            }
            while (running && channels.tone) {
                clicker.setSearchTone(
                    enabled = true,
                    targetHz = SearchTone.frequencyHz(ratio()),
                )
                delay(TICK_MILLIS)
            }
        }
    }

    /**
     * Вибрация: тот же сигнал без звука — частота пульса несёт то же
     * отношение, что высота тона. Внутри фона пульса нет по построению.
     */
    private suspend fun runPulse() {
        settings.searchFeedbackChannels.distinctUntilChanged().collect { channels ->
            if (!channels.vibro) return@collect
            var dndCheckedAt = 0L
            var dndAllows = true
            while (running && channels.vibro) {
                val now = System.currentTimeMillis()
                if (now - dndCheckedAt >= DND_POLL_MILLIS) {
                    dndCheckedAt = now
                    dndAllows = Feedback.dndAllowsFeedback(appContext)
                }
                val interval = SearchVibro.intervalMillis(ratio())
                if (interval == null || !dndAllows) {
                    delay(SearchVibro.SLOW_INTERVAL_MILLIS / 2)
                    continue
                }
                Feedback.pulse(appContext)
                delay(interval)
            }
        }
    }

    /**
     * Отношение, которое ведут тон и вибрация.
     *
     * Знаменатель выбирается тем же правилом, что на экране
     * ([SearchReferences]): поставленная рукой точка отсчёта главнее, за ней
     * записанный эталон, за ним изученный фон места. Нет ни того, ни другого —
     * отношения нет, и относительные каналы молчат.
     */
    private suspend fun ratio(): Double? {
        val sample = status.lastSample.value ?: return null
        if (System.currentTimeMillis() - sample.receivedAtMillis > STALE_MILLIS) return null
        val mark = navigateSession.state.reference
        val record = BackgroundRecord.decode(settings.searchBackgroundRaw.first())
        val learned = AdaptiveBackground.of(
            (status.baseline.value as? BaselineState.Active)?.baseline,
        )
        val reference = SearchReferences.choose(
            record = record,
            check = null,
            learned = learned,
            mark = mark,
        )
        val denominator = when (reference) {
            is SearchReference.Marked -> reference.reference.ratePerSecond
            is SearchReference.Recorded -> reference.record.cps.toDouble()
            is SearchReference.Learned -> reference.background.cps.toDouble()
            SearchReference.None -> return null
        }
        if (denominator <= 0.0 || !denominator.isFinite()) return null
        return sample.countRate / denominator
    }

    private companion object {

        /**
         * Насколько отсчёт считается свежим для отклика, мс. **Инженерный
         * параметр**: те же пять секунд, что у экрана — прибор пишет раз в
         * секунду, и пять пропущенных означают обрыв, а не задержку.
         */
        const val STALE_MILLIS = 5_000L

        /** Как часто пересчитывается высота тона, мс: темп потока прибора. */
        const val TICK_MILLIS = 1_000L

        /**
         * Как часто спрашивается «не тихий ли режим», мс. На самой быстрой
         * частоте пульсы идут через 120 мс, и спрашивать систему на каждый —
         * работа впустую: ответ меняется раз в час.
         */
        const val DND_POLL_MILLIS = 1_000L
    }
}
