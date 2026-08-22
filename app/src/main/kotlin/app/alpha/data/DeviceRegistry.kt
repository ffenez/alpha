package app.alpha.data

import app.alpha.data.db.DeviceDao
import app.alpha.data.db.RareDataDao
import app.alpha.data.db.SampleDao
import app.alpha.data.db.SpectrumDao
import app.alpha.data.db.SpectrumTemplateDao
import app.alpha.data.db.DeviceEntity
import app.alpha.device.DeviceInfo
import app.alpha.device.DeviceModel
import kotlinx.coroutines.flow.Flow

/**
 * Приборы, с которыми приложение работало.
 *
 * ## Что здесь важно
 *
 * Прибор узнаётся по серийнику — он приходит от самого прибора. BLE-адрес
 * хранится рядом, потому что подключаться приложение умеет только по нему, но
 * ключом он быть не может: у части телефонов адрес случайный и меняется.
 *
 * Имя даёт человек. Пока не дал, прибор называется своей моделью, а если
 * моделей две одинаковых — с хвостом серийника, иначе в списке они
 * неразличимы. Серийник целиком в имя не выносится: это техническая
 * подробность, а не название.
 */
class DeviceRegistry(
    private val dao: DeviceDao,
    private val samples: SampleDao? = null,
    private val spectra: SpectrumDao? = null,
    private val rare: RareDataDao? = null,
    private val templates: SpectrumTemplateDao? = null,
) {

    fun devices(): Flow<List<DeviceEntity>> = dao.observeAll()

    suspend fun all(): List<DeviceEntity> = dao.all()

    /**
     * Записать встречу с прибором: первый раз заводит запись, дальше обновляет
     * адрес, прошивку и время последней встречи.
     *
     * Имя, данное человеком, не трогается никогда.
     */
    suspend fun seen(info: DeviceInfo, nowMillis: Long): DeviceEntity {
        val serial = info.serialNumber.trim()
        if (serial.isEmpty()) return DeviceEntity(
            serialNumber = "",
            firstSeenAt = nowMillis,
            lastSeenAt = nowMillis,
        )
        val existing = dao.bySerial(serial)
        // Неопознанная модель не записывается: «RadiaCode» вместо имени —
        // это не сведения о приборе, а заглушка.
        val model = info.model.takeIf { it != DeviceModel.UNKNOWN }?.displayName
        if (existing == null) {
            val fresh = DeviceEntity(
                serialNumber = serial,
                model = model,
                firmware = info.firmware.toString(),
                address = info.address,
                firstSeenAt = nowMillis,
                lastSeenAt = nowMillis,
            )
            val id = dao.insert(fresh)
            return fresh.copy(id = id)
        }
        val updated = existing.copy(
            model = model ?: existing.model,
            firmware = info.firmware.toString(),
            address = info.address,
            lastSeenAt = nowMillis,
        )
        dao.update(updated)
        return updated
    }

    /** Переименовать; пустое имя означает «вернуть имя по модели». */
    suspend fun rename(id: Long, name: String?) {
        val current = dao.all().firstOrNull { it.id == id } ?: return
        dao.update(current.copy(displayName = name?.trim()?.ifEmpty { null }))
    }

    /**
     * Забыть прибор. Его записи ОСТАЮТСЯ: они данные, а не настройка, и
     * удаление данных — отдельное решение человека ([forgetWithData]).
     */
    suspend fun forget(id: Long) = dao.delete(id)

    /**
     * Сколько записей принадлежит прибору: число называется человеку ДО
     * удаления, потому что «удалить записи» без числа — это не выбор.
     */
    suspend fun records(serial: String): DeviceRecords = DeviceRecords(
        samples = samples?.countForDevice(serial) ?: 0L,
        spectra = spectra?.countForDevice(serial) ?: 0L,
    )

    /**
     * Забыть прибор ВМЕСТЕ с его записями. Необратимо, поэтому вызывается
     * только после подтверждения с названным числом записей.
     *
     * Не трогает записи, у которых прибор неизвестен: приписать их этому
     * прибору нечем, а удалить заодно значило бы удалить чужое.
     */
    suspend fun forgetWithData(id: Long, serial: String) {
        samples?.deleteForDevice(serial)
        rare?.deleteForDevice(serial)
        spectra?.deleteForDevice(serial)
        templates?.deleteForDevice(serial)
        dao.delete(id)
    }

    /** Что принадлежит прибору — для подтверждения удаления. */
    data class DeviceRecords(val samples: Long, val spectra: Long)

    companion object {

        /**
         * Как прибор называется в интерфейсе.
         *
         * @param others остальные известные приборы: если среди них есть
         *   безымянный той же модели, к названию добавляется хвост серийника —
         *   без него два одинаковых прибора в списке не различить.
         */
        fun label(device: DeviceEntity, others: List<DeviceEntity>, fallback: String): String {
            device.displayName?.let { return it }
            val model = device.model ?: fallback
            val sameModel = others.any {
                it.id != device.id && it.displayName == null && it.model == device.model
            }
            return if (sameModel) "$model · ${tail(device.serialNumber)}" else model
        }

        /**
         * Хвост серийника — столько знаков, сколько нужно, чтобы отличить, и не
         * больше: серийник целиком это техническая подробность.
         */
        fun tail(serial: String): String = serial.takeLast(TAIL_LENGTH)

        private const val TAIL_LENGTH = 4
    }
}
