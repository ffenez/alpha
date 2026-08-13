package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Оговорки о том, КАК посчитана картинка — под кнопкой «i», а не под полем.
 *
 * Правило проекта осталось прежним: если способ расчёта может изменить
 * прочтение данных, о нём обязаны сказать. Изменилось место. Постоянная полоса
 * мелкого текста под графиком читается один раз, а высоту забирает всегда — и
 * на экране, собранном ради картинки, она вытесняет саму картинку. Здесь тот
 * же текст лежит там, где его спрашивают.
 *
 * Диалог, а не блок в потоке: объяснение не имеет права двигать график, к
 * которому относится.
 */
@Composable
fun ChartNotesDialog(notes: List<String>, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                for (note in notes) {
                    Text(text = note, style = type.bodySmall, color = colors.ink2)
                }
                Chip(text = strings.close, color = colors.ink2, onClick = onClose)
            }
        }
    }
}
