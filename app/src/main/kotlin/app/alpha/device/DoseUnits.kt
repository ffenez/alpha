package app.alpha.device

/**
 * Dose-rate unit conversion for raw DATA_BUF values.
 *
 * The wire unit of `RealTimeData.doseRate` is not officially documented.
 * Field evidence (cdump/radiacode issue #56): a raw value of 0.0005 matches a
 * device display of 5 uSv/h, i.e. the raw unit behaves as rem/h (1 rem/h =
 * 10 000 uSv/h). We store raw values in the database (invariant: never lose
 * precision) and convert only at comparison/display boundaries through this
 * single constant.
 *
 * MUST be verified against the real device during the BLE spike; if the
 * factor is wrong, fixing it here fixes every consumer.
 */
object DoseUnits {
    const val RAW_TO_MICRO_SIEVERT_PER_HOUR = 10_000f

    fun rawToMicroSievertPerHour(raw: Float): Float = raw * RAW_TO_MICRO_SIEVERT_PER_HOUR
}
