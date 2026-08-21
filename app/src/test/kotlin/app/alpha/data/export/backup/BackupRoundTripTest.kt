package app.alpha.data.export.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Резервная копия: записали — прочитали — получили то же самое.
 *
 * Проверяется СМЫСЛ данных, а не идентификаторы строк: у двух телефонов
 * идентификаторы свои, и копия обязана переносить измерение, а не номер записи.
 * Отдельно проверяется то, ради чего копия вообще существует и что нельзя
 * увидеть глазами: повторный импорт не удваивает данные, испорченный архив не
 * восстанавливается частично, копия более новой версии не разбирается «на
 * удачу».
 */
class BackupRoundTripTest {

    private val now = 1_700_000_000_000L

    // --- источник данных для записи ---------------------------------------

    private class FakeSource(
        val profiles: BackupProfiles,
        val settings: List<Pair<String, String>>?,
        val measurements: List<BackupMeasurement>,
        val sessions: List<BackupSession> = emptyList(),
        val events: List<BackupEvent> = emptyList(),
        val rare: List<BackupRare> = emptyList(),
        val environment: List<BackupEnvironment> = emptyList(),
        val stations: List<BackupStation> = emptyList(),
        val routes: List<BackupRoute> = emptyList(),
        val points: List<BackupPoint> = emptyList(),
        val spectra: List<BackupSpectrum> = emptyList(),
        val templates: List<BackupTemplate> = emptyList(),
        val slices: List<BackupSlice> = emptyList(),
        val experiments: List<BackupExperiment> = emptyList(),
    ) : BackupSource {

        override suspend fun counts() = BackupCounts(
            measurements = measurements.size.toLong(),
            events = events.size.toLong(),
            rare = rare.size.toLong(),
            environment = environment.size.toLong(),
            stations = stations.size.toLong(),
            sessions = sessions.size.toLong(),
            routes = routes.size.toLong(),
            points = points.size.toLong(),
            spectra = spectra.size.toLong(),
            templates = templates.size.toLong(),
            slices = slices.size.toLong(),
            experiments = experiments.size.toLong(),
        )

        override suspend fun profiles() = profiles
        override suspend fun settings() = settings

        private fun <T> pages(items: List<T>) = BackupStream<T> { cursor, limit ->
            val from = cursor.toInt()
            val to = minOf(from + limit, items.size)
            BackupPage(
                items = if (from >= items.size) emptyList() else items.subList(from, to),
                nextCursor = if (to >= items.size) null else to.toLong(),
            )
        }

        override fun sessions() = pages(sessions)
        override fun measurements() = pages(measurements)
        override fun events() = pages(events)
        override fun rare() = pages(rare)
        override fun environment() = pages(environment)
        override fun stations() = pages(stations)
        override fun routes() = pages(routes)
        override fun points() = pages(points)
        override fun spectra() = pages(spectra)
        override fun templates() = pages(templates)
        override fun slices() = pages(slices)
        override fun experiments() = pages(experiments)
    }

