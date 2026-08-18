package app.alpha.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.data.db.ProfileEntity
import app.alpha.ui.logic.ProfileTree
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.MonitorCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Profile picker (spec §3): the profile decides which baseline the reading is
 * compared against, so the dialog says what picking one does and offers the
 * way back to automatic — a manual choice sticks until the user cancels it
 * (spec §3.2).
 *
 * Nesting is shown by indentation only; the active row also carries a filled
 * radio marker, so state is never colour alone.
 */
@Composable
fun ProfilePickerDialog(
    profiles: List<ProfileEntity>,
    activeProfileId: Long?,
    manual: Boolean,
    contextWording: String,
    onSelect: (Long) -> Unit,
    onReturnToAuto: () -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val visible = ProfileTree.visible(profiles)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(t.pickerTitle, style = type.title, color = colors.ink)
                Hint(
                    text = t.pickerSubtitle(contextWording),
                    style = type.bodySmall,
                    color = colors.muted,
                )

                visible.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        nested = profile.parentId != null,
                        selected = profile.id == activeProfileId,
                        onClick = {
                            onSelect(profile.id)
                            onDismiss()
                        },
                    )
                }

                if (manual) {
                    AppButton(
                        text = t.pickerReturnToAuto,
                        onClick = {
                            onReturnToAuto()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(
                        text = t.pickerAutoNote,
                    )
                }

                if (adding) {
                    AppTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = strings.profileNameHint,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = strings.add,
                            primary = true,
                            enabled = newName.isNotBlank(),
                            onClick = {
                                onCreate(newName.trim())
                                newName = ""
                                adding = false
                            },
                        )
                        AppButton(text = strings.cancel, onClick = { adding = false })
                    }
                } else {
                    AppButton(text = t.pickerNewProfile, onClick = { adding = true })
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileEntity,
    nested: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .padding(start = if (nested) Dimens.space4 else 0.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        RadioMark(selected)
        Text(
            text = listOf(profile.icon, profile.name).filter { it.isNotBlank() }.joinToString(" "),
            style = type.label,
            color = colors.ink,
        )
    }
}

/** Radio marker: hairline ring, filled with data teal when selected. */
@Composable
fun CheckMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(18.dp)
            .border(
                width = LocalAppMetrics.current.border,
                color = if (selected) colors.data else colors.line,
                shape = RoundedCornerShape(LocalAppMetrics.current.radiusChip),
            )
            .background(
                color = if (selected) colors.data else Color.Transparent,
                shape = RoundedCornerShape(LocalAppMetrics.current.radiusChip),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // A tick drawn from two strokes: the same thin-line language as
            // the tab icons, at the size a finger aims for.
            Canvas(Modifier.size(11.dp)) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(size.width * 0.1f, size.height * 0.55f)
                    lineTo(size.width * 0.4f, size.height * 0.85f)
                    lineTo(size.width * 0.92f, size.height * 0.18f)
                }
                drawPath(path, color = colors.onData, style = stroke)
            }
        }
    }
}

/** Round single-choice mark; [CheckMark] is its multi-choice sibling. */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(14.dp)
            .border(
                width = LocalAppMetrics.current.border,
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
