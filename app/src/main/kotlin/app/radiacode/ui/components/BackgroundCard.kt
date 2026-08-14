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
 * Справки здесь нет: во время поиска человек держит прибор в руках, и лишняя
 * дверь на рабочем экране — это дверь, в которую он не пойдёт. Причина
 * непригодности названа прямо строкой, а не спрятана за значком.
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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            // Само значение фона здесь не повторяется: оно стоит плиткой
            // рядом с отношением к нему, там, где его и читают. Карточке
            // остаётся то, чего больше нигде нет: когда фон снят, годится ли
            // он и что с ним делать.
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
