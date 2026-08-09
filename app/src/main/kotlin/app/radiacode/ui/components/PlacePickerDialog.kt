package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.data.db.PlaceEntity
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens

/**
 * Place picker (SPEC «Personal baseline»: baseline per place, selected
 * manually). Each place row shows an accent block marker on the active row —
 * state is never color alone. Creating a place is inline; management
 * (rename/delete) lives in Настройки.
 */
@Composable
fun PlacePickerDialog(
    places: List<PlaceEntity>,
    activePlaceId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        PixelBox(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                Text("МЕСТО ИЗМЕРЕНИЯ", style = type.label, color = colors.text)
                Text(
                    text = "У каждого места свой обычный фон — выберите, где сейчас прибор.",
                    style = type.bodySmall,
                    color = colors.textMuted,
                )

                places.forEach { place ->
                    val isActive = place.id == activePlaceId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = PixelDimens.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSelect(place.id)
                                onDismiss()
                            },
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (isActive) colors.accent else colors.surface2),
                        )
                        Text(
                            text = place.name.uppercase(),
                            style = type.label,
                            color = if (isActive) colors.accent else colors.text,
                        )
                    }
                }

                if (adding) {
                    PixelTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "название места",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                        PixelButton(
                            text = "ДОБАВИТЬ",
                            primary = true,
                            enabled = newName.isNotBlank(),
                            onClick = {
                                onCreate(newName.trim())
                                newName = ""
                                adding = false
                            },
                        )
                        PixelButton(text = "ОТМЕНА", onClick = { adding = false })
                    }
                } else {
                    PixelButton(text = "+ НОВОЕ МЕСТО", onClick = { adding = true })
                }
            }
        }
    }
}
