package app.alpha.ui.screens

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «14:03» — час и минута локального времени.
 *
 * Один помощник на пакет: три экрана держали три одинаковых частных копии, и
 * любая правка формата чинилась бы в трёх местах, а замечалась в одном.
 */
internal fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_OF_DAY)

/** «14:03:25» — там, где секунда сама по себе факт: начало интервала записи. */
internal fun timeOfDayWithSeconds(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_WITH_SECONDS)

private val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_WITH_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
