package app.radiacode.data.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * ANSI/IEEE N42.42-2011 export (the NIST radiation-instrument data format
 * analysis tools like InterSpec read). Written against the published schema
 * structure (namespace `http://physics.nist.gov/N42/2011/N42`): document →
 * RadInstrumentInformation → RadDetectorInformation → EnergyCalibration* →
 * RadMeasurement* with Spectrum/ChannelData and ISO-8601 durations.
 *
 * Own clean implementation; semantics cross-checked against the MIT-licensed
 * ckuethe/radiacode-tools n42convert output for the same device family.
 * Only what the app honestly knows is written: no invented detector
 * dimensions or efficiencies.
 */
object N42 {

    /** N42 class codes this exporter uses. */
    const val CLASS_FOREGROUND = "Foreground"
    const val CLASS_BACKGROUND = "Background"

    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** One measurement (a spectrum with its calibration and time bracket). */
    data class Measurement(
        /** [CLASS_FOREGROUND] or [CLASS_BACKGROUND]. */
        val classCode: String,
        /** Measurement start, epoch millis. */
        val startMillis: Long,
        /** Live/real time, seconds (the device reports a single duration). */
        val durationSeconds: Long,
        /** E(keV) = a0 + a1·ch + a2·ch². */
        val a0: Float,
        val a1: Float,
        val a2: Float,
        val counts: List<Int>,
    )

    fun write(
        foreground: Measurement,
        background: Measurement? = null,
        serialNumber: String? = null,
        model: String = "RadiaCode",
        softwareVersion: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        documentUuid: UUID = UUID.randomUUID(),
    ): String {
        val sb = StringBuilder(32 * 1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<RadInstrumentData xmlns=\"http://physics.nist.gov/N42/2011/N42\"")
        sb.append(" n42DocUUID=\"").append(documentUuid).append("\">\n")

        sb.append("  <RadInstrumentDataCreatorName>app.radiacode alpha")
            .append("</RadInstrumentDataCreatorName>\n")

        // Instrument: manufacturer/model from the device identity, our app as
        // the software component.
        sb.append("  <RadInstrumentInformation id=\"instrument-1\">\n")
        sb.append("    <RadInstrumentManufacturerName>RadiaCode")
            .append("</RadInstrumentManufacturerName>\n")
        serialNumber?.let {
            sb.append("    <RadInstrumentIdentifier>").append(escape(it))
                .append("</RadInstrumentIdentifier>\n")
        }
        sb.append("    <RadInstrumentModelName>").append(escape(model))
            .append("</RadInstrumentModelName>\n")
        sb.append("    <RadInstrumentClassCode>Spectroscopic Personal Radiation Detector")
            .append("</RadInstrumentClassCode>\n")
        sb.append("    <RadInstrumentVersion>\n")
        sb.append("      <RadInstrumentComponentName>Software")
            .append("</RadInstrumentComponentName>\n")
        sb.append("      <RadInstrumentComponentVersion>")
            .append(escape(softwareVersion ?: "unknown"))
            .append("</RadInstrumentComponentVersion>\n")
        sb.append("    </RadInstrumentVersion>\n")
        sb.append("  </RadInstrumentInformation>\n")

        // Detector: CsI(Tl) gamma scintillator — that much is device fact.
        sb.append("  <RadDetectorInformation id=\"detector-1\">\n")
        sb.append("    <RadDetectorCategoryCode>Gamma</RadDetectorCategoryCode>\n")
        sb.append("    <RadDetectorKindCode>CsI</RadDetectorKindCode>\n")
        sb.append("    <RadDetectorDescription>CsI(Tl) scintillator")
            .append("</RadDetectorDescription>\n")
        sb.append("  </RadDetectorInformation>\n")

        appendCalibration(sb, "calibration-fg", foreground)
        background?.let { appendCalibration(sb, "calibration-bg", it) }

        appendMeasurement(sb, "fg", foreground, zone)
        background?.let { appendMeasurement(sb, "bg", it, zone) }

        sb.append("</RadInstrumentData>\n")
        return sb.toString()
    }

    private fun appendCalibration(sb: StringBuilder, id: String, m: Measurement) {
        sb.append("  <EnergyCalibration id=\"").append(id).append("\">\n")
        sb.append("    <CoefficientValues>")
            .append(m.a0).append(' ').append(m.a1).append(' ').append(m.a2)
            .append("</CoefficientValues>\n")
        sb.append("  </EnergyCalibration>\n")
    }

    private fun appendMeasurement(sb: StringBuilder, key: String, m: Measurement, zone: ZoneId) {
        val duration = "PT" + m.durationSeconds + "S"
        sb.append("  <RadMeasurement id=\"measurement-").append(key).append("\">\n")
        sb.append("    <MeasurementClassCode>").append(m.classCode)
            .append("</MeasurementClassCode>\n")
        sb.append("    <StartDateTime>")
            .append(Instant.ofEpochMilli(m.startMillis).atZone(zone).format(DATE_TIME))
            .append("</StartDateTime>\n")
        // The device reports one accumulation duration; real time and live
        // time are written equal (dead time is not reported).
        sb.append("    <RealTimeDuration>").append(duration).append("</RealTimeDuration>\n")
        sb.append("    <Spectrum id=\"spectrum-").append(key).append('"')
            .append(" radDetectorInformationReference=\"detector-1\"")
            .append(" energyCalibrationReference=\"calibration-").append(key).append("\">\n")
        sb.append("      <LiveTimeDuration>").append(duration).append("</LiveTimeDuration>\n")
        sb.append("      <ChannelData compressionCode=\"None\">")
        m.counts.forEachIndexed { index, count ->
            if (index > 0) sb.append(' ')
            sb.append(count)
        }
        sb.append("</ChannelData>\n")
        sb.append("    </Spectrum>\n")
        sb.append("  </RadMeasurement>\n")
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }
}
