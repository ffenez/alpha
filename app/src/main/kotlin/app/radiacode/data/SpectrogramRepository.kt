package app.radiacode.data

import app.radiacode.analysis.HistorySlice
import app.radiacode.analysis.SpectrogramBinning
import app.radiacode.analysis.SpectrogramHistory
import app.radiacode.data.db.SpectrogramDao
import app.radiacode.data.db.SpectrogramSliceEntity

/**
 * Чтение и запись постоянной истории спектрограммы (ADR 007).
 *
 * Писатель ровно один — служба измерения через
 * [app.radiacode.service.SpectrogramStore]; экран только читает окно.
 */
class SpectrogramRepository(private val dao: SpectrogramDao) {

    suspend fun append(slice: HistorySlice) {
        dao.upsert(listOf(slice.toEntity()))
    }

    /**
     * Срезы, пересекающие окно, старые → новые. При превышении [limit]
     * возвращаются НОВЕЙШИЕ: окно смотрят от «сейчас» назад, и обрезать надо
     * дальний край, а не ближний.
     *
     * Срезы схемы, которой эта сборка не знает, не возвращаются: сложить их с
     * текущими значило бы приписать импульсы чужим границам энергии.
     */
    suspend fun window(
        fromMillis: Long,
        toMillis: Long,
        limit: Int = MAX_WINDOW_SLICES,
    ): List<HistorySlice> =
        dao.window(fromMillis, toMillis, limit)
            .asReversed()
            .filter { SpectrogramBinning.bandCount(it.schemeId) == it.bandCount }
            .map { it.toSlice() }

    suspend fun count(): Int = dao.count()

    /**
     * Прореживание старой истории: всё старше
     * [SpectrogramHistory.AS_RECORDED_MILLIS] сливается в срезы длиной
     * [SpectrogramHistory.COMPACTED_SLICE_MILLIS]. Возвращает, на сколько строк
     * стало меньше.
     *
     * Идёт часовыми кусками: час — это и предел памяти на один шаг (при 5 с
     * записи в нём 720 срезов), и естественная граница, на которой прерывание
     * не оставляет полуслитого куска — корзины выровнены по эпохе, поэтому
     * повторный проход по уже прорежённому часу ничего не меняет.
     */
    suspend fun compact(nowMillis: Long): Int {
        val boundary = nowMillis - SpectrogramHistory.AS_RECORDED_MILLIS
        var cursor = dao.earliestStart() ?: return 0
        var removed = 0
        while (cursor < boundary) {
            // Курсор прыгает к первому срезу, который ещё короче цели: пустые
            // и уже прорежённые участки истории не стоят ни одного запроса.
            val next = dao.nextShortSliceStart(
                from = cursor,
                to = boundary,
                maxDurationMillis = SpectrogramHistory.COMPACTED_SLICE_MILLIS,
            ) ?: break
            cursor = next.floorDiv(CHUNK_MILLIS) * CHUNK_MILLIS
            val to = minOf(cursor + CHUNK_MILLIS, boundary)
            val rows = dao.range(cursor, to, CHUNK_LIMIT)
            if (rows.size >= CHUNK_LIMIT) {
                // Крышка строк сработала — часть куска осталась за кадром, а
                // замена диапазона стёрла бы её. Кусок пропускается целиком:
                // потерять историю хуже, чем оставить её неприрежённой.
                cursor = to
                continue
            }
            if (rows.size > 1) {
                val compacted = SpectrogramHistory.compact(
                    rows.map { it.toSlice() },
                    SpectrogramHistory.COMPACTED_SLICE_MILLIS,
                )
                if (compacted.size < rows.size) {
                    dao.replaceRange(cursor, to, compacted.map { it.toEntity() })
                    removed += rows.size - compacted.size
                }
            }
            cursor = to
        }
        return removed
    }

    companion object {
        /** Потолок строк на одно окно: 2 ч пятисекундной записи — 1440. */
        const val MAX_WINDOW_SLICES = 3_000

        /** Шаг прореживания по времени. */
        const val CHUNK_MILLIS = 3_600_000L

        /** Потолок строк одного шага прореживания (час при 5 с — 720). */
        const val CHUNK_LIMIT = 5_000
    }
}

/** Строка ↔ срез: блоб счёта — i32 LE, тот же кодек, что у снимков спектра. */
fun HistorySlice.toEntity(): SpectrogramSliceEntity = SpectrogramSliceEntity(
    startMillis = startMillis,
    endMillis = endMillis,
    durationMillis = durationMillis,
    schemeId = schemeId,
    bandCount = bandCounts.size,
    counts = SpectrumBlob.encode(bandCounts.toList()),
    cps = cps,
    doseMicroSvH = doseMicroSvH,
    sliceCount = sliceCount,
)

fun SpectrogramSliceEntity.toSlice(): HistorySlice = HistorySlice(
    startMillis = startMillis,
    endMillis = endMillis,
    durationMillis = durationMillis,
    schemeId = schemeId,
    bandCounts = SpectrumBlob.decode(counts).toIntArray(),
    cps = cps,
    doseMicroSvH = doseMicroSvH,
    sliceCount = sliceCount,
)
