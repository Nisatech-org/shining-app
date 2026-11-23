package app.shining.reserveinfo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.push.MenuState
import app.shining.push.MenuStatusRow

/** Next week's menus, per vendor: out, partly out, or not yet. Tapping it opens Reserve. */
@Composable
fun MenuStatusCard(
    rows: List<MenuStatusRow>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShiningCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji = R.drawable.emoji_calendar, size = 36.dp, emojiSize = 20.dp)
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.home_menu_status_title),
                style = ShiningTheme.typography.title,
                color = ShiningTheme.colors.ink,
            )
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name,
                    style = ShiningTheme.typography.subtitle,
                    color = ShiningTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                StatusPill(
                    text = row.detail,
                    tone = when (row.state) {
                        MenuState.Published -> PillTone.Positive
                        MenuState.Partial -> PillTone.Warning
                        MenuState.Waiting -> PillTone.Neutral
                    },
                    icon = if (row.state == MenuState.Published) R.drawable.ic_check else R.drawable.ic_clock,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MenuStatusCardPreview() {
    ShiningTheme {
        MenuStatusCard(
            rows = listOf(
                MenuStatusRow("سلف", MenuState.Published, "از سه‌شنبه ۱۴:۰۳"),
                MenuStatusRow("کلانا", MenuState.Partial, "۲ روز از ۵"),
                MenuStatusRow("یونی‌فود", MenuState.Waiting, "معمولاً چهارشنبه حدود ۱۰:۰۰"),
            ),
            onClick = {},
        )
    }
}
