package app.radiacode.service

import app.radiacode.device.ConnectionState
import app.radiacode.device.DeviceInfo
import app.radiacode.device.FwVersion
import app.radiacode.ui.logic.StreamState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Свежесть — факт о ПРИХОДЕ данных, а не о часах прибора.
 *
 * Полевой дефект, который чинился трижды: «нет новых данных · 29 с» при
 * зелёном кружке связи, притом что записи приходили каждую секунду. Возраст
 * считался как `сейчас − метка записи`, а метка стоит на базе времени прибора
 * — она ИЗМЕРЯЕТСЯ по ходу сеанса и может уехать на десятки секунд. Плюс
 * показание бралось из базы, где строку с занятой меткой уникальный индекс
 * отбрасывает молча. Два независимых повода ошибиться в ответе на простой
 * вопрос «идут ли данные».
 */
class LiveSampleFreshnessTest {

    private val now = 1_700_000_000_000L

    private val connected = ConnectionState.Connected(
        DeviceInfo(
            address = "AA:BB:CC:DD:EE:FF",
            serialNumber = "RC-110-000042",
            firmware = FwVersion(4, 9, "", 4, 8, ""),
            spectrumFormatVersion = 1,
            configurationLines = emptyList(),
        ),
    )

    @Test
    fun `a sample that just arrived is live even when its device stamp is old`() {
        val status = ServiceStatus()
        status.onSample(
            ServiceStatus.LiveSample(
                // База времени прибора уехала на полминуты в прошлое.
                deviceTimestampMillis = now - 29_000,
                receivedAtMillis = now,
                doseRate = 0.0004f,
                doseRateErr = 15f,
                countRate = 24f,
                countRateErr = 10f,
            ),
        )

        val live = status.lastSample.value!!
        assertEquals(
            StreamState.Live,
            StreamState.of(live.receivedAtMillis, now, connected),
        )
        // По прежнему правилу тот же отсчёт давал «нет новых данных · 29 с».
        assertEquals(
            StreamState.Stale(29),
            StreamState.of(live.deviceTimestampMillis, now, connected),
        )
    }

    @Test
    fun `a stream that really stopped still ages`() {
        // Свежесть не «всегда зелёная»: перестали приходить — состояние честно
        // уходит из Live, потому что считается по последнему ПРИХОДУ.
        val status = ServiceStatus()
        status.onSample(
            ServiceStatus.LiveSample(
                deviceTimestampMillis = now - 40_000,
                receivedAtMillis = now - 40_000,
                doseRate = 0.0004f,
                doseRateErr = 15f,
                countRate = 24f,
                countRateErr = 10f,
            ),
        )

        val live = status.lastSample.value!!
        assertEquals(
            StreamState.Stale(40),
            StreamState.of(live.receivedAtMillis, now, connected),
        )
    }

    @Test
    fun `the device stamp is kept, because it answers a different question`() {
        // Метка прибора отвечает на «когда измерено» и остаётся осью графиков;
        // подменять её приходом нельзя — иначе история поедет.
        val status = ServiceStatus()
        status.onSample(
            ServiceStatus.LiveSample(
                deviceTimestampMillis = now - 1_000,
                receivedAtMillis = now,
                doseRate = 0.0004f,
                doseRateErr = 15f,
                countRate = 24f,
                countRateErr = 10f,
            ),
        )

        assertEquals(now - 1_000, status.lastSample.value?.deviceTimestampMillis)
    }
}
