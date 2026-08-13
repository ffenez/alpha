package app.radiacode.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.tag
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.MonitorCatalogue
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Certainty marker of spec §2, rendered as the quietest thing on the card: a
 * muted mono footnote next to the value. It must never compete with the value
 * itself — its whole job is to stop a derived number from reading like a
 * direct measurement.
 */
@Composable
fun EvidenceTag(evidence: Evidence, modifier: Modifier = Modifier) {
    Text(
        text = evidence.tag(MonitorCatalogue.of(LocalStrings.current.language)),
        style = LocalAppTypography.current.footnote,
        color = LocalAppColors.current.muted,
        modifier = modifier,
    )
}
