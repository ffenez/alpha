package app.alpha.service

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.HistorySlice
import app.alpha.analysis.Spectrogram
import app.alpha.analysis.SpectrogramBinning
import app.alpha.analysis.SpectrogramRing
import app.alpha.analysis.SpectrogramSlice
import app.alpha.data.SpectrogramRepository
import app.alpha.protocol.Spectrum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сквозная запись спектрограммы: каждый опрошенный службой спектр вычитается из
 * предыдущего в интервальный срез, который кладётся И в кольцо для живого
 * просмотра (~2 ч, [SpectrogramRing.DEFAULT_CAPACITY]), И в базу (ADR 007).
 *
 * Кольцо — быстрый доступ к последним минутам, база — сама история: после
 * перезапуска процесса картинка восстанавливается из базы ([restore]), а не
 * начинается с пустоты.
 *
 * Писатель здесь ровно один — служба измерения. Экраны только читают.
 */
class SpectrogramStore(private val repository: SpectrogramRepository? = null) {

    private val ring = SpectrogramRing()

    private var previous: Spectrum? = null
    private var previousAtMillis: Long = 0L

    private val _slices = MutableStateFlow<List<SpectrogramSlice>>(emptyList())
    /** All retained slices, oldest → newest. */
    val slices: StateFlow<List<SpectrogramSlice>> = _slices.asStateFlow()

    private val _latest = MutableStateFlow<SpectrogramSlice?>(null)
    /** Newest slice (Поиск «тон по энергии» consumes just this). */
    val latest: StateFlow<SpectrogramSlice?> = _latest.asStateFlow()

    /**
     * Feed one polled since-reset spectrum. The first poll after start or a
     * reset only re-arms the diff base; a valid growth of the accumulation
     * produces a slice stamped with the live dose/CPS at that moment.
     */
    suspend fun onSpectrum(spectrum: Spectrum, atMillis: Long, cps: Float?, doseMicroSvH: Float?) {
        val prev = previous
        val startMillis = previousAtMillis
        previous = spectrum
        previousAtMillis = atMillis
        val interval = Spectrogram.intervalCounts(
            currentCounts = spectrum.counts,
            currentSeconds = spectrum.durationSeconds,
            previousCounts = prev?.counts,
            previousSeconds = prev?.durationSeconds ?: 0L,
        ) ?: return
        val intervalSeconds = spectrum.durationSeconds - (prev?.durationSeconds ?: 0L)
        val bands = Spectrogram.bandCounts(
            interval,
            EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2),
        )
        val slice = SpectrogramSlice(
            timestampMillis = atMillis,
            intervalSeconds = intervalSeconds,
            bandCounts = bands,
            cps = cps,
            doseMicroSvH = doseMicroSvH,
        )
        ring.add(slice)
        _slices.value = ring.snapshot()
        _latest.value = slice
        repository?.append(
            HistorySlice(
                // Начало интервала — момент ПРЕДЫДУЩЕГО опроса: именно с него
                // прибор набирал эти импульсы.
                startMillis = startMillis,
                endMillis = atMillis,
                // Экспозиция берётся у прибора (рост накопления), а не из
                // настенного времени: они расходятся, и делить надо на неё.
                durationMillis = intervalSeconds * 1000L,
                schemeId = SpectrogramBinning.CURRENT_SCHEME,
                bandCounts = IntArray(bands.size) { bands[it].toInt() },
                cps = cps,
                doseMicroSvH = doseMicroSvH,
            ),
        )
    }

    /** Device-side spectrum reset: drop the diff base, keep recorded history. */
    fun onReset() {
        previous = null
        previousAtMillis = 0L
    }

    /**
     * Наполняет кольцо из базы при старте службы — после перезапуска процесса
     * картинка обязана продолжаться, а не начинаться заново. Уже показанные
     * живые срезы не трогаются: восстановление имеет смысл ровно один раз, до
     * первого опроса.
     */
    suspend fun restore(nowMillis: Long) {
        val source = repository ?: return
        if (_slices.value.isNotEmpty()) return
        val restored = source
            .window(nowMillis - RESTORE_WINDOW_MILLIS, nowMillis)
            .map { it.toDisplaySlice() }
        if (restored.isEmpty()) return
        for (slice in restored) ring.add(slice)
        _slices.value = ring.snapshot()
        _latest.value = ring.latest()
    }

    /** Прореживание старой истории; возвращает, на сколько строк стало меньше. */
    suspend fun compact(nowMillis: Long): Int = repository?.compact(nowMillis) ?: 0

    companion object {
        /** Сколько истории поднимается в кольцо при старте — его вместимость. */
        const val RESTORE_WINDOW_MILLIS = 2L * 3_600_000L
    }
}

/**
 * Хранимый срез → колонка картинки. Момент колонки — КОНЕЦ интервала (так же,
 * как у живого среза: он появляется в момент опроса), длительность — экспозиция.
 */
fun HistorySlice.toDisplaySlice(): SpectrogramSlice = SpectrogramSlice(
    timestampMillis = endMillis,
    intervalSeconds = durationMillis / 1000L,
    bandCounts = FloatArray(bandCounts.size) { bandCounts[it].toFloat() },
    cps = cps,
    doseMicroSvH = doseMicroSvH,
)
