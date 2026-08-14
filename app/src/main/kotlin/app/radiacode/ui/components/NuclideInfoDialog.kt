package app.radiacode.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.radiacode.analysis.Nuclide
import app.radiacode.ui.logic.NuclideCard
import app.radiacode.ui.logic.NuclideCardModel
import app.radiacode.ui.logic.NuclideCardTone
import app.radiacode.ui.logic.NuclideCheck
import app.radiacode.ui.logic.NuclideLineRow
import app.radiacode.ui.logic.NuclideLineVerdict
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.NuclideCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.ui.theme.Motion

/**
 * Справка о нуклиде — bottom sheet, открываемый из строки кандидата на Спектре.
 *
 * Порядок сверху вниз отвечает на вопрос, с которым сюда приходят: **что
 * совпало → какие линии проверялись → что за нуклид → где встречается → что
 * усилило бы гипотезу → ограничение метода**. Энциклопедия (полный список
 * линий, provenance чисел) свёрнута вторым уровнем.
 *
 * Карточка НИЧЕГО не решает сама: весь текст и все вердикты собирает чистая
 * [NuclideCard.build] из результата того же сопоставления, что дало кандидата в
 * списке. Заголовок закреплён, «×» в шапке, системный «назад» закрывает лист —
 * большой кнопки «Закрыть» внизу нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuclideInfoDialog(
    nuclide: Nuclide,
    /** Проверка кандидата движком доказательств; null — спектра нет. */
    check: NuclideCheck? = null,
    /**
     * Тап по строке линии: показать эту энергию отметкой на спектре. Лист
     * закрывает ВЫЗЫВАЮЩИЙ экран — картинка под листом всё равно не видна, и
     * оставлять её закрытой после нажатия «покажи на спектре» бессмысленно.
     * null — строки не нажимаются (спектра под листом нет).
     */
    onShowOnSpectrum: ((Float) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = NuclideCatalogue.of(LocalStrings.current.language)
    val model = remember(nuclide, check, t) { NuclideCard.build(nuclide, check, t) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.ink,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.line) },
    ) {
        // Шапка живёт ВНЕ прокручиваемой колонки: имя нуклида должно оставаться
        // на экране, иначе на середине таблицы уже не видно, о ком речь.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.space4, end = Dimens.space2, bottom = Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.title,
                style = type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            CloseButton(label = t.close, onClick = onDismiss)
        }
        AppDivider()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                // Снизу воздуха больше: последний блок не должен упираться в
                // край листа и системную навигацию.
                .padding(
                    start = Dimens.space4,
                    end = Dimens.space4,
                    top = Dimens.space4,
                    bottom = Dimens.space8,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.space4),
        ) {
            CardBody(model, onShowOnSpectrum)
        }
    }
}

/** «×» в шапке: цель нажатия во всю зону большого пальца, а не глиф в 12 dp. */
@Composable
private fun CloseButton(label: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Box(
        modifier = Modifier
            .size(Dimens.touchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "×", style = type.title, color = colors.ink2)
    }
}

