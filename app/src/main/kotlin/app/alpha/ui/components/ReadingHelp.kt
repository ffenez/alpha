package app.alpha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.alpha.ui.text.LocalStrings

/**
 * Справка прибора: что значат мощность дозы и скорость счёта и как их читать.
 *
 * ## Одна на оба режима
 *
 * Наблюдение и Поиск показывают одни и те же две величины и подчиняются одному
 * правилу чтения — различается только знаменатель шкалы, и о нём в справке
 * сказано. Две отдельные справки об одном разошлись бы через полгода.
 *
 * ## Почему кнопка, а не текст на экране
 *
 * Это обучающий текст: он нужен один раз и не меняет ни одного показания,
 * поэтому живёт за кнопкой «i» и исчезает вместе с пояснениями
 * ([ExplainInfoButton]). Рабочий экран от него не зависит.
 */
@Composable
fun ReadingHelpButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    ExplainInfoButton(onClick = { open = true }, modifier = modifier)
    if (open) {
        ChartNotesDialog(
            title = strings.readingHelpTitle,
            notes = listOf(
                strings.readingHelpDoseRate,
                strings.readingHelpCountRate,
                strings.readingHelpNoise,
                strings.readingHelpScale,
            ),
            onClose = { open = false },
        )
    }
}

/**
 * Ряд с кнопкой справки у левого края карточки прибора.
 *
 * Отдельный компонент, а не голая кнопка в каждом экране: место кнопки —
 * часть дизайн-системы, и повторять выравнивание в двух режимах значило бы
 * дать им разойтись.
 */
@Composable
fun ReadingHelpRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        ReadingHelpButton()
    }
}