    /** Приёмник, который просто копит восстановленное — как вторая база. */
    private class FakeSink(private val existingKeys: MutableSet<String> = mutableSetOf()) :
        BackupSink {

        var mode: RestoreMode? = null
        var selection: RestoreSelection? = null
        var finished = false
        var settings: List<Pair<String, String>> = emptyList()
        var profiles: BackupProfiles = BackupProfiles()
        val measurements = mutableListOf<BackupMeasurement>()
        val sessions = mutableListOf<BackupSession>()
        val events = mutableListOf<BackupEvent>()
        val rare = mutableListOf<BackupRare>()
        val environment = mutableListOf<BackupEnvironment>()
        val stations = mutableListOf<BackupStation>()
        val routes = mutableListOf<BackupRoute>()
        val points = mutableListOf<BackupPoint>()
        val spectra = mutableListOf<BackupSpectrum>()
        val templates = mutableListOf<BackupTemplate>()
        val slices = mutableListOf<BackupSlice>()
        val experiments = mutableListOf<BackupExperiment>()

        override suspend fun begin(mode: RestoreMode, selection: RestoreSelection) {
            this.mode = mode
            this.selection = selection
        }

        /**
         * Ключ совпадения — СВОЙ у каждого ряда: момент сессии и момент
         * измерения — одно и то же число, и общий на всех набор ключей
         * потерял бы первое измерение каждой сессии.
         */
        private fun <T> add(
            batch: List<T>,
            into: MutableList<T>,
            kind: String,
            key: (T) -> String,
        ): RestoreCount {
            var added = 0L
            var skipped = 0L
            for (item in batch) {
                if (existingKeys.add(kind + "/" + key(item))) {
                    into += item
                    added++
                } else {
                    skipped++
                }
            }
            return RestoreCount(added, skipped)
        }

        override suspend fun settings(entries: List<Pair<String, String>>) {
            settings = entries
        }

        override suspend fun profiles(bundle: BackupProfiles): RestoreCount {
            profiles = bundle
            return RestoreCount(added = bundle.profiles.size.toLong())
        }

        override suspend fun sessions(batch: List<BackupSession>) =
            add(batch, sessions, "sessions") { it.key }

        override suspend fun measurements(batch: List<BackupMeasurement>) =
            add(batch, measurements, "measurements") { it.key }

        override suspend fun events(batch: List<BackupEvent>) = add(batch, events, "events") { it.key }
        override suspend fun rare(batch: List<BackupRare>) = add(batch, rare, "rare") { it.key }
        override suspend fun environment(batch: List<BackupEnvironment>) =
            add(batch, environment, "environment") { it.key }
        override suspend fun stations(batch: List<BackupStation>) =
            add(batch, stations, "stations") { it.key }
        override suspend fun routes(batch: List<BackupRoute>) = add(batch, routes, "routes") { it.key }
        override suspend fun points(batch: List<BackupPoint>) = add(batch, points, "points") { it.key }
        override suspend fun spectra(batch: List<BackupSpectrum>) = add(batch, spectra, "spectra") { it.key }
        override suspend fun templates(batch: List<BackupTemplate>) =
            add(batch, templates, "templates") { it.key }
        override suspend fun slices(batch: List<BackupSlice>) = add(batch, slices, "slices") { it.key }
        override suspend fun experiments(batch: List<BackupExperiment>) =
            add(batch, experiments, "experiments") { it.key }

        override suspend fun finish() {
            finished = true
        }
    }

    // --- фикстуры ---------------------------------------------------------

    private fun measurement(index: Int) = BackupMeasurement(
        timestamp = now + index * 1_000L,
        doseRate = 0.15f + index * 0.001f,
        doseRateErr = 12f,
        countRate = 24.5f,
        countRateErr = 7f,
        flags = 1,
        realTimeFlags = 0,
        profileName = if (index % 2 == 0) "Дом" else null,
        baselineExcluded = null,
    )

