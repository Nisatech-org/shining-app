package app.shining.profile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.Dot
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.ui.theme.ShiningBlue
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.domain.util.toLocalNumber
import app.shining.profile.api.dto.nurture.toSamadNurtureProfile
import app.shining.profile.ui.fixture.profileNurtureProfileDto
import app.shining.profile.ui.model.ProfileScreenUIModel
import app.shining.profile.ui.model.toProfileScreenUIModel

private val CardInk = Color.White

/**
 * The account as a blue "card": credit in large type, the student number where a card
 * number would be, and the owner's name.
 */
@Composable
fun CreditHeroCard(
    data: ProfileScreenUIModel?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to ShiningBlue,
                    0.55f to Color(0xFF4A7BFF),
                    1f to Color(0xFF7FA2FF),
                )
            )
    ) {
        // Two soft circles for depth, like light on a plastic card.
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.BottomStart)
                .offset(x = 60.dp, y = 70.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.profile_credit_title),
                    style = ShiningTheme.typography.label,
                    color = CardInk.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Shining",
                    style = ShiningTheme.typography.subtitle,
                    color = CardInk,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Dot(color = CardInk, size = 6.dp)
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = data?.credit?.toGroupedLocalNumber() ?: "–",
                style = ShiningTheme.typography.display,
                color = CardInk,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.student_number),
                            style = ShiningTheme.typography.caption,
                            color = CardInk.copy(alpha = 0.65f),
                        )
                        Text(
                            text = data?.userName?.chunked(3)?.joinToString(" ")?.toLocalNumber() ?: "",
                            // Digit groups read left to right, like a card number.
                            style = ShiningTheme.typography.title.copy(textDirection = TextDirection.Ltr),
                            color = CardInk,
                        )
                    }
                    Text(
                        text = data?.fullName.orEmpty(),
                        style = ShiningTheme.typography.bodyStrong,
                        color = CardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun CreditHeroCardPreview() {
    ShiningTheme {
        CreditHeroCard(
            data = profileNurtureProfileDto.toSamadNurtureProfile().toProfileScreenUIModel(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
