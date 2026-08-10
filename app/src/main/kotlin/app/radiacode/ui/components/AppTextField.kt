package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Single-line input: surface-2 fill, hairline border, 10dp radius, data-teal
 * cursor. Muted [placeholder] shows while empty.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val textStyle = if (numeric) type.value else type.body
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.data),
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        modifier = modifier,
        decorationBox = { innerTextField ->
            val shape = RoundedCornerShape(Dimens.radiusButton)
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = Dimens.touchTarget)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface2)
                    .border(Dimens.border, colors.line, shape)
                    .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = textStyle, color = colors.muted)
                }
                innerTextField()
            }
        },
    )
}