    private fun fullSource(measurements: Int = 5_000) = FakeSource(
        profiles = BackupProfiles(
            profiles = listOf(
                BackupProfile(
                    name = "Дом",
                    icon = "⌂",
                    parentName = null,
                    archived = false,
                    autoActivate = true,
                    baselineLearning = true,
                    role = "user",
                    baselineEpochMillis = null,
                    createdAt = now - 100_000,
                ),
            ),
            networks = listOf(BackupNetwork("Дом", "hash-1", "Home_5G", now - 90_000)),
            epochs = listOf(BackupEpoch("Дом", now - 80_000, now - 70_000, "{}", "user_shift", now)),
            fingerprints = listOf(
                BackupFingerprint(
                    profileName = "Дом",
                    createdAt = now - 60_000,
                    accumulatedSeconds = 3_600,
                    sampleCount = 3_600,
                    doseLow = 0.12f, doseMedian = 0.15f, doseHigh = 0.19f,
                    doseP25 = 0.13f, doseP75 = 0.17f, doseMad = 0.01f,
                    cpsLow = 20f, cpsMedian = 24f, cpsHigh = 28f,
                    spectrumSeconds = 3_600,
                    a0 = 1.2f, a1 = 2.4f, a2 = 0.0003f,
                    channelCount = 1024,
                    spectrumBase64 = BackupBinary.encode(byteArrayOf(1, 2, 3, 4)),
                    origin = "auto",
                    algorithmVersion = 3,
                ),
            ),
        ),
        settings = listOf("theme" to "s:dark", "dose_unit" to "s:uSv"),
        measurements = (0 until measurements).map { measurement(it) },
        sessions = listOf(BackupSession(now, now + 3_600_000, "Дом")),
        events = listOf(
            BackupEvent(now + 500, "deviation", 2, "выше обычного", 0, 0, 0.4f, 55.7, 37.6),
        ),
        rare = listOf(BackupRare(now, 12.5f, 24f, 84f, 3_600, 0)),
        stations = listOf(
            BackupStation(now, now, 55.75, 37.61, 8f, 100, 1013.2f, "у камня"),
            BackupStation(now + 60_000, now + 60_000, 55.76, 37.62, 12f, null, null, null),
        ),
        environment = listOf(
            // Одна запись со всеми датчиками и одна с половиной: телефон без
            // барометра обязан восстанавливаться так же, как телефон с ним.
            BackupEnvironment(now, 1013.2f, 48.6f, 0.4f, 31.4f, 60),
            BackupEnvironment(now + 10_000, null, 47.9f, null, 31.5f, 1),
        ),
        routes = listOf(BackupRoute("Парк", now, now + 3_600_000, 3_800.0, false)),
        points = listOf(
            BackupPoint(
                routeKey = BackupRoute("Парк", now, null, null, false).key,
                timestamp = now + 1_000,
                latitude = 55.75,
                longitude = 37.61,
                accuracyMeters = 8f,
                doseRate = 0.16f,
                countRate = 25f,
                altitudeMeters = 150.0,
            ),
        ),
        spectra = listOf(
            BackupSpectrum(
                timestamp = now,
                accumulated = true,
                isBackgroundReference = false,
                origin = "user",
                label = "Спектр «дома» <b>",
                analysisMeta = null,
                durationSeconds = 7_200,
                a0 = 1.1f, a1 = 2.2f, a2 = 0.0002f,
                channelCount = 1024,
                countsBase64 = BackupBinary.encode(ByteArray(4096) { (it % 251).toByte() }),
                deviceSerial = "RC-110-0001",
                firmware = "4.8",
                epochId = 7,
                trigger = "manual",
            ),
        ),
        templates = listOf(
            // Снятый шаблон с прибором и заметкой — и импортированный, у
            // которого прибора нет: копия обязана вернуть оба вида.
            BackupTemplate(
                name = "Th-232",
                createdAt = now - 50_000,
                deviceSerial = "RC-110-0001",
                deviceName = "RadiaCode 110",
                a0 = 1.1f, a1 = 2.2f, a2 = 0.0002f,
                durationSeconds = 14_400,
                resolution662 = 0.085f,
                channelCount = 1024,
                countsBase64 = BackupBinary.encode(ByteArray(4096) { (it % 97).toByte() }),
                source = "measured",
                note = "торцевой калильник",
            ),
            BackupTemplate(
                name = "K-40",
                createdAt = now - 40_000,
                deviceSerial = null,
                deviceName = null,
                a0 = 0.9f, a1 = 2.1f, a2 = 0.0003f,
                durationSeconds = 7_200,
                resolution662 = 0.12f,
                channelCount = 1024,
                countsBase64 = BackupBinary.encode(ByteArray(4096) { (it % 61).toByte() }),
                source = "imported",
                note = null,
            ),
        ),
        slices = listOf(
            BackupSlice(now, now + 5_000, 5_000, "v1", 96, BackupBinary.encode(ByteArray(384)), 24f, 0.15f, 1),
        ),
        experiments = listOf(
            BackupExperiment(
                kind = "object",
                profileName = "Дом",
                createdAt = now,
                note = "кофе",
                geometry = "образец на столе",
                distanceCm = 5,
                placement = "стол",
                orientation = "сверху",
                plannedSeconds = 300,
                algorithmVersion = 2,
                params = "{}",
                runs = listOf(
                    BackupRun("A", now, now + 300_000, "spectrum-key", "{}", 5f, null),
                ),
            ),
        ),
    )

    private fun manifest() = BackupManifest(
        createdAt = "2026-08-17T02:25:00+03:00",
        appVersion = "0.7.7",
        databaseSchemaVersion = 16,
        deviceModel = "RadiaCode 110",
        content = BackupContent(),
    )

    private fun writeBackup(source: FakeSource): ByteArray {
        val out = ByteArrayOutputStream()
        runBlocking { BackupWriter(out).write(source, manifest()) }
        return out.toByteArray()
    }

