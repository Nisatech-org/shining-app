package app.shining.settings.ui.component.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.login.ui.component.ShiningLogo
import app.shining.settings.ui.model.SettingsQuestionAnswerRowUIModel

@Composable
fun SettingsBottomSheet(
    onReportIssueClick: () -> Unit,
    onGithubRedirectClick: () -> Unit,
) {
    val questions = listOf(
        R.string.about_us_question_2 to R.string.about_us_answer_2,
        R.string.about_us_question_3 to R.string.about_us_answer_3,
        R.string.about_us_question_5 to R.string.about_us_answer_5,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShiningLogo(size = 44)
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = ShiningTheme.typography.title,
                    color = ShiningTheme.colors.ink,
                )
                Text(
                    text = stringResource(R.string.splash_screen_caption),
                    style = ShiningTheme.typography.caption,
                    color = ShiningTheme.colors.inkSecondary,
                )
            }
        }

        Text(
            text = stringResource(R.string.about_us_text),
            style = ShiningTheme.typography.body,
            color = ShiningTheme.colors.ink,
        )

        ShiningCard(
            contentPadding = PaddingValues(0.dp),
            background = ShiningTheme.colors.surfaceMuted,
        ) {
            SettingsQuestionAnswerRow(
                data = SettingsQuestionAnswerRowUIModel(
                    question = stringResource(R.string.about_us_question_1),
                    answer = stringResource(R.string.about_us_answer_1),
                ),
            ) {
                ShiningButton(
                    text = stringResource(R.string.code_github_address),
                    icon = R.drawable.ic_github,
                    size = ShiningButtonSize.Small,
                    onClick = onGithubRedirectClick,
                )
            }
            questions.forEach { (question, answer) ->
                ShiningDivider()
                SettingsQuestionAnswerRow(
                    data = SettingsQuestionAnswerRowUIModel(
                        question = stringResource(question),
                        answer = stringResource(answer),
                    )
                )
            }
        }

        ShiningButton(
            text = stringResource(R.string.about_us_send_feedback),
            icon = R.drawable.ic_bug,
            size = ShiningButtonSize.Large,
            onClick = onReportIssueClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
