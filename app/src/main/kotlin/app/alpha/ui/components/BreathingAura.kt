package app.alpha.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import app.alpha.ui.theme.LocalAppColors
import kotlin.math.PI
import kotlin.math.cos

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
 * ## Как оно дышит
 *
 * Фаза копится по кадрам, а форма вдоха — косинус: живое дыхание ускоряется к
 * середине и замирает на концах, и то же самое даёт `(1 − cos)/2` без кривых
 * Безье. Накопленная фаза важна отдельно: в Поиске период — ПОКАЗАНИЕ
 * ([app.alpha.ui.logic.SearchPulse]), он меняется с каждым отсчётом, и
 * перезапуск анимации на каждой смене рвал бы дыхание скачком. Здесь смена
 * периода меняет только скорость набора фазы — вдох продолжается с того же
 * места, как у тона, который скользит по частоте.
 *
 * Не анимирует измеренное: само число меняется шагом, дышит подсветка вокруг
 * него. Системное «отключить анимацию» гасит движение, но не сообщение —
 * свечение остаётся на месте и не дышит.
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
     * Период вдоха-выдоха (полный цикл), мс. По умолчанию [PERIOD_MILLIS] —
     * признак жизни; Поиск передаёт сюда период от близости к точке отсчёта.
     */
    periodMillis: Int = PERIOD_MILLIS,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val inspection = LocalInspectionMode.current
    val moving = live && !inspection && rememberMotionAllowed()
    val period by rememberUpdatedState(periodMillis.coerceAtLeast(1))
    var amount by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(moving) {
        if (!moving) {
            amount = 0f
            return@LaunchedEffect
        }
        // Фаза в долях цикла; скорость перечитывается каждый кадр, поэтому
        // смена периода не рвёт дыхание.
        var phase = 0.0
        // «Бесконечный» кадровый цикл — через политику бесконечных анимаций:
        // в тестах она его глушит, и композиция может стать idle.
        var lastNanos = withInfiniteAnimationFrameNanos { it }
        while (true) {
            val nowNanos = withInfiniteAnimationFrameNanos { it }
            phase += (nowNanos - lastNanos) / 1e6 / period
            lastNanos = nowNanos
            amount = ((1.0 - cos(2.0 * PI * phase)) / 2.0).toFloat()
        }
    }
    Box(
        modifier = modifier.drawBehind {
            // Замерший поток — застывшее свечение: движение здесь означает
            // «данные идут», и врать им нельзя.
            val radius = size.minDimension * (0.60f + 0.18f * amount)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        // Замерший поток оставляет тусклое свечение: элемент
                        // виден, но не движется — это и есть сообщение.
                        // На светлой подложке та же прозрачность читается
                        // слабее, поэтому там свечение плотнее.
                        tint.copy(
                            alpha = if (live) {
                                if (colors.isDark) 0.18f + 0.22f * amount else 0.12f + 0.18f * amount
                            } else {
                                if (colors.isDark) 0.07f else 0.05f
                            },
                        ),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height * 0.40f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width / 2f, size.height * 0.40f),
            )
        },
        content = { content() },
    )
}

/**
 * Период дыхания, мс.
 *
 * **Инженерный параметр**: 2,6 с на полный вдох-выдох — спокойное дыхание.
 * Быстрее подсветка читается как тревога, медленнее перестаёт восприниматься
 * как движение.
 */
const val PERIOD_MILLIS = 2_600
