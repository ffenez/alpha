package app.alpha.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.ui.logic.ProfileShift
import app.alpha.ui.logic.WhyInput
import app.alpha.ui.logic.WhyLine
import app.alpha.ui.logic.WhyReport
import app.alpha.ui.logic.WhyReportBuilder
import app.alpha.ui.logic.WhyLevel
import app.alpha.ui.logic.WhyScale
import app.alpha.ui.logic.WhySection
import app.alpha.ui.logic.WhyTone
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.MonitorCatalogue
import app.alpha.ui.text.MonitorStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * «Почему такой вывод» (why-spec).
 *
 * The order is the audit trail of the conclusion — **вывод → доказательство →
 * статистика → состояние профиля → критерии алгоритма** — разложенный по трём
 * уровням (14.md): первый экран отвечает «что это значит», «Показать методику
 * и расчёты» открывает статистику профиля и критерии, а внутри неё «Показать
 * технические параметры» — MAD, число корзин, формулы, χ² и z. Выбор второго
 * уровня запоминается.
 *
 * The sheet renders a [WhyReport]; it holds no wording of its own, because
 * every phrase here is pinned by `WhyReportTest`.
 */
@Composable
fun WhySheet(
    input: WhyInput,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** §7: the deviation has held long enough to ask about the place itself. */
    offerProfileShift: Boolean = false,
    onUpdateProfile: () -> Unit = {},
    onKeepProfile: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    val report = WhyReportBuilder.build(input, strings)
    // Экспертный уровень НЕ запоминается: методику открывают, чтобы читать её
    // дальше, а формулы — чтобы посмотреть один раз.
    var expert by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                // Шапка: имя и «×». Кнопки «Понятно» внизу нет — справка не
                // требует согласия, её закрывают касанием мимо, «назад» или
                // крестиком, и место внизу она занимала всегда.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.whyTitle,
                        style = type.title,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    AppCloseButton(onClose = onDismiss)
                }

                // Первый уровень отвечает на три вопроса: что происходит,
                // есть ли отклонение, почему такой вывод. Ничего из методики
                // здесь нет — она за строками раскрытия внизу.
                StatusRow(text = report.status, color = toneColor(report.tone))
                Hint(text = report.sentence, style = type.bodySmall, color = colors.ink2)

                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    HelpBlock(strings.nowSection, report.nowValue ?: "—")

                    // Фон места: сколько собрано из нужного и полоса под этим.
                    report.learning?.let { learning ->
                        HelpBlock(
                            title = t.placeBackgroundTitle,
                            value = t.collectedOf(learning.collected, learning.required),
                        ) {
                            ProgressBar(fraction = learning.fraction)
                        }
                    }
                    if (report.learning == null && report.usualValue != null) {
                        HelpBlock(strings.usualRangeHere, report.usualValue)
                    }

                    HelpBlock(t.comparisonTitle, report.comparison) {
                        report.scale?.let { BandScale(it, toneColor(report.tone)) }
                    }

                    report.excluded?.let { HelpBlock(t.excludedTitle, it) }

                    // Оговорка стоит на ПЕРВОМ уровне: она про смысл вывода, а
                    // не про его расчёт.
                    // «Не является заключением о радиационной безопасности» —
                    // не пояснение, а граница вывода: остаётся при любом
                    // положении переключателя.
                    if (report.caveat.isNotBlank()) {
                        Text(text = report.caveat, style = type.footnote, color = colors.ink2)
                    }

                    if (offerProfileShift) {
                        ProfileShiftBlock(
                            profileName = input.profileName,
                            onUpdate = onUpdateProfile,
                            onKeep = onKeepProfile,
                        )
                    }

                    // Второй уровень — двумя строками раскрытия, без кнопок
                    // «Скрыть …»: по умолчанию они закрыты, а шеврон говорит
                    // сам за себя.
                    if (report.hasAdvanced) {
                        DisclosureRow(
                            title = t.howDeviationTitle,
                            expanded = expanded,
                            onToggle = { onExpandedChange(!expanded) },
                        ) {
                            report.sections(WhyLevel.METHOD).forEach { SectionBlock(it) }
                            Hint(text = report.legend)
                        }
                    }
                    if (report.hasExpert) {
                        DisclosureRow(
                            title = t.technicalTitle,
                            expanded = expert,
                            onToggle = { expert = !expert },
                        ) {
                            report.sections(WhyLevel.EXPERT).forEach { SectionBlock(it) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Блок первого уровня: заголовок, значение и, при необходимости, что-то под
 * ними — полоса прогресса или шкала диапазона.
 */
@Composable
private fun HelpBlock(
    title: String,
    value: String,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        Text(text = title.uppercase(), style = type.labelSmall, color = colors.muted)
        Text(text = value, style = type.value, color = colors.ink)
        content?.invoke()
    }
}

/**
 * Полоса сбора фона.
 *
 * Число «0,9 ч из 3 ч» отвечает на вопрос точно, полоса — мгновенно: сколько
 * осталось, видно, не читая.
 */
@Composable
private fun ProgressBar(fraction: Float) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.dataText),
        )
    }
}

