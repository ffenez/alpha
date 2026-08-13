package app.radiacode.service

import app.radiacode.data.ExperimentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Идущий прогон A/B-эксперимента, видимый экрану как состояние. */
data class AbRun(
    val experimentId: Long,
    val runId: Long,
    val label: String,
    val startedAtMillis: Long,
    /** Плановая длительность, с; 0 = до ручной остановки. */
    val plannedSeconds: Long,
) {
    fun elapsedSeconds(nowMillis: Long): Long =
        ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)

    fun remainingSeconds(nowMillis: Long): Long? =
        if (plannedSeconds <= 0L) null else (plannedSeconds - elapsedSeconds(nowMillis)).coerceAtLeast(0L)
}

/**
 * Владелец идущего прогона эксперимента — **вне композиции**.
 *
 * Прогон жил в экране: таймер, автостоп и флаг «идёт эксперимент» держались
 * `LaunchedEffect`, поэтому переход на другую вкладку или сворачивание
 * приложения тихо убивали прогон. Пятиминутный замер не заканчивался, спектр
 * прогона не снимался, а интервал снова начинал учить обычный фон — то есть
 * эксперимент не просто прерывался, он ещё и портил статистику места.
 *
 * Теперь прогон принадлежит графу приложения, как и замер локального фона
 * Поиска ([LocalBackgroundRecorder]): экран только читает [state] и нажимает
 * старт/стоп. Пока прогон идёт, рекордер сам держит опрос спектра
 * ([SpectrumHub]) и сам поднимает флаг эксперимента — иначе спектр прогона
 * зависел бы от того, открыт ли экран.
 */
class AbRunRecorder(
    private val scope: CoroutineScope,
    private val experiments: ExperimentRepository,
    private val spectrumHub: SpectrumHub,
    private val status: ServiceStatus,
    /** Снимок спектра прогона на его конце; null = снять не удалось. */
    private val captureSpectrum: suspend (runId: Long, nowMillis: Long) -> Long?,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<AbRun?>(null)
    val state: StateFlow<AbRun?> = _state.asStateFlow()

    /** Последний исход: экран показывает его, когда возвращается. */
    /**
     * Что записыватель хочет сказать человеку — КОДОМ, а не текстом.
     *
     * Формулировку выбирает экран: он один знает язык интерфейса, а сервис
     * пишет прогон и без открытого экрана.
     */
    enum class Notice { RUN_WITHOUT_SPECTRUM }

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private var timer: Job? = null

    fun start(experimentId: Long, runId: Long, label: String, plannedSeconds: Long) {
        timer?.cancel()
        val run = AbRun(
            experimentId = experimentId,
            runId = runId,
            label = label,
            startedAtMillis = clock(),
            plannedSeconds = plannedSeconds,
        )
        _state.value = run
        // Условие 4 пайплайна допуска: интервал эксперимента не учит фон.
        status.onExperiment(ServiceStatus.SOURCE_AB, "A/B эксперимент")
        spectrumHub.attach()
        if (plannedSeconds <= 0L) return
        timer = scope.launch {
            while (true) {
                val now = clock()
                if (now - run.startedAtMillis >= plannedSeconds * 1000L) {
                    finish(run)
                    return@launch
                }
                delay(TICK_MILLIS)
            }
        }
    }

    /** Ручная остановка. */
    fun stop() {
        val run = _state.value ?: return
        timer?.cancel()
        timer = null
        scope.launch { finish(run) }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private suspend fun finish(run: AbRun) {
        val now = clock()
        val spectrumId = captureSpectrum(run.runId, now)
        experiments.finishRun(run.runId, now, spectrumId)
        _state.value = null
        timer = null
        spectrumHub.detach()
        status.onExperiment(ServiceStatus.SOURCE_AB, null)
        _notice.value = if (spectrumId == null) Notice.RUN_WITHOUT_SPECTRUM else null
    }

    private companion object {
        /** Разрешение автостопа: секунда достаточна для минутных прогонов. */
        const val TICK_MILLIS = 1_000L
    }
}
