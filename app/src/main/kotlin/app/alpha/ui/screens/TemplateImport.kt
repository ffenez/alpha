package app.alpha.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.SpectrumTemplate
import app.alpha.data.TemplateRepository
import app.alpha.data.export.BecqMoniXml
import app.alpha.data.export.BecqMoniXmlException
import app.alpha.data.export.SpeFile
import app.alpha.data.export.SpeFileException
import app.alpha.device.DeviceModel
import app.alpha.ui.text.UnmixStrings
import java.io.IOException
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Импорт шаблона из чужого файла: `.spe` (IAEA/Maestro) или BecqMoni XML.
 *
 * Шаблон из файла всегда чужой: серийник в него не пишется, поэтому
 * [TemplateRepository.fitness] увидит FOREIGN или откажет, а разрешение
 * измеряется по самому спектру — паспортного числа для чужого прибора нет.
 *
 * Два отказа, без которых форма молча испортила бы состав: файл без шкалы
 * энергий (приводить не к чему) и файл без времени накопления (доли считаются
 * от скорости счёта).
 */

/** Разбор чужого файла может дать что угодно; больше этого не читаем. */
private const val MAX_TEMPLATE_BYTES = 20 * 1024 * 1024

internal suspend fun importTemplateFile(
    graph: AppGraph,
    context: Context,
    uri: Uri,
    t: UnmixStrings,
): String = withContext(Dispatchers.IO) {
    val text = readText(context, uri) ?: return@withContext t.importUnreadable

    val parsed = try {
        parseTemplate(text)
    } catch (e: SpeFileException) {
        return@withContext "${t.importNotRecognised} ${e.message.orEmpty()}".trim()
    } catch (e: BecqMoniXmlException) {
        return@withContext "${t.importNotRecognised} ${e.message.orEmpty()}".trim()
    } catch (e: Throwable) {
        // Чужой файл — это ввод, а не код: он вправе нарушить любое допущение
        // разбора, но не уронить приложение.
        return@withContext "${t.importNotRecognised} ${e::class.simpleName}".trim()
    } ?: return@withContext t.importNotRecognised

    val calibration = parsed.calibration ?: return@withContext t.importNoScale
    if (parsed.counts.size < SpectrumTemplate.MIN_CHANNELS) return@withContext t.importNotRecognised
    val seconds = parsed.seconds?.takeIf { it > 0.0 }?.roundToLong()
        ?: return@withContext t.importNoTime

    val name = parsed.name ?: fileTitle(context, uri) ?: t.importedDefaultName
    graph.templateRepository.record(
        name = name,
        counts = parsed.counts,
        calibration = calibration,
        seconds = seconds,
        // Паспортного разрешения у чужого прибора нет; консервативное значение
        // используется, только если по спектру ширину линии измерить не вышло.
        resolution662 = DeviceModel.DEFAULT_RESOLUTION_662,
        deviceSerial = null,
        deviceName = parsed.deviceName,
        atMillis = parsed.atMillis ?: System.currentTimeMillis(),
    )
    t.imported(name)
}

/** Разобранный файл, приведённый к тому, что нужно шаблону. */
private class ParsedTemplate(
    val counts: List<Int>,
    val calibration: EnergyCalibration?,
    val seconds: Double?,
    val name: String?,
    val deviceName: String?,
    val atMillis: Long?,
)

private fun parseTemplate(text: String): ParsedTemplate? {
    val head = text.trimStart()
    return if (head.startsWith("<")) {
        val data = BecqMoniXml.parse(text).data
        val spectrum = data.spectrum
        ParsedTemplate(
            counts = spectrum.counts,
            calibration = spectrum.calibration
                ?.let { EnergyCalibration(it.a0.toFloat(), it.a1.toFloat(), it.a2.toFloat()) },
            // Живое время точнее полного: доли считаются от скорости счёта.
            seconds = spectrum.liveSeconds ?: spectrum.realSeconds,
            name = data.sampleName ?: data.sampleNote,
            deviceName = data.deviceName,
            atMillis = data.endMillis ?: data.startMillis,
        )
    } else {
        val data = SpeFile.parse(text).data
        ParsedTemplate(
            counts = data.counts,
            calibration = data.calibration?.let {
                shiftToZero(EnergyCalibration(it.a0.toFloat(), it.a1.toFloat(), it.a2.toFloat()), data.firstChannel)
            },
            seconds = data.liveSeconds,
            name = data.title,
            deviceName = null,
            atMillis = data.startMillis,
        )
    }
}

/**
 * Шкала файла считает от номера канала в файле, а шаблон хранит счёт с нуля.
 * При `$DATA:` от ненулевого канала подстановка ch = i + first даёт
 * a0' = a0 + a1·f + a2·f², a1' = a1 + 2·a2·f, a2' = a2.
 */
private fun shiftToZero(calibration: EnergyCalibration, firstChannel: Int): EnergyCalibration {
    if (firstChannel == 0) return calibration
    val f = firstChannel.toFloat()
    return EnergyCalibration(
        a0 = calibration.a0 + calibration.a1 * f + calibration.a2 * f * f,
        a1 = calibration.a1 + 2f * calibration.a2 * f,
        a2 = calibration.a2,
    )
}

private fun readText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readBytes()
        if (bytes.size > MAX_TEMPLATE_BYTES) null else String(bytes, Charsets.UTF_8)
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: Throwable) {
    null
}

/** Имя файла без расширения: им подписывается шаблон, если имени внутри нет. */
private fun fileTitle(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0 || !cursor.moveToFirst()) return@use null
        cursor.getString(index)
    }
}.getOrNull()
    ?.substringBeforeLast('.')
    ?.trim()
    ?.ifEmpty { null }
