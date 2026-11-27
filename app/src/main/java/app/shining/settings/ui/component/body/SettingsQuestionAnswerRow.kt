package app.shining.settings.ui.component.body

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.settings.ui.model.SettingsQuestionAnswerRowUIModel

/** A question that expands to show its answer, plus optional extra content. */
@Composable
fun SettingsQuestionAnswerRow(
    data: SettingsQuestionAnswerRowUIModel,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.question,
                style = ShiningTheme.typography.bodyStrong,
                color = ShiningTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = ShiningTheme.colors.inkSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                Text(
                    text = data.answer,
                    style = ShiningTheme.typography.body,
                    color = ShiningTheme.colors.inkSecondary,
                )
                content?.let {
                    Spacer(modifier = Modifier.size(12.dp))
                    it()
                }
            }
        }
    }
}
