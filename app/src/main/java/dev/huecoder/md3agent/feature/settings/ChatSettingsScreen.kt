package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.CameraFrame
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_chat), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.chat_settings_section_replies))
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_show_tokens),
                summary = stringResource(R.string.chat_settings_show_tokens_summary),
                checked = current.chat.showTokenUsage,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(showTokenUsage = value) } },
            )
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_auto_title),
                summary = stringResource(R.string.chat_settings_auto_title_summary),
                checked = current.chat.autoTitle,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(autoTitle = value) } },
            )
            SectionHeader(stringResource(R.string.chat_settings_section_context))
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_auto_compact),
                summary = stringResource(R.string.chat_settings_auto_compact_summary),
                checked = current.chat.autoCompact,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(autoCompact = value) } },
            )
            if (current.chat.autoCompact) {
                PercentSlider(R.string.chat_settings_compact_at, current.chat.compactAt, COMPACT_AT_RANGE) { value ->
                    viewModel.updateChat { it.copy(compactAt = value) }
                }
            }
            SectionHeader(stringResource(R.string.chat_settings_section_camera))
            ChoiceRow(
                title = stringResource(R.string.chat_settings_camera_frame),
                choices = CAMERA_FRAME_CHOICES,
                selected = current.chat.cameraFrame,
                onSelect = { frame -> viewModel.updateChat { it.copy(cameraFrame = frame) } },
                hint = stringResource(R.string.chat_settings_camera_frame_hint),
            )
            SectionHeader(stringResource(R.string.chat_settings_section_typing))
            TypingSettingsCard(current.chat.typing, onChange = { style -> viewModel.updateChat { it.copy(typing = style) } })
        }
    }
}

private val COMPACT_AT_RANGE = 0.5f..0.95f

private val CAMERA_FRAME_CHOICES = listOf(
    Choice(CameraFrame.FULL_SCREEN, R.string.camera_frame_full),
    Choice(CameraFrame.RATIO_16_9, R.string.camera_frame_16_9),
    Choice(CameraFrame.RATIO_4_3, R.string.camera_frame_4_3),
)
