package app.shining.debug

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.theme.ShiningTheme

/** See [DebugViewModel]. Laid out left to right, since it's English and mostly code-like text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(viewModel: DebugViewModel, navController: NavController) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(true) { viewModel.refresh() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ShiningTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            ScreenHeader(title = "Debug", subtitle = "push, server and background checks", onBackClick = { navController.popBackStack() })

            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShiningButton(text = "Copy log", onClick = {
                    // The log part is read fresh from the file, so a check that just finished is in it.
                    clipboard.setText(AnnotatedString(viewModel.copyText()))
                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                }, size = ShiningButtonSize.Small)
                ShiningButton(text = "Share log", onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Shining debug log")
                        .putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                    context.startActivity(Intent.createChooser(send, "Share the Shining log"))
                }, size = ShiningButtonSize.Small)
                ShiningButton(text = "Run menu check now", onClick = viewModel::runMenuCheck, style = ShiningButtonStyle.Secondary, size = ShiningButtonSize.Small)
                ShiningButton(text = "Run reminder check now", onClick = viewModel::runReminderCheck, style = ShiningButtonStyle.Secondary, size = ShiningButtonSize.Small)
                ShiningButton(text = "Refresh", onClick = viewModel::refresh, style = ShiningButtonStyle.Secondary, size = ShiningButtonSize.Small)
                ShiningButton(text = "Clear log", onClick = viewModel::clearLog, style = ShiningButtonStyle.Danger, size = ShiningButtonSize.Small)
            }

            ShiningCard(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.info.value.forEach { (key, value) ->
                    Column {
                        Text(key, style = ShiningTheme.typography.label, color = ShiningTheme.colors.inkSecondary)
                        SelectionContainer { Text(value, style = ShiningTheme.typography.body, color = ShiningTheme.colors.ink) }
                    }
                }
            }

            Text(
                "Log (newest first)",
                style = ShiningTheme.typography.label,
                color = ShiningTheme.colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            ShiningCard(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                val lines = viewModel.log.value.lines().filter { it.isNotBlank() }.asReversed().take(400)
                SelectionContainer {
                    Text(
                        text = lines.joinToString("\n").ifEmpty { "(empty)" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = ShiningTheme.colors.ink,
                    )
                }
            }
        }
    }
}
