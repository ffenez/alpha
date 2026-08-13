package app.radiacode.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.RcXmlException
import app.radiacode.protocol.Spectrum
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SpectrumCatalogue
import app.radiacode.ui.text.SpectrumRu
import app.radiacode.ui.text.SpectrumStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared SAF plumbing for spectrum files (Спектр and История): stream I/O on
 * the IO dispatcher plus the import transaction. All format logic lives in
 * `data/export` (pure, JVM-tested); this file only moves bytes and words the
 * outcome for dialogs.
 */

/** User-facing outcome of a file operation. */
internal data class SpectrumFileNotice(
    val title: String,
    val lines: List<String>,
    val isError: Boolean = false,
)

/** Outcome dialog shared by the Спектр and История file flows. */
@Composable
internal fun SpectrumFileNoticeDialog(notice: SpectrumFileNotice, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = SpectrumCatalogue.of(LocalStrings.current.language)
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = notice.title,
                    style = type.title,
                    color = if (notice.isError) colors.warn else colors.ink,
                )
                notice.lines.forEach { line ->
                    Text(text = line, style = type.body, color = colors.ink2)
                }
                AppButton(text = t.acknowledge, onClick = onDismiss)
            }
        }
    }
}

/** App version for export metadata; null if the package manager balks. */
internal fun appVersionName(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()

/** Import size guard: honest refusal instead of an OOM on a wrong file. */
private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

/** Двоичная запись в выбранный файл — для архива отладки. */
internal suspend fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(bytes)
                true
            } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

internal suspend fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

/**
 * Reads, parses and stores an RC-XML file: the main spectrum as an imported
 * snapshot (labeled, timestamped by the file's own EndTime when present) and
 * its BackgroundEnergySpectrum — the raw data — as a second labeled row.
 */
internal suspend fun importRcXmlFile(
    graph: AppGraph,
    context: Context,
    uri: Uri,
    zone: ZoneId = ZoneId.systemDefault(),
    s: SpectrumStrings = SpectrumRu,
): SpectrumFileNotice = withContext(Dispatchers.IO) {
    val text = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > MAX_IMPORT_BYTES) {
                return@withContext SpectrumFileNotice(
                    title = s.importFailed,
                    lines = listOf(s.importTooLarge),
                    isError = true,
                )
            }
            String(bytes, Charsets.UTF_8)
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: Throwable) {
        // Провайдер чужого приложения может бросить что угодно (включая
        // IllegalStateException и OutOfMemoryError на подставном потоке);
        // «не смог прочитать файл» — верный ответ на всё это, падение — нет.
        null
    } ?: return@withContext SpectrumFileNotice(
        title = s.importFailed,
        lines = listOf(s.importUnreadable),
        isError = true,
    )

    val parsed = try {
        RcXml.parse(text, zone)
    } catch (e: RcXmlException) {
        return@withContext SpectrumFileNotice(
            title = s.importFailed,
            lines = listOf(
                e.message ?: s.importNotRecognised,
                s.importChooseXml,
            ),
            isError = true,
        )
    } catch (e: Throwable) {
        // Чужой файл — это ввод, а не код: он может нарушить любое допущение
        // разбора, и падение приложения на чужом файле недопустимо. Класс и
        // сообщение печатаются на экране НАМЕРЕННО: человеку их не понять, но
        // именно их он присылает, когда импорт не удался, и по ним видно
        // место без отладочного архива.
        return@withContext SpectrumFileNotice(
            title = s.importFailed,
            lines = listOf(
                s.importNotRecognised,
                "${e::class.simpleName}: ${e.message.orEmpty()}".trim(),
            ),
            isError = true,
        )
    }

    val data = parsed.data
    // Метка снимка уезжает в базу (`spectra.label`) и в файл экспорта: она НЕ
    // переводится, иначе снимок, импортированный по-русски, после смены языка
    // остался бы русским — половина перевода хуже честного «по разделам».
    val label = data.sampleName ?: data.spectrum.name ?: "Импортированный спектр"
    val main = data.spectrum
    val background = data.background
    try {
        graph.measurementRepository.importSpectrum(
            spectrum = Spectrum(
                durationSeconds = main.measurementSeconds,
                a0 = main.a0,
                a1 = main.a1,
                a2 = main.a2,
                counts = main.counts,
            ),
            label = label,
            timestamp = data.endMillis,
        )
        if (background != null) {
            graph.measurementRepository.importSpectrum(
                spectrum = Spectrum(
                    durationSeconds = background.measurementSeconds,
                    a0 = background.a0,
                    a1 = background.a1,
                    a2 = background.a2,
                    counts = background.counts,
                ),
                // Тоже метка в базе — по той же причине без языка.
                label = "$label · фон",
                timestamp = data.endMillis,
            )
        }
    } catch (e: Throwable) {
        // Запись в журнал — последний шаг, и он тоже не имеет права ронять
        // приложение: снимок не сохранён, об этом надо сказать, а не упасть.
        return@withContext SpectrumFileNotice(
            title = s.importFailed,
            lines = listOf(
                s.importNotSaved,
                "${e::class.simpleName}: ${e.message.orEmpty()}".trim(),
            ),
            isError = true,
        )
    }

    val lines = mutableListOf(
        s.importedSummary(
            label = label,
            channels = s.channels(main.counts.size),
            clock = SpectrumFormat.accumulationClock(main.measurementSeconds),
        ),
    )
    if (background != null) {
        lines += s.importedBackgroundRow
    }
    lines += parsed.warnings
    lines += s.importedInHistory
    SpectrumFileNotice(title = s.importedTitle, lines = lines)
}
