package app.alpha.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.alpha.ui.theme.LocalAppColors

/**
 * Дыхание: пока идут измерения, за числом дышит свечение.
 *
 * ## Что это говорит
 *
 * Ровно одно: прибор жив и данные приходят. Поток замолчал — дыхание
 * останавливается, и это видно раньше, чем человек прочитает чип связи.
 * Радиус и цвет свечения берутся от отношения к обычному фону места, поэтому
 * оно ещё и подкрашивает главное число тем же смыслом, что и его цвет.
 *
 * ## Чего это не делает
 *
 * Не анимирует измеренное: само число меняется шагом, дышит подсветка вокруг
 * него. Период по умолчанию постоянный ([PERIOD_MILLIS]) — это признак жизни, а
 * не показание. На экране Поиска тот же компонент получает период от
 * [app.alpha.ui.logic.SearchPulse], и там период — ПОКАЗАНИЕ близости; какой
 * это случай, видно по [periodMillis] на месте вызова.
 *
 * ## Светлые оформления
 *
 * На бумажной подложке та же прозрачность читается слабее, поэтому там
 * свечение плотнее: дыхание обязано быть видно в любом оформлении, иначе оно
 * не сообщение, а украшение для одной темы.
 */
@Composable
fun BreathingAura(
    /** Идут ли измерения прямо сейчас. */
    live: Boolean,
    /** Цвет свечения — тот же, что у главного числа. */
    tint: Color = LocalAppColors.current.ok,
    /**
     * Период вдоха-выдоха, мс. По умолчанию [PERIOD_MILLIS] — признак жизни;
     * Поиск передаёт сюда период от близости к точке отсчёта.
     */
    periodMillis: Int = PERIOD_MILLIS,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    // Системное «отключить анимацию» гасит движение, но не сообщение: при
    // выключенном движении свечение остаётся на месте, просто не дышит.
    val moving = live && rememberMotionAllowed()
    val breath = rememberInfiniteTransition(label = "breath")
    val phase by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathPhase",
    )
    Box(
        modifier = modifier.drawBehind {
            // Замерший поток — застывшее свечение: движение здесь означает
            // «данные идут», и врать им нельзя.
            val amount = if (moving) phase else 0f
            val radius = size.minDimension * (0.62f + 0.16f * amount)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        // Замерший поток оставляет тусклое свечение: элемент
                        // виден, но не движется — это и есть сообщение.
                        // На светлой подложке та же прозрачность читается
                        // слабее, поэтому там свечение плотнее.
                        tint.copy(
                            alpha = if (live) {
                                if (colors.isDark) 0.16f + 0.20f * amount else 0.10f + 0.16f * amount
                            } else {
                                if (colors.isDark) 0.07f else 0.05f
                            },
                        ),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width / 2f, size.height * 0.42f),
            )
        },
        content = { content() },
    )
}

/**
 * Период дыхания, мс.
 *
 * **Инженерный параметр**: 2,6 с — спокойный вдох-выдох. Быстрее подсветка
 * читается как тревога, медленнее перестаёт восприниматься как движение.
 */
const val PERIOD_MILLIS = 2_600