@Composable
private fun CardBody(model: NuclideCardModel, onShowOnSpectrum: ((Float) -> Unit)?) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    // 1. Статус: зачем этот нуклид вообще показан.
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        StatusRow(
            text = model.status.headline,
            color = when (model.status.tone) {
                NuclideCardTone.DATA -> colors.dataText
                NuclideCardTone.UNCERTAIN -> colors.warn
                NuclideCardTone.NEUTRAL -> colors.muted
            },
        )
        Text(text = model.status.detail, style = type.bodySmall, color = colors.ink2)
    }

    // 2. Проверка по линиям — главный блок карточки.
    Section(model.sectionLineCheck) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ColumnLabel(model.columnLine, 1.4f)
            ColumnLabel(model.columnYield, 1f)
            ColumnLabel(model.columnResult, 1.6f)
        }
        model.lineCheck.forEach { row ->
            LineRow(row, onShowOnSpectrum?.let { show -> { show(row.energyKeV) } })
        }
        // Нажимаемость строки названа один раз под таблицей: значок «›» в
        // каждой строке спорил бы с значком вердикта, а он несёт смысл.
        if (onShowOnSpectrum != null) {
            Text(text = model.lineTapHint, style = type.footnote, color = colors.ink2)
        }
        model.ratio.forEach { line ->
            Text(text = line, style = type.footnote, color = colors.muted)
        }
        Text(text = model.yieldNote, style = type.footnote, color = colors.muted)
    }

    // 3. Свойства нуклида — один компактный блок, без повторов происхождения.
    Section(model.sectionAbout) {
        model.about.forEach { fact ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fact.label,
                    style = type.footnote,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = fact.value,
                    style = type.bodySmall,
                    color = colors.ink,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }

    Section(model.sectionEveryday) {
        Text(text = model.everyday, style = type.bodySmall, color = colors.ink2)
    }

    Section(model.sectionStrengthen) {
        model.strengthen.forEach { bullet ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "•", style = type.bodySmall, color = colors.muted)
                Text(
                    text = bullet,
                    style = type.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.padding(start = Dimens.space2),
                )
            }
        }
        Text(text = model.strengthenNote, style = type.footnote, color = colors.muted)
    }

    // 4. Одна оговорка про метод — внизу и только здесь.
    Section(model.sectionLimitation) {
        Text(text = model.limitation, style = type.bodySmall, color = colors.muted)
    }

    // 5. Второй уровень: полный список линий и происхождение чисел.
    Disclosure(model.allLinesLabel) {
        model.allLines.forEach { line ->
            Text(text = line, style = type.valueSmall, color = colors.ink)
        }
    }
    // Откуда числа — одной строкой. Построчный список источников и
    // неопределённостей снят: он повторял для каждой линии один и тот же
    // источник и один и тот же отказ назвать неопределённость.
    Text(text = model.provenance.summary, style = type.footnote, color = colors.muted)
}

/** Подпись колонки таблицы — тише данных, но на своём месте по ширине. */
@Composable
private fun RowScope.ColumnLabel(text: String, weight: Float) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Text(
        text = text,
        style = type.labelSmall,
        color = colors.muted,
        modifier = Modifier.weight(weight),
    )
}

/** Заголовок раздела и его содержимое; разделитель отбивает раздел от соседа. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Text(text = title.uppercase(), style = type.labelSmall, color = colors.ink2)
        AppDivider()
        content()
    }
}

/**
 * Строка таблицы проверки. Значок дублирует слово, а не заменяет его: вердикт
 * не должен читаться цветом или формой в одиночку.
 *
 * Строка нажимается ([onShow]): по нажатию лист закрывается, а энергия линии
 * отмечается на спектре. Цель нажатия — вся строка высотой не меньше
 * [Dimens.touchTarget]; действие названо словами для экранного диктора, потому
 * что визуально строка остаётся строкой таблицы.
 */
@Composable
private fun LineRow(row: NuclideLineRow, onShow: (() -> Unit)?) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val matched = row.verdict == NuclideLineVerdict.MATCHED
    Column(
        modifier = if (onShow == null) {
            Modifier
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget)
                .clickable(onClickLabel = row.actionLabel, onClick = onShow)
        },
        verticalArrangement = Arrangement.Center,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.energyText,
                style = type.footnoteMono,
                color = if (matched) colors.ink else colors.ink2,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                text = row.yieldText,
                style = type.footnoteMono,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${verdictMark(row.verdict)} ${row.verdictText}",
                style = type.footnote,
                color = if (matched) colors.dataText else colors.muted,
                modifier = Modifier.weight(1.6f),
            )
        }
        // Измеренная энергия и ΔE — только там, где их знает matcher.
        row.measuredText?.let { text ->
            Text(
                text = text,
                style = type.footnote,
                color = colors.muted,
                modifier = Modifier.padding(start = Dimens.space3),
            )
        }
    }
}

private fun verdictMark(verdict: NuclideLineVerdict): String = when (verdict) {
    NuclideLineVerdict.MATCHED -> "●"
    NuclideLineVerdict.NOT_FOUND -> "○"
    NuclideLineVerdict.INDISTINGUISHABLE -> "◌"
    NuclideLineVerdict.UNDETERMINED -> "·"
    NuclideLineVerdict.OUT_OF_SCALE -> "·"
    NuclideLineVerdict.NOT_EVALUATED -> "·"
}

/** Свёрнутый второй уровень: по умолчанию закрыт, раскрывается нажатием. */
@Composable
private fun Disclosure(label: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.animateContentSize(Motion.contentSize()),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Поле, а не студия: строка раскрытия — полноценная цель нажатия.
                .heightIn(min = Dimens.touchTarget)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = type.label,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Text(text = if (expanded) "▴" else "▾", style = type.label, color = colors.ink2)
        }
        if (expanded) content()
    }
}
