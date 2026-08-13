package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.ui.logic.BackgroundCardModel
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Карточка записанного фона Поиска (ТЗ §10) — короткая.
 *
 * На рабочем экране стоят три вещи: уровень, момент с длительностью замера и —
 * если точка отсчёта больше не годится — одна строка с названной причиной.
 * Абзац, который раньше жил прямо здесь, ушёл под «i»: во время поиска человек
 * держит прибор в руках и читает карточку глазами, занятыми другим.
 *
 * Модель приходит готовой (`SearchBaseline.card`) — компонент ничего не решает
 * сам, поэтому решение «что на первом уровне» проверяется тестом.
 */
@Composable
fun BackgroundCard(
    model: BackgroundCardModel,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    if (detailsOpen && model.details.isNotEmpty()) {
        Dialog(onDismissRequest = { detailsOpen = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = model.level, style = type.label, color = colors.ink)
                    for (line in model.details) {
                        Text(text = line, style = type.bodySmall, color = colors.muted)
                    }
                    AppButton(
                        text = LocalStrings.current.close,
                        onClick = { detailsOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.level,
                    style = type.value,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (model.details.isNotEmpty()) {
                    Chip(text = "i", color = colors.ink2, onClick = { detailsOpen = true })
                }
            }
            model.basis?.let {
                Text(text = it, style = type.footnote, color = colors.ink2)
            }
            // Причина названа прямо в строке: «непригоден» без причины
            // заставляет гадать, что чинить.
            model.reason?.let {
                Text(text = it, style = type.bodySmall, color = colors.warn)
            }
            AppButton(
                text = model.action,
                onClick = onAction,
                primary = !model.usable,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
