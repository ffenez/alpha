package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Переименование записи — одно окно на все записи.
 *
 * Маршрут, спектр и любая будущая запись зовутся одинаково: поле, «Сохранить»,
 * «Отмена». Своё окно у каждой сущности означало бы, что через полгода они
 * разойдутся в мелочах — где-то подсказка, где-то нет, где-то кнопка первая.
 */
@Composable
fun RenameDialog(
    title: String,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Приведение имени к допустимому виду по ходу набора. */
    clean: (String) -> String = { it },
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    var text by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = title, style = type.label, color = colors.ink)
                AppTextField(
                    value = text,
                    onValueChange = { text = clean(it) },
                    placeholder = placeholder,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = strings.saveName,
                        onClick = { onSave(text) },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.cancel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Подтверждение необратимого действия.
 *
 * Текст говорит о ПОСЛЕДСТВИИ, а не переспрашивает «вы уверены?»: уверенность
 * человека — не то, что стоит выяснять, а вот «вместе с записью уйдут её
 * измерения» он мог не знать.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = title, style = type.title, color = colors.ink)
                Text(text = body, style = type.bodySmall, color = colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(text = confirmText, onClick = onConfirm)
                    AppButton(text = strings.cancel, onClick = onDismiss)
                }
            }
        }
    }
}