    private fun open(bytes: ByteArray): () -> InputStream = { ByteArrayInputStream(bytes) }

    // --- сама проверка ----------------------------------------------------

    @Test
    fun `копия читается и возвращает то же самое`() {
        val source = fullSource(measurements = 5_000)
        val bytes = writeBackup(source)

        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        assertEquals(BackupFormat.VERSION, info.manifest.formatVersion)
        assertEquals("RadiaCode 110", info.manifest.deviceModel)
        assertEquals(5_000L, info.counts.measurements)
        assertEquals(1L, info.counts.sessions)
        assertEquals(1L, info.counts.spectra)
        assertEquals(2L, info.counts.templates)

        val sink = FakeSink()
        val summary = runBlocking {
            BackupReader.restore(open(bytes), info, RestoreMode.MERGE, RestoreSelection(), sink)
        }

        assertEquals(RestoreMode.MERGE, sink.mode)
        assertTrue(sink.finished)
        assertEquals(source.measurements, sink.measurements)
        assertEquals(source.sessions, sink.sessions)
        assertEquals(source.events, sink.events)
        assertEquals(source.rare, sink.rare)
        assertEquals(source.environment, sink.environment)
        assertEquals(source.stations, sink.stations)
        assertEquals(source.routes, sink.routes)
        assertEquals(source.points, sink.points)
        assertEquals(source.spectra, sink.spectra)
        assertEquals(source.templates, sink.templates)
        assertEquals(source.slices, sink.slices)
        assertEquals(source.experiments, sink.experiments)
        assertEquals(source.profiles, sink.profiles)
        assertEquals(source.settings, sink.settings)
        assertTrue(summary.settingsRestored)
        assertEquals(5_000L, summary.added[BackupStage.MEASUREMENTS])
    }

    @Test
    fun `повторный импорт той же копии ничего не удваивает`() {
        val bytes = writeBackup(fullSource(measurements = 100))
        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        val keys = mutableSetOf<String>()

        val first = FakeSink(keys)
        runBlocking {
            BackupReader.restore(open(bytes), info, RestoreMode.MERGE, RestoreSelection(), first)
        }
        val second = FakeSink(keys)
        val summary = runBlocking {
            BackupReader.restore(open(bytes), info, RestoreMode.MERGE, RestoreSelection(), second)
        }

        assertEquals(100, first.measurements.size)
        assertTrue(second.measurements.isEmpty(), "второй импорт добавил измерения заново")
        assertEquals(100L, summary.skipped[BackupStage.MEASUREMENTS])
        assertEquals(null, summary.added[BackupStage.MEASUREMENTS]?.takeIf { it > 0 })
    }

    @Test
    fun `испорченный архив не восстанавливается`() {
        val bytes = writeBackup(fullSource(measurements = 50))
        // Портим содержимое: один байт внутри сжатых данных.
        val broken = bytes.copyOf()
        broken[broken.size / 2] = (broken[broken.size / 2] + 1).toByte()

        val result = BackupReader.inspect(open(broken))
        assertTrue(result.isFailure, "испорченная копия обязана не пройти проверку")
    }

    @Test
    fun `копия более новой версии не разбирается наугад`() {
        val out = ByteArrayOutputStream()
        val future = manifest().copy(formatVersion = BackupFormat.VERSION + 1)
        runBlocking { BackupWriter(out).write(fullSource(measurements = 10), future) }

        val problem = BackupReader.inspect(open(out.toByteArray())).exceptionOrNull()
        val backup = problem as? BackupException
        assertTrue(backup?.problem is BackupProblem.TooNew, "ожидалась причина «копия новее»: $problem")
    }