/**
 * The P10–P90 band with the current value on it (§2): a position is read
 * faster than one more line of text, and it is the same statement — the dot
 * outside the band is exactly what «выше P90» means.
 */
@Composable
private fun BandScale(scale: WhyScale, tone: Color) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            val trackY = size.height / 2f
            val radius = 5.dp.toPx()
            // The band itself; the track outside it stays visible so «выше
            // P90» has somewhere to be drawn.
            drawLine(
                color = colors.line,
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 2.dp.toPx(),
            )
            val bandStart = size.width * 0.15f
            val bandWidth = size.width * 0.70f
            drawRect(
                color = colors.ink2.copy(alpha = 0.18f),
                topLeft = Offset(bandStart, trackY - 6.dp.toPx()),
                size = Size(bandWidth, 12.dp.toPx()),
            )
            val x = bandStart + bandWidth * scale.position
            drawCircle(color = colors.surface, radius = radius + 2f, center = Offset(x, trackY))
            drawCircle(color = tone, radius = radius, center = Offset(x, trackY))
        }
        // Засечки шкалы — P10 · медиана · P90. Текущее значение подписано
        // парой «Сейчас ↔ Обычный диапазон здесь» над шкалой и не дублируется.
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "P10 ${scale.lowLabel}", style = type.axis, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${strings.median.lowercase()} ${scale.medianLabel}",
                style = type.axis,
                color = colors.muted,
            )
            Spacer(Modifier.weight(1f))
            Text(text = "P90 ${scale.highLabel}", style = type.axis, color = colors.muted)
        }
        // Точка на краю обязана себя назвать: иначе «вне диапазона» читается
        // как «ровно на границе».
        if (scale.outside) {
            Text(
                text = MonitorCatalogue.of(strings.language).outsideBand(scale.currentLabel),
                style = type.axis,
                color = colors.ink2,
            )
        }
    }
}

/**
 * «Уровень изменился надолго» (§7). The app may ask; only the user may answer,
 * and both answers are spelled out before either is pressed — the update is
 * not something the app can undo for them.
 */
@Composable
private fun ProfileShiftBlock(
    profileName: String?,
    onUpdate: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t: MonitorStrings = MonitorCatalogue.of(LocalStrings.current.language)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        StatusRow(text = ProfileShift.title(t), color = colors.warn)
        Text(
            text = ProfileShift.sentence(profileName, t),
            style = type.bodySmall,
            color = colors.ink2,
        )
        Hint(text = ProfileShift.explanation(t))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = ProfileShift.updateAction(t),
                onClick = onUpdate,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = ProfileShift.keepAction(t),
                onClick = onKeep,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionBlock(section: WhySection) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            if (section.tone != WhyTone.UNKNOWN) {
                Spacer(Modifier.weight(1f))
                StatusDot(toneColor(section.tone))
            }
        }
        section.lines.forEach { WhyRow(it) }
        // Пояснение секции — то же пояснение, что и на экранах: числа и
        // вывод остаются, объяснение выключается вместе со всеми.
        section.critical?.let {
            Text(
                text = it,
                style = type.footnote,
                color = colors.ink2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        section.note?.let {
            Hint(text = it, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** §14: colour is the state of the comparison, never a safety verdict. */
@Composable
private fun toneColor(tone: WhyTone): Color {
    val colors = LocalAppColors.current
    return when (tone) {
        WhyTone.OK -> colors.ok
        WhyTone.ATTENTION -> colors.warn
        WhyTone.ALARM -> colors.crit
        WhyTone.UNKNOWN -> colors.muted
    }
}

/** One «label — value — tag» row; shared with the Поиск sheet. */
@Composable
internal fun WhyRow(line: WhyLine) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Ширину делит подпись (она переносится словами), у значения есть
        // неприкосновенный минимум; не поместилось вместе — значение уходит
        // целой строкой ниже и никогда не рвётся посимвольно.
        BoxWithConstraints {
            val narrow = maxWidth < NARROW_ROW_WIDTH
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = line.label, style = type.bodySmall, color = colors.ink2)
                    Text(text = line.value, style = type.value, color = colors.ink)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = line.label,
                        style = type.bodySmall,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(Dimens.space2))
                    Text(
                        text = line.value,
                        style = type.value,
                        color = colors.ink,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = MIN_VALUE_WIDTH),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        // Критическое — всегда: ±σ, объём замера, «сравнивать не с чем».
        // Пояснение — вместе со всеми пояснениями.
        line.critical?.let {
            Text(text = it, style = type.footnote, color = colors.ink2)
        }
        line.note?.let { Hint(text = it) }
    }
}

/**
 * Ниже этой ширины строка раскладывается в два ряда.
 * **Инженерный параметр**: под 240 dp подпись и число одной строкой уже
 * дерутся за место, и проигрывает всегда число.
 */
private val NARROW_ROW_WIDTH = 240.dp

/** Неприкосновенная ширина значения: «2 × P90 профиля» — это одна строка. */
private val MIN_VALUE_WIDTH = 96.dp
