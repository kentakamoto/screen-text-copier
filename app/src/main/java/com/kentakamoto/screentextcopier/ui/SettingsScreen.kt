package com.kentakamoto.screentextcopier.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kentakamoto.screentextcopier.R
import com.kentakamoto.screentextcopier.data.CopyMode
import com.kentakamoto.screentextcopier.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val copyMode by viewModel.copyMode.collectAsState()
    val buttonOpacity by viewModel.buttonOpacity.collectAsState()
    val buttonSizeDp by viewModel.buttonSizeDp.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // --- コピー動作 ---
            Text(
                text = stringResource(R.string.copy_mode_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CopyMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = copyMode == mode,
                        onClick = { viewModel.setCopyMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = CopyMode.entries.size
                        )
                    ) {
                        Text(
                            when (mode) {
                                CopyMode.CLIPBOARD -> stringResource(R.string.clipboard_mode)
                                CopyMode.SHARE -> stringResource(R.string.share_mode)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // --- ボタン外観 ---
            Text(
                text = stringResource(R.string.button_appearance),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 透明度スライダー
            Text(
                text = "${stringResource(R.string.button_opacity)}: ${(buttonOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = buttonOpacity,
                onValueChange = { viewModel.setButtonOpacity(it) },
                valueRange = 0.2f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // サイズスライダー
            Text(
                text = "${stringResource(R.string.button_size)}: ${buttonSizeDp}dp",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = buttonSizeDp.toFloat(),
                onValueChange = { viewModel.setButtonSizeDp(it.toInt()) },
                valueRange = 40f..80f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
