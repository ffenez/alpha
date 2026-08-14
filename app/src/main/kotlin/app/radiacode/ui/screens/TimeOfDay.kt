package app.radiacode.ui.screens

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

private val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
