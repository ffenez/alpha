package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Place picker (SPEC «Personal baseline»: baseline per place, selected
 * manually). The active row carries a filled radio marker — state is never
 * color alone. Creating a place is inline; management (rename/delete) lives
 * in Настройки.
 */
@Composable
fun PlacePickerDialog(
    places: List<PlaceEntity>,
    activePlaceId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text("Место измерения", style = type.title, color = colors.ink)
                Text(
                    text = "У каждого места свой обычный фон — выберите, где сейчас прибор.",
                    style = type.bodySmall,
                    color = colors.muted,
                )

                places.forEach { place ->
                    val isActive = place.id == activePlaceId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSelect(place.id)
                                onDismiss()
                            },
                    ) {
                        RadioMark(isActive)
                        Text(
                            text = place.name,
                            style = type.label,
                            color = colors.ink,
                        )
                    }
                }

                if (adding) {
                    AppTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "название места",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = "Добавить",
                            primary = true,
                            enabled = newName.isNotBlank(),
                            onClick = {
                                onCreate(newName.trim())
                                newName = ""
                                adding = false
                            },
                        )
                        AppButton(text = "Отмена", onClick = { adding = false })
                    }
                } else {
                    AppButton(text = "+ Новое место", onClick = { adding = true })
                }
            }
        }
    }
}

/** Radio marker: hairline ring, filled with data teal when selected. */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(14.dp)
            .border(
                width = Dimens.border,
                color = if (selected) colors.data else colors.line,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(8.dp).background(colors.data, CircleShape))
        }
    }
}