    @Test
    fun `копия, переупакованная с папкой внутри, всё равно копия`() {
        // Файловые менеджеры и облака распаковывают и пакуют заново, кладя
        // части на уровень глубже. Части те же — отказывать незачем.
        val original = writeBackup(fullSource(measurements = 20))
        val nested = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(nested).use { out ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(original)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    out.putNextEntry(java.util.zip.ZipEntry("Alpha-backup/" + entry.name))
                    zip.copyTo(out)
                    out.closeEntry()
                }
            }
        }

        val info = BackupReader.inspect(open(nested.toByteArray())).getOrThrow()
        assertEquals(20L, info.counts.measurements)
    }

    @Test
    fun `пустой файл назван пустым, а не чужим`() {
        // «Это не копия» на пустом файле не подсказывает ничего; облако,
        // отдавшее заглушку, — самая частая причина.
        val problem = (BackupReader.inspect(open(ByteArray(0))).exceptionOrNull()
            as BackupException).problem
        assertEquals(BackupProblem.EmptyFile, problem)
    }

    @Test
    fun `чужой архив честно назван не копией`() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zip.write("не копия".toByteArray())
            zip.closeEntry()
        }
        val problem = BackupReader.inspect(open(out.toByteArray())).exceptionOrNull()
        assertTrue((problem as? BackupException)?.problem is BackupProblem.NotABackup)
    }

    @Test
    fun `пустая база даёт читаемую копию`() {
        val empty = FakeSource(BackupProfiles(), emptyList(), emptyList())
        val bytes = writeBackup(empty)
        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        assertEquals(0L, info.counts.measurements)

        val sink = FakeSink()
        runBlocking {
            BackupReader.restore(open(bytes), info, RestoreMode.MERGE, RestoreSelection(), sink)
        }
        assertTrue(sink.measurements.isEmpty())
        assertTrue(sink.finished)
    }

    @Test
    fun `выбор частей уважается`() {
        val bytes = writeBackup(fullSource(measurements = 20))
        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        val sink = FakeSink()
        runBlocking {
            BackupReader.restore(
                open(bytes),
                info,
                RestoreMode.MERGE,
                RestoreSelection(measurements = false, spectra = false),
                sink,
            )
        }
        assertTrue(sink.measurements.isEmpty())
        assertTrue(sink.spectra.isEmpty())
        assertFalse(sink.routes.isEmpty(), "маршруты не выключали")
        assertEquals(1, sink.profiles.profiles.size)
    }

    @Test
    fun `копия за период говорит о себе, что она за период`() {
        // Копия за месяц и копия за всё время выглядят одинаково; отличить их
        // через полгода будет неоткуда, если период не записан в самой копии.
        val bytes = ByteArrayOutputStream().also { out ->
            runBlocking {
                BackupWriter(out).write(
                    fullSource(measurements = 10),
                    manifest().copy(fromMillis = now - 30L * 24 * 60 * 60 * 1000),
                )
            }
        }.toByteArray()

        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        assertEquals(now - 30L * 24 * 60 * 60 * 1000, info.manifest.fromMillis)
    }

    @Test
    fun `копия прежней версии читается как копия за всё время`() {
        // У старых копий поля периода нет — и это не «период с нуля», а «всё».
        val bytes = writeBackup(fullSource(measurements = 10))
        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        assertEquals(null, info.manifest.fromMillis)
    }

    @Test
    fun `кириллица и разметка в заметках переживают копию`() {
        val bytes = writeBackup(fullSource(measurements = 1))
        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        val sink = FakeSink()
        runBlocking {
            BackupReader.restore(open(bytes), info, RestoreMode.MERGE, RestoreSelection(), sink)
        }
        assertEquals("Спектр «дома» <b>", sink.spectra.single().label)
    }

    @Test
    fun `двоичные данные возвращаются байт в байт`() {
        val original = ByteArray(4096) { (it % 251).toByte() }
        val bytes = writeBackup(fullSource(measurements = 1))
        val info = BackupReader.inspect(open(bytes)).getOrThrow()
        val sink = FakeSink()
        runBlocking {
            BackupReader.restore(open(bytes), info, RestoreMode.MERGE, RestoreSelection(), sink)
        }
        assertTrue(original.contentEquals(BackupBinary.decode(sink.spectra.single().countsBase64)))
    }

    @Test
    fun `прогресс доходит до конца ряда`() {
        val source = fullSource(measurements = 3_000)
        val out = ByteArrayOutputStream()
        var lastMeasurements = 0L
        runBlocking {
            BackupWriter(out, pageSize = 500).write(source, manifest()) { progress ->
                if (progress.stage == BackupStage.MEASUREMENTS) lastMeasurements = progress.done
            }
        }
        assertEquals(3_000L, lastMeasurements)
    }
}
