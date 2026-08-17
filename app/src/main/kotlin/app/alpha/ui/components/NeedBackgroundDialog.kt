package app.alpha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * «Фон ещё не записан» — ответ на нажатие того, что без фона не работает.
 *
 * Раньше такие элементы просто выключались. Выключенная кнопка не объясняет
 * ничего: человек нажимает, ничего не происходит, и остаётся гадать — то ли
 * приложение сломалось, то ли он чего-то не сделал. Здесь действие остаётся
 * живым и на нажатие отвечает: что оно делает, чего ему не хватает и каким
 * одним действием это исправить.
 *
 * Первым идёт объяснение САМОГО нажатого элемента ([what]) — вычитание и серая
 * кривая делают разное, и общая фраза «нужен фон» не сказала бы, зачем он тут
 * нужен.
 */
@Composable
fun NeedBackgroundDialog(
    /** Что делает нажатый элемент и почему без фона не может. */
    what: String,
    /** Кнопка записи; null — записывать нечем (прибор не подключён). */
    onRecord: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.needBackgroundTitle, style = type.title, color = colors.ink)
                Text(text = what, style = type.bodySmall, color = colors.ink2)
                Text(text = t.needBackgroundHow, style = type.bodySmall, color = colors.ink2)
                if (onRecord == null) {
                    Text(
                        text = t.needBackgroundNoDevice,
                        style = type.footnote,
                        color = colors.muted,
                    )
                } else {
                    AppButton(
                        text = t.setAsBackground,
                        onClick = {
                            onRecord()
                            onDismiss()
                        },
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = strings.close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
