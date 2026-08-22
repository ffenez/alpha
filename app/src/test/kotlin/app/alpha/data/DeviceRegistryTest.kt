package app.alpha.data

import app.alpha.data.db.DeviceDao
import app.alpha.data.db.DeviceEntity
import app.alpha.device.DeviceInfo
import app.alpha.device.FwVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Список приборов: прибор заводится один раз, имя человека не переписывается
 * встречами, а два одинаковых прибора должны быть различимы в списке.
 */
class DeviceRegistryTest {

    private class FakeDao : DeviceDao {
        val rows = mutableListOf<DeviceEntity>()
        override suspend fun insert(device: DeviceEntity): Long {
            if (rows.any { it.serialNumber == device.serialNumber }) return -1L
            rows += device.copy(id = rows.size + 1L)
            return rows.size.toLong()
        }

        override suspend fun update(device: DeviceEntity) {
            val index = rows.indexOfFirst { it.id == device.id }
            if (index >= 0) rows[index] = device
        }

        override fun observeAll(): Flow<List<DeviceEntity>> = flowOf(rows.toList())
        override suspend fun all(): List<DeviceEntity> = rows.toList()
        override suspend fun bySerial(serial: String): DeviceEntity? =
            rows.firstOrNull { it.serialNumber == serial }

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }
    }

    private fun info(serial: String, address: String = "AA:BB:CC:DD:EE:FF") = DeviceInfo(
        address = address,
        serialNumber = serial,
        firmware = FwVersion(4, 14, "", 4, 14, ""),
        spectrumFormatVersion = 1,
        configurationLines = emptyList(),
    )

    @Test
    fun `повторная встреча не заводит второй прибор`() = runTest {
        val dao = FakeDao()
        val registry = DeviceRegistry(dao)
        registry.seen(info("RC-110-000000"), 1_000L)
        registry.seen(info("RC-110-000000", address = "11:22:33:44:55:66"), 2_000L)

        assertEquals(1, dao.rows.size)
        // Адрес и момент встречи обновляются: по адресу приложение возвращается.
        assertEquals("11:22:33:44:55:66", dao.rows.single().address)
        assertEquals(2_000L, dao.rows.single().lastSeenAt)
        assertEquals(1_000L, dao.rows.single().firstSeenAt)
    }

    @Test
    fun `имя, данное человеком, встреча не переписывает`() = runTest {
        val dao = FakeDao()
        val registry = DeviceRegistry(dao)
        registry.seen(info("RC-110-000000"), 1_000L)
        registry.rename(dao.rows.single().id, "Дача")

        registry.seen(info("RC-110-000000"), 5_000L)

        assertEquals("Дача", dao.rows.single().displayName)
    }

    @Test
    fun `пустое имя возвращает имя по модели`() = runTest {
        val dao = FakeDao()
        val registry = DeviceRegistry(dao)
        registry.seen(info("RC-110-000000"), 1_000L)
        val id = dao.rows.single().id
        registry.rename(id, "Дача")
        registry.rename(id, "   ")

        assertNull(dao.rows.single().displayName)
    }

    @Test
    fun `два одинаковых прибора различимы в списке`() = runTest {
        val dao = FakeDao()
        val registry = DeviceRegistry(dao)
        registry.seen(info("RC-110-000001"), 1_000L)
        registry.seen(info("RC-110-000002", address = "11:22:33:44:55:66"), 2_000L)
        val all = dao.rows.toList()

        val first = DeviceRegistry.label(all[0], all, "RadiaCode")
        val second = DeviceRegistry.label(all[1], all, "RadiaCode")

        assertTrue(first != second, "два безымянных прибора одной модели названы одинаково")
        assertTrue(first.endsWith("0001"), first)
        assertTrue(second.endsWith("0002"), second)
    }

    @Test
    fun `забытый прибор уходит из списка, а записи остаются`() = runTest {
        val dao = FakeDao()
        val registry = DeviceRegistry(dao)
        registry.seen(info("RC-110-000001"), 1_000L)

        registry.forget(dao.rows.single().id)

        assertEquals(0, dao.rows.size)
        // Данные удаляет только отдельное решение: у этого вызова доступа к
        // измерениям нет вовсе.
    }

    @Test
    fun `названный прибор зовётся своим именем без серийника`() = runTest {
        val dao = FakeDao()
        val registry = DeviceRegistry(dao)
        registry.seen(info("RC-110-000001"), 1_000L)
        registry.seen(info("RC-110-000002", address = "11:22:33:44:55:66"), 2_000L)
        registry.rename(dao.rows[0].id, "Дача")
        val all = dao.rows.toList()

        assertEquals("Дача", DeviceRegistry.label(all[0], all, "RadiaCode"))
    }
}
