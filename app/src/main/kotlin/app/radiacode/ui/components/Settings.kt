package app.radiacode.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.ui.theme.Motion

/**
 * Язык настроек: строка, значение, переключатель.
 *
 * ## Зачем один набор
 *
 * Настройки собрались из разных времён и говорили на разных языках: где-то
 * настоящий выбор, где-то чип «Вкл/Выкл», притворяющийся переключателем,
 * где-то абзац объяснения там, где хватило бы значения справа. Экран из-за
 * этого читается как склад параметров, а не как список того, что можно
 * изменить.
 *
 * Здесь ровно четыре элемента, и у каждого одна работа:
 *
 * - [SettingsSection] — группа с заголовком; ОДНА плоскость на группу, а не
 *   карточка на каждую настройку;
 * - [SettingRow] — «название · текущее значение ›»: значение видно ДО входа,
 *   и ради того, чтобы узнать состояние, никуда идти не нужно;
 * - [SwitchSettingRow] — двоичное состояние настоящим переключателем;
 * - [SettingsTopBar] — «‹ Название»: один заголовок на экран.
 */
@Composable
fun SettingsSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
                modifier = Modifier.padding(
                    start = Dimens.space2,
                    bottom = Dimens.space1,
                ),
            )
        }
        Card(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Column(content = content)
        }
    }
}

/**
 * Строка настройки: название, при необходимости пояснение под ним, текущее
 * значение справа и знак перехода, если строка куда-то ведёт.
 *
 * Пояснение — одна короткая фраза и только там, где без неё название
 * непонятно. Абзац на первом уровне означает, что настройку стоит назвать
 * лучше, а не объяснить длиннее.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    /** Значение выделено цветом данных — когда оно само по себе состояние. */
    valueHighlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.body,
                color = if (enabled) colors.ink else colors.muted,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = type.footnoteMono,
                color = if (valueHighlighted) colors.dataText else colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) NavArrow()
    }
}

/**
 * Строка редкого выбора: значение справа, варианты — списком поверх.
 *
 * Язык, оформление, единицы меняют раз в жизни прибора. Постоянный
 * сегментированный переключатель ради такого выбора занимает строку экрана
 * навсегда и уравнивает редкое с частым; строка со значением показывает то же
 * самое одним словом, а список открывается по нажатию.
 *
 * Список — всплывающий, а не выезжающий снизу: он остаётся РЯДОМ со строкой,
 * из которой открыт, поэтому видно, что именно выбирают. Модальная панель на
 * пол-экрана ради двух вариантов прячет сам вопрос.
 */
@Composable
fun <T> ChoiceSettingRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SettingRow(
            title = title,
            subtitle = subtitle,
            value = label(selected),
            onClick = { open = true },
        )
        AppMenu(expanded = open, onDismiss = { open = false }, alignment = Alignment.BottomEnd) {
            for (option in options) {
                AppMenuItem(
                    text = label(option),
                    state = if (option == selected) "•" else null,
                    stateOn = option == selected,
                    onClick = {
                        open = false
                        if (option != selected) onSelect(option)
                    },
                )
            }
        }
    }
}

/** Строка с двоичным состоянием — настоящим переключателем, а не словом. */
@Composable
fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        enabled = enabled,
        onClick = { if (enabled) onChange(!checked) },
        trailing = { AppSwitch(checked = checked, onChange = onChange, enabled = enabled) },
    )
}

/**
 * Переключатель в языке терминала: дорожка с волосяной рамкой, включённая —
 * цвета данных.
 *
 * Своя отрисовка, а не системная, по той же причине, что и остальные элементы:
 * материаловский переключатель приносит свою палитру и свои радиусы и рядом с
 * карточками терминала читается как чужая деталь. Поведение при этом обычное —
 * нажатие по всей строке, состояние несут и цвет, и положение.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surface2
            checked -> colors.data.copy(alpha = 0.35f)
            else -> colors.surface2
        },
        animationSpec = Motion.fast(),
        label = "switchTrack",
    )
    val knobColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.muted
            checked -> colors.dataText
            else -> colors.ink2
        },
        animationSpec = Motion.fast(),
        label = "switchKnob",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) TRACK_WIDTH - KNOB_SIZE - KNOB_INSET else KNOB_INSET,
        animationSpec = Motion.fast(),
        label = "switchKnobOffset",
    )
    Box(
        modifier = modifier
            .size(width = TRACK_WIDTH, height = TRACK_HEIGHT)
            .clip(RoundedCornerShape(TRACK_HEIGHT / 2))
            .background(track)
            .border(
                LocalAppMetrics.current.border,
                if (checked && enabled) colors.data else colors.line,
                RoundedCornerShape(TRACK_HEIGHT / 2),
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(KNOB_SIZE)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}

/**
 * Шапка вложенного экрана настроек: «‹ Тревоги».
 *
 * Было две вещи сразу — крупная кнопка «← Назад» слева и чип с названием
 * страницы справа. Кнопка занимала высоту, которой на экране настроек всегда
 * не хватает, а чип повторял то, что и так написано в строке, откуда пришли.
 * Заголовок здесь один и он же кнопка возврата.
 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Действие справа: редкое и одно — иначе это уже панель, а не шапка. */
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                )
                .padding(
                    top = Dimens.space2,
                    bottom = Dimens.space2,
                    end = Dimens.space2,
                ),
        ) {
            Text(text = "‹", style = type.title, color = colors.ink2)
            Spacer(Modifier.width(Dimens.space2))
            Text(
                text = title,
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** Волосяная черта между строками одной группы. */
@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Dimens.space3)
            .height(LocalAppMetrics.current.border)
            .background(LocalAppColors.current.line),
    )
}

private val TRACK_WIDTH = 44.dp
private val TRACK_HEIGHT = 26.dp
private val KNOB_SIZE = 18.dp
private val KNOB_INSET = 4.dp
