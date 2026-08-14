package app.radiacode.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.data.db.ProfileEntity
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.RadioMark
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Поздняя правка профиля сессии (spec §20). Диалог честно предупреждает, что
 * меняется не подпись, а принадлежность измерений: сессия перейдёт в
 * статистику другого профиля.
 */
@Composable
internal fun SessionProfileDialog(
    startedAt: Long,
    profileId: Long?,
    profiles: List<ProfileEntity>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = strings.sessionProfileTitle, style = type.title, color = colors.ink)
                Text(
                    text = strings.sessionProfileBody(
                        HistoryFormat.dayTime(startedAt, System.currentTimeMillis(), s = h),
                    ),
                    style = type.bodySmall,
                    color = colors.muted,
                )
                ProfileTree.visible(profiles).forEach { profile ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPick(profile.id) },
                            ),
                    ) {
                        RadioMark(profile.id == profileId)
                        Text(
                            text = ProfileTree.displayName(profile, profiles),
                            style = type.label,
                            color = colors.ink,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Dimens.touchTarget)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPick(null) },
                        ),
                ) {
                    RadioMark(profileId == null)
                    Text(text = strings.noProfile, style = type.label, color = colors.ink)
                }
                AppButton(text = strings.cancel, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
