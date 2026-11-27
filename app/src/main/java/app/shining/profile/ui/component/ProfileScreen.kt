package app.shining.profile.ui.component

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
import androidx.compose.ui.unit.sp
import app.shining.R
import app.shining.domain.ui.component.Dot
import app.shining.domain.ui.component.NotchCorner
import app.shining.domain.ui.component.NotchGap
import app.shining.domain.ui.component.NotchedCard
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.profile.api.dto.nurture.toSamadNurtureProfile
import app.shining.profile.ui.fixture.profileNurtureProfileDto
import app.shining.profile.ui.model.ProfileScreenUIModel
import app.shining.profile.ui.model.toProfileScreenUIModel

/**
 * The account as a lime "card": credit in large type and the owner's name, with the button to add
 * credit sitting in a notch cut out of the card's bottom end corner.
 */
@Composable
fun CreditHeroCard(
    data: ProfileScreenUIModel?,
    onIncreaseCreditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = ShiningTheme.colors.onBrand
    NotchedCard(
        notchWidth = 176.dp,
        notchHeight = 56.dp + NotchGap,
        corner = NotchCorner.BottomEnd,
        modifier = modifier,
        notch = {
            ShiningButton(
                text = stringResource(R.string.increase_credit_button_label),
                icon = R.drawable.ic_add,
                onClick = onIncreaseCreditClick,
                size = ShiningButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.profile_credit_title),
                style = ShiningTheme.typography.label,
                color = ink.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Dot(color = ink, size = 6.dp)
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = "Shining", style = ShiningTheme.typography.subtitle, color = ink)
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = data?.credit?.toGroupedLocalNumber() ?: "–",
            style = ShiningTheme.typography.display.copy(fontSize = 44.sp, lineHeight = 56.sp),
            color = ink,
        )
        Spacer(modifier = Modifier.size(20.dp))
        Text(
            text = data?.fullName.orEmpty(),
            style = ShiningTheme.typography.subtitle,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Stays clear of the notch.
            modifier = Modifier.padding(end = 168.dp),
        )
    }
}

@Preview
@Composable
private fun CreditHeroCardPreview() {
    ShiningTheme {
        CreditHeroCard(
            data = profileNurtureProfileDto.toSamadNurtureProfile().toProfileScreenUIModel(),
            onIncreaseCreditClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
