package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens

/**
 * Console-style single-line input: 2dp frame, radius 0, surface-2 fill,
 * accent block cursor. Muted [placeholder] shows while empty.
 */
@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = type.label.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = PixelDimens.touchTarget)
                    .fillMaxWidth()
                    .background(colors.surface2)
                    .border(2.dp, colors.frame)
                    .padding(horizontal = PixelDimens.space3, vertical = PixelDimens.space2),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = type.label, color = colors.textMuted)
                }
                innerTextField()
            }
        },
    )
}
