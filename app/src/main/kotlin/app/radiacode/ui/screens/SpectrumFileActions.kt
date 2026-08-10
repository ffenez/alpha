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
                AppButton(text = "Понятно", onClick = onDismiss)
            }
        }
    }
}

/** Import size guard: honest refusal instead of an OOM on a wrong file. */
private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

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
): SpectrumFileNotice = withContext(Dispatchers.IO) {
    val text = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > MAX_IMPORT_BYTES) {
                return@withContext SpectrumFileNotice(
                    title = "Импорт не удался",
                    lines = listOf(
                        "Файл больше 20 МБ — это не похоже на спектр RadiaCode.",
                    ),
                    isError = true,
                )
            }
            String(bytes, Charsets.UTF_8)
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } ?: return@withContext SpectrumFileNotice(
        title = "Импорт не удался",
        lines = listOf("Файл не удалось прочитать — попробуйте выбрать его ещё раз."),
        isError = true,
    )

    val parsed = try {
        RcXml.parse(text, zone)
    } catch (e: RcXmlException) {
        return@withContext SpectrumFileNotice(
            title = "Импорт не удался",
            lines = listOf(
                e.message ?: "файл не распознан",
                "Выберите XML-файл спектра RadiaCode.",
            ),
            isError = true,
        )
    }

    val data = parsed.data
    val label = data.sampleName ?: data.spectrum.name ?: "Импортированный спектр"
    val main = data.spectrum
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
    val background = data.background
    if (background != null) {
        graph.measurementRepository.importSpectrum(
            spectrum = Spectrum(
                durationSeconds = background.measurementSeconds,
                a0 = background.a0,
                a1 = background.a1,
                a2 = background.a2,
                counts = background.counts,
            ),
            label = "$label · фон",
            timestamp = data.endMillis,
        )
    }

    val lines = mutableListOf(
        "«$label» · ${main.counts.size} каналов · " +
            "Δt ${SpectrumFormat.accumulationClock(main.measurementSeconds)}",
    )
    if (background != null) {
        lines += "Фоновый спектр из файла сохранён отдельной строкой."
    }
    lines += parsed.warnings
    lines += "Снимок появился в Истории — там же сравнение и экспорт."
    SpectrumFileNotice(title = "Спектр импортирован", lines = lines)
}
